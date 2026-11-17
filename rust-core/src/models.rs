use serde::{Deserialize, Deserializer, Serialize};

/// Go backend (`fiber.Map` / `gorm`) nil slice veya boş değerleri JSON `null` olarak
/// döndürdüğünde (`"custom_domains": null`, `"recipients": null`, `"aliases": null` vb.)
/// `serde_json`'ın hata vermesini önleyip `Default::default()` değerine dönüştürür.
fn deserialize_null_default<'de, D, T>(deserializer: D) -> Result<T, D::Error>
where
    T: Default + Deserialize<'de>,
    D: Deserializer<'de>,
{
    let opt = Option::<T>::deserialize(deserializer)?;
    Ok(opt.unwrap_or_default())
}

/// Mailx API'sinin desteklediği alias oluşturma formatları (`api/internal/model/alias_name.go`)
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum AliasFormat {
    Words,
    Random,
    Uuid,
    Wildcard,
    Custom,
}

impl AliasFormat {
    pub fn as_str(&self) -> &'static str {
        match self {
            AliasFormat::Words => "words",
            AliasFormat::Random => "random",
            AliasFormat::Uuid => "uuid",
            AliasFormat::Wildcard => "wildcard",
            AliasFormat::Custom => "custom",
        }
    }
}

/// Alias yönlendirme ve engelleme istatistikleri (`model.AliasStats` - `api/internal/model/alias.go`)
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasStats {
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub forwards: i64,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub blocks: i64,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub replies: i64,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub sends: i64,
}

/// Mailx Alias veri modeli (`api/internal/model/alias.go` & `wxt/lib/types.ts`)
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Alias {
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub id: String,
    #[serde(default)]
    pub created_at: Option<String>,
    #[serde(default)]
    pub updated_at: Option<String>,
    #[serde(default)]
    pub deleted_at: Option<serde_json::Value>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub name: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub enabled: bool,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub description: String,
    /// Virgülle ayrılmış alıcı e-posta adresleri (örn: "user@example.com,backup@example.com")
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub recipients: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub from_name: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub wildcard: bool,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub origin: i32,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub pinned: bool,
    #[serde(default)]
    pub paused_until: Option<String>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub stats: AliasStats,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub is_custom_domain: bool,
    #[serde(default)]
    pub is_domain_verified: Option<bool>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub is_domain_enabled: bool,
}

impl Alias {
    /// Virgülle ayrılmış `recipients` alanını dizi olarak döndürür.
    pub fn recipient_list(&self) -> Vec<String> {
        self.recipients
            .split(',')
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
            .collect()
    }

    /// Alias'ın şu anda duraklatılmış (paused) olup olmadığını kontrol eder.
    pub fn is_paused(&self) -> bool {
        self.paused_until
            .as_ref()
            .map(|s| !s.trim().is_empty())
            .unwrap_or(false)
    }
}

/// `GET /v1/api/aliases` ve `GET /v1/aliases` yanıt modeli (`model.AliasList`)
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasListResponse {
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub aliases: Vec<Alias>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub total: usize,
}

/// `POST /v1/api/alias` ve `PUT /v1/api/alias/:id` istek gövdesi (`api.AliasReq`)
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasRequest {
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub description: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub enabled: bool,
    /// Virgülle ayrılmış doğrulanmış alıcı e-postaları (zorunlu)
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub recipients: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub from_name: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub format: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub domain: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub wildcard_local_part: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub wildcard_delimiter: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub local_part: Option<String>,
}

impl AliasRequest {
    /// Mevcut bir `Alias` nesnesini koruyarak güvenli güncelleme (`PUT /v1/api/alias/:id`)
    /// için tam nesne oluşturur (`api/internal/transport/api/alias.go` -> `UpdateAlias`).
    pub fn from_existing(alias: &Alias) -> Self {
        let domain = alias.name.split('@').nth(1).unwrap_or_default().to_string();
        Self {
            description: alias.description.clone(),
            enabled: alias.enabled,
            recipients: alias.recipients.clone(),
            from_name: alias.from_name.clone(),
            format: if alias.wildcard {
                "wildcard".to_string()
            } else {
                "words".to_string()
            },
            domain,
            wildcard_local_part: None,
            wildcard_delimiter: None,
            local_part: None,
        }
    }
}

/// `POST /v1/api/alias` başarılı yanıtı (`201 Created`)
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CreateAliasResponse {
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub message: String,
    pub alias: Alias,
}

/// `PUT /v1/api/alias/:id/pin` istek gövdesi (`api.AliasPinReq`)
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasPinRequest {
    pub pinned: bool,
}

/// `PUT /v1/alias/:id/pause` istek gövdesi (`api.AliasPauseReq`)
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasPauseRequest {
    pub value: i32,
    pub unit: String,
}

/// Özel alan adı modeli (`model.Domain` - `api/internal/model/domain.go`)
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CustomDomain {
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub id: String,
    #[serde(default)]
    pub created_at: Option<String>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub name: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub description: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub recipient: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub from_name: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub enabled: bool,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub catch_all: bool,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub create_alias: bool,
    #[serde(default)]
    pub owner_verified_at: Option<String>,
    #[serde(default)]
    pub mx_verified_at: Option<String>,
    #[serde(default)]
    pub send_verified_at: Option<String>,
}

/// `POST /v1/api/authenticate` ve `POST /v1/login` birleşik yanıt modeli
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AuthResponse {
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub token: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub domain: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub domains: Vec<String>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub custom_domains: Vec<CustomDomain>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub recipient: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub recipients: Vec<String>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub alias_format: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub from_name: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub log_issues: bool,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub remove_header: bool,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub list_unsubscribe_behaviour: i32,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub block_trackers: bool,
}

/// `GET /v1/api/defaults` ve `GET /v1/defaults` yanıtı (`api/internal/transport/api/access_key.go`)
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DefaultsResponse {
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub id: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub domain: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub domains: Vec<String>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub custom_domains: Vec<CustomDomain>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub recipient: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub recipients: Vec<String>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub alias_format: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub from_name: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub log_issues: bool,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub remove_header: bool,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub list_unsubscribe_behaviour: i32,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub block_trackers: bool,
}

/// `GET /v1/api/aliases` filtreleme parametreleri
#[derive(Debug, Clone)]
pub struct AliasFilter {
    /// 1-tabanlı sayfa numarası (`page=1`)
    pub page: usize,
    /// Sayfa başına kayıt limiti (mobilde varsayılan 50, maksimum 1000)
    pub limit: usize,
    /// `created_at`, `updated_at` veya `name`
    pub sort_by: String,
    /// `ASC` veya `DESC`
    pub sort_order: String,
    /// `active_inactive`, `active`, `inactive`, `deleted`, `all`
    pub status: String,
    /// `"true"`, `"false"` veya `""`
    pub wildcard: String,
    /// Arama metni (`^[-a-zA-Z0-9 ._+@]+$`)
    pub search: String,
}

impl Default for AliasFilter {
    fn default() -> Self {
        Self {
            page: 1,
            limit: 50,
            sort_by: "created_at".to_string(),
            sort_order: "DESC".to_string(),
            status: "active_inactive".to_string(),
            wildcard: String::new(),
            search: String::new(),
        }
    }
}

/// Çevrimdışı önbellek (Offline Cache) veri kapsayıcısı
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct OfflineCache {
    pub updated_at_epoch_ms: u64,
    pub defaults: Option<DefaultsResponse>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub aliases: Vec<Alias>,
}
