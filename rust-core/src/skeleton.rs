use crate::models::{
    Alias, AliasRequest, CsvImportPlan, CustomDomain, DailyStatPoint, SkeletonInfo,
    TelemetrySummary,
};
use crate::utils::generate_random_suffix;
use std::cmp::Ordering;
use std::sync::{OnceLock, RwLock};
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Default)]
pub struct MailxSkeletonRuntime {
    pub last_alias_count: usize,
    pub last_sync_epoch_secs: u64,
}

static SKELETON_RUNTIME: OnceLock<RwLock<MailxSkeletonRuntime>> = OnceLock::new();

fn runtime_state() -> &'static RwLock<MailxSkeletonRuntime> {
    SKELETON_RUNTIME.get_or_init(|| RwLock::new(MailxSkeletonRuntime::default()))
}

pub fn get_skeleton_info() -> SkeletonInfo {
    SkeletonInfo {
        version: env!("CARGO_PKG_VERSION").to_string(),
        engine: "Mailx Rust Native Core Skeleton".to_string(),
        page_alignment_kb: 16,
        zeroize_memory_drop: true,
        ui_design_system: "Jetpack Compose Material 3 Expressive (1.5.0-rc01)".to_string(),
        icon_system: "Lucide Icons v1.70 + Material Symbols Rounded".to_string(),
    }
}

/// Filters and sorts an alias JSON array natively in Rust so the UI layer stays a pure declarative view.
pub fn filter_and_sort_aliases(
    aliases_json: &str,
    search_query: &str,
    selected_chip: &str,
    sort_field: &str,
    sort_descending: bool,
    pinned_first: bool,
) -> Result<Vec<serde_json::Value>, String> {
    let trimmed = aliases_json.trim();
    if trimmed.is_empty() {
        return Ok(Vec::new());
    }

    let raw_items: Vec<serde_json::Value> = serde_json::from_str(trimmed)
        .map_err(|e| format!("Invalid aliases JSON array: {}", e))?;

    if let Ok(mut rt) = runtime_state().write() {
        rt.last_alias_count = raw_items.len();
        rt.last_sync_epoch_secs = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
    }

    let query_lower = search_query.trim().to_ascii_lowercase();

    let mut filtered: Vec<(Alias, serde_json::Value)> = Vec::with_capacity(raw_items.len());
    for val in raw_items {
        let parsed: Alias = match serde_json::from_value(val.clone()) {
            Ok(a) => a,
            Err(_) => continue,
        };

        let is_deleted = parsed.is_deleted();
        let matches_chip = match selected_chip {
            "pinned_only" => parsed.pinned && !is_deleted,
            "wildcard_only" => parsed.wildcard && !is_deleted,
            "active" => parsed.enabled && !is_deleted,
            "inactive" => !parsed.enabled && !is_deleted,
            "deleted" => is_deleted,
            _ => !is_deleted,
        };
        if !matches_chip {
            continue;
        }

        if !query_lower.is_empty() {
            let matches_query = parsed.name.to_ascii_lowercase().contains(&query_lower)
                || parsed
                    .description
                    .to_ascii_lowercase()
                    .contains(&query_lower)
                || parsed
                    .recipients
                    .to_ascii_lowercase()
                    .contains(&query_lower)
                || parsed
                    .from_name
                    .to_ascii_lowercase()
                    .contains(&query_lower);
            if !matches_query {
                continue;
            }
        }

        filtered.push((parsed, val));
    }

    filtered.sort_by(|(a, _), (b, _)| {
        if pinned_first && a.pinned != b.pinned {
            return if a.pinned {
                Ordering::Less
            } else {
                Ordering::Greater
            };
        }

        let cmp = match sort_field {
            "updated_at" | "UPDATED_AT" => a
                .updated_at
                .as_deref()
                .unwrap_or("")
                .cmp(b.updated_at.as_deref().unwrap_or("")),
            "name" | "NAME" => a
                .name
                .to_ascii_lowercase()
                .cmp(&b.name.to_ascii_lowercase()),
            "forwards" | "FORWARDS" => a.stats.forwards.cmp(&b.stats.forwards),
            "blocks" | "BLOCKS" => a.stats.blocks.cmp(&b.stats.blocks),
            "replies" | "REPLIES" => a.stats.replies.cmp(&b.stats.replies),
            "sends" | "SENDS" => a.stats.sends.cmp(&b.stats.sends),
            _ => a
                .created_at
                .as_deref()
                .unwrap_or("")
                .cmp(b.created_at.as_deref().unwrap_or("")),
        };

        if sort_descending {
            cmp.reverse()
        } else {
            cmp
        }
    });

    Ok(filtered.into_iter().map(|(_, v)| v).collect())
}

