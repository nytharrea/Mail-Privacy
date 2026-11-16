use crate::models::*;
use crate::secret::SecretString;
use crate::utils::{build_aliases_query_path, validate_access_key};
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use std::sync::{Arc, RwLock};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MailxError {
    InvalidBaseUrl(String),
    InvalidAccessKey(String),
    Unauthorized(String),
    RateLimited(String),
    ApiError { status: u16, message: String },
    NetworkError(String),
    SerializationError(String),
}

impl std::fmt::Display for MailxError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            MailxError::InvalidBaseUrl(msg) => write!(f, "Geçersiz sunucu adresi: {}", msg),
            MailxError::InvalidAccessKey(msg) => write!(f, "{}", msg),
            MailxError::Unauthorized(msg) => write!(f, "Oturum yetkisi geçersiz: {}", msg),
            MailxError::RateLimited(msg) => write!(f, "İstek sınırı aşıldı: {}", msg),
            MailxError::ApiError { status, message } => {
                write!(f, "API Hatası ({}): {}", status, message)
            }
            MailxError::NetworkError(msg) => write!(f, "Ağ hatası: {}", msg),
            MailxError::SerializationError(msg) => write!(f, "Veri çözümleme hatası: {}", msg),
        }
    }
}

#[derive(Deserialize)]
struct ApiErrorBody {
    error: Option<String>,
    message: Option<String>,
}

/// Mailx REST API İstemcisi
///
/// - Varsayılan olarak yalnızca `https://` kabul eder (`allow_insecure_localhost` yalnızca yerel test içindir).
/// - `401 Unauthorized` alındığında saklanan `AccessKey` ile **yalnızca 1 kez** sessizce yeniden
///   `POST /v1/api/authenticate` dener (5 deneme / 10 dk rate-limit döngüsüne girmemek için).
#[derive(Clone)]
pub struct MailxClient {
    base_url: String,
    access_key: Arc<RwLock<Option<SecretString>>>,
    bearer_token: Arc<RwLock<Option<SecretString>>>,
}

