use crate::models::{
    Alias, AliasFilter, AliasHygieneIssue, AliasHygieneReport, ContextualAliasCandidate,
    ContextualAliasSuggestions, EmailHeaderAnalysis, HeaderHopItem, PhoneticToken,
    PrivacyPostureReport, QrMatrix, RemovedTrackerItem, SanitizedUrlItem, TrackersReport,
    UrlSanitizeReport,
};
use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

static ENTROPY_COUNTER: AtomicU64 = AtomicU64::new(0x9E37_79B9_7F4A_7C15);

const TRACKING_QUERY_PARAMS: &[&str] = &[
    "utm_source",
    "utm_medium",
    "utm_campaign",
    "utm_term",
    "utm_content",
    "utm_id",
    "utm_name",
    "utm_reader",
    "utm_viz_id",
    "utm_pubreferrer",
    "utm_swu",
    "fbclid",
    "gclid",
    "dclid",
    "gbraid",
    "wbraid",
    "msclkid",
    "twclid",
    "ttclid",
    "yclid",
    "mc_cid",
    "mc_eid",
    "mkt_tok",
    "_hsenc",
    "_hsmi",
    "hsctatracking",
    "oly_enc_id",
    "oly_anon_id",
    "rb_clickid",
    "vero_id",
    "wickedid",
    "igshid",
    "si",
    "spm",
    "scm",
    "ref_src",
    "ref_url",
    "_openstat",
    "wt.mc_id",
    "pk_campaign",
    "pk_kwd",
    "piwik_campaign",
    "matomo_campaign",
    "trk",
    "trkinfo",
    "lipi",
    "licu",
    "sc_cid",
    "s_kwcid",
];

const STEALTH_WORDS_A: &[&str] = &[
    "amber", "arctic", "astral", "autumn", "breeze", "canyon", "cedar", "cobalt", "coral",
    "crimson", "crystal", "cyber", "dawn", "delta", "drift", "echo", "ember", "emerald", "falcon",
    "fjord", "frost", "glacier", "granite", "harbor", "hazel", "horizon", "indigo", "ivory",
    "jade", "jasper", "kestrel", "lagoon", "lumen", "lunar", "maple", "meadow", "meteor",
    "mirage", "mist", "nebula", "nimbus", "nordic", "nova", "obsidian", "onyx", "opal", "orbit",
    "osprey", "prism", "pulsar", "quartz", "raven", "reef", "ridge", "river", "sable", "saffron",
    "sapphire", "shadow", "sierra", "silver", "solstice", "sonic", "spark", "spruce", "stellar",
    "summit", "tempest", "terra", "thistle", "timber", "titan", "topaz", "tundra", "velvet",
    "verdant", "vertex", "vortex", "whisper", "willow", "zephyr", "zenith",
];

const STEALTH_WORDS_B: &[&str] = &[
    "anchor", "arrow", "atlas", "beacon", "blade", "bloom", "bridge", "brook", "canopy",
    "cascade", "castle", "cavern", "circuit", "citadel", "cloud", "comet", "compass", "crest",
    "crown", "current", "cipher", "lantern", "dome", "feather", "field", "flame", "flare",
    "forge", "fountain", "garden", "gate", "Grove", "haven", "helm", "hollow", "island",
    "kernel", "key", "lantern", "leaf", "ledge", "lens", "light", "lodge", "matrix", "mirror",
    "monolith", "nexus", "oasis", "orbit", "palace", "path", "peak", "petal", "pillar", "pine",
    "plaza", "Point", "portal", "pulse", "relay", "sanctuary", "scale", "sector", "sentry",
    "shard", "shield", "shore", "signal", "sphere", "spire", "spring", "stone", "stream",
    "tower", "trail", "vault", "vector", "vessel", "vista", "wave", "wing",
];

pub fn validate_access_key(access_key: &str) -> Result<(), String> {
    let trimmed = access_key.trim();
    if trimmed.len() != 85 {
        return Err(format!(
            "Invalid Access Key length: {} characters (must be exactly 85 characters).",
            trimmed.len()
        ));
    }
    let parts: Vec<&str> = trimmed.split('.').collect();
    if parts.len() != 2 || parts[0].len() != 36 || parts[1].len() != 48 {
        return Err(
            "Invalid Access Key format: expected '<36-char-UUID>.<48-char-token>'.".to_string(),
        );
    }
    Ok(())
}

pub fn generate_reverse_alias(alias_email: &str, target_email: &str) -> Result<String, String> {
    let alias_trimmed = alias_email.trim();
    let target_trimmed = target_email.trim();

    let (alias_local, alias_domain) = alias_trimmed
        .split_once('@')
        .ok_or_else(|| "Invalid alias email address".to_string())?;
    if alias_local.is_empty() || alias_domain.is_empty() || !alias_domain.contains('.') {
        return Err("Invalid alias email address".to_string());
    }

    let (target_local, target_domain) = target_trimmed
        .split_once('@')
        .ok_or_else(|| "Invalid recipient email address".to_string())?;
    if target_local.is_empty() || target_domain.is_empty() || !target_domain.contains('.') {
        return Err("Invalid recipient email address".to_string());
    }

    Ok(format!(
        "{}+{}={}@{}",
        alias_local, target_local, target_domain, alias_domain
    ))
}

