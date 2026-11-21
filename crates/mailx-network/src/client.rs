use mailx_domain::models::*;
use mailx_utils::random::random_hex;
use mailx_utils::secret::SecretString;
use mailx_utils::{build_aliases_query_path, encode_path_segment, validate_access_key};
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::fmt;
#[cfg(feature = "network")]
use std::io::Read;
use std::sync::{Arc, RwLock};
use std::time::Duration;

const LIVEZ_TIMEOUT: Duration = Duration::from_secs(10);
const API_TIMEOUT: Duration = Duration::from_secs(15);
const RAW_TIMEOUT: Duration = Duration::from_secs(20);
const IMPORT_TIMEOUT: Duration = Duration::from_secs(60);
#[cfg(feature = "network")]
const MAX_BODY_BYTES: u64 = 10 * 1024 * 1024;
const MAX_SAME_ORIGIN_REDIRECTS: usize = 2;
const MAX_ERROR_MESSAGE_CHARS: usize = 300;
const COOKIE_PREFIX: &str = "cookie:";
const UNREACHABLE_MESSAGE: &str = "Could not reach the API server.";

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

impl fmt::Display for MailxError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
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

impl std::error::Error for MailxError {}

#[derive(Clone, PartialEq, Eq)]
pub struct HttpRequest {
    pub method: String,
    pub url: String,
    pub headers: Vec<(String, String)>,
    pub body: Option<Vec<u8>>,
    pub timeout: Duration,
}

impl HttpRequest {
    pub fn header(&self, name: &str) -> Option<&str> {
        self.headers
            .iter()
            .find(|(key, _)| key.eq_ignore_ascii_case(name))
            .map(|(_, value)| value.as_str())
    }
}

#[derive(Clone, PartialEq, Eq)]
pub struct HttpResponse {
    pub status: u16,
    pub headers: Vec<(String, String)>,
    pub body: String,
}

impl HttpResponse {
    pub fn header(&self, name: &str) -> Option<&str> {
        self.headers
            .iter()
            .find(|(key, _)| key.eq_ignore_ascii_case(name))
            .map(|(_, value)| value.as_str())
    }

    pub fn header_values<'a>(&'a self, name: &'a str) -> impl Iterator<Item = &'a str> + 'a {
        self.headers
            .iter()
            .filter(move |(key, _)| key.eq_ignore_ascii_case(name))
            .map(|(_, value)| value.as_str())
    }

    pub fn is_success(&self) -> bool {
        (200..300).contains(&self.status)
    }
}

fn is_sensitive_header(name: &str) -> bool {
    [
        "authorization",
        "proxy-authorization",
        "cookie",
        "set-cookie",
    ]
    .iter()
    .any(|sensitive| name.eq_ignore_ascii_case(sensitive))
}

struct RedactedHeaders<'a>(&'a [(String, String)]);

impl fmt::Debug for RedactedHeaders<'_> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let mut list = f.debug_list();
        for (name, value) in self.0 {
            if is_sensitive_header(name) {
                list.entry(&format_args!("{name}: <redacted>"));
            } else {
                list.entry(&format_args!("{name}: {value}"));
            }
        }
        list.finish()
    }
}

impl fmt::Debug for HttpRequest {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("HttpRequest")
            .field("method", &self.method)
            .field("url", &self.url)
            .field("headers", &RedactedHeaders(&self.headers))
            .field("body_bytes", &self.body.as_ref().map(Vec::len))
            .field("timeout", &self.timeout)
            .finish()
    }
}

impl fmt::Debug for HttpResponse {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("HttpResponse")
            .field("status", &self.status)
            .field("headers", &RedactedHeaders(&self.headers))
            .field("body_bytes", &self.body.len())
            .finish()
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TransportError(pub String);

impl fmt::Display for TransportError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl std::error::Error for TransportError {}

pub trait Transport: Send + Sync {
    fn send(&self, request: &HttpRequest) -> Result<HttpResponse, TransportError>;
}

#[cfg(feature = "network")]
pub struct UreqTransport {
    agent: ureq::Agent,
}

#[cfg(feature = "network")]
impl UreqTransport {
    pub fn new() -> Self {
        let agent = ureq::AgentBuilder::new()
            .timeout_connect(Duration::from_secs(10))
            .timeout_read(Duration::from_secs(20))
            .timeout_write(Duration::from_secs(20))
            .redirects(0)
            .build();
        Self { agent }
    }
}

#[cfg(feature = "network")]
impl Default for UreqTransport {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(feature = "network")]
impl Transport for UreqTransport {
    fn send(&self, request: &HttpRequest) -> Result<HttpResponse, TransportError> {
        let mut pending = self
            .agent
            .request(&request.method, &request.url)
            .timeout(request.timeout);
        for (name, value) in &request.headers {
            pending = pending.set(name, value);
        }
        let outcome = match &request.body {
            Some(body) => pending.send_bytes(body),
            None => pending.call(),
        };
        match outcome {
            Ok(response) => read_ureq_response(response),
            Err(ureq::Error::Status(_, response)) => read_ureq_response(response),
            Err(error) => Err(TransportError(error.to_string())),
        }
    }
}

#[cfg(feature = "network")]
fn read_ureq_response(response: ureq::Response) -> Result<HttpResponse, TransportError> {
    let status = response.status();
    let mut names = response.headers_names();
    names.sort();
    names.dedup();
    let mut headers = Vec::new();
    for name in names {
        for value in response.all(&name) {
            headers.push((name.clone(), value.to_string()));
        }
    }
    let mut raw = Vec::new();
    response
        .into_reader()
        .take(MAX_BODY_BYTES)
        .read_to_end(&mut raw)
        .map_err(|error| TransportError(format!("Failed to read response body: {error}")))?;
    Ok(HttpResponse {
        status,
        headers,
        body: String::from_utf8_lossy(&raw).into_owned(),
    })
}

#[cfg(not(feature = "network"))]
struct DisabledTransport;

#[cfg(not(feature = "network"))]
impl Transport for DisabledTransport {
    fn send(&self, _request: &HttpRequest) -> Result<HttpResponse, TransportError> {
        Err(TransportError(
            "Network support is disabled in this build.".to_string(),
        ))
    }
}

fn default_transport() -> Arc<dyn Transport> {
    #[cfg(feature = "network")]
    {
        Arc::new(UreqTransport::new())
    }
    #[cfg(not(feature = "network"))]
    {
        Arc::new(DisabledTransport)
    }
}

fn only_https_error() -> MailxError {
    MailxError::InvalidBaseUrl(
        "Only HTTPS server URLs are supported for security reasons.".to_string(),
    )
}

fn split_scheme(url: &str) -> Option<(String, &str)> {
    let (scheme, rest) = url.split_once("://")?;
    if scheme.is_empty() || !scheme.bytes().all(|b| b.is_ascii_alphanumeric()) {
        return None;
    }
    Some((scheme.to_ascii_lowercase(), rest))
}

fn is_valid_port(port: &str) -> bool {
    !port.is_empty()
        && port.len() <= 5
        && port.bytes().all(|b| b.is_ascii_digit())
        && port
            .parse::<u32>()
            .is_ok_and(|value| (1..=65535).contains(&value))
}

fn is_valid_hostname(host: &str) -> bool {
    host.split('.').all(|label| {
        !label.is_empty()
            && label
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b == b'-')
    })
}

fn is_valid_authority(authority: &str) -> bool {
    if let Some(bracketed) = authority.strip_prefix('[') {
        let Some((literal, tail)) = bracketed.split_once(']') else {
            return false;
        };
        let literal_ok = literal.contains(':')
            && literal
                .bytes()
                .all(|b| b.is_ascii_hexdigit() || b == b':' || b == b'.');
        let port_ok = tail.is_empty() || tail.strip_prefix(':').is_some_and(is_valid_port);
        return literal_ok && port_ok;
    }
    match authority.split_once(':') {
        Some((host, port)) => is_valid_hostname(host) && is_valid_port(port),
        None => is_valid_hostname(authority),
    }
}

fn authority_of(rest: &str) -> Option<&str> {
    let end = rest.find(['/', '?', '#']).unwrap_or(rest.len());
    let authority = &rest[..end];
    is_valid_authority(authority).then_some(authority)
}

fn host_of_authority(authority: &str) -> &str {
    if let Some(bracketed) = authority.strip_prefix('[') {
        return bracketed.split(']').next().unwrap_or(bracketed);
    }
    authority.split(':').next().unwrap_or(authority)
}

fn is_loopback_host(host: &str) -> bool {
    host.eq_ignore_ascii_case("localhost") || host == "127.0.0.1" || host == "::1"
}

fn origin_of(url: &str) -> Option<(String, String)> {
    let (scheme, rest) = split_scheme(url)?;
    let authority = authority_of(rest)?.to_ascii_lowercase();
    let default_port = match scheme.as_str() {
        "https" => ":443",
        "http" => ":80",
        _ => "",
    };
    if !default_port.is_empty() {
        if let Some(stripped) = authority.strip_suffix(default_port) {
            return Some((scheme, stripped.to_string()));
        }
    }
    Some((scheme, authority))
}

