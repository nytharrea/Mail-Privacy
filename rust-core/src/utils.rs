use crate::models::{
    AliasFilter, PhoneticToken, PrivacyPostureReport, RemovedTrackerItem, TrackersReport,
};
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

static ENTROPY_COUNTER: AtomicU64 = AtomicU64::new(0x9E37_79B9_7F4A_7C15);

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
        return Err(
            "Prefix may only contain letters, digits, '-' or '_'.".to_string(),
        );
    }

    if !wildcard_alias.starts_with("*+") && !wildcard_alias.starts_with("*.") {
        return Err(
            "Selected address is not a Wildcard alias (must start with '*+' or '*.')."
                .to_string(),
        );
    }

    Ok(wildcard_alias.replacen('*', prefix_trimmed, 1))
}

pub fn generate_random_suffix(length: usize) -> String {
    let clamped = length.clamp(6, 12);
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_nanos() as u64)
        .unwrap_or(0x517C_C1B7_2722_0A95);
    let seq = ENTROPY_COUNTER.fetch_add(0x9E37_79B9_7F4A_7C15, Ordering::Relaxed);
    let mut state = nanos ^ seq.rotate_left(17) ^ 0xBF58_476D_1CE4_E5B9;
    if state == 0 {
        state = 0x94D0_49BB_1331_11EB;
    }
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
    let decoded_str = String::from_utf8(decoded_bytes)
        .map_err(|_| "Invalid UTF-8 in trackers report".to_string())?;
    let json_val: serde_json::Value = serde_json::from_str(&decoded_str)
        .map_err(|_| "Invalid JSON in trackers report".to_string())?;

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
}
