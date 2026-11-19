use mailx_domain::models::*;
use mailx_utils::secret::SecretString;
use mailx_utils::{build_aliases_query_path, validate_access_key};
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use std::sync::{Arc, RwLock};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MailxError {
    InvalidBaseUrl(String),
    InvalidAccessKey(String),
    Unauthorized(String),
    RateLimited(String),
    TotpRequired(String),
    StepUpRequired(String),
    ApiError { status: u16, message: String },
    NetworkError(String),
    SerializationError(String),
}

impl MailxError {
    pub fn error_code(&self) -> Option<i64> {
        match self {
            MailxError::TotpRequired(_) => Some(70001),
            MailxError::StepUpRequired(_) => Some(80001),
            _ => None,
        }
    }
}

impl std::fmt::Display for MailxError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            MailxError::InvalidBaseUrl(msg) => write!(f, "Invalid server URL: {}", msg),
            MailxError::InvalidAccessKey(msg) => write!(f, "{}", msg),
            MailxError::Unauthorized(msg) => write!(f, "Session unauthorized: {}", msg),
            MailxError::RateLimited(msg) => write!(f, "Rate limit exceeded: {}", msg),
            MailxError::TotpRequired(msg) => write!(f, "{}", msg),
            MailxError::StepUpRequired(msg) => write!(f, "{}", msg),
            MailxError::ApiError { status, message } => {
                write!(f, "API Error ({}): {}", status, message)
            }
            MailxError::NetworkError(msg) => write!(f, "Network error: {}", msg),
            MailxError::SerializationError(msg) => write!(f, "Data parsing error: {}", msg),
        }
    }
}

#[derive(Deserialize)]
struct ApiErrorBody {
    error: Option<String>,
    message: Option<String>,
    code: Option<i64>,
}

#[derive(Clone)]
pub struct MailxClient {
    base_url: String,
    agent: ureq::Agent,
    access_key: Arc<RwLock<Option<SecretString>>>,
    bearer_token: Arc<RwLock<Option<SecretString>>>,
}

impl MailxClient {
    pub fn normalize_base_url(raw: &str) -> String {
        let mut s = raw.trim().trim_end_matches('/').to_string();
        for suffix in [
            "/v1/api/authenticate",
            "/api/v1/authenticate",
            "/v1/login",
            "/v1/api",
            "/api/v1",
            "/v1",
            "/api",
            "/login",
        ] {
            if s.to_ascii_lowercase().ends_with(suffix) {
                let new_len = s.len() - suffix.len();
                s.truncate(new_len);
                s = s.trim_end_matches('/').to_string();
            }
        }

        match s.to_ascii_lowercase().as_str() {
            "https://mailx.net" | "https://www.mailx.net" | "https://app.mailx.net" => {
                "https://api.mailx.net".to_string()
            }
            "http://mailx.net" | "http://www.mailx.net" | "http://app.mailx.net" => {
                "http://api.mailx.net".to_string()
            }
            _ => s,
        }
    }

    pub fn new(base_url: &str, allow_insecure_localhost: bool) -> Result<Self, MailxError> {
        let trimmed = Self::normalize_base_url(base_url);
        if !trimmed.starts_with("https://") {
            let is_localhost = trimmed.starts_with("http://localhost")
                || trimmed.starts_with("http://127.0.0.1")
                || trimmed.starts_with("http://10.0.2.2");
            if !(allow_insecure_localhost && is_localhost) {
                return Err(MailxError::InvalidBaseUrl(
                    "Only HTTPS server URLs are supported for security reasons.".to_string(),
                ));
            }
        }
        let agent = ureq::AgentBuilder::new()
            .timeout_connect(std::time::Duration::from_secs(10))
            .timeout_read(std::time::Duration::from_secs(20))
            .timeout_write(std::time::Duration::from_secs(20))
            .build();
        Ok(Self {
            base_url: trimmed,
            agent,
            access_key: Arc::new(RwLock::new(None)),
            bearer_token: Arc::new(RwLock::new(None)),
        })
    }

    pub fn base_url(&self) -> &str {
        &self.base_url
    }

    pub fn set_credentials(&self, access_key: Option<String>, bearer_token: Option<String>) {
        if let Ok(mut ak) = self.access_key.write() {
            *ak = access_key.filter(|s| !s.is_empty()).map(SecretString::new);
        }
        if let Ok(mut bt) = self.bearer_token.write() {
            *bt = bearer_token
                .filter(|s| !s.is_empty())
                .map(SecretString::new);
        }
    }

    pub fn current_token(&self) -> Option<String> {
        self.bearer_token
            .read()
            .ok()
            .and_then(|g| g.as_ref().map(|s| s.expose().to_string()))
    }