pub fn generate_wildcard_on_the_fly(wildcard_alias: &str, prefix: &str) -> Result<String, String> {
    let prefix_trimmed = prefix.trim();
    if prefix_trimmed.is_empty()
        || !prefix_trimmed
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
    {
        return Err("Prefix may only contain letters, digits, '-' or '_'.".to_string());
    }

    if !wildcard_alias.starts_with("*+") && !wildcard_alias.starts_with("*.") {
        return Err(
            "Selected address is not a Wildcard alias (must start with '*+' or '*.').".to_string(),
        );
    }

    Ok(wildcard_alias.replacen('*', prefix_trimmed, 1))
}

fn next_splitmix64(seed_hint: u64) -> u64 {
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_nanos() as u64)
        .unwrap_or(0x517C_C1B7_2722_0A95);
    let seq = ENTROPY_COUNTER.fetch_add(0x9E37_79B9_7F4A_7C15, Ordering::Relaxed);
    let mut z = nanos ^ seq.rotate_left(19) ^ seed_hint.wrapping_mul(0xBF58_476D_1CE4_E5B9);
    z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
    z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
    let out = z ^ (z >> 31);
    if out == 0 {
        0x9E37_79B9_7F4A_7C15
    } else {
        out
    }
}

pub fn generate_random_suffix(length: usize) -> String {
    let clamped = length.clamp(6, 12);
    let mut state = next_splitmix64(clamped as u64);
    const CHARSET: &[u8] = b"abcdefghijklmnopqrstuvwxyz0123456789";
    let mut out = String::with_capacity(clamped);
    for _ in 0..clamped {
        state ^= state << 13;
        state ^= state >> 7;
        state ^= state << 17;
        let idx = (state as usize) % CHARSET.len();
        out.push(CHARSET[idx] as char);
    }
    out
}

pub fn encode_nato_phonetic(input: &str) -> Vec<PhoneticToken> {
    input
        .trim()
        .chars()
        .enumerate()
        .map(|(i, ch)| {
            let word = match ch.to_ascii_uppercase() {
                'A' => "Alpha",
                'B' => "Bravo",
                'C' => "Charlie",
                'D' => "Delta",
                'E' => "Echo",
                'F' => "Foxtrot",
                'G' => "Golf",
                'H' => "Hotel",
                'I' => "India",
                'J' => "Juliett",
                'K' => "Kilo",
                'L' => "Lima",
                'M' => "Mike",
                'N' => "November",
                'O' => "Oscar",
                'P' => "Papa",
                'Q' => "Quebec",
                'R' => "Romeo",
                'S' => "Sierra",
                'T' => "Tango",
                'U' => "Uniform",
                'V' => "Victor",
                'W' => "Whiskey",
                'X' => "X-ray",
                'Y' => "Yankee",
                'Z' => "Zulu",
                '0' => "Zero",
                '1' => "One",
                '2' => "Two",
                '3' => "Three",
                '4' => "Four",
                '5' => "Five",
                '6' => "Six",
                '7' => "Seven",
                '8' => "Eight",
                '9' => "Nine",
                '.' => "Dot",
                '@' => "At",
                '-' => "Dash",
                '_' => "Underscore",
                '+' => "Plus",
                '*' => "Star",
                _ => "Symbol",
            };
            PhoneticToken {
                index: i + 1,
                symbol: ch.to_string(),
                word: word.to_string(),
            }
        })
        .collect()
}

pub fn encode_qr_matrix(payload: &str) -> Result<QrMatrix, String> {
    let trimmed = payload.trim();
    if trimmed.is_empty() {
        return Err("QR payload is empty".to_string());
    }
    let code =
        qrcode_rs::QrCode::with_error_correction_level(trimmed.as_bytes(), qrcode_rs::EcLevel::M)
            .map_err(|e| format!("QR encoding error: {:?}", e))?;
    let width = code.width();
    let colors = code.to_colors();
    let mut rows = Vec::with_capacity(width);
    for y in 0..width {
        let mut row = String::with_capacity(width);
        for x in 0..width {
            let bit = match colors[y * width + x] {
                qrcode_rs::Color::Dark => "1",
                qrcode_rs::Color::Light => "0",
            };
            row.push_str(bit);
        }
        rows.push(row);
    }
    Ok(QrMatrix { width, rows })
}

