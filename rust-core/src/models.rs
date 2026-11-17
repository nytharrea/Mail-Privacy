use serde::{Deserialize, Deserializer, Serialize};

fn deserialize_null_default<'de, D, T>(deserializer: D) -> Result<T, D::Error>
where
    T: Default + Deserialize<'de>,
    D: Deserializer<'de>,
{
    let opt = Option::<T>::deserialize(deserializer)?;
    Ok(opt.unwrap_or_default())
}

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
    pub fn recipient_list(&self) -> Vec<String> {
        self.recipients
            .split(',')
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
            .collect()
    }

    pub fn is_paused(&self) -> bool {
        self.paused_until
            .as_ref()
            .map(|s| !s.trim().is_empty())
            .unwrap_or(false)
    }

    pub fn is_deleted(&self) -> bool {
        match &self.deleted_at {
            None => false,
            Some(serde_json::Value::Null) => false,
            Some(serde_json::Value::String(s)) => !s.trim().is_empty() && s.trim() != "null",
            Some(_) => true,
        }
    }
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasListResponse {
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub aliases: Vec<Alias>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub total: usize,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasRequest {
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub description: String,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub enabled: bool,
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

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CreateAliasResponse {
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub message: String,
    pub alias: Alias,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasPinRequest {
    pub pinned: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasPauseRequest {
    pub value: i32,
    pub unit: String,
}

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

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RemovedTrackerItem {
    pub domain: String,
    pub element: String,
    pub reason: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct TrackersReport {
    pub total: usize,
    pub trackers: Vec<RemovedTrackerItem>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct PhoneticToken {
    pub index: usize,
    pub symbol: String,
    pub word: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct QrMatrix {
    pub width: usize,
    pub rows: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct PrivacyPostureReport {
    pub score: u8,
    pub grade: String,
    pub active_aliases: usize,
    pub total_aliases: usize,
    pub block_rate_percent: u8,
    pub pgp_active: bool,
    pub trackers_blocked: bool,
    pub header_stripped: bool,
    pub rules_active: usize,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SanitizedUrlItem {
    pub original_url: String,
    pub clean_url: String,
    pub removed_params: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct UrlSanitizeReport {
    pub cleaned_text: String,
    pub urls_inspected: usize,
    pub trackers_removed: usize,
    pub items: Vec<SanitizedUrlItem>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct HeaderHopItem {
    pub index: usize,
    pub from_host: String,
    pub by_host: String,
    pub protocol: String,
    pub timestamp: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct EmailHeaderAnalysis {
    pub security_score: u8,
    pub verdict: String,
    pub spf_status: String,
    pub dkim_status: String,
    pub dmarc_status: String,
    pub from_header: String,
    pub return_path: String,
    pub reply_to: String,
    pub subject: String,
    pub from_return_path_mismatch: bool,
    pub reply_to_mismatch: bool,
    pub mailx_relay_detected: bool,
    pub client_ip_stripped: bool,
    pub hop_count: usize,
    pub hops: Vec<HeaderHopItem>,
    pub findings: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasHygieneIssue {
    pub alias_id: String,
    pub alias_name: String,
    pub category: String,
    pub severity: String,
    pub detail: String,
    pub recommended_action: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasHygieneReport {
    pub hygiene_score: u8,
    pub grade: String,
    pub total_inspected: usize,
    pub burned_count: usize,
    pub unlabeled_count: usize,
    pub duplicate_service_count: usize,
    pub dormant_count: usize,
    pub recipient_concentration_percent: u8,
    pub issues: Vec<AliasHygieneIssue>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ContextualAliasCandidate {
    pub label: String,
    pub alias_email: String,
    pub local_part: String,
    pub entropy_bits: f32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ContextualAliasSuggestions {
    pub normalized_service: String,
    pub domain: String,
    pub candidates: Vec<ContextualAliasCandidate>,
}

#[derive(Debug, Clone)]
pub struct AliasFilter {
    pub page: usize,
    pub limit: usize,
    pub sort_by: String,
    pub sort_order: String,
    pub status: String,
    pub wildcard: String,
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

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct OfflineCache {
    pub updated_at_epoch_ms: u64,
    pub defaults: Option<DefaultsResponse>,
    #[serde(default, deserialize_with = "deserialize_null_default")]
    pub aliases: Vec<Alias>,
}