/// Converts days since 1970-01-01 to (year, month, day) using Howard Hinnant's civil calendar algorithm.
fn civil_from_days(days_since_epoch: i64) -> (i32, u32, u32) {
    let z = days_since_epoch + 719468;
    let era = if z >= 0 { z } else { z - 146096 } / 146097;
    let doe = (z - era * 146097) as u32;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    let y = (yoe as i64) + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let year = (y + if m <= 2 { 1 } else { 0 }) as i32;
    (year, m, d)
}

fn format_iso_date(days_since_epoch: i64) -> (String, String) {
    let (y, m, d) = civil_from_days(days_since_epoch);
    (
        format!("{:04}-{:02}-{:02}", y, m, d),
        format!("{:02}.{:02}", d, m),
    )
}

/// Computes 7-day telemetry buckets and aggregate counts natively in Rust.
pub fn compute_seven_day_telemetry(
    raw_stats_json: &str,
    aliases_json: &str,
    reference_epoch_secs: i64,
) -> Result<TelemetrySummary, String> {
    let now_secs = if reference_epoch_secs > 0 {
        reference_epoch_secs
    } else {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs() as i64)
            .unwrap_or(1_794_960_000)
    };
    let today_days = now_secs.div_euclid(86_400);

    let mut day_buckets: Vec<(String, String, [i64; 4])> = Vec::with_capacity(7);
    for offset in (0..=6).rev() {
        let d = today_days - offset;
        let (iso, label) = format_iso_date(d);
        day_buckets.push((iso, label, [0, 0, 0, 0]));
    }

    let mut alias_forwards = 0i64;
    let mut alias_blocks = 0i64;
    let mut alias_replies = 0i64;
    let mut alias_sends = 0i64;
    let mut active_aliases = 0usize;
    let mut inactive_aliases = 0usize;
    let mut pinned_aliases = 0usize;
    let mut wildcard_aliases = 0usize;
    let mut deleted_aliases = 0usize;
    let mut total_aliases = 0usize;

    if !aliases_json.trim().is_empty() {
        if let Ok(aliases) = serde_json::from_str::<Vec<Alias>>(aliases_json) {
            total_aliases = aliases.len();
            for a in &aliases {
                alias_forwards += a.stats.forwards;
                alias_blocks += a.stats.blocks;
                alias_replies += a.stats.replies;
                alias_sends += a.stats.sends;
                if a.is_deleted() {
                    deleted_aliases += 1;
                } else {
                    if a.enabled {
                        active_aliases += 1;
                    } else {
                        inactive_aliases += 1;
                    }
                    if a.pinned {
                        pinned_aliases += 1;
                    }
                    if a.wildcard {
                        wildcard_aliases += 1;
                    }
                }
            }
        }
    }

    let mut server_forwards = 0i64;
    let mut server_blocks = 0i64;
    let mut server_replies = 0i64;
    let mut server_sends = 0i64;
    let mut server_aliases = 0usize;

    if !raw_stats_json.trim().is_empty() {
        if let Ok(val) = serde_json::from_str::<serde_json::Value>(raw_stats_json) {
            let root = val.get("data").unwrap_or(&val);
            server_forwards = root.get("forwards").and_then(|v| v.as_i64()).unwrap_or(0);
            server_blocks = root.get("blocks").and_then(|v| v.as_i64()).unwrap_or(0);
            server_replies = root.get("replies").and_then(|v| v.as_i64()).unwrap_or(0);
            server_sends = root.get("sends").and_then(|v| v.as_i64()).unwrap_or(0);
            server_aliases = root
                .get("aliases")
                .and_then(|v| v.as_u64())
                .unwrap_or(total_aliases as u64) as usize;

            if let Some(msgs) = root.get("messages").and_then(|v| v.as_array()) {
                for m in msgs {
                    let date_prefix = m
                        .get("created_at")
                        .and_then(|v| v.as_str())
                        .unwrap_or("")
                        .trim();
                    if date_prefix.len() >= 10 {
                        let day_iso = &date_prefix[..10];
                        let msg_type = m.get("type").and_then(|v| v.as_i64()).unwrap_or(1);
                        if let Some(bucket) = day_buckets.iter_mut().find(|b| b.0 == day_iso) {
                            match msg_type {
                                1 => bucket.2[0] += 1,
                                2 => bucket.2[1] += 1,
                                3 => bucket.2[2] += 1,
                                4 => bucket.2[3] += 1,
                                _ => {}
                            }
                        }
                    }
                }
            }
        }
    }

    let daily_points = day_buckets
        .into_iter()
        .map(|(date_iso, label, counts)| DailyStatPoint {
            date_iso,
            label,
            forwards: counts[0],
            replies: counts[1],
            sends: counts[2],
            blocks: counts[3],
        })
        .collect();

    Ok(TelemetrySummary {
        forwards: alias_forwards.max(server_forwards),
        blocks: alias_blocks.max(server_blocks),
        replies: alias_replies.max(server_replies),
        sends: alias_sends.max(server_sends),
        aliases: total_aliases.max(server_aliases),
        active_aliases,
        inactive_aliases,
        pinned_aliases,
        wildcard_aliases,
        deleted_aliases,
        daily_points,
    })
}