pub fn compute_privacy_posture(
    forwards: u64,
    blocks: u64,
    active_aliases: usize,
    total_aliases: usize,
    pgp_active: bool,
    trackers_blocked: bool,
    header_stripped: bool,
    rules_active: usize,
) -> PrivacyPostureReport {
    let mut score: u8 = 40;
    if trackers_blocked {
        score = score.saturating_add(20);
    }
    if pgp_active {
        score = score.saturating_add(20);
    }
    if header_stripped {
        score = score.saturating_add(8);
    }
    if rules_active > 0 {
        score = score.saturating_add(7);
    }
    if active_aliases > 0 {
        score = score.saturating_add(5);
    }
    let score = score.min(100);
    let grade = match score {
        92..=100 => "A+",
        82..=91 => "A",
        70..=81 => "B+",
        58..=69 => "B",
        _ => "C",
    }
    .to_string();
    let total_traffic = forwards.saturating_add(blocks);
    let block_rate_percent = if total_traffic == 0 {
        0
    } else {
        ((blocks.saturating_mul(100)) / total_traffic).min(100) as u8
    };
    PrivacyPostureReport {
        score,
        grade,
        active_aliases,
        total_aliases,
        block_rate_percent,
        pgp_active,
        trackers_blocked,
        header_stripped,
        rules_active,
    }
}

fn decode_base64_url(input: &str) -> Result<Vec<u8>, String> {
    let mut normalized = input
        .trim()
        .replace('-', "+")
        .replace('_', "/")
        .chars()
        .filter(|c| !c.is_whitespace())
        .collect::<String>();

    while normalized.len() % 4 != 0 {
        normalized.push('=');
    }

    fn val(b: u8) -> Result<u8, String> {
        match b {
            b'A'..=b'Z' => Ok(b - b'A'),
            b'a'..=b'z' => Ok(b - b'a' + 26),
            b'0'..=b'9' => Ok(b - b'0' + 52),
            b'+' => Ok(62),
            b'/' => Ok(63),
            _ => Err("Invalid base64url character".to_string()),
        }
    }

    let bytes = normalized.as_bytes();
    let mut out = Vec::with_capacity((bytes.len() / 4) * 3);
    for chunk in bytes.chunks(4) {
        if chunk.len() < 4 {
            return Err("Invalid base64url length".to_string());
        }
        let v0 = val(chunk[0])?;
        let v1 = val(chunk[1])?;
        out.push((v0 << 2) | (v1 >> 4));
        if chunk[2] != b'=' {
            let v2 = val(chunk[2])?;
            out.push((v1 << 4) | (v2 >> 2));
            if chunk[3] != b'=' {
                let v3 = val(chunk[3])?;
                out.push((v2 << 6) | v3);
            }
        }
    }
    Ok(out)
}

pub fn decode_trackers_report(raw: &str) -> Result<TrackersReport, String> {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return Err("Please paste a Trackers Report URL or encoded fragment.".to_string());
    }
    let fragment = if let Some((_, hash_part)) = trimmed.split_once('#') {
        hash_part.trim()
    } else {
        trimmed.trim_start_matches('#')
    };

    if fragment.is_empty() {
        return Err("Report link is missing the encoded fragment.".to_string());
    }

    let decoded_bytes = decode_base64_url(fragment)?;
    let decoded_str =
        String::from_utf8(decoded_bytes).map_err(|_| "Invalid UTF-8 in trackers report".to_string())?;
    let json_val: serde_json::Value =
        serde_json::from_str(&decoded_str).map_err(|_| "Invalid JSON in trackers report".to_string())?;

    let n = json_val
        .get("n")
        .and_then(|v| v.as_u64())
        .ok_or_else(|| "Missing tracker count in report".to_string())? as usize;
    let t_arr = json_val
        .get("t")
        .and_then(|v| v.as_array())
        .ok_or_else(|| "Missing trackers array in report".to_string())?;

    let mut trackers = Vec::with_capacity(t_arr.len());
    for item in t_arr {
        if let Some(obj) = item.as_object() {
            let domain = obj
                .get("d")
                .and_then(|v| v.as_str())
                .unwrap_or_default()
                .to_string();
            let element = obj
                .get("e")
                .and_then(|v| v.as_str())
                .unwrap_or_default()
                .to_string();
            let reason = obj
                .get("r")
                .and_then(|v| v.as_str())
                .unwrap_or_default()
                .to_string();
            trackers.push(RemovedTrackerItem {
                domain,
                element,
                reason,
            });
        }
    }

    let total = n.max(trackers.len());
    Ok(TrackersReport { total, trackers })
}

fn is_tracking_param(key_lower: &str) -> bool {
    if key_lower.starts_with("utm_")
        || key_lower.starts_with("hsa_")
        || key_lower.starts_with("mtm_")
        || key_lower.starts_with("matomo_")
        || key_lower.starts_with("piwik_")
    {
        return true;
    }
    TRACKING_QUERY_PARAMS.contains(&key_lower)
}