fn resolve_same_origin(current: &str, location: &str) -> Option<String> {
    if location.chars().any(|c| c.is_control() || c == '\\') {
        return None;
    }
    let current_origin = origin_of(current)?;
    if location.starts_with("//") {
        return None;
    }
    if location.starts_with('/') {
        let (scheme, rest) = split_scheme(current)?;
        let authority = authority_of(rest)?;
        return Some(format!("{scheme}://{authority}{location}"));
    }
    if origin_of(location)? == current_origin {
        return Some(location.to_string());
    }
    None
}

fn is_redirect_status(status: u16) -> bool {
    matches!(status, 301 | 302 | 303 | 307 | 308)
}

fn redirected_request(previous: &HttpRequest, status: u16, url: String) -> HttpRequest {
    let is_get_or_head =
        previous.method.eq_ignore_ascii_case("GET") || previous.method.eq_ignore_ascii_case("HEAD");
    let keeps_method = match status {
        303 => is_get_or_head,
        301 | 302 => !previous.method.eq_ignore_ascii_case("POST"),
        _ => true,
    };
    let mut headers = previous.headers.clone();
    let (method, body) = if keeps_method {
        (previous.method.clone(), previous.body.clone())
    } else {
        headers.retain(|(name, _)| !name.eq_ignore_ascii_case("content-type"));
        ("GET".to_string(), None)
    };
    HttpRequest {
        method,
        url,
        headers,
        body,
        timeout: previous.timeout,
    }
}

fn is_dot_segment(segment: &str) -> bool {
    matches!(
        segment.to_ascii_lowercase().as_str(),
        "." | ".." | "%2e" | ".%2e" | "%2e." | "%2e%2e"
    )
}

fn validate_request_path(path: &str) -> Result<(), MailxError> {
    let invalid = || {
        MailxError::InvalidBaseUrl(
            "Request path must start with a single '/' and contain no whitespace, backslashes, empty segments or dot segments."
                .to_string(),
        )
    };
    if !path.starts_with('/') || path.starts_with("//") {
        return Err(invalid());
    }
    if path
        .chars()
        .any(|c| c.is_whitespace() || c.is_control() || c == '\\')
    {
        return Err(invalid());
    }
    let path_only = path.find(['?', '#']).map_or(path, |end| &path[..end]);
    if path_only[1..]
        .split('/')
        .any(|segment| segment.is_empty() || is_dot_segment(segment))
    {
        return Err(invalid());
    }
    Ok(())
}

fn is_http_token(value: &str) -> bool {
    !value.is_empty()
        && value
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b"!#$%&'*+-.^_`|~".contains(&b))
}

fn is_visible_ascii(value: &str) -> bool {
    !value.is_empty() && value.bytes().all(|b| (0x21..=0x7E).contains(&b))
}

fn is_safe_cookie_value(value: &str) -> bool {
    is_visible_ascii(value) && !value.contains([';', ','])
}

fn limit_chars(text: &str) -> String {
    if text.chars().count() <= MAX_ERROR_MESSAGE_CHARS {
        return text.to_string();
    }
    let mut cut: String = text.chars().take(MAX_ERROR_MESSAGE_CHARS).collect();
    cut.push('…');
    cut
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
    let trimmed = raw.trim_start();
    if !trimmed.starts_with('<') {
        return false;
    }
    let lower = trimmed.to_ascii_lowercase();
    lower.starts_with("<!doctype html")
        || lower.starts_with("<html")
        || lower.contains("<head>")
        || lower.contains("<body>")
}

fn non_empty_string_field(body: &Value, key: &str) -> Option<String> {
    body.get(key)
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|text| !text.is_empty())
        .map(str::to_string)
}

fn extract_cookie_by_name(response: &HttpResponse, cookie_name: &str) -> Option<String> {
    let prefix = format!("{}=", cookie_name);
    for header in response.header_values("set-cookie") {
        for part in header.split(';') {
            if let Some(cookie_val) = part.trim().strip_prefix(&prefix) {
                if !cookie_val.is_empty() {
                    return Some(cookie_val.to_string());
                }
            }
        }
    }
    None
}

fn has_api_error_message(body: &str) -> bool {
    serde_json::from_str::<Value>(body).is_ok_and(|value| {
        non_empty_string_field(&value, "error").is_some()
            || non_empty_string_field(&value, "message").is_some()
    })
}

fn parse_json_or_text(text: &str) -> Value {
    let trimmed = text.trim();
    if trimmed.is_empty() {
        return json!({ "message": "OK" });
    }
    serde_json::from_str::<Value>(trimmed).unwrap_or_else(|_| json!({ "text": text }))
}

fn json_headers(with_content_type: bool) -> Vec<(String, String)> {
    let mut headers = vec![("Accept".to_string(), "application/json".to_string())];
    if with_content_type {
        headers.push(("Content-Type".to_string(), "application/json".to_string()));
    }
    headers
}

struct Call<'a> {
    method: &'a str,
    path: &'a str,
    headers: Vec<(String, String)>,
    body: Option<Vec<u8>>,
    timeout: Duration,
    retry_on_html: bool,
}