/// Formats an alias JSON list into a clean CSV document in Rust.
pub fn format_aliases_csv_export(aliases_json: &str) -> Result<String, String> {
    let aliases: Vec<Alias> = if aliases_json.trim().is_empty() {
        Vec::new()
    } else {
        serde_json::from_str(aliases_json).map_err(|e| format!("Invalid aliases JSON: {}", e))?
    };

    let mut out = String::from("alias,description,enabled,recipients\n");
    for a in aliases {
        let clean_desc = a.description.replace(',', " ").replace(['\r', '\n'], " ");
        let clean_rec = a.recipients.replace(',', " ").replace(['\r', '\n'], " ");
        out.push_str(&format!(
            "{},{},{},{}\n",
            a.name.trim(),
            clean_desc.trim(),
            a.enabled,
            clean_rec.trim()
        ));
    }
    Ok(out)
}

/// Parses CSV text for bulk alias import and maps custom domain names to domain IDs in Rust.
pub fn parse_aliases_csv_import(
    csv_text: &str,
    custom_domains_json: &str,
    default_recipient: &str,
) -> Result<CsvImportPlan, String> {
    let custom_domains: Vec<CustomDomain> = if custom_domains_json.trim().is_empty() {
        Vec::new()
    } else {
        serde_json::from_str(custom_domains_json).unwrap_or_default()
    };

    let lines: Vec<&str> = csv_text
        .lines()
        .map(|l| l.trim())
        .filter(|l| !l.is_empty())
        .collect();

    if lines.is_empty() {
        return Ok(CsvImportPlan {
            requests: Vec::new(),
            skipped: 0,
        });
    }

    let start_idx = if lines[0].to_ascii_lowercase().starts_with("alias,") {
        1
    } else {
        0
    };

    let mut requests = Vec::new();
    let mut skipped = 0usize;

    for line in lines.into_iter().skip(start_idx).take(500) {
        let cols: Vec<&str> = line.split(',').collect();
        if cols.len() < 4 {
            skipped += 1;
            continue;
        }

        let full_alias = cols[0].trim();
        let description = cols[1].trim().to_string();
        let enabled = cols[2].trim().eq_ignore_ascii_case("true");
        let joined_rec = cols[3..]
            .join(",")
            .trim()
            .replace(' ', ",")
            .split(',')
            .map(|s| s.trim())
            .filter(|s| !s.is_empty())
            .collect::<Vec<_>>()
            .join(",");

        let recipients = if joined_rec.is_empty() {
            default_recipient.trim().to_string()
        } else {
            joined_rec
        };

        let Some((local_part, domain_name)) = full_alias.split_once('@') else {
            skipped += 1;
            continue;
        };

        if local_part.trim().is_empty()
            || domain_name.trim().is_empty()
            || recipients.trim().is_empty()
        {
            skipped += 1;
            continue;
        }

        let resolved_domain = custom_domains
            .iter()
            .find(|d| d.name.eq_ignore_ascii_case(domain_name.trim()))
            .and_then(|d| {
                if d.id.trim().is_empty() {
                    None
                } else {
                    Some(d.id.clone())
                }
            })
            .unwrap_or_else(|| domain_name.trim().to_string());

        requests.push(AliasRequest {
            description,
            enabled,
            recipients,
            from_name: String::new(),
            format: "custom".to_string(),
            domain: resolved_domain,
            wildcard_local_part: None,
            wildcard_delimiter: None,
            local_part: Some(local_part.trim().to_string()),
        });
    }

    Ok(CsvImportPlan { requests, skipped })
}