fn clean_single_url(raw_url: &str) -> (String, Vec<String>) {
    let (before_hash, hash_suffix) = match raw_url.split_once('#') {
        Some((b, h)) => (b, Some(h)),
        None => (raw_url, None),
    };
    let (base_path, query_str) = match before_hash.split_once('?') {
        Some((b, q)) => (b, q),
        None => return (raw_url.to_string(), Vec::new()),
    };

    let mut kept_pairs = Vec::new();
    let mut removed_params = Vec::new();

    for pair in query_str.split('&') {
        if pair.is_empty() {
            continue;
        }
        let key = pair.split('=').next().unwrap_or("");
        let key_lower = key.trim().to_ascii_lowercase();
        if is_tracking_param(&key_lower) {
            removed_params.push(key.to_string());
        } else {
            kept_pairs.push(pair);
        }
    }

    let mut rebuilt = String::with_capacity(raw_url.len());
    rebuilt.push_str(base_path);
    if !kept_pairs.is_empty() {
        rebuilt.push('?');
        rebuilt.push_str(&kept_pairs.join("&"));
    }
    if let Some(h) = hash_suffix {
        if !h.to_ascii_lowercase().starts_with("utm_") {
            rebuilt.push('#');
            rebuilt.push_str(h);
        } else {
            removed_params.push("#utm_fragment".to_string());
        }
    }

    (rebuilt, removed_params)
}

pub fn sanitize_tracking_urls(raw_text: &str) -> UrlSanitizeReport {
    let mut cleaned_text = String::with_capacity(raw_text.len());
    let mut items = Vec::new();
    let mut urls_inspected = 0usize;
    let mut trackers_removed = 0usize;

    for token in raw_text.split_inclusive(|c: char| c.is_whitespace()) {
        let trimmed = token.trim_end_matches(|c: char| c.is_whitespace());
        let trailing_ws = &token[trimmed.len()..];
        let clean_candidate =
            trimmed.trim_matches(|c: char| matches!(c, '<' | '>' | '"' | '\'' | '(' | ')' | ','));

        if clean_candidate.starts_with("https://") || clean_candidate.starts_with("http://") {
            urls_inspected += 1;
            let (clean_url, removed) = clean_single_url(clean_candidate);
            trackers_removed += removed.len();
            items.push(SanitizedUrlItem {
                original_url: clean_candidate.to_string(),
                clean_url: clean_url.clone(),
                removed_params: removed,
            });
            let replaced = trimmed.replacen(clean_candidate, &clean_url, 1);
            cleaned_text.push_str(&replaced);
            cleaned_text.push_str(trailing_ws);
        } else {
            cleaned_text.push_str(token);
        }
    }

    UrlSanitizeReport {
        cleaned_text,
        urls_inspected,
        trackers_removed,
        items,
    }
}

fn extract_email_domain(raw_value: &str) -> String {
    let clean = raw_value.trim();
    let inner = if let Some((_, after_lt)) = clean.rsplit_once('<') {
        after_lt.trim_end_matches('>')
    } else {
        clean.trim_matches(|c: char| c == '<' || c == '>' || c == '"' || c.is_whitespace())
    };
    inner
        .rsplit_once('@')
        .map(|(_, dom)| dom.trim().to_ascii_lowercase())
        .unwrap_or_default()
}

fn parse_auth_token(auth_results_lower: &str, token_prefix: &str) -> String {
    if let Some(pos) = auth_results_lower.find(token_prefix) {
        let rest = &auth_results_lower[pos + token_prefix.len()..];
        let status: String = rest
            .chars()
            .take_while(|c| c.is_ascii_alphanumeric() || *c == '-' || *c == '_')
            .collect();
        if !status.is_empty() {
            return status;
        }
    }
    "none".to_string()
}

