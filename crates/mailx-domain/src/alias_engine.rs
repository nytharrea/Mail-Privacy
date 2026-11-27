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

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum AliasChip {
    HighSpam,
    Paused { days_left: u32 },
    Disabled,
    Wildcard,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum AliasKind {
    Standard,
    Wildcard,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasRow {
    pub id: String,
    pub local: String,
    pub domain: String,
    pub description: String,
    pub kind: AliasKind,
    pub chip: Option<AliasChip>,
    pub pinned: bool,
    pub tint: u8,
    pub forwards: u32,
    pub blocks: u32,
    pub replies: u32,
    pub sends: u32,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AliasPage {
    pub rows: Vec<AliasRow>,
    pub total: u32,
}

pub fn tint_for(address: &str) -> u8 {
    let mut hash: u32 = 0x811c_9dc5;
    for byte in address.trim().as_bytes() {
        hash ^= u32::from(byte.to_ascii_lowercase());
        hash = hash.wrapping_mul(0x0100_0193);
    }
    (hash % 8) as u8
}

fn days_from_civil(year: i64, month: i64, day: i64) -> i64 {
    let y = if month <= 2 { year - 1 } else { year };
    let era = if y >= 0 { y } else { y - 399 } / 400;
    let yoe = y - era * 400;
    let mp = if month > 2 { month - 3 } else { month + 9 };
    let doy = (153 * mp + 2) / 5 + day - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    era * 146_097 + doe - 719_468
}

pub fn parse_iso_utc_unix(raw: &str) -> Option<i64> {
    let s = raw.trim();
    if s.len() < 19 {
        return None;
    }
    let bytes = s.as_bytes();
    if bytes[4] != b'-' || bytes[7] != b'-' || (bytes[10] != b'T' && bytes[10] != b' ') {
        return None;
    }
    if bytes[13] != b':' || bytes[16] != b':' {
        return None;
    }
    let year: i64 = s[0..4].parse().ok()?;
    let month: i64 = s[5..7].parse().ok()?;
    let day: i64 = s[8..10].parse().ok()?;
    let hour: i64 = s[11..13].parse().ok()?;
    let minute: i64 = s[14..16].parse().ok()?;
    let second: i64 = s[17..19].parse().ok()?;
    if !(1..=12).contains(&month)
        || !(1..=31).contains(&day)
        || !(0..=23).contains(&hour)
        || !(0..=59).contains(&minute)
        || !(0..=60).contains(&second)
    {
        return None;
    }
    let mut rest = &s[19..];
    if let Some(after_dot) = rest.strip_prefix('.') {
        let frac_len = after_dot
            .bytes()
            .take_while(|b| b.is_ascii_digit())
            .count();
        rest = &after_dot[frac_len..];
    }
    let offset_secs: i64 = if rest.is_empty() || rest.eq_ignore_ascii_case("z") {
        0
    } else {
        let sign: i64 = match rest.as_bytes().first()? {
            b'+' => 1,
            b'-' => -1,
            _ => return None,
        };
        let tz = &rest[1..];
        let (tz_h, tz_m) = if tz.len() == 5 && tz.as_bytes()[2] == b':' {
            (tz[0..2].parse::<i64>().ok()?, tz[3..5].parse::<i64>().ok()?)
        } else if tz.len() == 4 {
            (tz[0..2].parse::<i64>().ok()?, tz[2..4].parse::<i64>().ok()?)
        } else {
            return None;
        };
        if !(0..=23).contains(&tz_h) || !(0..=59).contains(&tz_m) {
            return None;
        }
        sign * (tz_h * 3600 + tz_m * 60)
    };
    let days = days_from_civil(year, month, day);
    let utc = days * 86_400 + hour * 3600 + minute * 60 + second - offset_secs;
    Some(utc)
}

pub fn paused_days_left(alias: &Alias, now_unix: i64) -> Option<u32> {
    let raw = alias.paused_until.as_deref()?.trim();
    if raw.is_empty() || raw.eq_ignore_ascii_case("null") {
        return None;
    }
    match parse_iso_utc_unix(raw) {
        Some(until) if until > now_unix => {
            let diff = until.saturating_sub(now_unix);
            let days = diff.saturating_add(86_399) / 86_400;
            Some(days.clamp(1, i64::from(u32::MAX)) as u32)
        }
        Some(_) => None,
        None => Some(0),
    }
}

pub fn chip_for(alias: &Alias, now_unix: i64) -> Option<AliasChip> {
    let stats = &alias.stats;
    if !is_alias_deleted(alias) && stats.blocks >= 3 && stats.blocks >= stats.forwards {
        return Some(AliasChip::HighSpam);
    }
    if let Some(days_left) = paused_days_left(alias, now_unix) {
        return Some(AliasChip::Paused { days_left });
    }
    if !alias.enabled {
        return Some(AliasChip::Disabled);
    }
    alias.wildcard.then_some(AliasChip::Wildcard)
}

pub fn row_for(alias: &Alias, now_unix: i64) -> AliasRow {
    let (local, domain) = match alias.name.split_once('@') {
        Some((l, d)) => (l.to_string(), d.to_string()),
        None => (alias.name.clone(), String::new()),
    };
    let kind = if alias.wildcard {
        AliasKind::Wildcard
    } else {
        AliasKind::Standard
    };
    AliasRow {
        id: alias.id.clone(),
        local,
        domain,
        description: alias.description.clone(),
        kind,
        chip: chip_for(alias, now_unix),
        pinned: alias.pinned,
        tint: tint_for(&alias.name),
        forwards: alias.stats.forwards.clamp(0, i64::from(u32::MAX)) as u32,
        blocks: alias.stats.blocks.clamp(0, i64::from(u32::MAX)) as u32,
        replies: alias.stats.replies.clamp(0, i64::from(u32::MAX)) as u32,
        sends: alias.stats.sends.clamp(0, i64::from(u32::MAX)) as u32,
    }
}

pub fn build_alias_page(aliases: &[Alias], spec: &AliasQuerySpec, now_unix: i64) -> AliasPage {
    let filtered = filter_and_sort_aliases(aliases, spec);
    let rows: Vec<AliasRow> = filtered.iter().map(|a| row_for(a, now_unix)).collect();
    let total = rows.len().min(u32::MAX as usize) as u32;
    AliasPage { rows, total }
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

    #[test]
    fn test_chip_priority_order_and_active_silent() {
        let now = parse_iso_utc_unix("2026-11-27T00:00:00Z").unwrap();
        let active = sample_alias("1", "quiet.haze16@mailx.net", true, true, 12);
        assert_eq!(chip_for(&active, now), None);

        let mut high_spam = sample_alias("2", "river.moss88@mailx.net", false, false, 3);
        high_spam.wildcard = true;
        high_spam.paused_until = Some("2026-12-01T00:00:00Z".to_string());
        high_spam.stats.blocks = 5;
        assert_eq!(chip_for(&high_spam, now), Some(AliasChip::HighSpam));

        let mut paused = sample_alias("3", "sand.drift52@mailx.net", false, false, 7);
        paused.wildcard = true;
        paused.paused_until = Some("2026-11-30T00:00:00Z".to_string());
        assert_eq!(chip_for(&paused, now), Some(AliasChip::Paused { days_left: 3 }));

        let mut disabled = sample_alias("4", "off@mailx.net", false, false, 0);
        disabled.wildcard = true;
        assert_eq!(chip_for(&disabled, now), Some(AliasChip::Disabled));

        let wild = sample_alias("5", "*+wild@mailx.net", true, false, 0);
        assert_eq!(chip_for(&wild, now), Some(AliasChip::Wildcard));
    }

    #[test]
    fn test_deleted_alias_never_reports_high_spam() {
        let now = parse_iso_utc_unix("2026-11-27T00:00:00Z").unwrap();
        let mut deleted = sample_alias("1", "gone@mailx.net", false, false, 0);
        deleted.deleted_at = Some(serde_json::Value::String("2026-11-20T00:00:00Z".to_string()));
        deleted.stats.blocks = 10;
        assert_eq!(chip_for(&deleted, now), Some(AliasChip::Disabled));
    }

    #[test]
    fn test_paused_days_left_handles_expiry_offset_and_invalid_date() {
        let now = parse_iso_utc_unix("2026-11-27T12:00:00Z").unwrap();
        let mut a = sample_alias("1", "p@mailx.net", true, false, 0);
        a.paused_until = Some("2026-11-27T11:59:59Z".to_string());
        assert_eq!(paused_days_left(&a, now), None);

        a.paused_until = Some("2026-11-28T14:00:00+02:00".to_string());
        assert_eq!(paused_days_left(&a, now), Some(1));

        a.paused_until = Some("not-a-timestamp".to_string());
        assert_eq!(paused_days_left(&a, now), Some(0));
    }

    #[test]
    fn test_tint_for_is_deterministic_case_insensitive_and_bounded() {
        let t1 = tint_for("Quiet.Haze16@Mailx.Net");
        let t2 = tint_for("quiet.haze16@mailx.net");
        assert_eq!(t1, t2);
        assert!(t1 < 8);
    }

    #[test]
    fn test_row_for_and_build_alias_page() {
        let now = parse_iso_utc_unix("2026-11-27T00:00:00Z").unwrap();
        let a1 = sample_alias("1", "quiet.haze16@mailx.net", true, true, 128);
        let a2 = sample_alias("2", "*+shop@mydomain.com", true, false, 58);
        let page = build_alias_page(&[a1, a2], &AliasQuerySpec::default(), now);
        assert_eq!(page.total, 2);
        assert_eq!(page.rows[0].local, "quiet.haze16");
        assert_eq!(page.rows[0].domain, "mailx.net");
        assert_eq!(page.rows[0].kind, AliasKind::Standard);
        assert_eq!(page.rows[0].forwards, 128);
        assert_eq!(page.rows[1].kind, AliasKind::Wildcard);
        assert_eq!(page.rows[1].chip, Some(AliasChip::Wildcard));
    }
}