/// Sanitizes tracking parameters (utm_*, fbclid, gclid, mc_eid, etc.) from URLs in text.
pub fn sanitize_tracking_urls(raw_text: &str) -> serde_json::Value {
    const TRACKING_PARAMS: &[&str] = &[
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "utm_id",
        "fbclid",
        "gclid",
        "dclid",
        "msclkid",
        "mc_cid",
        "mc_eid",
        "mkt_tok",
        "yclid",
        "twclid",
        "igshid",
        "si",
        "_hsenc",
        "_hsmi",
        "vero_id",
        "oly_enc_id",
        "oly_anon_id",
        "rb_clickid",
    ];

    let mut removed_params: Vec<String> = Vec::new();
    let mut urls_cleaned = 0usize;

    let sanitized_tokens: Vec<String> = raw_text
        .split_whitespace()
        .map(|token| {
            if (token.starts_with("http://") || token.starts_with("https://"))
                && token.contains('?')
            {
                if let Some((base, query_and_hash)) = token.split_once('?') {
                    let (query, hash_opt) = match query_and_hash.split_once('#') {
                        Some((q, h)) => (q, Some(h)),
                        None => (query_and_hash, None),
                    };
                    let mut kept = Vec::new();
                    let mut modified = false;
                    for pair in query.split('&') {
                        let key = pair.split('=').next().unwrap_or("").to_ascii_lowercase();
                        if TRACKING_PARAMS.contains(&key.as_str()) || key.starts_with("utm_") {
                            removed_params.push(key);
                            modified = true;
                        } else if !pair.is_empty() {
                            kept.push(pair);
                        }
                    }
                    if modified {
                        urls_cleaned += 1;
                    }
                    let mut rebuilt = base.to_string();
                    if !kept.is_empty() {
                        rebuilt.push('?');
                        rebuilt.push_str(&kept.join("&"));
                    }
                    if let Some(h) = hash_opt {
                        rebuilt.push('#');
                        rebuilt.push_str(h);
                    }
                    return rebuilt;
                }
            }
            token.to_string()
        })
        .collect();

    serde_json::json!({
        "sanitized_text": sanitized_tokens.join(" "),
        "urls_cleaned": urls_cleaned,
        "removed_params_count": removed_params.len(),
        "removed_params": removed_params
    })
}

/// Analyzes raw RFC-5322 email headers for SPF, DKIM, DMARC, TLS, and relay hops in Rust.
pub fn analyze_email_headers(raw_headers: &str) -> serde_json::Value {
    let lower = raw_headers.to_ascii_lowercase();
    let spf_pass = lower.contains("spf=pass");
    let dkim_pass = lower.contains("dkim=pass");
    let dmarc_pass = lower.contains("dmarc=pass");
    let tls_active = lower.contains("tls") || lower.contains("esmtps");
    let hop_count = raw_headers
        .lines()
        .filter(|l| l.trim_start().to_ascii_lowercase().starts_with("received:"))
        .count();

    serde_json::json!({
        "spf_status": if spf_pass { "PASS" } else { "MISSING/WARN" },
        "dkim_status": if dkim_pass { "PASS" } else { "MISSING/WARN" },
        "dmarc_status": if dmarc_pass { "PASS" } else { "MISSING/WARN" },
        "tls_encrypted": tls_active,
        "hop_count": hop_count,
        "verdict": if spf_pass && dkim_pass { "Authenticated Relay" } else { "Unverified / Partial Headers" }
    })
}

/// Generates contextual service-specific alias candidates in Rust.
pub fn generate_contextual_aliases(
    service_input: &str,
    domain: &str,
    count: usize,
) -> serde_json::Value {
    let clean_service: String = service_input
        .trim()
        .to_ascii_lowercase()
        .chars()
        .filter(|c| c.is_ascii_alphanumeric())
        .take(16)
        .collect();
    let prefix = if clean_service.is_empty() {
        "vault".to_string()
    } else {
        clean_service
    };
    let clean_domain = if domain.trim().is_empty() {
        "mailx.net"
    } else {
        domain.trim()
    };
    let total = count.clamp(1, 8);
    let mut candidates = Vec::with_capacity(total);
    for _ in 0..total {
        let token = generate_random_suffix(6);
        candidates.push(format!("{}.{}@{}", prefix, token, clean_domain));
    }
    serde_json::json!({
        "service": prefix,
        "domain": clean_domain,
        "candidates": candidates
    })
}