pub fn analyze_email_headers(raw_headers: &str) -> Result<EmailHeaderAnalysis, String> {
    let trimmed = raw_headers.trim();
    if trimmed.is_empty() {
        return Err("Please paste raw email headers to analyze.".to_string());
    }

    let mut unfolded_lines: Vec<String> = Vec::new();
    for line in trimmed.lines() {
        if (line.starts_with(' ') || line.starts_with('\t')) && !unfolded_lines.is_empty() {
            if let Some(last) = unfolded_lines.last_mut() {
                last.push(' ');
                last.push_str(line.trim());
            }
        } else {
            unfolded_lines.push(line.trim_end().to_string());
        }
    }

    let mut from_header = String::new();
    let mut return_path = String::new();
    let mut reply_to = String::new();
    let mut subject = String::new();
    let mut auth_results_combined = String::new();
    let mut received_lines: Vec<String> = Vec::new();
    let mut has_originating_ip = false;
    let mut has_user_agent_leak = false;
    let mut mailx_relay_detected = false;

    for line in &unfolded_lines {
        if let Some((k, v)) = line.split_once(':') {
            let key = k.trim().to_ascii_lowercase();
            let val = v.trim();
            match key.as_str() {
                "from" if from_header.is_empty() => from_header = val.to_string(),
                "return-path" if return_path.is_empty() => return_path = val.to_string(),
                "reply-to" if reply_to.is_empty() => reply_to = val.to_string(),
                "subject" if subject.is_empty() => subject = val.to_string(),
                "authentication-results" | "arc-authentication-results" => {
                    auth_results_combined.push(' ');
                    auth_results_combined.push_str(&val.to_ascii_lowercase());
                }
                "received-spf" => {
                    if !auth_results_combined.contains("spf=") {
                        let first_word = val
                            .split_whitespace()
                            .next()
                            .unwrap_or("none")
                            .to_ascii_lowercase();
                        auth_results_combined.push_str(&format!(" spf={first_word}"));
                    }
                }
                "received" => {
                    if val.to_ascii_lowercase().contains("mailx")
                        || val.to_ascii_lowercase().contains("zeropost")
                    {
                        mailx_relay_detected = true;
                    }
                    received_lines.push(val.to_string());
                }
                "x-originating-ip" | "x-sender-ip" | "x-client-ip" => {
                    has_originating_ip = true;
                }
                "x-mailer" | "user-agent" => {
                    has_user_agent_leak = true;
                }
                "x-mailx-id" | "x-mailx-alias" | "x-anonaddy-original-sender" => {
                    mailx_relay_detected = true;
                }
                _ => {}
            }
        }
    }

    let spf_status = parse_auth_token(&auth_results_combined, "spf=");
    let dkim_status = parse_auth_token(&auth_results_combined, "dkim=");
    let dmarc_status = parse_auth_token(&auth_results_combined, "dmarc=");

    let from_domain = extract_email_domain(&from_header);
    let return_domain = extract_email_domain(&return_path);
    let reply_domain = extract_email_domain(&reply_to);

    if from_domain.contains("mailx") || return_domain.contains("mailx") {
        mailx_relay_detected = true;
    }

    let from_return_path_mismatch = !from_domain.is_empty()
        && !return_domain.is_empty()
        && from_domain != return_domain
        && !return_domain.ends_with(&format!(".{from_domain}"))
        && !from_domain.ends_with(&format!(".{return_domain}"));

    let reply_to_mismatch =
        !from_domain.is_empty() && !reply_domain.is_empty() && from_domain != reply_domain;

    let mut hops = Vec::with_capacity(received_lines.len());
    for (idx, rec) in received_lines.iter().rev().enumerate() {
        let lower = rec.to_ascii_lowercase();
        let from_host = lower
            .split("from ")
            .nth(1)
            .and_then(|s| s.split_whitespace().next())
            .unwrap_or("local")
            .to_string();
        let by_host = lower
            .split("by ")
            .nth(1)
            .and_then(|s| s.split_whitespace().next())
            .unwrap_or("relay")
            .to_string();
        let protocol = lower
            .split("with ")
            .nth(1)
            .and_then(|s| s.split_whitespace().next())
            .unwrap_or("esmtps")
            .to_uppercase();
        let timestamp = rec
            .rsplit_once(';')
            .map(|(_, ts)| ts.trim().to_string())
            .unwrap_or_default();
        hops.push(HeaderHopItem {
            index: idx + 1,
            from_host,
            by_host,
            protocol,
            timestamp,
        });
    }

    let mut score: i32 = 55;
    let mut findings = Vec::new();

    if spf_status == "pass" {
        score += 15;
        findings.push("SPF signature passed server verification.".to_string());
    } else if spf_status == "fail" || spf_status == "softfail" {
        score -= 22;
        findings.push(format!("SPF check reported '{spf_status}' — sender IP may be unauthorized."));
    }

    if dkim_status == "pass" {
        score += 15;
        findings.push("DKIM cryptographic signature is intact.".to_string());
    } else if dkim_status == "fail" {
        score -= 22;
        findings.push("DKIM signature verification failed.".to_string());
    }

    if dmarc_status == "pass" {
        score += 15;
        findings.push("DMARC domain alignment policy passed.".to_string());
    } else if dmarc_status == "fail" {
        score -= 25;
        findings.push("DMARC alignment failed — strong spoofing indicator.".to_string());
    }

    if from_return_path_mismatch && !mailx_relay_detected {
        score -= 14;
        findings.push(format!(
            "From domain ({from_domain}) differs from Return-Path ({return_domain})."
        ));
    }

    if reply_to_mismatch {
        score -= 16;
        findings.push(format!(
            "Reply-To domain ({reply_domain}) redirects replies away from From domain ({from_domain})."
        ));
    }

    if has_originating_ip {
        score -= 10;
        findings.push("Originating client IP header is exposed in headers.".to_string());
    } else {
        findings.push("No originating client IP leak detected.".to_string());
    }

    if has_user_agent_leak {
        score -= 5;
        findings.push("X-Mailer / User-Agent fingerprint header is present.".to_string());
    }

    if mailx_relay_detected {
        score += 8;
        findings.push("Protected Mailx relay routing confirmed.".to_string());
    }

    let clamped_score = score.clamp(0, 100) as u8;
    let verdict = match clamped_score {
        80..=100 => "Verified & Hardened",
        55..=79 => "Moderate Caution",
        _ => "High Spoofing / Leak Risk",
    }
    .to_string();

    Ok(EmailHeaderAnalysis {
        security_score: clamped_score,
        verdict,
        spf_status,
        dkim_status,
        dmarc_status,
        from_header,
        return_path,
        reply_to,
        subject,
        from_return_path_mismatch,
        reply_to_mismatch,
        mailx_relay_detected,
        client_ip_stripped: !has_originating_ip,
        hop_count: hops.len(),
        hops,
        findings,
    })
}