impl MailxClient {
    pub fn new(base_url: &str, allow_insecure_localhost: bool) -> Result<Self, MailxError> {
        let trimmed = base_url.trim().trim_end_matches('/').to_string();
        if !trimmed.starts_with("https://") {
            let is_localhost = trimmed.starts_with("http://localhost")
                || trimmed.starts_with("http://127.0.0.1")
                || trimmed.starts_with("http://10.0.2.2");
            if !(allow_insecure_localhost && is_localhost) {
                return Err(MailxError::InvalidBaseUrl(
                    "Güvenlik nedeniyle yalnızca HTTPS sunucu adresleri desteklenir.".to_string(),
                ));
            }
        }
        Ok(Self {
            base_url: trimmed,
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

    fn parse_error_body(status: u16, raw: &str) -> MailxError {
        let parsed = serde_json::from_str::<ApiErrorBody>(raw)
            .ok()
            .and_then(|b| b.error.or(b.message))
            .unwrap_or_else(|| {
                if raw.trim().is_empty() {
                    format!("HTTP {}", status)
                } else {
                    raw.trim().to_string()
                }
            });

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
    pub fn check_livez(&self) -> Result<bool, MailxError> {
        let url = format!("{}/livez", self.base_url);
        let resp = ureq::get(&url)
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
    pub fn authenticate(&self, access_key_raw: &str) -> Result<AuthResponse, MailxError> {
        validate_access_key(access_key_raw).map_err(MailxError::InvalidAccessKey)?;
        let clean_key = access_key_raw.trim().to_string();
        let url = format!("{}/v1/api/authenticate", self.base_url);
        let payload = serde_json::json!({ "access_key": clean_key });

        let resp = ureq::post(&url)
            .set("Content-Type", "application/json")
            .timeout(std::time::Duration::from_secs(15))
            .send_string(&payload.to_string());

        match resp {
            Ok(r) => {
                let text = r
                    .into_string()
                    .map_err(|e| MailxError::NetworkError(e.to_string()))?;
                let auth_res: AuthResponse = serde_json::from_str(&text)
                    .map_err(|e| MailxError::SerializationError(e.to_string()))?;
                self.set_credentials(Some(clean_key), Some(auth_res.token.clone()));
                Ok(auth_res)
            }
            Err(ureq::Error::Status(code, r)) => {
                let body = r.into_string().unwrap_or_default();
                Err(Self::parse_error_body(code, &body))
            }
            Err(e) => Err(MailxError::NetworkError(e.to_string())),
        }
    }

    #[cfg(feature = "network")]
    fn execute_authenticated<T: DeserializeOwned>(
        &self,
        method: &str,
        path: &str,
        json_body: Option<&str>,
        allow_reauth: bool,
    ) -> Result<T, MailxError> {
        let token = self
            .current_token()
            .ok_or_else(|| MailxError::Unauthorized("Oturum anahtarı bulunamadı.".to_string()))?;

        let url = format!("{}{}", self.base_url, path);
        let req = ureq::request(method, &url)
            .set("Authorization", &format!("Bearer {}", token))
            .set("Accept", "application/json")
            .timeout(std::time::Duration::from_secs(15));

        let resp = if let Some(body) = json_body {
            req.set("Content-Type", "application/json")
                .send_string(body)
        } else {
            req.call()
        };

        match resp {
            Ok(r) => {
                let text = r
                    .into_string()
                    .map_err(|e| MailxError::NetworkError(e.to_string()))?;
                serde_json::from_str::<T>(&text)
                    .map_err(|e| MailxError::SerializationError(e.to_string()))
            }
            Err(ureq::Error::Status(401, r)) => {
                let body = r.into_string().unwrap_or_default();
                // 401 durumunda saklı Access Key varsa yalnızca 1 kez sessizce yenile
                if allow_reauth {
                    let saved_key = self
                        .access_key
                        .read()
                        .ok()
                        .and_then(|g| g.as_ref().map(|s| s.expose().to_string()));
                    if let Some(key) = saved_key {
                        if self.authenticate(&key).is_ok() {
                            return self.execute_authenticated(method, path, json_body, false);
                        }
                    }
                }
                Err(Self::parse_error_body(401, &body))
            }
            Err(ureq::Error::Status(code, r)) => {
                let body = r.into_string().unwrap_or_default();
                Err(Self::parse_error_body(code, &body))
            }
            Err(e) => Err(MailxError::NetworkError(e.to_string())),
        }
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
    fn test_https_enforcement() {
        assert!(MailxClient::new("https://mailx.net", false).is_ok());
        assert!(MailxClient::new("http://mailx.net", false).is_err());
        assert!(MailxClient::new("http://localhost:3000", true).is_ok());
    }

    #[test]
    fn test_alias_deserialization_and_modify_roundtrip() {
        let sample_json = r#"{
            "aliases": [
                {
                    "id": "b3d7f6a2-1122-3344-5566-778899aabbcc",
                    "name": "silent.forest88@mailx.net",
                    "enabled": true,
                    "description": "GitHub Hesabı",
                    "recipients": "kipchak@tutanota.com",
                    "from_name": "Nytharrea",
                    "wildcard": false,
                    "pinned": true,
                    "paused_until": null,
                    "stats": {
                        "forwards": 14,
                        "blocks": 2,
                        "replies": 1,
                        "sends": 0
                    },
                    "is_custom_domain": false,
                    "is_domain_verified": null,
                    "is_domain_enabled": true
                }
            ],
            "total": 1
        }"#;

        let parsed: AliasListResponse = serde_json::from_str(sample_json).unwrap();
        assert_eq!(parsed.total, 1);
        let first = &parsed.aliases[0];
        assert_eq!(first.name, "silent.forest88@mailx.net");
        assert_eq!(first.stats.forwards, 14);
        assert!(!first.is_paused());

        // Read-modify-write güvenliği testi
        let mut update_req = AliasRequest::from_existing(first);
        update_req.enabled = false;
        assert_eq!(update_req.domain, "mailx.net");
        assert_eq!(update_req.recipients, "kipchak@tutanota.com");
        assert!(!update_req.enabled);
    }
}