/// Audits alias hygiene (high-spam aliases, inactive aliases, missing descriptions) in Rust.
pub fn audit_alias_hygiene(aliases_json: &str) -> serde_json::Value {
    let aliases: Vec<Alias> = serde_json::from_str(aliases_json).unwrap_or_default();
    let total = aliases.len();
    let missing_desc = aliases
        .iter()
        .filter(|a| !a.is_deleted() && a.description.trim().is_empty())
        .count();
    let high_block = aliases
        .iter()
        .filter(|a| !a.is_deleted() && a.stats.blocks > a.stats.forwards && a.stats.blocks >= 3)
        .count();
    let dormant = aliases
        .iter()
        .filter(|a| !a.is_deleted() && a.enabled && a.total_activity() == 0)
        .count();

    serde_json::json!({
        "total_audited": total,
        "missing_description_count": missing_desc,
        "high_block_ratio_count": high_block,
        "dormant_active_count": dormant
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rust_skeleton_filter_and_sort() {
        let sample = r#"[
            {"id":"1","name":"beta@mailx.net","enabled":true,"pinned":false,"wildcard":false,"description":"Shopping","recipients":"me@proton.me","stats":{"forwards":5,"blocks":1,"replies":0,"sends":0},"created_at":"2026-11-10T10:00:00Z"},
            {"id":"2","name":"alpha@mailx.net","enabled":true,"pinned":true,"wildcard":false,"description":"Work","recipients":"me@proton.me","stats":{"forwards":2,"blocks":0,"replies":1,"sends":0},"created_at":"2026-11-09T10:00:00Z"},
            {"id":"3","name":"*.wild@mailx.net","enabled":false,"pinned":false,"wildcard":true,"description":"Wild","recipients":"me@proton.me","stats":{"forwards":12,"blocks":4,"replies":0,"sends":0},"created_at":"2026-11-11T10:00:00Z"}
        ]"#;

        let all_pinned_first =
            filter_and_sort_aliases(sample, "", "active_inactive", "forwards", true, true).unwrap();
        assert_eq!(all_pinned_first.len(), 3);
        assert_eq!(all_pinned_first[0]["id"], "2");
        assert_eq!(all_pinned_first[1]["id"], "3");

        let only_wildcard =
            filter_and_sort_aliases(sample, "", "wildcard_only", "name", false, false).unwrap();
        assert_eq!(only_wildcard.len(), 1);
        assert_eq!(only_wildcard[0]["id"], "3");
    }

    #[test]
    fn test_rust_skeleton_seven_day_telemetry() {
        let stats_json = r#"{"forwards":10,"blocks":3,"replies":1,"sends":2,"aliases":2,"messages":[{"created_at":"2026-11-17T12:00:00Z","type":1},{"created_at":"2026-11-17T13:00:00Z","type":4}]}"#;
        let aliases_json = r#"[{"id":"1","name":"a@mailx.net","enabled":true,"pinned":true,"wildcard":false,"stats":{"forwards":12,"blocks":3,"replies":1,"sends":2}}]"#;
        let summary =
            compute_seven_day_telemetry(stats_json, aliases_json, 1_794_916_800).unwrap();
        assert_eq!(summary.forwards, 12);
        assert_eq!(summary.blocks, 3);
        assert_eq!(summary.active_aliases, 1);
        assert_eq!(summary.pinned_aliases, 1);
        assert_eq!(summary.daily_points.len(), 7);
        let last = summary.daily_points.last().unwrap();
        assert_eq!(last.date_iso, "2026-11-17");
        assert_eq!(last.forwards, 1);
        assert_eq!(last.blocks, 1);
    }

    #[test]
    fn test_rust_skeleton_csv_export_and_import() {
        let aliases_json = r#"[{"id":"1","name":"shop@mydomain.com","description":"Online, Shop","enabled":true,"recipients":"me@proton.me"}]"#;
        let csv = format_aliases_csv_export(aliases_json).unwrap();
        assert!(csv.contains("shop@mydomain.com,Online  Shop,true,me@proton.me"));

        let domains_json = r#"[{"id":"dom_123","name":"mydomain.com","enabled":true}]"#;
        let plan = parse_aliases_csv_import(&csv, domains_json, "fallback@proton.me").unwrap();
        assert_eq!(plan.requests.len(), 1);
        assert_eq!(plan.requests[0].domain, "dom_123");
        assert_eq!(plan.requests[0].local_part.as_deref(), Some("shop"));
    }

    #[test]
    fn test_privacy_lab_url_sanitizer() {
        let res = sanitize_tracking_urls("https://example.com/item?id=42&utm_source=news&fbclid=abc123");
        assert_eq!(res["sanitized_text"], "https://example.com/item?id=42");
        assert_eq!(res["removed_params_count"], 2);
    }
}
