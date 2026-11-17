use serde::{Deserialize, Serialize};
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DailyStatPoint {
    pub date_iso: String,
    pub label: String,
    pub forwards: usize,
    pub replies: usize,
    pub sends: usize,
    pub blocks: usize,
    pub total: usize,
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct AggregatedUserStats {
    pub forwards: usize,
    pub blocks: usize,
    pub replies: usize,
    pub sends: usize,
    pub aliases: usize,
    pub daily_points: Vec<DailyStatPoint>,
}

/// Converts days since Unix epoch (1970-01-01) into (year, month, day) using Howard Hinnant's civil_from_days algorithm.
pub fn civil_from_days(z: i64) -> (i32, u32, u32) {
    let z = z + 719468;
    let era = if z >= 0 { z } else { z - 146096 } / 146097;
    let doe = (z - era * 146097) as u32;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    let y = (yoe as i64 + era * 400) as i32;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let year = if m <= 2 { y + 1 } else { y };
    (year, m, d)
}

/// Converts a civil (year, month, day) date into days since Unix epoch (1970-01-01).
pub fn days_from_civil(year: i32, month: u32, day: u32) -> i64 {
    let y = year as i64 - if month <= 2 { 1 } else { 0 };
    let era = if y >= 0 { y } else { y - 399 } / 400;
    let yoe = (y - era * 400) as u32;
    let m = month;
    let doy = (153 * (if m > 2 { m - 3 } else { m + 9 }) + 2) / 5 + day - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    era * 146097 + doe as i64 - 719468
}

pub fn current_utc_epoch_days() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| (d.as_secs() / 86_400) as i64)
        .unwrap_or_else(|_| days_from_civil(2026, 11, 17))
}

pub fn format_iso_date_yyyy_mm_dd(year: i32, month: u32, day: u32) -> String {
    format!("{:04}-{:02}-{:02}", year, month, day)
}

pub fn format_short_dd_mm(month: u32, day: u32) -> String {
    format!("{:02}.{:02}", day, month)
}

pub fn extract_iso_date_prefix(raw_timestamp: &str) -> Option<String> {
    let trimmed = raw_timestamp.trim();
    if trimmed.len() < 10 {
        return None;
    }
    let prefix = &trimmed[..10];
    let bytes = prefix.as_bytes();
    if bytes[4] == b'-'
        && bytes[7] == b'-'
        && bytes[0..4].iter().all(|b| b.is_ascii_digit())
        && bytes[5..7].iter().all(|b| b.is_ascii_digit())
        && bytes[8..10].iter().all(|b| b.is_ascii_digit())
    {
        Some(prefix.to_string())
    } else {
        None
    }
}

pub fn aggregate_user_stats_json(
    raw_stats_json: &str,
    fallback_alias_count: usize,
    anchor_epoch_days: Option<i64>,
) -> Result<AggregatedUserStats, String> {
    let root: serde_json::Value = serde_json::from_str(raw_stats_json)
        .map_err(|e| format!("Invalid user stats JSON: {}", e))?;

    let data = root.get("data").unwrap_or(&root);
    let today_days = anchor_epoch_days.unwrap_or_else(current_utc_epoch_days);

    let mut buckets: Vec<(String, String, [usize; 4])> = Vec::with_capacity(7);
    for offset in (0..=6).rev() {
        let day_num = today_days - offset;
        let (y, m, d) = civil_from_days(day_num);
        buckets.push((
            format_iso_date_yyyy_mm_dd(y, m, d),
            format_short_dd_mm(m, d),
            [0usize; 4],
        ));
    }

    if let Some(messages) = data.get("messages").and_then(|v| v.as_array()) {
        for msg in messages {
            let created_at = msg
                .get("created_at")
                .and_then(|v| v.as_str())
                .unwrap_or_default();
            let msg_type = msg.get("type").and_then(|v| v.as_i64()).unwrap_or(1);
            if let Some(day_iso) = extract_iso_date_prefix(created_at) {
                if let Some(bucket) = buckets.iter_mut().find(|(iso, _, _)| iso == &day_iso) {
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

    let daily_points = buckets
        .into_iter()
        .map(|(date_iso, label, counts)| {
            let total = counts[0] + counts[1] + counts[2] + counts[3];
            DailyStatPoint {
                date_iso,
                label,
                forwards: counts[0],
                replies: counts[1],
                sends: counts[2],
                blocks: counts[3],
                total,
            }
        })
        .collect();

    let forwards = data
        .get("forwards")
        .and_then(|v| v.as_u64())
        .unwrap_or(0) as usize;
    let blocks = data
        .get("blocks")
        .and_then(|v| v.as_u64())
        .unwrap_or(0) as usize;
    let replies = data
        .get("replies")
        .and_then(|v| v.as_u64())
        .unwrap_or(0) as usize;
    let sends = data
        .get("sends")
        .and_then(|v| v.as_u64())
        .unwrap_or(0) as usize;
    let aliases = data
        .get("aliases")
        .and_then(|v| v.as_u64())
        .map(|v| v as usize)
        .unwrap_or(fallback_alias_count);

    Ok(AggregatedUserStats {
        forwards,
        blocks,
        replies,
        sends,
        aliases,
        daily_points,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_civil_calendar_roundtrip() {
        let days = days_from_civil(2026, 11, 17);
        let (y, m, d) = civil_from_days(days);
        assert_eq!((y, m, d), (2026, 11, 17));
    }

    #[test]
    fn test_aggregate_user_stats_json() {
        let anchor = days_from_civil(2026, 11, 17);
        let sample = r#"{
            "forwards": 42,
            "blocks": 9,
            "replies": 4,
            "sends": 2,
            "aliases": 15,
            "messages": [
                {"created_at": "2026-11-17T08:30:00Z", "type": 1},
                {"created_at": "2026-11-17T09:15:00Z", "type": 4},
                {"created_at": "2026-11-16T19:00:00Z", "type": 2}
            ]
        }"#;
        let stats = aggregate_user_stats_json(sample, 10, Some(anchor)).unwrap();
        assert_eq!(stats.forwards, 42);
        assert_eq!(stats.blocks, 9);
        assert_eq!(stats.daily_points.len(), 7);
        let last = stats.daily_points.last().unwrap();
        assert_eq!(last.date_iso, "2026-11-17");
        assert_eq!(last.label, "17.11");
        assert_eq!(last.forwards, 1);
        assert_eq!(last.blocks, 1);
        assert_eq!(last.total, 2);
    }
}
