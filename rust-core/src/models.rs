use serde::{Deserialize, Serialize};

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

/// Alias yönlendirme ve engelleme istatistikleri (`model.AliasStats`)
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasStats {
    #[serde(default)]
    pub forwards: i64,
    #[serde(default)]
    pub blocks: i64,
    #[serde(default)]
    pub replies: i64,
    #[serde(default)]
    pub sends: i64,
}

/// Mailx Alias veri modeli (`api/internal/model/alias.go` & `wxt/lib/types.ts`)
/// Bilinmeyen yeni alanları tolere etmek için varsayılanlar tanımlanmıştır.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Alias {
    pub id: String,
    #[serde(default)]
    pub created_at: Option<String>,
    #[serde(default)]
    pub updated_at: Option<String>,
    #[serde(default)]
    pub deleted_at: Option<String>,
    pub name: String,
    #[serde(default)]
    pub enabled: bool,
    #[serde(default)]
    pub description: String,
    /// Virgülle ayrılmış alıcı e-posta adresleri (örn: "user@example.com,backup@example.com")
    #[serde(default)]
    pub recipients: String,
    #[serde(default)]
    pub from_name: String,
    #[serde(default)]
    pub wildcard: bool,
    #[serde(default)]
    pub pinned: bool,
    #[serde(default)]
    pub paused_until: Option<String>,
    #[serde(default)]
    pub stats: AliasStats,
    #[serde(default)]
    pub is_custom_domain: bool,
    #[serde(default)]
    pub is_domain_verified: Option<bool>,
    #[serde(default)]
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

/// `GET /v1/api/aliases` yanıt modeli (`model.AliasList`)
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasListResponse {
    #[serde(default)]
    pub aliases: Vec<Alias>,
    #[serde(default)]
    pub total: usize,
}

/// `POST /v1/api/alias` ve `PUT /v1/api/alias/:id` istek gövdesi (`api.AliasReq`)
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasRequest {
    pub description: String,
    pub enabled: bool,
    /// Virgülle ayrılmış doğrulanmış alıcı e-postaları (zorunlu)
    pub recipients: String,
    pub from_name: String,
    pub format: String,
    pub domain: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub wildcard_local_part: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub wildcard_delimiter: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub local_part: Option<String>,
}

impl AliasRequest {
    /// Mevcut bir `Alias` nesnesini koruyarak güvenli kısmi güncelleme (`PUT /v1/api/alias/:id`)
    /// için tam nesne oluşturur (`PUT` uç noktası tüm alanları bekler).
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

/// `POST /v1/api/alias` başarılı yanıtı
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CreateAliasResponse {
    pub message: String,
    pub alias: Alias,
}

/// `PUT /v1/api/alias/:id/pin` istek gövdesi (`api.AliasPinReq`)
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasPinRequest {
    pub pinned: bool,
}

/// Özel alan adı modeli (`wxt/lib/types.ts` -> `CustomDomain`)
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CustomDomain {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub enabled: bool,
    #[serde(default)]
    pub owner_verified_at: Option<String>,
    #[serde(default)]
    pub mx_verified_at: Option<String>,
    #[serde(default)]
    pub send_verified_at: Option<String>,
}

/// `POST /v1/api/authenticate` yanıtı
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AuthResponse {
    pub token: String,
    #[serde(default)]
    pub domain: String,
    #[serde(default)]
    pub domains: Vec<String>,
    #[serde(default)]
    pub custom_domains: Vec<CustomDomain>,
    #[serde(default)]
    pub recipient: String,
    #[serde(default)]
    pub recipients: Vec<String>,
    #[serde(default)]
    pub alias_format: String,
}

/// `GET /v1/api/defaults` yanıtı
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DefaultsResponse {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub domain: String,
    #[serde(default)]
    pub domains: Vec<String>,
    #[serde(default)]
    pub custom_domains: Vec<CustomDomain>,
    #[serde(default)]
    pub recipient: String,
    #[serde(default)]
    pub recipients: Vec<String>,
    #[serde(default)]
    pub alias_format: String,
    #[serde(default)]
    pub from_name: String,
    #[serde(default)]
    pub log_issues: bool,
    #[serde(default)]
    pub remove_header: bool,
    #[serde(default)]
    pub list_unsubscribe_behaviour: i32,
    #[serde(default)]
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
    /// Arama metni
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
    pub aliases: Vec<Alias>,
}