pub fn audit_alias_hygiene(aliases_json: &str) -> Result<AliasHygieneReport, String> {
    let trimmed = aliases_json.trim();
    let aliases: Vec<Alias> = if trimmed.is_empty() {
        Vec::new()
    } else if trimmed.starts_with('[') {
        serde_json::from_str(trimmed).map_err(|e| format!("Failed to parse aliases JSON: {e}"))?
    } else {
        let wrapper: serde_json::Value =
            serde_json::from_str(trimmed).map_err(|e| format!("Failed to parse JSON: {e}"))?;
        if let Some(arr) = wrapper.get("aliases") {
            serde_json::from_value(arr.clone())
                .map_err(|e| format!("Failed to parse aliases array: {e}"))?
        } else {
            Vec::new()
        }
    };

    let active_pool: Vec<&Alias> = aliases.iter().filter(|a| !a.is_deleted()).collect();
    let total_inspected = active_pool.len();

    let mut burned_count = 0usize;
    let mut unlabeled_count = 0usize;
    let mut duplicate_service_count = 0usize;
    let mut dormant_count = 0usize;
    let mut issues = Vec::new();

    let mut desc_map: HashMap<String, Vec<&Alias>> = HashMap::new();
    let mut recipient_counts: HashMap<String, usize> = HashMap::new();

    for alias in &active_pool {
        for rec in alias.recipient_list() {
            *recipient_counts.entry(rec.to_ascii_lowercase()).or_insert(0) += 1;
        }

        let forwards = alias.stats.forwards.max(0) as u64;
        let blocks = alias.stats.blocks.max(0) as u64;
        let replies = alias.stats.replies.max(0) as u64;
        let sends = alias.stats.sends.max(0) as u64;
        let total_traffic = forwards + blocks + replies + sends;

        if blocks >= 5 && (blocks * 100) / total_traffic.max(1) >= 40 {
            burned_count += 1;
            issues.push(AliasHygieneIssue {
                alias_id: alias.id.clone(),
                alias_name: alias.name.clone(),
                category: "burned".to_string(),
                severity: "high".to_string(),
                detail: format!(
                    "{blocks} blocked vs {forwards} forwarded emails — likely leaked to spam lists"
                ),
                recommended_action: "Pause or rotate this alias and block the offending sender domain."
                    .to_string(),
            });
        }

        if alias.enabled && !alias.wildcard && alias.description.trim().is_empty() {
            unlabeled_count += 1;
            issues.push(AliasHygieneIssue {
                alias_id: alias.id.clone(),
                alias_name: alias.name.clone(),
                category: "unlabeled".to_string(),
                severity: "medium".to_string(),
                detail: "Active alias has no service label or description".to_string(),
                recommended_action: "Add the service or website name so future leaks can be traced."
                    .to_string(),
            });
        }

        if alias.enabled && !alias.wildcard && total_traffic == 0 {
            dormant_count += 1;
        }

        let norm_desc = alias.description.trim().to_ascii_lowercase();
        if !norm_desc.is_empty() && alias.enabled {
            desc_map.entry(norm_desc).or_default().push(*alias);
        }
    }

    for (service_key, group) in &desc_map {
        if group.len() > 1 {
            duplicate_service_count += group.len() - 1;
            if let Some(first) = group.first() {
                issues.push(AliasHygieneIssue {
                    alias_id: first.id.clone(),
                    alias_name: first.name.clone(),
                    category: "duplicate".to_string(),
                    severity: "low".to_string(),
                    detail: format!(
                        "{} active aliases share the label '{}'",
                        group.len(),
                        service_key
                    ),
                    recommended_action: "Consolidate or disable redundant aliases for the same service."
                        .to_string(),
                });
            }
        }
    }

    let max_rec = recipient_counts.values().copied().max().unwrap_or(0);
    let recipient_concentration_percent = if total_inspected == 0 {
        0
    } else {
        ((max_rec * 100) / total_inspected).min(100) as u8
    };

    let mut penalty: u8 = 0;
    penalty = penalty.saturating_add((burned_count as u8).saturating_mul(12).min(36));
    penalty = penalty.saturating_add((unlabeled_count as u8).saturating_mul(4).min(24));
    penalty = penalty.saturating_add((duplicate_service_count as u8).saturating_mul(5).min(15));
    penalty = penalty.saturating_add((dormant_count as u8).saturating_mul(2).min(10));

    let hygiene_score = 100u8.saturating_sub(penalty);
    let grade = match hygiene_score {
        92..=100 => "A+",
        82..=91 => "A",
        70..=81 => "B+",
        58..=69 => "B",
        _ => "C",
    }
    .to_string();

    Ok(AliasHygieneReport {
        hygiene_score,
        grade,
        total_inspected,
        burned_count,
        unlabeled_count,
        duplicate_service_count,
        dormant_count,
        recipient_concentration_percent,
        issues,
    })
}