    pub fn is_web_cookie_session(&self) -> bool {
        self.current_token()
            .map(|t| t.starts_with("cookie:"))
            .unwrap_or(false)
    }

    pub fn resolve_scoped_path(path: &str, is_cookie_session: bool) -> String {
        if is_cookie_session {
            if path == "/v1/api/logout" {
                return "/v1/user/logout".to_string();
            }
            if let Some(rest) = path.strip_prefix("/v1/api/") {
                return format!("/v1/{}", rest);
            }
        }
        path.to_string()
    }

    fn candidate_urls(&self, path: &str) -> Vec<String> {
        let mut urls = Vec::new();
        let primary = format!("{}{}", self.base_url, path);
        urls.push(primary);

        if let Some(rest) = self.base_url.strip_prefix("https://") {
            let is_local = rest.starts_with("localhost") || rest.starts_with("127.");
            if !rest.starts_with("api.") && !is_local {
                let host_without_www = rest.strip_prefix("www.").unwrap_or(rest);
                let api_base = format!("https://api.{}", host_without_www);
                let candidate = format!("{}{}", api_base, path);
                if !urls.contains(&candidate) {
                    urls.push(candidate);
                }
            }
        }

        if let Some(tail) = path.strip_prefix("/v1/api/") {
            let alt_path = format!("/api/v1/{}", tail);
            let alt_primary = format!("{}{}", self.base_url, alt_path);
            if !urls.contains(&alt_primary) {
                urls.push(alt_primary);
            }
        }

        urls
    }

    fn strip_html_tags(input: &str) -> String {
        let mut out = String::with_capacity(input.len());
        let mut in_tag = false;
        for ch in input.chars() {
            match ch {
                '<' => in_tag = true,
                '>' => {
                    in_tag = false;
                    out.push(' ');
                }
                _ if !in_tag => out.push(ch),
                _ => {}
            }
        }
        out.split_whitespace().collect::<Vec<_>>().join(" ")
    }

    fn is_html_body(raw: &str) -> bool {
        let lower = raw.trim_start().to_ascii_lowercase();
        lower.starts_with("<!doctype html")
            || lower.starts_with("<html")
            || lower.contains("<head>")
            || lower.contains("<body>")
    }

    fn parse_error_body(status: u16, raw: &str) -> MailxError {
        let body_struct = serde_json::from_str::<ApiErrorBody>(raw).ok();
        let code = body_struct.as_ref().and_then(|b| b.code);
        let parsed = body_struct
            .and_then(|b| b.error.or(b.message))
            .unwrap_or_else(|| {
                let trimmed = raw.trim();
                if trimmed.is_empty() {
                    format!("HTTP {}", status)
                } else if Self::is_html_body(trimmed) {
                    let clean_text = Self::strip_html_tags(trimmed);
                    if status == 405 {
                        let detail = if clean_text.is_empty() {
                            "405 Not Allowed"
                        } else {
                            &clean_text
                        };
                        format!(
                            "Server rejected POST request ({}). Ensure you are using the API server URL (https://api.mailx.net) instead of the web frontend.",
                            detail
                        )
                    } else if clean_text.is_empty() {
                        format!("HTTP {}", status)
                    } else {
                        clean_text
                    }
                } else {
                    trimmed.to_string()
                }
            });

        if code == Some(70001) {
            return MailxError::TotpRequired(parsed);
        }
        if code == Some(80001) {
            return MailxError::StepUpRequired(parsed);
        }

        match status {
            401 => MailxError::Unauthorized(parsed),
            429 => MailxError::RateLimited(parsed),
            _ => MailxError::ApiError {
                status,
                message: parsed,
            },
        }
    }

    #[cfg(feature = "network")]
    fn extract_cookie_by_name(resp: &ureq::Response, cookie_name: &str) -> Option<String> {
        let prefix = format!("{}=", cookie_name);
        for val in resp.all("set-cookie") {
            for part in val.split(';') {
                let trimmed = part.trim();
                if let Some(cookie_val) = trimmed.strip_prefix(&prefix) {
                    if !cookie_val.is_empty() {
                        return Some(cookie_val.to_string());
                    }
                }
            }
        }
        if let Some(val) = resp.header("Set-Cookie") {
            for part in val.split(';') {
                let trimmed = part.trim();
                if let Some(cookie_val) = trimmed.strip_prefix(&prefix) {
                    if !cookie_val.is_empty() {
                        return Some(cookie_val.to_string());
                    }
                }
            }
        }
        None
    }