#[derive(Clone)]
pub struct MailxClient {
    base_url: String,
    transport: Arc<dyn Transport>,
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
                let remaining = s[..new_len].trim_end_matches('/');
                if split_scheme(remaining).is_some_and(|(_, rest)| !rest.is_empty()) {
                    s = remaining.to_string();
                }
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
        Self::with_transport(base_url, allow_insecure_localhost, default_transport())
    }

    pub fn with_transport(
        base_url: &str,
        allow_insecure_localhost: bool,
        transport: Arc<dyn Transport>,
    ) -> Result<Self, MailxError> {
        let normalized = Self::normalize_base_url(base_url);
        if normalized
            .chars()
            .any(|c| c.is_whitespace() || c.is_control() || c == '\\')
        {
            return Err(MailxError::InvalidBaseUrl(
                "Server URL contains invalid characters.".to_string(),
            ));
        }
        let (scheme, rest) = split_scheme(&normalized).ok_or_else(only_https_error)?;
        let authority = authority_of(rest).ok_or_else(|| {
            MailxError::InvalidBaseUrl(
                "Server URL needs a plain host name (ASCII letters, digits, '-' and '.'), an optional port and no credentials."
                    .to_string(),
            )
        })?;
        if rest.contains(['?', '#']) {
            return Err(MailxError::InvalidBaseUrl(
                "Server URL must not contain a query or fragment.".to_string(),
            ));
        }
        let host = host_of_authority(authority);
        if host.is_empty() {
            return Err(MailxError::InvalidBaseUrl(
                "Server URL needs a host and no credentials.".to_string(),
            ));
        }
        let allowed = scheme == "https"
            || (scheme == "http" && allow_insecure_localhost && is_loopback_host(host));
        if !allowed {
            return Err(only_https_error());
        }
        Ok(Self {
            base_url: format!("{scheme}://{rest}"),
            transport,
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
            .map(|t| t.starts_with(COOKIE_PREFIX))
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

    fn candidate_urls(&self, path: &str) -> Result<Vec<String>, MailxError> {
        validate_request_path(path)?;
        let mut urls = vec![format!("{}{}", self.base_url, path)];
        if let Some(tail) = path.strip_prefix("/v1/api/") {
            urls.push(format!("{}/api/v1/{}", self.base_url, tail));
        }
        Ok(urls)
    }

    fn parse_error_body(status: u16, raw: &str) -> MailxError {
        let body = serde_json::from_str::<Value>(raw).ok();
        let code = body
            .as_ref()
            .and_then(|b| b.get("code"))
            .and_then(Value::as_i64);
        let parsed = body
            .as_ref()
            .and_then(|b| non_empty_string_field(b, "error"))
            .or_else(|| {
                body.as_ref()
                    .and_then(|b| non_empty_string_field(b, "message"))
            })
            .map(|text| limit_chars(&text))
            .unwrap_or_else(|| {
                let trimmed = raw.trim();
                if trimmed.is_empty() {
                    format!("HTTP {}", status)
                } else if is_html_body(trimmed) {
                    let clean_text = limit_chars(&strip_html_tags(trimmed));
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
                    limit_chars(trimmed)
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

    fn require_success(response: HttpResponse) -> Result<HttpResponse, MailxError> {
        if response.is_success() {
            Ok(response)
        } else {
            Err(Self::parse_error_body(response.status, &response.body))
        }
    }

    fn send_checked(&self, request: HttpRequest) -> Result<HttpResponse, MailxError> {
        let mut current = request;
        for _ in 0..=MAX_SAME_ORIGIN_REDIRECTS {
            let response = self
                .transport
                .send(&current)
                .map_err(|error| MailxError::NetworkError(error.to_string()))?;
            if !is_redirect_status(response.status) {
                return Ok(response);
            }
            let Some(location) = response.header("location") else {
                return Ok(response);
            };
            let Some(next_url) = resolve_same_origin(&current.url, location) else {
                return Err(MailxError::ApiError {
                    status: response.status,
                    message:
                        "Server redirected to a different host; credentials were not forwarded."
                            .to_string(),
                });
            };
            current = redirected_request(&current, response.status, next_url);
        }
        Err(MailxError::ApiError {
            status: 310,
            message: "Too many redirects.".to_string(),
        })
    }

    fn execute(&self, call: &Call<'_>) -> Result<HttpResponse, MailxError> {
        let urls = self.candidate_urls(call.path)?;
        let total = urls.len();
        let mut earlier_api_error: Option<HttpResponse> = None;
        for (index, url) in urls.into_iter().enumerate() {
            let is_last = index + 1 == total;
            let request = HttpRequest {
                method: call.method.to_string(),
                url,
                headers: call.headers.clone(),
                body: call.body.clone(),
                timeout: call.timeout,
            };
            match self.send_checked(request) {
                Ok(response) => {
                    let wrong_route = matches!(response.status, 404 | 405);
                    let html_instead_of_api =
                        call.retry_on_html && response.is_success() && is_html_body(&response.body);
                    if !is_last && (wrong_route || html_instead_of_api) {
                        if wrong_route
                            && earlier_api_error.is_none()
                            && has_api_error_message(&response.body)
                        {
                            earlier_api_error = Some(response);
                        }
                        continue;
                    }
                    if wrong_route && !has_api_error_message(&response.body) {
                        if let Some(earlier) = earlier_api_error.take() {
                            return Ok(earlier);
                        }
                    }
                    return Ok(response);
                }
                Err(error) => {
                    if is_last {
                        return Err(error);
                    }
                }
            }
        }
        Err(MailxError::NetworkError(UNREACHABLE_MESSAGE.to_string()))
    }

    fn auth_headers(token: &str) -> Result<Vec<(String, String)>, MailxError> {
        match token.strip_prefix(COOKIE_PREFIX) {
            Some(cookie) => {
                if !is_safe_cookie_value(cookie) {
                    return Err(MailxError::Unauthorized(
                        "Stored session cookie is malformed.".to_string(),
                    ));
                }
                Ok(vec![("Cookie".to_string(), format!("authn={cookie}"))])
            }
            None => {
                if !is_visible_ascii(token) {
                    return Err(MailxError::Unauthorized(
                        "Stored session token is malformed.".to_string(),
                    ));
                }
                Ok(vec![(
                    "Authorization".to_string(),
                    format!("Bearer {token}"),
                )])
            }
        }
    }

    fn session_cookie_value(&self, message: &str) -> Result<String, MailxError> {
        let raw_token = self
            .current_token()
            .ok_or_else(|| MailxError::Unauthorized("Session token not found.".to_string()))?;
        raw_token
            .strip_prefix(COOKIE_PREFIX)
            .map(str::to_string)
            .ok_or_else(|| MailxError::Unauthorized(message.to_string()))
    }

    fn defaults_to_auth_response(
        token: String,
        defaults: Result<DefaultsResponse, MailxError>,
        fallback_email: &str,
    ) -> AuthResponse {
        match defaults {
            Ok(def) => AuthResponse {
                token,
                domain: def.domain,
                domains: def.domains,
                custom_domains: def.custom_domains,
                recipient: if def.recipient.is_empty() {
                    fallback_email.to_string()
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
            },
            Err(_) => AuthResponse {
                token,
                domain: "mailx.net".to_string(),
                domains: vec!["mailx.net".to_string()],
                custom_domains: Vec::new(),
                recipient: fallback_email.to_string(),
                recipients: if fallback_email.is_empty() {
                    Vec::new()
                } else {
                    vec![fallback_email.to_string()]
                },
                alias_format: "words".to_string(),
                from_name: String::new(),
                log_issues: false,
                remove_header: false,
                list_unsubscribe_behaviour: 0,
                block_trackers: false,
            },
        }
    }

    pub fn check_livez(&self) -> Result<bool, MailxError> {
        let request = HttpRequest {
            method: "GET".to_string(),
            url: format!("{}/livez", self.base_url),
            headers: Vec::new(),
            body: None,
            timeout: LIVEZ_TIMEOUT,
        };
        let response = self.send_checked(request)?;
        if response.status >= 400 {
            return Err(Self::parse_error_body(response.status, &response.body));
        }
        Ok(response.is_success())
    }

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
        let payload = json!({
            "email": clean_email,
            "password": password,
            "otp": otp.trim(),
            "remember": true
        })
        .to_string();

        let response = Self::require_success(self.execute(&Call {
            method: "POST",
            path: "/v1/login",
            headers: json_headers(true),
            body: Some(payload.into_bytes()),
            timeout: API_TIMEOUT,
            retry_on_html: false,
        })?)?;

        let authn_cookie = extract_cookie_by_name(&response, "authn").ok_or_else(|| {
            MailxError::Unauthorized("Server did not return a session cookie (authn).".to_string())
        })?;
        let cookie_token = format!("{COOKIE_PREFIX}{authn_cookie}");
        self.set_credentials(None, Some(cookie_token.clone()));
        Ok(Self::defaults_to_auth_response(
            cookie_token,
            self.fetch_defaults(),
            clean_email,
        ))
    }

    pub fn passkey_login_begin(&self, email: &str) -> Result<Value, MailxError> {
        let clean_email = email.trim();
        let (path, payload, is_discoverable) = if clean_email.is_empty() {
            (
                "/v1/login/passkey/begin",
                json!({ "remember": true }).to_string(),
                true,
            )
        } else {
            (
                "/v1/login/begin",
                json!({ "email": clean_email, "remember": true }).to_string(),
                false,
            )
        };

        let response = self.execute(&Call {
            method: "POST",
            path,
            headers: json_headers(true),
            body: Some(payload.into_bytes()),
            timeout: API_TIMEOUT,
            retry_on_html: false,
        })?;

        if !response.is_success() {
            if !is_discoverable {
                return self.passkey_login_begin("");
            }
            return Err(Self::parse_error_body(response.status, &response.body));
        }

        let ceremony_cookie = extract_cookie_by_name(&response, "authntemp").unwrap_or_default();
        let options: Value = serde_json::from_str(&response.body)
            .map_err(|e| MailxError::SerializationError(e.to_string()))?;
        Ok(json!({
            "options": options,
            "ceremony_cookie": ceremony_cookie,
            "discoverable": is_discoverable
        }))
    }

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
        let mut headers = json_headers(true);
        let ceremony = ceremony_cookie.trim();
        if !ceremony.is_empty() {
            if !is_safe_cookie_value(ceremony) {
                return Err(MailxError::Unauthorized(
                    "Passkey ceremony cookie is malformed.".to_string(),
                ));
            }
            headers.push(("Cookie".to_string(), format!("authntemp={ceremony}")));
        }

        let response = Self::require_success(self.execute(&Call {
            method: "POST",
            path,
            headers,
            body: Some(credential_json.as_bytes().to_vec()),
            timeout: API_TIMEOUT,
            retry_on_html: false,
        })?)?;

        let authn_cookie = extract_cookie_by_name(&response, "authn").ok_or_else(|| {
            MailxError::Unauthorized(
                "Server did not return a session cookie (authn) after passkey verification."
                    .to_string(),
            )
        })?;
        let body_json: Value = serde_json::from_str(&response.body).unwrap_or_default();
        let resolved_email = body_json
            .get("email")
            .and_then(Value::as_str)
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| fallback_email.trim())
            .to_string();

        let cookie_token = format!("{COOKIE_PREFIX}{authn_cookie}");
        self.set_credentials(None, Some(cookie_token.clone()));
        Ok(Self::defaults_to_auth_response(
            cookie_token,
            self.fetch_defaults(),
            &resolved_email,
        ))
    }

    pub fn passkey_register_begin(&self) -> Result<Value, MailxError> {
        let cookie_val = self.session_cookie_value(
            "Passkey registration requires an email/web session rather than a read/write API key.",
        )?;
        let headers = Self::auth_headers(&format!("{COOKIE_PREFIX}{cookie_val}"))?
            .into_iter()
            .chain(json_headers(false))
            .collect();

        let response = Self::require_success(self.execute(&Call {
            method: "POST",
            path: "/v1/register/add",
            headers,
            body: None,
            timeout: API_TIMEOUT,
            retry_on_html: false,
        })?)?;

        let ceremony_cookie = extract_cookie_by_name(&response, "authntemp").unwrap_or_default();
        let options: Value = serde_json::from_str(&response.body)
            .map_err(|e| MailxError::SerializationError(e.to_string()))?;
        Ok(json!({
            "options": options,
            "ceremony_cookie": ceremony_cookie
        }))
    }

    pub fn passkey_register_finish(
        &self,
        ceremony_cookie: &str,
        credential_json: &str,
    ) -> Result<Value, MailxError> {
        let cookie_val =
            self.session_cookie_value("Passkey registration requires an email/web session.")?;
        if !is_safe_cookie_value(&cookie_val) {
            return Err(MailxError::Unauthorized(
                "Stored session cookie is malformed.".to_string(),
            ));
        }
        let ceremony = ceremony_cookie.trim();
        let cookie_header = if ceremony.is_empty() {
            format!("authn={cookie_val}")
        } else if is_safe_cookie_value(ceremony) {
            format!("authn={cookie_val}; authntemp={ceremony}")
        } else {
            return Err(MailxError::Unauthorized(
                "Passkey ceremony cookie is malformed.".to_string(),
            ));
        };
        let mut headers = json_headers(true);
        headers.push(("Cookie".to_string(), cookie_header));

        let response = Self::require_success(self.execute(&Call {
            method: "POST",
            path: "/v1/register/add/finish",
            headers,
            body: Some(credential_json.as_bytes().to_vec()),
            timeout: API_TIMEOUT,
            retry_on_html: false,
        })?)?;

        if let Some(new_authn) = extract_cookie_by_name(&response, "authn") {
            self.set_credentials(None, Some(format!("{COOKIE_PREFIX}{new_authn}")));
        }
        Ok(serde_json::from_str(&response.body).unwrap_or_else(|_| json!({ "message": "OK" })))
    }

    pub fn authenticate(&self, access_key_raw: &str) -> Result<AuthResponse, MailxError> {
        validate_access_key(access_key_raw).map_err(MailxError::InvalidAccessKey)?;
        let clean_key = access_key_raw.trim().to_string();
        let payload = json!({ "access_key": clean_key }).to_string();

        let response = Self::require_success(self.execute(&Call {
            method: "POST",
            path: "/v1/api/authenticate",
            headers: json_headers(true),
            body: Some(payload.into_bytes()),
            timeout: API_TIMEOUT,
            retry_on_html: true,
        })?)?;

        let auth_res = serde_json::from_str::<AuthResponse>(&response.body)
            .map_err(|e| MailxError::SerializationError(e.to_string()))?;
        if !is_visible_ascii(&auth_res.token) {
            return Err(MailxError::Unauthorized(
                "Server did not return a valid session token.".to_string(),
            ));
        }
        self.set_credentials(Some(clean_key), Some(auth_res.token.clone()));
        Ok(auth_res)
    }

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
        let is_cookie = raw_token.starts_with(COOKIE_PREFIX);
        let scoped_path = Self::resolve_scoped_path(path, is_cookie);

        let body = json_body.filter(|b| !b.is_empty());
        let mut headers = json_headers(body.is_some());
        headers.extend(Self::auth_headers(&raw_token)?);

        let response = self.execute(&Call {
            method,
            path: &scoped_path,
            headers,
            body: body.map(|b| b.as_bytes().to_vec()),
            timeout: API_TIMEOUT,
            retry_on_html: true,
        })?;

        if response.status == 401 {
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
            return Err(Self::parse_error_body(401, &response.body));
        }

        let response = Self::require_success(response)?;
        if response.body.trim().is_empty() {
            if let Ok(empty_val) = serde_json::from_str::<T>("{}") {
                return Ok(empty_val);
            }
        }
        serde_json::from_str::<T>(&response.body)
            .map_err(|e| MailxError::SerializationError(e.to_string()))
    }

    pub fn execute_raw_json(
        &self,
        method: &str,
        path: &str,
        json_body: Option<&str>,
    ) -> Result<Value, MailxError> {
        if !is_http_token(method) {
            return Err(MailxError::InvalidBaseUrl(
                "Request method is invalid.".to_string(),
            ));
        }
        let raw_token = self.current_token().unwrap_or_default();
        let scoped_path = Self::resolve_scoped_path(path, raw_token.starts_with(COOKIE_PREFIX));

        let body = json_body.filter(|b| !b.is_empty());
        let mut headers = vec![(
            "Accept".to_string(),
            "application/json, text/plain, text/csv, */*".to_string(),
        )];
        if body.is_some() {
            headers.push(("Content-Type".to_string(), "application/json".to_string()));
        }
        if !raw_token.is_empty() {
            headers.extend(Self::auth_headers(&raw_token)?);
        }

        let response = Self::require_success(self.execute(&Call {
            method,
            path: &scoped_path,
            headers,
            body: body.map(|b| b.as_bytes().to_vec()),
            timeout: RAW_TIMEOUT,
            retry_on_html: false,
        })?)?;
        Ok(parse_json_or_text(&response.body))
    }

    pub fn import_aliases_csv(&self, csv_text: &str) -> Result<Value, MailxError> {
        if csv_text.trim().is_empty() {
            return Err(MailxError::SerializationError(
                "CSV content is empty.".to_string(),
            ));
        }

        let raw_token = self.current_token().unwrap_or_default();
        let path =
            Self::resolve_scoped_path("/v1/aliases/import", raw_token.starts_with(COOKIE_PREFIX));
        let boundary = format!("----MailxCsvImport{}", random_hex(12));
        let mut body = Vec::with_capacity(csv_text.len() + 256);
        body.extend_from_slice(
            format!(
                "--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"aliases.csv\"\r\nContent-Type: text/csv; charset=utf-8\r\n\r\n"
            )
            .as_bytes(),
        );
        body.extend_from_slice(csv_text.as_bytes());
        body.extend_from_slice(format!("\r\n--{boundary}--\r\n").as_bytes());

        let mut headers = json_headers(false);
        headers.push((
            "Content-Type".to_string(),
            format!("multipart/form-data; boundary={boundary}"),
        ));
        if !raw_token.is_empty() {
            headers.extend(Self::auth_headers(&raw_token)?);
        }

        let response = Self::require_success(self.execute(&Call {
            method: "POST",
            path: &path,
            headers,
            body: Some(body),
            timeout: IMPORT_TIMEOUT,
            retry_on_html: false,
        })?)?;
        Ok(parse_json_or_text(&response.body))
    }

    pub fn fetch_aliases(&self, filter: &AliasFilter) -> Result<AliasListResponse, MailxError> {
        let path = build_aliases_query_path(filter);
        self.execute_authenticated("GET", &path, None, true)
    }

    pub fn fetch_defaults(&self) -> Result<DefaultsResponse, MailxError> {
        self.execute_authenticated("GET", "/v1/api/defaults", None, true)
    }

    pub fn create_alias(&self, req: &AliasRequest) -> Result<CreateAliasResponse, MailxError> {
        let body = serde_json::to_string(req)
            .map_err(|e| MailxError::SerializationError(e.to_string()))?;
        self.execute_authenticated("POST", "/v1/api/alias", Some(&body), true)
    }

    pub fn update_alias(&self, alias_id: &str, req: &AliasRequest) -> Result<Value, MailxError> {
        let body = serde_json::to_string(req)
            .map_err(|e| MailxError::SerializationError(e.to_string()))?;
        let path = format!("/v1/api/alias/{}", encode_path_segment(alias_id));
        self.execute_authenticated("PUT", &path, Some(&body), true)
    }

    pub fn pin_alias(&self, alias_id: &str, pinned: bool) -> Result<Value, MailxError> {
        let body = serde_json::to_string(&AliasPinRequest { pinned })
            .map_err(|e| MailxError::SerializationError(e.to_string()))?;
        let path = format!("/v1/api/alias/{}/pin", encode_path_segment(alias_id));
        self.execute_authenticated("PUT", &path, Some(&body), true)
    }

    pub fn delete_alias(&self, alias_id: &str) -> Result<Value, MailxError> {
        let path = format!("/v1/api/alias/{}", encode_path_segment(alias_id));
        self.execute_authenticated("DELETE", &path, None, true)
    }

    pub fn logout(&self) -> Result<Value, MailxError> {
        let res = self.execute_authenticated("POST", "/v1/api/logout", None, false);
        self.set_credentials(None, None);
        res
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::VecDeque;
    use std::sync::Mutex;

    const VALID_KEY: &str =
        "550e8400-e29b-41d4-a716-446655440000.a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6";
    const BASE: &str = "https://api.example.org";

    type Scripted = Result<HttpResponse, TransportError>;

    struct MockTransport {
        responses: Mutex<VecDeque<Scripted>>,
        requests: Mutex<Vec<HttpRequest>>,
    }

    impl MockTransport {
        fn new(responses: Vec<Scripted>) -> Arc<Self> {
            Arc::new(Self {
                responses: Mutex::new(responses.into()),
                requests: Mutex::new(Vec::new()),
            })
        }

        fn requests(&self) -> Vec<HttpRequest> {
            self.requests.lock().unwrap().clone()
        }
    }

    impl Transport for MockTransport {
        fn send(&self, request: &HttpRequest) -> Result<HttpResponse, TransportError> {
            self.requests.lock().unwrap().push(request.clone());
            self.responses
                .lock()
                .unwrap()
                .pop_front()
                .unwrap_or_else(|| Err(TransportError("no scripted response left".to_string())))
        }
    }

    fn reply(status: u16, body: &str) -> Scripted {
        reply_with(status, &[], body)
    }

    fn reply_with(status: u16, headers: &[(&str, &str)], body: &str) -> Scripted {
        Ok(HttpResponse {
            status,
            headers: headers
                .iter()
                .map(|(k, v)| (k.to_string(), v.to_string()))
                .collect(),
            body: body.to_string(),
        })
    }

    fn unreachable_host() -> Scripted {
        Err(TransportError("connection refused".to_string()))
    }

    fn client_with(responses: Vec<Scripted>) -> (MailxClient, Arc<MockTransport>) {
        let transport = MockTransport::new(responses);
        let client = MailxClient::with_transport(BASE, false, transport.clone()).unwrap();
        (client, transport)
    }

    fn body_json(request: &HttpRequest) -> Value {
        serde_json::from_slice(request.body.as_deref().unwrap()).unwrap()
    }

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
    fn plain_http_is_limited_to_exact_loopback_hosts() {
        for allowed in [
            "http://localhost:3000",
            "http://LOCALHOST",
            "http://127.0.0.1:8080",
            "http://[::1]:8080",
        ] {
            assert!(MailxClient::new(allowed, true).is_ok(), "{allowed}");
            assert!(MailxClient::new(allowed, false).is_err(), "{allowed}");
        }
        for rejected in [
            "http://localhost.evil.example",
            "http://127.0.0.1.evil.example",
            "http://10.0.2.2:8080",
            "http://10.0.2.2.evil.example",
            "http://192.168.1.10:8080",
            "http://evil.example",
            "http://localhost@evil.example",
            "ftp://localhost",
            "localhost:3000",
            "mailx.net",
        ] {
            assert!(MailxClient::new(rejected, true).is_err(), "{rejected}");
        }
    }

    #[test]
    fn malformed_urls_are_rejected() {
        for rejected in [
            "https://",
            "https:///v1",
            "https://user:secret@api.example.org",
            "https://api.example.org/with space",
            "https://api.exa\nmple.org",
            "https://evil.example\\.api.mailx.net",
            "https://api.example.org\\@evil.example",
            "https://api.example.org\\evil.example",
            "https://api.example.org:",
            "https://api.example.org:0",
            "https://api.example.org:99999",
            "https://api.example.org:80a",
            "https://api..example.org",
            "https://.example.org",
            "https://münchen.example",
            "https://%65vil.example",
            "https://[::1",
            "https://[evil]:443",
            "https://::1",
            "https://api.example.org?next=/v1",
            "https://api.example.org#/v1",
            "",
        ] {
            assert!(MailxClient::new(rejected, true).is_err(), "{rejected:?}");
        }
        let upper = MailxClient::new("HTTPS://API.Example.org/", false).unwrap();
        assert_eq!(upper.base_url(), "https://API.Example.org");
    }

    #[test]
    fn well_formed_authorities_are_accepted() {
        for accepted in [
            "https://api.example.org:8443",
            "https://xn--mnchen-3ya.example",
            "https://mail-1.example.org",
            "https://[2001:db8::1]:8443",
            "https://192.0.2.10:8080",
        ] {
            assert!(MailxClient::new(accepted, false).is_ok(), "{accepted}");
        }
    }

    #[test]
    fn suffix_stripping_never_eats_the_host() {
        for url in [
            "https://api",
            "https://v1",
            "https://login/",
            "https://api/",
        ] {
            let normalized = MailxClient::normalize_base_url(url);
            assert!(normalized.starts_with("https://"), "{url} -> {normalized}");
        }
        assert_eq!(
            MailxClient::normalize_base_url("https://mail.example.org/api/v1"),
            "https://mail.example.org"
        );
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
    fn candidates_stay_on_the_configured_host() {
        let (client, _) = client_with(Vec::new());
        assert_eq!(
            client.candidate_urls("/v1/api/aliases").unwrap(),
            vec![
                format!("{BASE}/v1/api/aliases"),
                format!("{BASE}/api/v1/aliases")
            ]
        );
        assert_eq!(
            client.candidate_urls("/v1/login").unwrap(),
            vec![format!("{BASE}/v1/login")]
        );

        let self_hosted = MailxClient::with_transport(
            "https://mail.example.org",
            false,
            MockTransport::new(Vec::new()),
        )
        .unwrap();
        for path in ["/v1/api/aliases", "/v1/login", "/v1/user/logout"] {
            for url in self_hosted.candidate_urls(path).unwrap() {
                assert!(url.starts_with("https://mail.example.org/"), "{url}");
            }
        }
    }

    #[test]
    fn request_paths_cannot_change_the_authority() {
        let (client, transport) = client_with(Vec::new());
        for bad in [
            "@evil.example/x",
            "//evil.example/x",
            "v1/x",
            "/v1/a b",
            "/v1/a\\b",
            "/v1/../x",
            "/v1/./x",
            "/v1/%2e%2E/x",
            "/v1/.%2e/x",
            "/v1//x",
            "/v1/x/",
            "/v1/x/..",
            "/v1/x/..?y=1",
            "/",
            "",
        ] {
            let result = client.execute_raw_json("GET", bad, None);
            assert!(
                matches!(result, Err(MailxError::InvalidBaseUrl(_))),
                "{bad:?}"
            );
        }
        assert!(transport.requests().is_empty());
    }

    #[test]
    fn dots_inside_ids_and_queries_are_still_allowed() {
        for path in [
            "/v1/api/aliases?search=../x&page=1",
            "/v1/api/alias/a.b",
            "/v1/api/alias/..%2Fx",
            "/v1/api/alias/%252e%252e",
            "/v1/api/alias/...",
        ] {
            assert!(validate_request_path(path).is_ok(), "{path}");
        }
    }

    #[test]
    fn request_methods_must_be_http_tokens() {
        let (client, transport) = client_with(Vec::new());
        for bad in ["", "GE T", "GET\r\nX-Evil: 1", "G@T", "GET /x HTTP/1.1\r\n"] {
            let result = client.execute_raw_json(bad, "/v1/api/aliases", None);
            assert!(
                matches!(result, Err(MailxError::InvalidBaseUrl(_))),
                "{bad:?}"
            );
        }
        assert!(transport.requests().is_empty());
    }

    #[test]
    fn wrong_route_falls_back_to_the_alternate_path_on_the_same_host() {
        let (client, transport) = client_with(vec![reply(404, ""), reply(200, r#"{"ok":true}"#)]);
        client.set_credentials(None, Some("token-123".to_string()));
        let value = client
            .execute_raw_json("GET", "/v1/api/aliases", None)
            .unwrap();
        assert_eq!(value["ok"], true);

        let requests = transport.requests();
        assert_eq!(requests.len(), 2);
        assert_eq!(requests[0].url, format!("{BASE}/v1/api/aliases"));
        assert_eq!(requests[1].url, format!("{BASE}/api/v1/aliases"));
        for request in &requests {
            assert!(request.url.starts_with(BASE));
            assert_eq!(request.header("authorization"), Some("Bearer token-123"));
        }
    }

    #[test]
    fn last_candidate_error_is_reported_without_further_guessing() {
        let (client, transport) = client_with(vec![reply(405, ""), reply(404, "")]);
        client.set_credentials(None, Some("token-123".to_string()));
        let error = client
            .execute_raw_json("GET", "/v1/api/aliases", None)
            .unwrap_err();
        assert!(matches!(error, MailxError::ApiError { status: 404, .. }));
        assert_eq!(transport.requests().len(), 2);
    }

    #[test]
    fn transport_failures_try_the_alternate_path_then_report_network_error() {
        let (client, transport) = client_with(vec![unreachable_host(), unreachable_host()]);
        let error = client
            .execute_raw_json("GET", "/v1/api/aliases", None)
            .unwrap_err();
        assert!(matches!(error, MailxError::NetworkError(_)));
        assert_eq!(transport.requests().len(), 2);

        let (client, transport) = client_with(vec![unreachable_host(), reply(200, "{}")]);
        assert!(client
            .execute_raw_json("GET", "/v1/api/aliases", None)
            .is_ok());
        assert_eq!(transport.requests().len(), 2);
    }

    #[test]
    fn server_errors_are_not_retried() {
        let (client, transport) = client_with(vec![reply(500, r#"{"error":"boom"}"#)]);
        let error = client
            .execute_raw_json("GET", "/v1/api/aliases", None)
            .unwrap_err();
        match error {
            MailxError::ApiError { status, message } => {
                assert_eq!(status, 500);
                assert_eq!(message, "boom");
            }
            other => panic!("unexpected error {other:?}"),
        }
        assert_eq!(transport.requests().len(), 1);
    }

    #[test]
    fn cross_origin_redirects_never_receive_credentials() {
        let (client, transport) = client_with(vec![
            reply_with(302, &[("Location", "https://evil.example/steal")], ""),
            reply(200, "{}"),
        ]);
        client.set_credentials(None, Some("token-123".to_string()));
        let error = client
            .execute_raw_json("GET", "/v1/login", None)
            .unwrap_err();
        assert!(error.to_string().contains("different host"));
        let requests = transport.requests();
        assert_eq!(requests.len(), 1);
        assert!(requests.iter().all(|r| r.url.starts_with(BASE)));
    }

    #[test]
    fn refused_redirect_may_still_use_the_alternate_path_on_the_same_host() {
        let (client, transport) = client_with(vec![
            reply_with(302, &[("Location", "https://evil.example/steal")], ""),
            reply(200, r#"{"ok":true}"#),
        ]);
        client.set_credentials(None, Some("token-123".to_string()));
        let value = client
            .execute_raw_json("GET", "/v1/api/aliases", None)
            .unwrap();
        assert_eq!(value["ok"], true);
        let requests = transport.requests();
        assert_eq!(requests.len(), 2);
        assert!(requests.iter().all(|r| r.url.starts_with(BASE)));
    }

    #[test]
    fn scheme_downgrade_and_protocol_relative_redirects_are_refused() {
        for location in [
            "http://api.example.org/v1/api/aliases",
            "//evil.example/x",
            "https://api.example.org.evil.example/x",
            "https://api.example.org:8443/x",
        ] {
            let (client, transport) = client_with(vec![
                reply_with(301, &[("Location", location)], ""),
                reply(200, "{}"),
            ]);
            client.set_credentials(None, Some("token-123".to_string()));
            assert!(
                client.execute_raw_json("GET", "/v1/login", None).is_err(),
                "{location}"
            );
            assert_eq!(transport.requests().len(), 1, "{location}");
        }
    }

    #[test]
    fn same_origin_redirects_are_followed_with_method_and_credentials() {
        let (client, transport) = client_with(vec![
            reply_with(307, &[("Location", "/v1/api/aliases/")], ""),
            reply(200, r#"{"followed":true}"#),
        ]);
        client.set_credentials(None, Some("token-123".to_string()));
        let value = client
            .execute_raw_json("PUT", "/v1/api/aliases", Some(r#"{"a":1}"#))
            .unwrap();
        assert_eq!(value["followed"], true);

        let requests = transport.requests();
        assert_eq!(requests.len(), 2);
        assert_eq!(requests[1].url, format!("{BASE}/v1/api/aliases/"));
        assert_eq!(requests[1].method, "PUT");
        assert_eq!(requests[1].body, requests[0].body);
        assert_eq!(
            requests[1].header("authorization"),
            Some("Bearer token-123")
        );
    }

    #[test]
    fn post_redirected_with_302_becomes_a_bodyless_get() {
        let (client, transport) = client_with(vec![
            reply_with(302, &[("Location", "https://api.example.org/v1/other")], ""),
            reply(200, "{}"),
        ]);
        client
            .execute_raw_json("POST", "/v1/login", Some(r#"{"a":1}"#))
            .unwrap();
        let requests = transport.requests();
        assert_eq!(requests[1].method, "GET");
        assert!(requests[1].body.is_none());
        assert!(requests[1].header("content-type").is_none());
    }

    #[test]
    fn put_and_delete_keep_their_method_on_301_and_302() {
        for (method, status) in [("DELETE", 301), ("DELETE", 302), ("PUT", 301), ("PUT", 302)] {
            let (client, transport) = client_with(vec![
                reply_with(status, &[("Location", "/v1/api/moved")], ""),
                reply(200, "{}"),
            ]);
            client.set_credentials(None, Some("token-123".to_string()));
            client
                .execute_raw_json(method, "/v1/api/alias/abc", Some(r#"{"a":1}"#))
                .unwrap();
            let requests = transport.requests();
            assert_eq!(requests[1].method, method, "{method} {status}");
            assert_eq!(requests[1].url, format!("{BASE}/v1/api/moved"));
            assert_eq!(requests[1].body, requests[0].body);
        }
    }

    #[test]
    fn see_other_turns_any_method_into_a_bodyless_get() {
        for method in ["POST", "PUT", "DELETE", "PATCH"] {
            let (client, transport) = client_with(vec![
                reply_with(303, &[("Location", "/v1/api/result")], ""),
                reply(200, "{}"),
            ]);
            client.set_credentials(None, Some("token-123".to_string()));
            client
                .execute_raw_json(method, "/v1/api/alias/abc", Some(r#"{"a":1}"#))
                .unwrap();
            let requests = transport.requests();
            assert_eq!(requests[1].method, "GET", "{method}");
            assert!(requests[1].body.is_none(), "{method}");
            assert!(requests[1].header("content-type").is_none(), "{method}");
        }
    }

    #[test]
    fn redirect_targets_with_backslashes_or_control_characters_are_refused() {
        for location in [
            "/\\evil.example/x",
            "https://api.example.org\\@evil.example/x",
            "/v1/x\r\nX-Evil: 1",
            "https://api.example.org/v1\t/x",
        ] {
            let (client, transport) = client_with(vec![
                reply_with(302, &[("Location", location)], ""),
                reply(200, "{}"),
            ]);
            client.set_credentials(None, Some("token-123".to_string()));
            assert!(
                client.execute_raw_json("GET", "/v1/login", None).is_err(),
                "{location:?}"
            );
            assert_eq!(transport.requests().len(), 1, "{location:?}");
        }
    }

    #[test]
    fn redirect_loops_are_cut_off() {
        let looping = || reply_with(302, &[("Location", "/v1/loop")], "");
        let (client, transport) = client_with(vec![looping(), looping(), looping(), looping()]);
        let error = client
            .execute_raw_json("GET", "/v1/loop", None)
            .unwrap_err();
        assert!(error.to_string().contains("Too many redirects"));
        assert_eq!(transport.requests().len(), MAX_SAME_ORIGIN_REDIRECTS + 1);
    }

    #[test]
    fn redirect_without_location_is_an_error_status() {
        let (client, _) = client_with(vec![reply(302, "")]);
        let error = client
            .execute_raw_json("GET", "/v1/login", None)
            .unwrap_err();
        assert!(matches!(error, MailxError::ApiError { status: 302, .. }));
    }

    #[test]
    fn authorization_style_follows_the_session_type() {
        let (client, transport) = client_with(vec![reply(200, "{}"), reply(200, "{}")]);
        client.set_credentials(None, Some("cookie:abc123".to_string()));
        client
            .execute_raw_json("GET", "/v1/api/aliases", None)
            .unwrap();
        client.set_credentials(None, Some("bearer-token".to_string()));
        client
            .execute_raw_json("GET", "/v1/api/aliases", None)
            .unwrap();

        let requests = transport.requests();
        assert_eq!(requests[0].url, format!("{BASE}/v1/aliases"));
        assert_eq!(requests[0].header("cookie"), Some("authn=abc123"));
        assert!(requests[0].header("authorization").is_none());
        assert_eq!(requests[1].url, format!("{BASE}/v1/api/aliases"));
        assert_eq!(
            requests[1].header("authorization"),
            Some("Bearer bearer-token")
        );
        assert!(requests[1].header("cookie").is_none());
    }

    #[test]
    fn malformed_stored_tokens_are_rejected_before_sending() {
        for token in ["bad token", "line\r\nbreak: x", "cookie:a;b", "cookie:a b"] {
            let (client, transport) = client_with(Vec::new());
            client.set_credentials(None, Some(token.to_string()));
            let error = client
                .execute_raw_json("GET", "/v1/api/aliases", None)
                .unwrap_err();
            assert!(matches!(error, MailxError::Unauthorized(_)), "{token:?}");
            assert!(transport.requests().is_empty(), "{token:?}");
        }
    }

    #[test]
    fn raw_requests_without_a_session_send_no_credentials() {
        let (client, transport) = client_with(vec![reply(200, "{}")]);
        client
            .execute_raw_json("POST", "/v1/resetpassword", Some("{}"))
            .unwrap();
        let request = &transport.requests()[0];
        assert!(request.header("authorization").is_none());
        assert!(request.header("cookie").is_none());
        assert_eq!(request.header("content-type"), Some("application/json"));
    }

    #[test]
    fn raw_responses_are_normalised_to_json() {
        let (client, _) = client_with(vec![
            reply(200, ""),
            reply(200, "a,b\n1,2"),
            reply(200, "[1,2]"),
        ]);
        assert_eq!(
            client.execute_raw_json("GET", "/v1/x", None).unwrap()["message"],
            "OK"
        );
        assert_eq!(
            client.execute_raw_json("GET", "/v1/x", None).unwrap()["text"],
            "a,b\n1,2"
        );
        assert!(client
            .execute_raw_json("GET", "/v1/x", None)
            .unwrap()
            .is_array());
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

    #[test]
    fn error_bodies_map_to_typed_errors() {
        assert!(matches!(
            MailxClient::parse_error_body(401, r#"{"error":"nope"}"#),
            MailxError::Unauthorized(m) if m == "nope"
        ));
        assert!(matches!(
            MailxClient::parse_error_body(429, r#"{"message":"slow down"}"#),
            MailxError::RateLimited(m) if m == "slow down"
        ));
        let step_up = MailxClient::parse_error_body(403, r#"{"error":"verify","code":80001}"#);
        assert!(matches!(step_up, MailxError::StepUpRequired(_)));
        assert_eq!(step_up.error_code(), Some(80001));
        assert!(matches!(
            MailxClient::parse_error_body(502, ""),
            MailxError::ApiError { status: 502, message } if message == "HTTP 502"
        ));
        assert!(matches!(
            MailxClient::parse_error_body(500, "plain failure text"),
            MailxError::ApiError { message, .. } if message == "plain failure text"
        ));
        assert!(matches!(
            MailxClient::parse_error_body(500, r#"{"error":"","message":"fallback"}"#),
            MailxError::ApiError { message, .. } if message == "fallback"
        ));
        assert!(matches!(
            MailxClient::parse_error_body(500, r#"{"code":"not-a-number","error":"e"}"#),
            MailxError::ApiError { message, .. } if message == "e"
        ));
    }

    #[test]
    fn oversized_error_bodies_are_truncated() {
        let huge = "x".repeat(5000);
        let message = MailxClient::parse_error_body(500, &huge).to_string();
        assert!(message.chars().count() < 400);
        let html = format!("<html><body>{}</body></html>", "y ".repeat(2000));
        let message = MailxClient::parse_error_body(500, &html).to_string();
        assert!(message.chars().count() < 400);
        for field in ["error", "message"] {
            let json = format!(r#"{{"{field}":"{}"}}"#, "z".repeat(5000));
            let message = MailxClient::parse_error_body(500, &json).to_string();
            assert!(message.chars().count() < 400, "{field}");
        }
    }

    #[test]
    fn only_markup_documents_count_as_html() {
        assert!(is_html_body("<!DOCTYPE html><html></html>"));
        assert!(is_html_body("  \n<HTML><BODY>x</BODY></HTML>"));
        assert!(is_html_body("<?xml version=\"1.0\"?><head></head>"));
        assert!(!is_html_body(r#"{"description":"<body>"}"#));
        assert!(!is_html_body(r#"["<head>"]"#));
        assert!(!is_html_body("plain text with <body> inside"));
        assert!(!is_html_body(""));
    }

    #[test]
    fn json_mentioning_markup_is_not_retried_as_html() {
        let (client, transport) =
            client_with(vec![reply(200, r#"{"token":"tok-9","domain":"<body>"}"#)]);
        let auth = client.authenticate(VALID_KEY).unwrap();
        assert_eq!(auth.domain, "<body>");
        assert_eq!(transport.requests().len(), 1);
    }

    #[test]
    fn a_meaningful_error_from_the_first_route_survives_a_bare_404_on_the_alternate() {
        let (client, transport) = client_with(vec![
            reply(404, r#"{"error":"alias not found"}"#),
            reply(404, "404 page not found"),
        ]);
        client.set_credentials(None, Some("token-123".to_string()));
        let error = client
            .execute_raw_json("DELETE", "/v1/api/alias/abc", None)
            .unwrap_err();
        assert!(
            matches!(&error, MailxError::ApiError { status: 404, message } if message == "alias not found"),
            "{error}"
        );
        assert_eq!(transport.requests().len(), 2);
    }

    #[test]
    fn two_api_errors_report_the_last_route() {
        let (client, _) = client_with(vec![
            reply(404, r#"{"error":"first"}"#),
            reply(404, r#"{"error":"second"}"#),
        ]);
        client.set_credentials(None, Some("token-123".to_string()));
        let error = client
            .execute_raw_json("DELETE", "/v1/api/alias/abc", None)
            .unwrap_err();
        assert!(
            matches!(&error, MailxError::ApiError { status: 404, message } if message == "second"),
            "{error}"
        );
    }

    #[test]
    fn transport_debug_output_hides_credentials_and_bodies() {
        let request = HttpRequest {
            method: "GET".to_string(),
            url: BASE.to_string(),
            headers: vec![
                (
                    "Authorization".to_string(),
                    "Bearer super-token".to_string(),
                ),
                ("cookie".to_string(), "authn=super-cookie".to_string()),
                ("Accept".to_string(), "application/json".to_string()),
            ],
            body: Some(b"super-body".to_vec()),
            timeout: API_TIMEOUT,
        };
        let rendered = format!("{request:?}");
        assert!(!rendered.contains("super-"), "{rendered}");
        assert!(rendered.contains("Authorization: <redacted>"));
        assert!(rendered.contains("cookie: <redacted>"));
        assert!(rendered.contains("Accept: application/json"));

        let response = HttpResponse {
            status: 200,
            headers: vec![(
                "Set-Cookie".to_string(),
                "authn=super-cookie; Path=/".to_string(),
            )],
            body: r#"{"token":"super-token"}"#.to_string(),
        };
        let rendered = format!("{response:?}");
        assert!(!rendered.contains("super-"), "{rendered}");
        assert!(rendered.contains("Set-Cookie: <redacted>"));
    }

    #[test]
    fn cookie_login_sets_the_session_and_posts_json_without_credentials() {
        let (client, transport) = client_with(vec![
            reply_with(
                200,
                &[("Set-Cookie", "authn=abc123; Path=/; HttpOnly; Secure")],
                "{}",
            ),
            reply(500, "{}"),
        ]);
        let auth = client
            .login_with_credentials(" user@example.org ", "pw", " 123456 ")
            .unwrap();
        assert_eq!(auth.token, "cookie:abc123");
        assert_eq!(client.current_token().as_deref(), Some("cookie:abc123"));
        assert!(client.is_web_cookie_session());

        let login = &transport.requests()[0];
        assert_eq!(login.url, format!("{BASE}/v1/login"));
        assert_eq!(login.method, "POST");
        assert!(login.header("authorization").is_none());
        assert!(login.header("cookie").is_none());
        let sent = body_json(login);
        assert_eq!(sent["email"], "user@example.org");
        assert_eq!(sent["password"], "pw");
        assert_eq!(sent["otp"], "123456");
        assert_eq!(sent["remember"], true);
    }

    #[test]
    fn login_without_session_cookie_is_unauthorized() {
        let (client, _) = client_with(vec![reply(200, "{}")]);
        let error = client
            .login_with_credentials("user@example.org", "pw", "")
            .unwrap_err();
        assert!(matches!(error, MailxError::Unauthorized(_)));
        assert!(client.current_token().is_none());
    }

    #[test]
    fn login_rejects_blank_input_without_a_request() {
        let (client, transport) = client_with(Vec::new());
        assert!(client.login_with_credentials("  ", "pw", "").is_err());
        assert!(client.login_with_credentials("a@b.c", "", "").is_err());
        assert!(transport.requests().is_empty());
    }

    #[test]
    fn totp_challenge_is_surfaced_with_its_code() {
        let (client, _) = client_with(vec![reply(
            400,
            r#"{"message":"Two-factor authentication is required to continue.","code":70001}"#,
        )]);
        let error = client
            .login_with_credentials("user@example.org", "pw", "")
            .unwrap_err();
        assert!(matches!(error, MailxError::TotpRequired(_)));
        assert_eq!(error.error_code(), Some(70001));
    }

    #[test]
    fn passkey_begin_for_an_email_falls_back_to_the_discoverable_flow() {
        let (client, transport) = client_with(vec![
            reply(500, r#"{"error":"unknown"}"#),
            reply_with(
                200,
                &[("Set-Cookie", "authntemp=tmp1; Path=/")],
                r#"{"publicKey":{"challenge":"c"}}"#,
            ),
        ]);
        let begin = client.passkey_login_begin("user@example.org").unwrap();
        assert_eq!(begin["discoverable"], true);
        assert_eq!(begin["ceremony_cookie"], "tmp1");
        assert_eq!(begin["options"]["publicKey"]["challenge"], "c");

        let requests = transport.requests();
        assert_eq!(requests[0].url, format!("{BASE}/v1/login/begin"));
        assert_eq!(requests[1].url, format!("{BASE}/v1/login/passkey/begin"));
        assert_eq!(body_json(&requests[0])["email"], "user@example.org");
        assert!(body_json(&requests[1]).get("email").is_none());
    }

    #[test]
    fn passkey_begin_discoverable_failure_is_reported() {
        let (client, transport) = client_with(vec![reply(500, r#"{"error":"down"}"#)]);
        let error = client.passkey_login_begin("").unwrap_err();
        assert!(matches!(error, MailxError::ApiError { status: 500, .. }));
        assert_eq!(transport.requests().len(), 1);
    }

    #[test]
    fn passkey_finish_sets_the_cookie_session_and_forwards_the_ceremony_cookie() {
        let (client, transport) = client_with(vec![
            reply_with(
                200,
                &[("Set-Cookie", "authn=sess9; Path=/")],
                r#"{"email":"me@example.org"}"#,
            ),
            reply(500, "{}"),
        ]);
        let auth = client
            .passkey_login_finish("tmp1", r#"{"id":"cred"}"#, true, "fallback@example.org")
            .unwrap();
        assert_eq!(auth.token, "cookie:sess9");
        assert_eq!(auth.recipient, "me@example.org");

        let finish = &transport.requests()[0];
        assert_eq!(finish.url, format!("{BASE}/v1/login/passkey/finish"));
        assert_eq!(finish.header("cookie"), Some("authntemp=tmp1"));
        assert_eq!(finish.body.as_deref(), Some(br#"{"id":"cred"}"#.as_slice()));
    }

    #[test]
    fn passkey_finish_rejects_a_malformed_ceremony_cookie() {
        let (client, transport) = client_with(Vec::new());
        let error = client
            .passkey_login_finish("a;authn=evil", "{}", true, "")
            .unwrap_err();
        assert!(matches!(error, MailxError::Unauthorized(_)));
        assert!(transport.requests().is_empty());
    }

    #[test]
    fn passkey_registration_requires_a_cookie_session() {
        let (client, transport) = client_with(Vec::new());
        assert!(matches!(
            client.passkey_register_begin(),
            Err(MailxError::Unauthorized(_))
        ));
        client.set_credentials(None, Some("api-key-token".to_string()));
        assert!(matches!(
            client.passkey_register_begin(),
            Err(MailxError::Unauthorized(_))
        ));
        assert!(matches!(
            client.passkey_register_finish("", "{}"),
            Err(MailxError::Unauthorized(_))
        ));
        assert!(transport.requests().is_empty());
    }

    #[test]
    fn passkey_registration_roundtrip_updates_the_cookie() {
        let (client, transport) = client_with(vec![
            reply_with(
                200,
                &[("Set-Cookie", "authntemp=reg1; Path=/")],
                r#"{"publicKey":{}}"#,
            ),
            reply_with(200, &[("Set-Cookie", "authn=renewed; Path=/")], ""),
        ]);
        client.set_credentials(None, Some("cookie:old".to_string()));
        let begin = client.passkey_register_begin().unwrap();
        assert_eq!(begin["ceremony_cookie"], "reg1");
        let finished = client
            .passkey_register_finish("reg1", r#"{"id":"x"}"#)
            .unwrap();
        assert_eq!(finished["message"], "OK");
        assert_eq!(client.current_token().as_deref(), Some("cookie:renewed"));

        let requests = transport.requests();
        assert_eq!(requests[0].header("cookie"), Some("authn=old"));
        assert_eq!(
            requests[1].header("cookie"),
            Some("authn=old; authntemp=reg1")
        );
    }

    #[test]
    fn csv_import_sends_multipart_with_a_fresh_random_boundary() {
        let (client, transport) = client_with(vec![
            reply(200, r#"{"imported":1}"#),
            reply(200, r#"{"imported":1}"#),
        ]);
        client.set_credentials(None, Some("token-123".to_string()));
        let result = client.import_aliases_csv("a,b\n1,2").unwrap();
        assert_eq!(result["imported"], 1);
        client.import_aliases_csv("a,b\n1,2").unwrap();

        let requests = transport.requests();
        assert_eq!(requests[0].url, format!("{BASE}/v1/aliases/import"));
        let content_type = requests[0].header("content-type").unwrap();
        let boundary = content_type.split("boundary=").nth(1).unwrap();
        let body = String::from_utf8(requests[0].body.clone().unwrap()).unwrap();
        assert!(body.starts_with(&format!("--{boundary}\r\n")));
        assert!(body.contains("name=\"file\"; filename=\"aliases.csv\""));
        assert!(body.contains("a,b\n1,2"));
        assert!(body.ends_with(&format!("--{boundary}--\r\n")));
        let other = requests[1].header("content-type").unwrap();
        assert_ne!(content_type, other);
    }

    #[test]
    fn csv_import_rejects_empty_input_without_a_request() {
        let (client, transport) = client_with(Vec::new());
        assert!(client.import_aliases_csv("  \n ").is_err());
        assert!(transport.requests().is_empty());
    }

    #[test]
    fn alias_ids_are_encoded_into_a_single_path_segment() {
        let (client, transport) = client_with(vec![reply(200, "{}")]);
        client.set_credentials(None, Some("token-123".to_string()));
        client.delete_alias("../../v1/api/logout").unwrap();
        let requests = transport.requests();
        assert_eq!(
            requests[0].url,
            format!("{BASE}/v1/api/alias/..%2F..%2Fv1%2Fapi%2Flogout")
        );
        assert_eq!(requests[0].method, "DELETE");
    }

    #[test]
    fn ids_that_would_collapse_the_path_are_rejected_before_sending() {
        let (client, transport) = client_with(Vec::new());
        client.set_credentials(None, Some("token-123".to_string()));
        for id in ["..", ".", ""] {
            assert!(
                matches!(client.delete_alias(id), Err(MailxError::InvalidBaseUrl(_))),
                "delete {id:?}"
            );
            assert!(
                matches!(
                    client.pin_alias(id, true),
                    Err(MailxError::InvalidBaseUrl(_))
                ),
                "pin {id:?}"
            );
        }
        assert!(transport.requests().is_empty());
    }

    #[test]
    fn logout_clears_credentials_even_when_the_server_fails() {
        let (client, _) = client_with(vec![reply(500, "{}")]);
        client.set_credentials(Some(VALID_KEY.to_string()), Some("token-123".to_string()));
        assert!(client.logout().is_err());
        assert!(client.current_token().is_none());
    }

    #[test]
    fn logout_for_cookie_sessions_uses_the_user_scope() {
        let (client, transport) = client_with(vec![reply(200, "{}")]);
        client.set_credentials(None, Some("cookie:abc".to_string()));
        client.logout().unwrap();
        let request = &transport.requests()[0];
        assert_eq!(request.url, format!("{BASE}/v1/user/logout"));
        assert_eq!(request.method, "POST");
        assert!(client.current_token().is_none());
    }

    #[test]
    fn livez_reports_health_and_errors() {
        let (client, transport) = client_with(vec![reply(200, "ok"), reply(503, "")]);
        assert!(client.check_livez().unwrap());
        assert!(client.check_livez().is_err());
        let requests = transport.requests();
        assert_eq!(requests[0].url, format!("{BASE}/livez"));
        assert!(requests[0].header("authorization").is_none());
    }

    #[test]
    fn authenticate_validates_the_key_before_any_request() {
        let (client, transport) = client_with(Vec::new());
        assert!(matches!(
            client.authenticate("short"),
            Err(MailxError::InvalidAccessKey(_))
        ));
        assert!(transport.requests().is_empty());
    }

    #[test]
    fn authenticate_retries_the_alternate_path_when_html_is_returned() {
        let (client, transport) = client_with(vec![
            reply(200, "<!doctype html><html><body>app</body></html>"),
            reply(200, r#"{"token":"tok-1","domain":"mailx.net"}"#),
        ]);
        let auth = client.authenticate(VALID_KEY).unwrap();
        assert_eq!(auth.token, "tok-1");
        assert_eq!(client.current_token().as_deref(), Some("tok-1"));

        let requests = transport.requests();
        assert_eq!(requests[0].url, format!("{BASE}/v1/api/authenticate"));
        assert_eq!(requests[1].url, format!("{BASE}/api/v1/authenticate"));
        assert_eq!(body_json(&requests[0])["access_key"], VALID_KEY);
    }

    #[test]
    fn authenticate_without_a_usable_token_is_unauthorized() {
        for body in [
            "{}",
            r#"{"error":"invalid key"}"#,
            r#"{"token":""}"#,
            r#"{"token":null}"#,
            r#"{"token":"has space"}"#,
        ] {
            let (client, _) = client_with(vec![reply(200, body)]);
            let result = client.authenticate(VALID_KEY);
            assert!(matches!(result, Err(MailxError::Unauthorized(_))), "{body}");
            assert!(client.current_token().is_none(), "{body}");
        }
    }

    #[test]
    fn expired_api_key_sessions_reauthenticate_once() {
        let (client, transport) = client_with(vec![
            reply(401, r#"{"error":"expired"}"#),
            reply(200, r#"{"token":"tok-2"}"#),
            reply(200, r#"{"deleted":true}"#),
        ]);
        client.set_credentials(Some(VALID_KEY.to_string()), Some("tok-1".to_string()));
        let value = client.delete_alias("alias-1").unwrap();
        assert_eq!(value["deleted"], true);

        let requests = transport.requests();
        assert_eq!(requests.len(), 3);
        assert_eq!(requests[0].header("authorization"), Some("Bearer tok-1"));
        assert_eq!(requests[1].url, format!("{BASE}/v1/api/authenticate"));
        assert_eq!(requests[2].header("authorization"), Some("Bearer tok-2"));
    }

    #[test]
    fn cookie_sessions_do_not_reauthenticate_on_401() {
        let (client, transport) = client_with(vec![reply(401, r#"{"error":"expired"}"#)]);
        client.set_credentials(Some(VALID_KEY.to_string()), Some("cookie:abc".to_string()));
        let error = client.delete_alias("alias-1").unwrap_err();
        assert!(matches!(error, MailxError::Unauthorized(_)));
        assert_eq!(transport.requests().len(), 1);
    }

    #[test]
    fn failed_reauthentication_reports_the_original_401() {
        let (client, transport) = client_with(vec![
            reply(401, r#"{"error":"expired"}"#),
            reply(401, r#"{"error":"revoked"}"#),
        ]);
        client.set_credentials(Some(VALID_KEY.to_string()), Some("tok-1".to_string()));
        let error = client.delete_alias("alias-1").unwrap_err();
        assert!(matches!(error, MailxError::Unauthorized(m) if m == "expired"));
        assert_eq!(transport.requests().len(), 2);
    }

    #[test]
    fn authenticated_calls_without_a_session_fail_fast() {
        let (client, transport) = client_with(Vec::new());
        assert!(matches!(
            client.delete_alias("alias-1"),
            Err(MailxError::Unauthorized(_))
        ));
        assert!(transport.requests().is_empty());
    }

    #[test]
    fn empty_success_bodies_are_accepted_for_json_values() {
        let (client, _) = client_with(vec![reply(204, "")]);
        client.set_credentials(None, Some("token-123".to_string()));
        assert!(client.delete_alias("alias-1").is_ok());
    }

    #[test]
    fn unparsable_success_bodies_are_serialization_errors() {
        let (client, _) = client_with(vec![reply(200, "definitely not json")]);
        client.set_credentials(None, Some("token-123".to_string()));
        assert!(matches!(
            client.delete_alias("alias-1"),
            Err(MailxError::SerializationError(_))
        ));
    }
}