fn estimate_shannon_entropy_bits(local_part: &str) -> f32 {
    let len = local_part.len() as f32;
    if len <= 0.0 {
        return 0.0;
    }
    let has_lower = local_part.chars().any(|c| c.is_ascii_lowercase());
    let has_digit = local_part.chars().any(|c| c.is_ascii_digit());
    let has_sep = local_part
        .chars()
        .any(|c| c == '.' || c == '-' || c == '_' || c == '+');
    let mut pool = 0.0f32;
    if has_lower {
        pool += 26.0;
    }
    if has_digit {
        pool += 10.0;
    }
    if has_sep {
        pool += 4.0;
    }
    let eff_pool = pool.max(16.0);
    (len * eff_pool.log2() * 10.0).round() / 10.0
}

pub fn generate_contextual_aliases(
    service_name: &str,
    domain: &str,
) -> Result<ContextualAliasSuggestions, String> {
    let clean_domain = domain.trim().trim_start_matches('@').to_ascii_lowercase();
    let eff_domain = if clean_domain.is_empty() || !clean_domain.contains('.') {
        "mailx.net".to_string()
    } else {
        clean_domain
    };

    let raw_service = service_name
        .trim()
        .trim_start_matches("https://")
        .trim_start_matches("http://")
        .trim_start_matches("www.");
    let host_part = raw_service.split('/').next().unwrap_or(raw_service);
    let base_token: String = host_part
        .split('.')
        .next()
        .unwrap_or(host_part)
        .chars()
        .filter(|c| c.is_ascii_alphanumeric())
        .map(|c| c.to_ascii_lowercase())
        .take(14)
        .collect();

    let normalized_service = if base_token.is_empty() {
        "vault".to_string()
    } else {
        base_token
    };

    let mut service_hash: u64 = 0xCBF2_9CE4_8422_2325;
    for b in normalized_service.bytes() {
        service_hash ^= b as u64;
        service_hash = service_hash.wrapping_mul(0x0100_0000_01B3);
    }

    let r1 = next_splitmix64(service_hash);
    let r2 = next_splitmix64(r1 ^ 0xA5A5_5A5A_F0F0_0F0F);

    let word_a = STEALTH_WORDS_A[(r1 as usize) % STEALTH_WORDS_A.len()].to_ascii_lowercase();
    let word_b = STEALTH_WORDS_B[(r2 as usize) % STEALTH_WORDS_B.len()].to_ascii_lowercase();
    let num_2 = (r1 % 90) + 10;

    let tag_suffix = generate_random_suffix(6);
    let stealth_hash = generate_random_suffix(10);
    let short_tag = generate_random_suffix(4);

    let c1_local = format!("{normalized_service}.{tag_suffix}");
    let c2_local = format!("{word_a}.{word_b}{num_2}");
    let c3_local = format!("mx_{stealth_hash}");
    let c4_local = format!("{normalized_service}_{word_a}_{short_tag}");
    let c5_local = format!("{normalized_service}+{short_tag}");

    let raw_list = [
        ("Compartment Tag", c1_local),
        ("Stealth Word Pair", c2_local),
        ("Zero-Link High Entropy", c3_local),
        ("Hybrid Service + Word", c4_local),
        ("Plus-Subaddressed", c5_local),
    ];

    let candidates = raw_list
        .into_iter()
        .map(|(label, local)| {
            let entropy_bits = estimate_shannon_entropy_bits(&local);
            let alias_email = format!("{local}@{eff_domain}");
            ContextualAliasCandidate {
                label: label.to_string(),
                alias_email,
                local_part: local,
                entropy_bits,
            }
        })
        .collect();

    Ok(ContextualAliasSuggestions {
        normalized_service,
        domain: eff_domain,
        candidates,
    })
}

pub fn url_encode(input: &str) -> String {
    let mut encoded = String::with_capacity(input.len());
    for byte in input.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                encoded.push(byte as char);
            }
            _ => {
                encoded.push_str(&format!("%{:02X}", byte));
            }
        }
    }
    encoded
}