    #[cfg(feature = "network")]
    fn extract_authn_cookie(resp: &ureq::Response) -> Option<String> {
        Self::extract_cookie_by_name(resp, "authn")
    }

    #[cfg(feature = "network")]
    pub fn check_livez(&self) -> Result<bool, MailxError> {
        let url = format!("{}/livez", self.base_url);
        let resp = self.agent.get(&url)
            .timeout(std::time::Duration::from_secs(10))
            .call();
        match resp {
            Ok(r) => Ok((200..300).contains(&r.status())),
            Err(ureq::Error::Status(code, r)) => {
                let body = r.into_string().unwrap_or_default();
                Err(Self::parse_error_body(code, &body))
            }
            Err(e) => Err(MailxError::NetworkError(e.to_string())),
        }
    }

    #[cfg(feature = "network")]
    pub fn login_with_credentials(
        &self,
        email: &str,
        password: &str,
        otp: &str,
    ) -> Result<AuthResponse, MailxError> {
        let clean_email = email.trim();
        if clean_email.is_empty() || password.is_empty() {
            return Err(MailxError::InvalidAccessKey(
                "Email address and password cannot be empty.".to_string(),
            ));
        }
        let payload = serde_json::json!({
            "email": clean_email,
            "password": password,
            "otp": otp.trim(),
            "remember": true
        })
        .to_string();

        let candidates = self.candidate_urls("/v1/login");
        let mut last_err: Option<MailxError> = None;

        for (idx, url) in candidates.iter().enumerate() {
            let resp = self.agent.post(url)
                .set("Content-Type", "application/json")
                .set("Accept", "application/json")
                .timeout(std::time::Duration::from_secs(15))
                .send_string(&payload);

            match resp {
                Ok(r) => {
                    let authn_cookie = Self::extract_authn_cookie(&r).ok_or_else(|| {
                        MailxError::Unauthorized(
                            "Server did not return a session cookie (authn).".to_string(),
                        )
                    })?;
                    let _ = r.into_string();
                    let cookie_token = format!("cookie:{}", authn_cookie);
                    self.set_credentials(None, Some(cookie_token.clone()));

                    match self.fetch_defaults() {
                        Ok(def) => {
                            return Ok(AuthResponse {
                                token: cookie_token,
                                domain: def.domain,
                                domains: def.domains,
                                custom_domains: def.custom_domains,
                                recipient: def.recipient,
                                recipients: def.recipients,
                                alias_format: def.alias_format,
                                from_name: def.from_name,
                                log_issues: def.log_issues,
                                remove_header: def.remove_header,
                                list_unsubscribe_behaviour: def.list_unsubscribe_behaviour,
                                block_trackers: def.block_trackers,
                            });
                        }
                        Err(_) => {
                            return Ok(AuthResponse {
                                token: cookie_token,
                                domain: "mailx.net".to_string(),
                                domains: vec!["mailx.net".to_string()],
                                custom_domains: Vec::new(),
                                recipient: clean_email.to_string(),
                                recipients: vec![clean_email.to_string()],
                                alias_format: "words".to_string(),
                                from_name: String::new(),
                                log_issues: false,
                                remove_header: false,
                                list_unsubscribe_behaviour: 0,
                                block_trackers: false,
                            });
                        }
                    }
                }
                Err(ureq::Error::Status(code, r)) => {
                    let body = r.into_string().unwrap_or_default();
                    let err = Self::parse_error_body(code, &body);
                    if idx + 1 < candidates.len() && (code == 404 || code == 405) {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
                Err(e) => {
                    let err = MailxError::NetworkError(e.to_string());
                    if idx + 1 < candidates.len() {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
            }
        }

        Err(last_err.unwrap_or_else(|| {
            MailxError::NetworkError("Could not reach the API server.".to_string())
        }))
    }

    #[cfg(feature = "network")]
    pub fn passkey_login_begin(&self, email: &str) -> Result<serde_json::Value, MailxError> {
        let clean_email = email.trim();
        let (path, payload, is_discoverable) = if clean_email.is_empty() {
            (
                "/v1/login/passkey/begin",
                serde_json::json!({ "remember": true }).to_string(),
                true,
            )
        } else {
            (
                "/v1/login/begin",
                serde_json::json!({ "email": clean_email, "remember": true }).to_string(),
                false,
            )
        };

        let candidates = self.candidate_urls(path);
        let mut last_err: Option<MailxError> = None;

        for (idx, url) in candidates.iter().enumerate() {
            let resp = self.agent.post(url)
                .set("Content-Type", "application/json")
                .set("Accept", "application/json")
                .timeout(std::time::Duration::from_secs(15))
                .send_string(&payload);

            match resp {
                Ok(r) => {
                    let ceremony_cookie = Self::extract_cookie_by_name(&r, "authntemp")
                        .unwrap_or_default();
                    let text = r
                        .into_string()
                        .map_err(|e| MailxError::NetworkError(e.to_string()))?;
                    let options: serde_json::Value = serde_json::from_str(&text)
                        .map_err(|e| MailxError::SerializationError(e.to_string()))?;
                    return Ok(serde_json::json!({
                        "options": options,
                        "ceremony_cookie": ceremony_cookie,
                        "discoverable": is_discoverable
                    }));
                }
                Err(ureq::Error::Status(code, r)) => {
                    let body = r.into_string().unwrap_or_default();
                    let err = Self::parse_error_body(code, &body);
                    if idx + 1 < candidates.len() && (code == 404 || code == 405) {
                        last_err = Some(err);
                        continue;
                    }

                    if !is_discoverable {
                        return self.passkey_login_begin("");
                    }
                    return Err(err);
                }
                Err(e) => {
                    let err = MailxError::NetworkError(e.to_string());
                    if idx + 1 < candidates.len() {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
            }
        }

        Err(last_err.unwrap_or_else(|| {
            MailxError::NetworkError("Could not reach the API server.".to_string())
        }))
    }

    #[cfg(feature = "network")]
    pub fn passkey_login_finish(
        &self,
        ceremony_cookie: &str,
        credential_json: &str,
        discoverable: bool,
        fallback_email: &str,
    ) -> Result<AuthResponse, MailxError> {
        let path = if discoverable {
            "/v1/login/passkey/finish"
        } else {
            "/v1/login/finish"
        };
        let candidates = self.candidate_urls(path);
        let mut last_err: Option<MailxError> = None;

        for (idx, url) in candidates.iter().enumerate() {
            let mut req = self.agent.post(url)
                .set("Content-Type", "application/json")
                .set("Accept", "application/json")
                .timeout(std::time::Duration::from_secs(15));

            if !ceremony_cookie.trim().is_empty() {
                req = req.set("Cookie", &format!("authntemp={}", ceremony_cookie.trim()));
            }

            let resp = req.send_string(credential_json);

            match resp {
                Ok(r) => {
                    let authn_cookie = Self::extract_authn_cookie(&r).ok_or_else(|| {
                        MailxError::Unauthorized(
                            "Server did not return a session cookie (authn) after passkey verification.".to_string(),
                        )
                    })?;
                    let body_text = r.into_string().unwrap_or_default();
                    let body_json: serde_json::Value =
                        serde_json::from_str(&body_text).unwrap_or_default();
                    let resolved_email = body_json
                        .get("email")
                        .and_then(|v| v.as_str())
                        .filter(|s| !s.is_empty())
                        .unwrap_or_else(|| fallback_email.trim());

                    let cookie_token = format!("cookie:{}", authn_cookie);
                    self.set_credentials(None, Some(cookie_token.clone()));

                    match self.fetch_defaults() {
                        Ok(def) => {
                            return Ok(AuthResponse {
                                token: cookie_token,
                                domain: def.domain,
                                domains: def.domains,
                                custom_domains: def.custom_domains,
                                recipient: if def.recipient.is_empty() {
                                    resolved_email.to_string()
                                } else {
                                    def.recipient
                                },
                                recipients: def.recipients,
                                alias_format: def.alias_format,
                                from_name: def.from_name,
                                log_issues: def.log_issues,
                                remove_header: def.remove_header,
                                list_unsubscribe_behaviour: def.list_unsubscribe_behaviour,
                                block_trackers: def.block_trackers,
                            });
                        }
                        Err(_) => {
                            return Ok(AuthResponse {
                                token: cookie_token,
                                domain: "mailx.net".to_string(),
                                domains: vec!["mailx.net".to_string()],
                                custom_domains: Vec::new(),
                                recipient: resolved_email.to_string(),
                                recipients: if resolved_email.is_empty() {
                                    Vec::new()
                                } else {
                                    vec![resolved_email.to_string()]
                                },
                                alias_format: "words".to_string(),
                                from_name: String::new(),
                                log_issues: false,
                                remove_header: false,
                                list_unsubscribe_behaviour: 0,
                                block_trackers: false,
                            });
                        }
                    }
                }
                Err(ureq::Error::Status(code, r)) => {
                    let body = r.into_string().unwrap_or_default();
                    let err = Self::parse_error_body(code, &body);
                    if idx + 1 < candidates.len() && (code == 404 || code == 405) {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
                Err(e) => {
                    let err = MailxError::NetworkError(e.to_string());
                    if idx + 1 < candidates.len() {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
            }
        }

        Err(last_err.unwrap_or_else(|| {
            MailxError::NetworkError("Could not reach the API server.".to_string())
        }))
    }

    #[cfg(feature = "network")]
    pub fn passkey_register_begin(&self) -> Result<serde_json::Value, MailxError> {
        let raw_token = self
            .current_token()
            .ok_or_else(|| MailxError::Unauthorized("Session token not found.".to_string()))?;
        let cookie_val = raw_token.strip_prefix("cookie:").ok_or_else(|| {
            MailxError::Unauthorized(
                "Passkey registration requires an email/web session rather than a read/write API key.".to_string(),
            )
        })?;

        let candidates = self.candidate_urls("/v1/register/add");
        let mut last_err: Option<MailxError> = None;

        for (idx, url) in candidates.iter().enumerate() {
            let resp = self.agent.post(url)
                .set("Accept", "application/json")
                .set("Cookie", &format!("authn={}", cookie_val))
                .timeout(std::time::Duration::from_secs(15))
                .call();

            match resp {
                Ok(r) => {
                    let ceremony_cookie = Self::extract_cookie_by_name(&r, "authntemp")
                        .unwrap_or_default();
                    let text = r
                        .into_string()
                        .map_err(|e| MailxError::NetworkError(e.to_string()))?;
                    let options: serde_json::Value = serde_json::from_str(&text)
                        .map_err(|e| MailxError::SerializationError(e.to_string()))?;
                    return Ok(serde_json::json!({
                        "options": options,
                        "ceremony_cookie": ceremony_cookie
                    }));
                }
                Err(ureq::Error::Status(code, r)) => {
                    let body = r.into_string().unwrap_or_default();
                    let err = Self::parse_error_body(code, &body);
                    if idx + 1 < candidates.len() && (code == 404 || code == 405) {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
                Err(e) => {
                    let err = MailxError::NetworkError(e.to_string());
                    if idx + 1 < candidates.len() {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
            }
        }

        Err(last_err.unwrap_or_else(|| {
            MailxError::NetworkError("Could not reach the API server.".to_string())
        }))
    }

    #[cfg(feature = "network")]
    pub fn passkey_register_finish(
        &self,
        ceremony_cookie: &str,
        credential_json: &str,
    ) -> Result<serde_json::Value, MailxError> {
        let raw_token = self
            .current_token()
            .ok_or_else(|| MailxError::Unauthorized("Session token not found.".to_string()))?;
        let cookie_val = raw_token.strip_prefix("cookie:").ok_or_else(|| {
            MailxError::Unauthorized(
                "Passkey registration requires an email/web session.".to_string(),
            )
        })?;

        let candidates = self.candidate_urls("/v1/register/add/finish");
        let mut last_err: Option<MailxError> = None;

        for (idx, url) in candidates.iter().enumerate() {
            let cookie_header = if ceremony_cookie.trim().is_empty() {
                format!("authn={}", cookie_val)
            } else {
                format!("authn={}; authntemp={}", cookie_val, ceremony_cookie.trim())
            };

            let resp = self.agent.post(url)
                .set("Content-Type", "application/json")
                .set("Accept", "application/json")
                .set("Cookie", &cookie_header)
                .timeout(std::time::Duration::from_secs(15))
                .send_string(credential_json);

            match resp {
                Ok(r) => {
                    if let Some(new_authn) = Self::extract_authn_cookie(&r) {
                        self.set_credentials(None, Some(format!("cookie:{}", new_authn)));
                    }
                    let text = r
                        .into_string()
                        .map_err(|e| MailxError::NetworkError(e.to_string()))?;
                    let val: serde_json::Value = serde_json::from_str(&text)
                        .unwrap_or_else(|_| serde_json::json!({ "message": "OK" }));
                    return Ok(val);
                }
                Err(ureq::Error::Status(code, r)) => {
                    let body = r.into_string().unwrap_or_default();
                    let err = Self::parse_error_body(code, &body);
                    if idx + 1 < candidates.len() && (code == 404 || code == 405) {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
                Err(e) => {
                    let err = MailxError::NetworkError(e.to_string());
                    if idx + 1 < candidates.len() {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
            }
        }

        Err(last_err.unwrap_or_else(|| {
            MailxError::NetworkError("Could not reach the API server.".to_string())
        }))
    }

    #[cfg(feature = "network")]
    pub fn authenticate(&self, access_key_raw: &str) -> Result<AuthResponse, MailxError> {
        validate_access_key(access_key_raw).map_err(MailxError::InvalidAccessKey)?;
        let clean_key = access_key_raw.trim().to_string();
        let payload = serde_json::json!({ "access_key": clean_key }).to_string();
        let candidates = self.candidate_urls("/v1/api/authenticate");
        let mut last_err: Option<MailxError> = None;

        for (idx, url) in candidates.iter().enumerate() {
            let resp = self.agent.post(url)
                .set("Content-Type", "application/json")
                .set("Accept", "application/json")
                .timeout(std::time::Duration::from_secs(15))
                .send_string(&payload);

            match resp {
                Ok(r) => {
                    let text = r
                        .into_string()
                        .map_err(|e| MailxError::NetworkError(e.to_string()))?;
                    match serde_json::from_str::<AuthResponse>(&text) {
                        Ok(auth_res) => {
                            self.set_credentials(Some(clean_key), Some(auth_res.token.clone()));
                            return Ok(auth_res);
                        }
                        Err(e) => {
                            last_err = Some(MailxError::SerializationError(e.to_string()));
                            if idx + 1 < candidates.len() && Self::is_html_body(&text) {
                                continue;
                            }
                            return Err(last_err.unwrap());
                        }
                    }
                }
                Err(ureq::Error::Status(code, r)) => {
                    let body = r.into_string().unwrap_or_default();
                    let err = Self::parse_error_body(code, &body);
                    if idx + 1 < candidates.len() && (code == 404 || code == 405) {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
                Err(e) => {
                    let err = MailxError::NetworkError(e.to_string());
                    if idx + 1 < candidates.len() {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
            }
        }

        Err(last_err.unwrap_or_else(|| {
            MailxError::NetworkError("Could not reach the API server.".to_string())
        }))
    }

    #[cfg(feature = "network")]
    fn execute_authenticated<T: DeserializeOwned>(
        &self,
        method: &str,
        path: &str,
        json_body: Option<&str>,
        allow_reauth: bool,
    ) -> Result<T, MailxError> {
        let raw_token = self
            .current_token()
            .ok_or_else(|| MailxError::Unauthorized("Session token not found.".to_string()))?;

        let is_cookie = raw_token.starts_with("cookie:");
        let scoped_path = Self::resolve_scoped_path(path, is_cookie);
        let candidates = self.candidate_urls(&scoped_path);
        let mut last_err: Option<MailxError> = None;

        for (idx, url) in candidates.iter().enumerate() {
            let mut req = self.agent.request(method, url)
                .set("Accept", "application/json")
                .timeout(std::time::Duration::from_secs(15));

            if let Some(cookie_val) = raw_token.strip_prefix("cookie:") {
                req = req.set("Cookie", &format!("authn={}", cookie_val));
            } else {
                req = req.set("Authorization", &format!("Bearer {}", raw_token));
            }

            let resp = if let Some(body) = json_body {
                if !body.is_empty() {
                    req.set("Content-Type", "application/json")
                        .send_string(body)
                } else {
                    req.call()
                }
            } else {
                req.call()
            };

            match resp {
                Ok(r) => {
                    let text = r
                        .into_string()
                        .map_err(|e| MailxError::NetworkError(e.to_string()))?;
                    if text.trim().is_empty() {
                        if let Ok(empty_val) = serde_json::from_str::<T>("{}") {
                            return Ok(empty_val);
                        }
                    }
                    match serde_json::from_str::<T>(&text) {
                        Ok(parsed) => return Ok(parsed),
                        Err(e) => {
                            last_err = Some(MailxError::SerializationError(e.to_string()));
                            if idx + 1 < candidates.len() && Self::is_html_body(&text) {
                                continue;
                            }
                            return Err(last_err.unwrap());
                        }
                    }
                }
                Err(ureq::Error::Status(401, r)) => {
                    let body = r.into_string().unwrap_or_default();
                    if allow_reauth && !is_cookie {
                        let saved_key = self
                            .access_key
                            .read()
                            .ok()
                            .and_then(|g| g.as_ref().map(|s| s.expose().to_string()));
                        if let Some(key) = saved_key {
                            if !key.starts_with("web:") && self.authenticate(&key).is_ok() {
                                return self.execute_authenticated(method, path, json_body, false);
                            }
                        }
                    }
                    return Err(Self::parse_error_body(401, &body));
                }
                Err(ureq::Error::Status(code, r)) => {
                    let body = r.into_string().unwrap_or_default();
                    let err = Self::parse_error_body(code, &body);
                    if idx + 1 < candidates.len() && (code == 404 || code == 405) {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
                Err(e) => {
                    let err = MailxError::NetworkError(e.to_string());
                    if idx + 1 < candidates.len() {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
            }
        }

        Err(last_err.unwrap_or_else(|| {
            MailxError::NetworkError("Could not reach the API server.".to_string())
        }))
    }

    #[cfg(feature = "network")]
    pub fn execute_raw_json(
        &self,
        method: &str,
        path: &str,
        json_body: Option<&str>,
    ) -> Result<serde_json::Value, MailxError> {
        let raw_token = self.current_token().unwrap_or_default();
        let is_cookie = raw_token.starts_with("cookie:");
        let scoped_path = Self::resolve_scoped_path(path, is_cookie);
        let candidates = self.candidate_urls(&scoped_path);
        let mut last_err: Option<MailxError> = None;

        for (idx, url) in candidates.iter().enumerate() {
            let mut req = self.agent.request(method, url)
                .set("Accept", "application/json, text/plain, text/csv, */*")
                .timeout(std::time::Duration::from_secs(20));

            if !raw_token.is_empty() {
                if let Some(cookie_val) = raw_token.strip_prefix("cookie:") {
                    req = req.set("Cookie", &format!("authn={}", cookie_val));
                } else {
                    req = req.set("Authorization", &format!("Bearer {}", raw_token));
                }
            }

            let resp = if let Some(body) = json_body {
                if !body.is_empty() {
                    req.set("Content-Type", "application/json")
                        .send_string(body)
                } else {
                    req.call()
                }
            } else {
                req.call()
            };

            match resp {
                Ok(r) => {
                    let text = r
                        .into_string()
                        .map_err(|e| MailxError::NetworkError(e.to_string()))?;
                    let trimmed = text.trim();
                    if trimmed.is_empty() {
                        return Ok(serde_json::json!({ "message": "OK" }));
                    }
                    if let Ok(val) = serde_json::from_str::<serde_json::Value>(trimmed) {
                        return Ok(val);
                    }
                    return Ok(serde_json::json!({ "text": text }));
                }
                Err(ureq::Error::Status(code, r)) => {
                    let body = r.into_string().unwrap_or_default();
                    let err = Self::parse_error_body(code, &body);
                    if idx + 1 < candidates.len() && (code == 404 || code == 405) {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
                Err(e) => {
                    let err = MailxError::NetworkError(e.to_string());
                    if idx + 1 < candidates.len() {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
            }
        }

        Err(last_err.unwrap_or_else(|| {
            MailxError::NetworkError("Could not reach the API server.".to_string())
        }))
    }

    #[cfg(feature = "network")]
    pub fn import_aliases_csv(&self, csv_text: &str) -> Result<serde_json::Value, MailxError> {
        if csv_text.trim().is_empty() {
            return Err(MailxError::SerializationError("CSV content is empty.".to_string()));
        }

        let raw_token = self.current_token().unwrap_or_default();
        let is_cookie = raw_token.starts_with("cookie:");
        let path = Self::resolve_scoped_path("/v1/aliases/import", is_cookie);
        let candidates = self.candidate_urls(&path);
        let boundary = format!(
            "----MailxCsvImport{}",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_nanos()
        );
        let mut body = Vec::with_capacity(csv_text.len() + 256);
        body.extend_from_slice(
            format!(
                "--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"aliases.csv\"\r\nContent-Type: text/csv; charset=utf-8\r\n\r\n"
            )
            .as_bytes(),
        );
        body.extend_from_slice(csv_text.as_bytes());
        body.extend_from_slice(format!("\r\n--{boundary}--\r\n").as_bytes());

        let mut last_err: Option<MailxError> = None;
        for (idx, url) in candidates.iter().enumerate() {
            let mut req = self
                .agent
                .post(url)
                .set("Accept", "application/json")
                .set("Content-Type", &format!("multipart/form-data; boundary={boundary}"))
                .timeout(std::time::Duration::from_secs(60));
            if !raw_token.is_empty() {
                if let Some(cookie_val) = raw_token.strip_prefix("cookie:") {
                    req = req.set("Cookie", &format!("authn={cookie_val}"));
                } else {
                    req = req.set("Authorization", &format!("Bearer {raw_token}"));
                }
            }
            match req.send_bytes(&body) {
                Ok(response) => {
                    let response_text = response
                        .into_string()
                        .map_err(|e| MailxError::NetworkError(e.to_string()))?;
                    let trimmed = response_text.trim();
                    if trimmed.is_empty() {
                        return Ok(serde_json::json!({"message":"OK"}));
                    }
                    return match serde_json::from_str::<serde_json::Value>(trimmed) {
                        Ok(value) => Ok(value),
                        Err(_) => Ok(serde_json::json!({"text": response_text})),
                    };
                }
                Err(ureq::Error::Status(code, response)) => {
                    let response_body = response.into_string().unwrap_or_default();
                    let err = Self::parse_error_body(code, &response_body);
                    if idx + 1 < candidates.len() && (code == 404 || code == 405) {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
                Err(error) => {
                    let err = MailxError::NetworkError(error.to_string());
                    if idx + 1 < candidates.len() {
                        last_err = Some(err);
                        continue;
                    }
                    return Err(err);
                }
            }
        }
        Err(last_err.unwrap_or_else(|| {
            MailxError::NetworkError("Could not reach the API server.".to_string())
        }))
    }

    #[cfg(feature = "network")]
    pub fn fetch_aliases(&self, filter: &AliasFilter) -> Result<AliasListResponse, MailxError> {
        let path = build_aliases_query_path(filter);
        self.execute_authenticated("GET", &path, None, true)
    }

    #[cfg(feature = "network")]
    pub fn fetch_defaults(&self) -> Result<DefaultsResponse, MailxError> {
        self.execute_authenticated("GET", "/v1/api/defaults", None, true)
    }

    #[cfg(feature = "network")]
    pub fn create_alias(&self, req: &AliasRequest) -> Result<CreateAliasResponse, MailxError> {
        let body = serde_json::to_string(req)
            .map_err(|e| MailxError::SerializationError(e.to_string()))?;
        self.execute_authenticated("POST", "/v1/api/alias", Some(&body), true)
    }

    #[cfg(feature = "network")]
    pub fn update_alias(
        &self,
        alias_id: &str,
        req: &AliasRequest,
    ) -> Result<serde_json::Value, MailxError> {
        let body = serde_json::to_string(req)
            .map_err(|e| MailxError::SerializationError(e.to_string()))?;
        let path = format!("/v1/api/alias/{}", alias_id);
        self.execute_authenticated("PUT", &path, Some(&body), true)
    }

    #[cfg(feature = "network")]
    pub fn pin_alias(&self, alias_id: &str, pinned: bool) -> Result<serde_json::Value, MailxError> {
        let body = serde_json::to_string(&AliasPinRequest { pinned })
            .map_err(|e| MailxError::SerializationError(e.to_string()))?;
        let path = format!("/v1/api/alias/{}/pin", alias_id);
        self.execute_authenticated("PUT", &path, Some(&body), true)
    }

    #[cfg(feature = "network")]
    pub fn delete_alias(&self, alias_id: &str) -> Result<serde_json::Value, MailxError> {
        let path = format!("/v1/api/alias/{}", alias_id);
        self.execute_authenticated("DELETE", &path, None, true)
    }

    #[cfg(feature = "network")]
    pub fn logout(&self) -> Result<serde_json::Value, MailxError> {
        let res = self.execute_authenticated("POST", "/v1/api/logout", None, false);
        self.set_credentials(None, None);
        res
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_https_enforcement_and_api_normalization() {
        let client = MailxClient::new("https://mailx.net", false).unwrap();
        assert_eq!(client.base_url(), "https://api.mailx.net");

        let client_with_path = MailxClient::new("https://mailx.net/v1/api/", false).unwrap();
        assert_eq!(client_with_path.base_url(), "https://api.mailx.net");

        assert!(MailxClient::new("http://mailx.net", false).is_err());
        assert!(MailxClient::new("http://localhost:3000", true).is_ok());
    }

    #[test]
    fn test_scoped_path_resolution() {
        assert_eq!(
            MailxClient::resolve_scoped_path("/v1/api/aliases?page=1", true),
            "/v1/aliases?page=1"
        );
        assert_eq!(
            MailxClient::resolve_scoped_path("/v1/api/logout", true),
            "/v1/user/logout"
        );
        assert_eq!(
            MailxClient::resolve_scoped_path("/v1/api/aliases", false),
            "/v1/api/aliases"
        );
    }

    #[test]
    fn test_html_error_body_sanitization_and_codes() {
        let nginx_405 = "<html>\r\n<head><title>405 Not Allowed</title></head>\r\n<body>\r\n<center><h1>405 Not Allowed</h1></center>\r\n<hr><center>nginx</center>\r\n</body>\r\n</html>";
        let err = MailxClient::parse_error_body(405, nginx_405);
        let msg = err.to_string();
        assert!(!msg.contains("<html>"));
        assert!(msg.contains("405 Not Allowed"));

        let totp_err = MailxClient::parse_error_body(
            400,
            r#"{"message":"Two-factor authentication is required to continue.","code":70001}"#,
        );
        assert_eq!(totp_err.error_code(), Some(70001));
    }
}
