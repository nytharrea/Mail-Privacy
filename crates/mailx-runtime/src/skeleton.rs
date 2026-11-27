use mailx_domain::models::{
    Alias, AliasRequest, CsvImportPlan, CustomDomain, DailyStatPoint, SkeletonInfo,
    TelemetrySummary,
};
use mailx_utils::generate_random_suffix;
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
        engine: "Mailx Rust Native Application Runtime".to_string(),
        page_alignment_kb: 16,
        zeroize_memory_drop: true,
        ui_design_system: "Jetpack Compose Material 3 (Graphite Mint)".to_string(),
        icon_system: "Mailx Line (51 custom 24dp VectorDrawable icons)".to_string(),
    }
}

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

    let raw_items: Vec<serde_json::Value> =
        serde_json::from_str(trimmed).map_err(|e| format!("Invalid aliases JSON array: {}", e))?;

    if let Ok(mut rt) = runtime_state().write() {
        rt.last_alias_count = raw_items.len();
        rt.last_sync_epoch_secs = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
    }

    let spec = mailx_domain::alias_engine::AliasQuerySpec {
        chip_key: selected_chip.to_string(),
        search_query: search_query.to_string(),
        sort_field: sort_field.to_string(),
        sort_descending,
        pinned_first,
    };

    let mut filtered: Vec<(Alias, serde_json::Value)> = Vec::with_capacity(raw_items.len());
    for val in raw_items {
        let parsed: Alias = match serde_json::from_value(val.clone()) {
            Ok(a) => a,
            Err(_) => continue,
        };
        if mailx_domain::alias_engine::matches_chip_filter(&parsed, &spec.chip_key)
            && mailx_domain::alias_engine::matches_search_query(&parsed, &spec.search_query)
        {
            filtered.push((parsed, val));
        }
    }

    filtered.sort_by(|(a, _), (b, _)| mailx_domain::alias_engine::compare_aliases(a, b, &spec));
    Ok(filtered.into_iter().map(|(_, v)| v).collect())
}

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

