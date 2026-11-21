use crate::models::Alias;
use serde::{Deserialize, Serialize};
use std::cmp::Ordering;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum AliasSortKey {
    CreatedAt,
    UpdatedAt,
    Name,
    Forwards,
    Blocks,
    Replies,
    Sends,
}

impl AliasSortKey {
    pub fn from_str_key(raw: &str) -> Self {
        match raw.trim().to_ascii_lowercase().as_str() {
            "updated_at" | "updatedat" => Self::UpdatedAt,
            "name" | "alias" | "email" => Self::Name,
            "forwards" | "forwarded" => Self::Forwards,
            "blocks" | "blocked" => Self::Blocks,
            "replies" | "replied" => Self::Replies,
            "sends" | "sent" => Self::Sends,
            _ => Self::CreatedAt,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasQuerySpec {
    pub chip_key: String,
    pub search_query: String,
    pub sort_field: String,
    pub sort_descending: bool,
    pub pinned_first: bool,
}

impl Default for AliasQuerySpec {
    fn default() -> Self {
        Self {
            chip_key: "active_inactive".to_string(),
            search_query: String::new(),
            sort_field: "created_at".to_string(),
            sort_descending: true,
            pinned_first: true,
        }
    }
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasDashboardMetrics {
    pub total_count: usize,
    pub active_count: usize,
    pub inactive_count: usize,
    pub pinned_count: usize,
    pub wildcard_count: usize,
    pub deleted_count: usize,
    pub custom_domain_count: usize,
    pub paused_count: usize,
    pub total_forwards: i64,
    pub total_blocks: i64,
    pub total_replies: i64,
    pub total_sends: i64,
    pub spotlight_ids: Vec<String>,
}

pub fn is_alias_deleted(alias: &Alias) -> bool {
    match &alias.deleted_at {
        None => false,
        Some(serde_json::Value::Null) => false,
        Some(serde_json::Value::String(s)) => {
            let trimmed = s.trim();
            !trimmed.is_empty() && !trimmed.eq_ignore_ascii_case("null")
        }
        Some(_) => true,
    }
}

pub fn alias_total_activity(alias: &Alias) -> i64 {
    alias
        .stats
        .forwards
        .saturating_add(alias.stats.blocks)
        .saturating_add(alias.stats.replies)
        .saturating_add(alias.stats.sends)
}

pub fn matches_chip_filter(alias: &Alias, chip_key: &str) -> bool {
    let deleted = is_alias_deleted(alias);
    match chip_key.trim() {
        "pinned_only" => alias.pinned && !deleted,
        "wildcard_only" => alias.wildcard && !deleted,
        "active" => alias.enabled && !deleted,
        "inactive" => !alias.enabled && !deleted,
        "deleted" => deleted,
        _ => !deleted,
    }
}

pub fn matches_search_query(alias: &Alias, raw_query: &str) -> bool {
    let q = raw_query.trim().to_ascii_lowercase();
    if q.is_empty() {
        return true;
    }
    alias.name.to_ascii_lowercase().contains(&q)
        || alias.description.to_ascii_lowercase().contains(&q)
        || alias.recipients.to_ascii_lowercase().contains(&q)
        || alias.from_name.to_ascii_lowercase().contains(&q)
}

pub fn compare_aliases(a: &Alias, b: &Alias, spec: &AliasQuerySpec) -> Ordering {
    if spec.pinned_first && a.pinned != b.pinned {
        return if a.pinned {
            Ordering::Less
        } else {
            Ordering::Greater
        };
    }

    let sort_key = AliasSortKey::from_str_key(&spec.sort_field);
    let raw_cmp = match sort_key {
        AliasSortKey::CreatedAt => {
            let ca = a.created_at.as_deref().unwrap_or("");
            let cb = b.created_at.as_deref().unwrap_or("");
            ca.cmp(cb)
        }
        AliasSortKey::UpdatedAt => {
            let ua = a.updated_at.as_deref().unwrap_or("");
            let ub = b.updated_at.as_deref().unwrap_or("");
            ua.cmp(ub)
        }
        AliasSortKey::Name => a
            .name
            .to_ascii_lowercase()
            .cmp(&b.name.to_ascii_lowercase()),
        AliasSortKey::Forwards => a.stats.forwards.cmp(&b.stats.forwards),
        AliasSortKey::Blocks => a.stats.blocks.cmp(&b.stats.blocks),
        AliasSortKey::Replies => a.stats.replies.cmp(&b.stats.replies),
        AliasSortKey::Sends => a.stats.sends.cmp(&b.stats.sends),
    };

    let directed = if spec.sort_descending {
        raw_cmp.reverse()
    } else {
        raw_cmp
    };

    if directed == Ordering::Equal {
        a.id.cmp(&b.id)
    } else {
        directed
    }
}

pub fn filter_and_sort_aliases(aliases: &[Alias], spec: &AliasQuerySpec) -> Vec<Alias> {
    let mut filtered: Vec<Alias> = aliases
        .iter()
        .filter(|a| {
            matches_chip_filter(a, &spec.chip_key) && matches_search_query(a, &spec.search_query)
        })
        .cloned()
        .collect();

    filtered.sort_by(|a, b| compare_aliases(a, b, spec));
    filtered
}

pub fn filter_and_sort_alias_ids_json(
    aliases_json: &str,
    spec: &AliasQuerySpec,
) -> Result<Vec<String>, String> {
    let parsed: Vec<Alias> = serde_json::from_str(aliases_json)
        .map_err(|e| format!("Failed to deserialize aliases JSON: {}", e))?;
    let sorted = filter_and_sort_aliases(&parsed, spec);
    Ok(sorted.into_iter().map(|a| a.id).collect())
}

pub fn compute_dashboard_metrics(aliases: &[Alias]) -> AliasDashboardMetrics {
    let mut metrics = AliasDashboardMetrics {
        total_count: aliases.len(),
        ..Default::default()
    };

    let mut non_deleted: Vec<&Alias> = Vec::with_capacity(aliases.len());

    for alias in aliases {
        metrics.total_forwards = metrics.total_forwards.saturating_add(alias.stats.forwards);
        metrics.total_blocks = metrics.total_blocks.saturating_add(alias.stats.blocks);
        metrics.total_replies = metrics.total_replies.saturating_add(alias.stats.replies);
        metrics.total_sends = metrics.total_sends.saturating_add(alias.stats.sends);

        if is_alias_deleted(alias) {
            metrics.deleted_count += 1;
        } else {
            non_deleted.push(alias);
            if alias.enabled {
                metrics.active_count += 1;
            } else {
                metrics.inactive_count += 1;
            }
            if alias.pinned {
                metrics.pinned_count += 1;
            }
            if alias.wildcard {
                metrics.wildcard_count += 1;
            }
            if alias.is_custom_domain {
                metrics.custom_domain_count += 1;
            }
            if alias.is_paused() {
                metrics.paused_count += 1;
            }
        }
    }

    non_deleted.sort_by(|a, b| match b.pinned.cmp(&a.pinned) {
        Ordering::Equal => alias_total_activity(b).cmp(&alias_total_activity(a)),
        other => other,
    });

    metrics.spotlight_ids = non_deleted
        .into_iter()
        .take(4)
        .map(|a| a.id.clone())
        .collect();

    metrics
}

pub fn merge_alias_collections(primary: &[Alias], secondary: &[Alias]) -> Vec<Alias> {
    let mut out: Vec<Alias> = Vec::with_capacity(primary.len() + secondary.len());
    for item in primary.iter().chain(secondary.iter()) {
        if !out.iter().any(|existing| existing.id == item.id) {
            out.push(item.clone());
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::AliasStats;

    fn sample_alias(id: &str, name: &str, enabled: bool, pinned: bool, forwards: i64) -> Alias {
        Alias {
            id: id.to_string(),
            created_at: Some(format!("2026-11-1{}T10:00:00Z", id)),
            updated_at: Some(format!("2026-11-1{}T12:00:00Z", id)),
            deleted_at: None,
            name: name.to_string(),
            enabled,
            description: format!("Desc for {}", name),
            recipients: "user@example.com".to_string(),
            from_name: String::new(),
            wildcard: name.starts_with("*+"),
            origin: 0,
            pinned,
            paused_until: None,
            stats: AliasStats {
                forwards,
                blocks: 0,
                replies: 0,
                sends: 0,
            },
            is_custom_domain: false,
            is_domain_verified: Some(true),
            is_domain_enabled: true,
        }
    }

    #[test]
    fn test_filter_and_sort_aliases_pinned_and_forwards() {
        let a1 = sample_alias("1", "alpha@mailx.net", true, false, 10);
        let a2 = sample_alias("2", "beta@mailx.net", true, true, 2);
        let a3 = sample_alias("3", "*+wild@mailx.net", false, false, 25);
        let list = vec![a1, a2, a3];

        let spec = AliasQuerySpec {
            chip_key: "active_inactive".to_string(),
            search_query: String::new(),
            sort_field: "forwards".to_string(),
            sort_descending: true,
            pinned_first: true,
        };
        let res = filter_and_sort_aliases(&list, &spec);
        assert_eq!(res.len(), 3);
        assert_eq!(res[0].id, "2");
        assert_eq!(res[1].id, "3");
        assert_eq!(res[2].id, "1");
    }

    #[test]
    fn test_dashboard_metrics_computation() {
        let a1 = sample_alias("1", "alpha@mailx.net", true, true, 7);
        let a2 = sample_alias("2", "*+wild@mailx.net", false, false, 3);
        let metrics = compute_dashboard_metrics(&[a1, a2]);
        assert_eq!(metrics.total_count, 2);
        assert_eq!(metrics.active_count, 1);
        assert_eq!(metrics.inactive_count, 1);
        assert_eq!(metrics.pinned_count, 1);
        assert_eq!(metrics.wildcard_count, 1);
        assert_eq!(metrics.total_forwards, 10);
        assert_eq!(
            metrics.spotlight_ids,
            vec!["1".to_string(), "2".to_string()]
        );
    }
}