pub fn build_aliases_query_path(filter: &AliasFilter) -> String {
    let limit = filter.limit.clamp(1, 1000);
    let page = filter.page.max(1);
    let mut path = format!(
        "/v1/api/aliases?page={}&limit={}&sort_by={}&sort_order={}",
        page,
        limit,
        url_encode(&filter.sort_by),
        url_encode(&filter.sort_order)
    );
    if !filter.status.is_empty() {
        path.push_str(&format!("&status={}", url_encode(&filter.status)));
    }
    if !filter.wildcard.is_empty() {
        path.push_str(&format!("&wildcard={}", url_encode(&filter.wildcard)));
    }
    if !filter.search.trim().is_empty() {
        path.push_str(&format!("&search={}", url_encode(filter.search.trim())));
    }
    path
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_validate_access_key_valid_and_invalid() {
        let uuid = "550e8400-e29b-41d4-a716-446655440000";
        let token = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6";
        let valid_key = format!("{uuid}.{token}");
        assert_eq!(valid_key.len(), 85);
        assert!(validate_access_key(&valid_key).is_ok());
        assert!(validate_access_key("short_key").is_err());
    }

    #[test]
    fn test_generate_reverse_alias_exact_mailx_formula() {
        let send_addr =
            generate_reverse_alias("autumn.wind42@mailx.net", "support@github.com").unwrap();
        assert_eq!(send_addr, "autumn.wind42+support=github.com@mailx.net");
    }

    #[test]
    fn test_generate_wildcard_on_the_fly() {
        let plus_alias = generate_wildcard_on_the_fly("*+contacts@mailx.net", "signal").unwrap();
        assert_eq!(plus_alias, "signal+contacts@mailx.net");

        let dot_alias = generate_wildcard_on_the_fly("*.contacts@mailx.net", "dentist").unwrap();
        assert_eq!(dot_alias, "dentist.contacts@mailx.net");
    }

    #[test]
    fn test_decode_trackers_report() {
        let encoded = "eyJuIjoyLCJ0IjpbeyJkIjoidHJhY2suZXhhbXBsZS5jb20iLCJlIjoiaW1nIiwiciI6ImRvbWFpbiJ9LHsiZCI6Im1haWxjaGltcC5jb20iLCJlIjoiaW1nIiwiciI6InRpbnkifV19";
        let report =
            decode_trackers_report(&format!("https://mailx.net/trackers-report#{encoded}"))
                .unwrap();
        assert_eq!(report.total, 2);
        assert_eq!(report.trackers.len(), 2);
        assert_eq!(report.trackers[0].domain, "track.example.com");
    }

    #[test]
    fn test_nato_and_privacy_posture() {
        let tokens = encode_nato_phonetic("a@b");
        assert_eq!(tokens.len(), 3);
        assert_eq!(tokens[0].word, "Alpha");
        assert_eq!(tokens[1].word, "At");
        let posture = compute_privacy_posture(120, 30, 10, 12, true, true, true, 3);
        assert_eq!(posture.score, 100);
        assert_eq!(posture.grade, "A+");
    }

    #[test]
    fn test_build_aliases_query_path() {
        let filter = AliasFilter {
            page: 1,
            limit: 50,
            sort_by: "created_at".to_string(),
            sort_order: "DESC".to_string(),
            status: "active".to_string(),
            wildcard: "false".to_string(),
            search: "github.com".to_string(),
        };
        let path = build_aliases_query_path(&filter);
        assert_eq!(
            path,
            "/v1/api/aliases?page=1&limit=50&sort_by=created_at&sort_order=DESC&status=active&wildcard=false&search=github.com"
        );
    }

    #[test]
    fn test_sanitize_tracking_urls_and_email_headers() {
        let input = "Check https://example.com/deal?id=42&utm_source=newsletter&fbclid=IwAR123&gclid=abc99 now!";
        let rep = sanitize_tracking_urls(input);
        assert_eq!(rep.urls_inspected, 1);
        assert_eq!(rep.trackers_removed, 3);
        assert!(rep.cleaned_text.contains("https://example.com/deal?id=42"));
        assert!(!rep.cleaned_text.contains("fbclid"));

        let raw_hdr = "From: Billing <billing@service.org>\nReturn-Path: <bounce@service.org>\nAuthentication-Results: mx.mailx.net; spf=pass; dkim=pass; dmarc=pass\nReceived: from mta.service.org by mx.mailx.net with ESMTPS; Tue, 17 Nov 2026 20:00:00 +0000";
        let analysis = analyze_email_headers(raw_hdr).unwrap();
        assert_eq!(analysis.spf_status, "pass");
        assert_eq!(analysis.dkim_status, "pass");
        assert_eq!(analysis.dmarc_status, "pass");
        assert!(analysis.mailx_relay_detected);
        assert!(analysis.security_score >= 90);
    }

    #[test]
    fn test_audit_alias_hygiene_and_contextual_suggestions() {
        let sample_json = r#"[{"id":"1","name":"shop@mailx.net","enabled":true,"description":"","recipients":"me@proton.me","stats":{"forwards":2,"blocks":8,"replies":0,"sends":0}}]"#;
        let hygiene = audit_alias_hygiene(sample_json).unwrap();
        assert_eq!(hygiene.burned_count, 1);
        assert_eq!(hygiene.unlabeled_count, 1);

        let suggestions = generate_contextual_aliases("https://github.com/login", "mailx.net").unwrap();
        assert_eq!(suggestions.normalized_service, "github");
        assert_eq!(suggestions.candidates.len(), 5);
    }
}