pub fn sanitize_tracking_urls(raw_text: &str) -> serde_json::Value {
    let mut removed_params: Vec<String> = Vec::new();
    let mut urls_cleaned = 0usize;

    let sanitized_tokens: Vec<String> = raw_text
        .split_whitespace()
        .map(|token| {
            if (token.starts_with("http://") || token.starts_with("https://"))
                && token.contains('?')
            {
                let res = mailx_domain::offline_audit::sanitize_tracking_url(token);
                if res.was_modified {
                    urls_cleaned += 1;
                    removed_params.extend(res.removed_parameters);
                }
                return res.sanitized_url;
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

pub fn analyze_email_headers(raw_headers: &str) -> serde_json::Value {
    let rfc_report = mailx_security::rfc_email::analyze_raw_email_headers(raw_headers);
    let mime_report = mailx_security::mime_inspector::inspect_mime_message(raw_headers);

    let lower = raw_headers.to_ascii_lowercase();
    let spf_pass = rfc_report.spf_pass || lower.contains("spf=pass");
    let dkim_pass = rfc_report.dkim_pass || lower.contains("dkim=pass");
    let dmarc_pass = rfc_report.dmarc_pass || lower.contains("dmarc=pass");
    let tls_active = lower.contains("tls") || lower.contains("esmtps");
    let hop_count = raw_headers
        .lines()
        .filter(|l| l.trim_start().to_ascii_lowercase().starts_with("received:"))
        .count();

    let return_path_mismatch = !rfc_report.from_address.is_empty()
        && !rfc_report.return_path_address.is_empty()
        && !rfc_report
            .from_address
            .eq_ignore_ascii_case(&rfc_report.return_path_address);

    let mut security_score: u8 = 40;
    if spf_pass {
        security_score = security_score.saturating_add(20);
    }
    if dkim_pass {
        security_score = security_score.saturating_add(20);
    }
    if dmarc_pass {
        security_score = security_score.saturating_add(10);
    }
    if tls_active {
        security_score = security_score.saturating_add(10);
    }
    if !mime_report.high_risk_attachments.is_empty() {
        security_score = security_score.saturating_sub(45);
    }

    let verdict = if !mime_report.high_risk_attachments.is_empty() {
        "High-Risk Attachment Detected"
    } else if spf_pass && dkim_pass {
        "Authenticated Relay"
    } else if spf_pass || dkim_pass {
        "Partially Authenticated Relay"
    } else {
        "Unverified / Partial Headers"
    };

    serde_json::json!({
        "from_address": rfc_report.from_address,
        "reply_to_address": rfc_report.reply_to_address,
        "return_path_address": rfc_report.return_path_address,
        "subject": rfc_report.subject,
        "spf_status": if spf_pass { "PASS" } else { "MISSING/WARN" },
        "dkim_status": if dkim_pass { "PASS" } else { "MISSING/WARN" },
        "dmarc_status": if dmarc_pass { "PASS" } else { "MISSING/WARN" },
        "tls_encrypted": tls_active,
        "hop_count": hop_count,
        "security_score": security_score,
        "has_list_unsubscribe": rfc_report.has_list_unsubscribe,
        "has_one_click_unsubscribe": rfc_report.one_click_unsubscribe,
        "return_path_mismatch": return_path_mismatch,
        "mailx_banner_present": rfc_report.mailx_banner_present,
        "content_type": mime_report.content_type,
        "is_pgp_mime_encrypted": mime_report.is_pgp_mime_encrypted,
        "is_pgp_mime_signed": mime_report.is_pgp_mime_signed,
        "has_inline_pgp_block": mime_report.has_inline_pgp_block,
        "attachment_filenames": mime_report.attachment_filenames,
        "high_risk_attachments": mime_report.high_risk_attachments,
        "verdict": verdict
    })
}

fn classify_service_category(service: &str) -> (&'static str, &'static str) {
    match service {
        "github" | "gitlab" | "cloudflare" | "aws" | "vercel" | "digitalocean" | "docker"
        | "npm" | "crates" | "openai" | "anthropic" => {
            ("Developer & Cloud", "Dev / Infrastructure Account")
        }
        "stripe" | "paypal" | "wise" | "revolut" | "binance" | "coinbase" | "kraken" | "bank" => {
            ("Finance & Payments", "Financial & Billing Relay")
        }
        "netflix" | "spotify" | "youtube" | "twitch" | "disney" | "hulu" | "plex" | "apple" => {
            ("Streaming & Media", "Media Subscription")
        }
        "amazon" | "ebay" | "aliexpress" | "etsy" | "shopify" | "ikea" | "walmart" | "trendyol"
        | "hepsiburada" => ("Shopping & E-Commerce", "Online Retail & Orders"),
        "steam" | "epic" | "playstation" | "xbox" | "nintendo" | "riot" | "discord" => {
            ("Gaming & Community", "Gaming Account")
        }
        "x" | "twitter" | "reddit" | "instagram" | "linkedin" | "mastodon" | "bluesky"
        | "telegram" | "signal" => ("Social & Messaging", "Social Platform"),
        _ => ("General Service", "Isolated Service Alias"),
    }
}

fn extract_clean_service_slug(raw_input: &str) -> String {
    let trimmed = raw_input.trim().to_ascii_lowercase();
    let without_scheme = trimmed
        .strip_prefix("https://")
        .or_else(|| trimmed.strip_prefix("http://"))
        .unwrap_or(&trimmed);
    let host_part = without_scheme
        .split('/')
        .next()
        .unwrap_or(without_scheme)
        .trim_start_matches("www.")
        .trim_start_matches("app.")
        .trim_start_matches("auth.")
        .trim_start_matches("login.")
        .trim_start_matches("checkout.");
    let primary_label = host_part.split('.').next().unwrap_or(host_part);
    let clean: String = primary_label
        .chars()
        .filter(|c| c.is_ascii_alphanumeric() || *c == '-')
        .take(18)
        .collect();
    let clean = clean.trim_matches('-').to_string();
    if clean.is_empty() {
        "vault".to_string()
    } else {
        clean
    }
}

pub fn generate_contextual_aliases(
    service_input: &str,
    domain: &str,
    count: usize,
) -> serde_json::Value {
    let prefix = extract_clean_service_slug(service_input);
    let clean_domain = if domain.trim().is_empty() {
        "mailx.net"
    } else {
        domain.trim().trim_start_matches('@')
    };
    let (category, desc_suffix) = classify_service_category(&prefix);
    let total = count.clamp(1, 8);
    let mut candidates = Vec::with_capacity(total);
    for idx in 0..total {
        if idx % 2 == 0 {
            let token = generate_random_suffix(6);
            candidates.push(format!("{}.{}@{}", prefix, token, clean_domain));
        } else {
            let word_part = mailx_security::wordlist::generate_words_local_part();
            let first_word = word_part.split('.').next().unwrap_or("shield");
            let num_suffix = &word_part[word_part.len().saturating_sub(2)..];
            candidates.push(format!(
                "{}.{}{}@{}",
                prefix, first_word, num_suffix, clean_domain
            ));
        }
    }
    let passphrase = mailx_security::wordlist::generate_passphrase(4, "-");
    let recommended_description = format!(
        "{}{} — {}",
        prefix[..1].to_ascii_uppercase(),
        &prefix[1..],
        desc_suffix
    );

    serde_json::json!({
        "service": prefix,
        "category": category,
        "recommended_description": recommended_description,
        "domain": clean_domain,
        "candidates": candidates,
        "suggested_passphrase": passphrase.phrase,
        "passphrase_entropy_bits": passphrase.entropy_bits
    })
}

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

    let compromised_aliases: Vec<serde_json::Value> = aliases
        .iter()
        .filter(|a| !a.is_deleted() && (a.stats.blocks >= 3 && a.stats.blocks >= a.stats.forwards))
        .take(6)
        .map(|a| {
            serde_json::json!({
                "id": a.id,
                "name": a.name,
                "description": a.description,
                "forwards": a.stats.forwards,
                "blocks": a.stats.blocks,
                "recommendation": "High block-to-forward ratio detected. Consider pausing or rotating this alias."
            })
        })
        .collect();

    let portfolio =
        mailx_domain::offline_audit::audit_alias_portfolio(&aliases, &[], true, true, true);
    let grade = match portfolio.hygiene_score {
        90..=100 => "A+",
        80..=89 => "A",
        70..=79 => "B",
        60..=69 => "C",
        _ => "D",
    };

    serde_json::json!({
        "total_audited": total,
        "missing_description_count": missing_desc,
        "high_block_ratio_count": high_block,
        "dormant_active_count": dormant,
        "overall_score": portfolio.hygiene_score,
        "grade": grade,
        "compromised_aliases": compromised_aliases,
        "findings": portfolio.findings
    })
}

pub fn decode_and_audit_trackers(url_or_fragment: &str) -> Result<serde_json::Value, String> {
    let report = mailx_utils::decode_trackers_report(url_or_fragment).map_err(|e| e.to_string())?;
    let audit = mailx_domain::tracker_db::audit_trackers_report(&report);
    Ok(serde_json::json!({
        "total": report.total,
        "trackers": report.trackers,
        "high_severity_count": audit.high_severity_count,
        "pixel_beacon_count": audit.pixel_beacon_count,
        "link_redirect_count": audit.link_redirect_count,
        "unique_domains": audit.unique_domains,
        "enriched_findings": audit.findings
    }))
}

pub fn inspect_openpgp_key_armor(armored_key: &str) -> serde_json::Value {
    let armor_summary = mailx_security::dns_security::inspect_pgp_public_key_armor(armored_key);
    match mailx_security::pgp_inspector::parse_openpgp_armored_public_key(armored_key) {
        Ok(packet_meta) => serde_json::json!({
            "valid_armor": packet_meta.valid_armor && armor_summary.is_valid_armor,
            "crc24_verified": packet_meta.crc24_verified,
            "algorithm_id": packet_meta.primary_algorithm_id,
            "algorithm_name": packet_meta.primary_algorithm_name,
            "created_timestamp": packet_meta.creation_timestamp_unix,
            "user_ids": packet_meta.user_ids,
            "subkey_count": packet_meta.subkey_count,
            "signature_count": packet_meta.signature_count,
            "packet_count": packet_meta.packets.len(),
            "base64_body_bytes": armor_summary.base64_body_bytes,
            "crc24_checksum": armor_summary.crc24_checksum,
            "summary": format!(
                "{} • {} packet(s) • CRC24 {}",
                packet_meta.primary_algorithm_name,
                packet_meta.packets.len(),
                if packet_meta.crc24_verified { "Verified" } else { "Unverified" }
            )
        }),
        Err(err) => serde_json::json!({
            "valid_armor": armor_summary.is_valid_armor,
            "crc24_verified": false,
            "algorithm_id": 0,
            "algorithm_name": armor_summary.key_type_hint,
            "created_timestamp": 0,
            "user_ids": Vec::<String>::new(),
            "subkey_count": 0,
            "signature_count": 0,
            "packet_count": 0,
            "base64_body_bytes": armor_summary.base64_body_bytes,
            "crc24_checksum": armor_summary.crc24_checksum,
            "summary": err
        }),
    }
}

pub fn evaluate_phishing_and_domain_risk(
    from_header: &str,
    reply_to_header: &str,
    return_path_header: &str,
) -> serde_json::Value {
    let decomposed_from = mailx_security::rfc_email::decompose_email_address(from_header);
    let spf_dkim_ok = return_path_header.is_empty()
        || return_path_header
            .to_ascii_lowercase()
            .contains(&decomposed_from.domain.to_ascii_lowercase());
    let phishing = mailx_security::phishing_guard::evaluate_sender_phishing_risk(
        from_header,
        reply_to_header,
        spf_dkim_ok,
        spf_dkim_ok,
    );
    let domain_trust =
        mailx_security::disposable_domains::assess_domain_trust(&phishing.sender_domain);
    serde_json::json!({
        "from_address": decomposed_from.raw,
        "display_name": decomposed_from.display_name,
        "sender_domain": phishing.sender_domain,
        "risk_score": phishing.risk_score,
        "risk_level": phishing.risk_level,
        "is_punycode_idn": phishing.is_punycode_idn,
        "reply_to_mismatch": phishing.reply_to_mismatch,
        "impersonated_brand": phishing.lookalike_brand_target.unwrap_or_default(),
        "triggers": phishing.warnings,
        "domain_classification": domain_trust.classification,
        "domain_provider": domain_trust.provider_name,
        "domain_trust_score": domain_trust.trust_score,
        "is_privacy_relay": domain_trust.is_privacy_relay,
        "is_ephemeral_throwaway": domain_trust.is_ephemeral_throwaway,
        "supports_dane_or_mta_sts": domain_trust.supports_dane_or_mta_sts,
        "security_notes": domain_trust.security_notes
    })
}

pub fn simulate_rule_evaluation(
    rule_json: &str,
    email_context_json: &str,
) -> Result<serde_json::Value, String> {
    let rule: mailx_security::rules_engine::MailxRuleSpec =
        serde_json::from_str(rule_json).map_err(|e| format!("Invalid rule JSON: {}", e))?;
    let ctx: mailx_security::rules_engine::SimulatedEmailContext =
        serde_json::from_str(email_context_json)
            .map_err(|e| format!("Invalid simulated email context JSON: {}", e))?;
    let res = mailx_security::rules_engine::evaluate_rule(&rule, &ctx);
    serde_json::to_value(res).map_err(|e| e.to_string())
}

pub fn analyze_mta_bounce_log(
    _log_type: &str,
    status_text: &str,
    message_text: &str,
    remote_mta: &str,
    raw_bounce: &str,
) -> serde_json::Value {
    let combined_msg = if raw_bounce.trim().is_empty() {
        message_text.to_string()
    } else {
        format!("{} {}", message_text, raw_bounce)
    };
    let analysis = mailx_security::mta_diagnostics::analyze_mta_diagnostic_log(
        status_text,
        &combined_msg,
        remote_mta,
    );
    serde_json::to_value(analysis).unwrap_or_else(|_| serde_json::json!({}))
}

pub fn generate_totp_token(
    base32_secret: &str,
    unix_seconds: u64,
) -> Result<serde_json::Value, String> {
    let ts = if unix_seconds > 0 {
        unix_seconds
    } else {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0)
    };
    let snap = mailx_security::totp_engine::generate_totp_code_at(base32_secret, ts, 30, 6)?;
    serde_json::to_value(snap).map_err(|e| e.to_string())
}

pub fn generate_passphrase_and_candidate(
    format: &str,
    domain: &str,
    word_count: usize,
) -> serde_json::Value {
    let candidate = mailx_security::wordlist::generate_alias_preview_candidate(format, domain);
    let passphrase = mailx_security::wordlist::generate_passphrase(word_count.clamp(3, 10), "-");
    serde_json::json!({
        "full_address": candidate.full_address,
        "local_part": candidate.local_part,
        "domain": candidate.domain,
        "format": candidate.format,
        "alias_entropy_bits": candidate.entropy_bits,
        "alias_candidate": {
            "address": candidate.full_address,
            "local_part": candidate.local_part,
            "domain": candidate.domain,
            "format": candidate.format,
            "entropy_bits": candidate.entropy_bits
        },
        "passphrase": passphrase.phrase,
        "passphrase_words": passphrase.word_count,
        "passphrase_entropy_bits": passphrase.entropy_bits
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
        let summary = compute_seven_day_telemetry(stats_json, aliases_json, 1_794_916_800).unwrap();
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
        let res =
            sanitize_tracking_urls("https://example.com/item?id=42&utm_source=news&fbclid=abc123");
        assert_eq!(res["sanitized_text"], "https://example.com/item?id=42");
        assert_eq!(res["removed_params_count"], 2);
    }

    #[test]
    fn test_contextual_service_and_phishing_and_rule_sim() {
        let ctx_gen =
            generate_contextual_aliases("https://checkout.stripe.com/pay", "mailx.net", 4);
        assert_eq!(ctx_gen["service"], "stripe");
        assert_eq!(ctx_gen["category"], "Finance & Payments");
        assert_eq!(ctx_gen["candidates"].as_array().unwrap().len(), 4);

        let risk = evaluate_phishing_and_domain_risk(
            "\"PayPal Security\" <support@paypa1.com>",
            "hacker@mailinator.com",
            "bounce@paypa1.com",
        );
        assert!(risk["risk_score"].as_u64().unwrap() >= 55);
        assert_eq!(risk["impersonated_brand"], "PayPal");

        let rule_json = r#"{"id":"r1","name":"Block Spam","operator":"AND","forwards":true,"replies":false,"sends":false,"active":true,"conditions":[{"type":"subject","match":"contains","values":["crypto"]}],"actions":[{"type":"block","value":""}]}"#;
        let email_ctx = r#"{"sender":"promo@spam.io","sender_name":"Spam","subject":"Free Crypto Giveaway","alias":"a@mailx.net","alias_description":"","destination":"me@proton.me","is_mailing_list":true,"has_attachments":false,"alias_is_wildcard":false,"relay_type":"forwards"}"#;
        let sim = simulate_rule_evaluation(rule_json, email_ctx).unwrap();
        assert_eq!(sim["matched"], true);
        assert_eq!(sim["should_block"], true);
    }
}
