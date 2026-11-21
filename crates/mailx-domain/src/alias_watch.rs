//! Alias watch-list change detection.
//!
//! This module is deliberately side-effect free. Android schedules checks and
//! displays notifications; Rust decides whether a watched alias has new forwards.

use serde::Serialize;
use serde_json::Value;
use std::collections::{HashMap, HashSet};

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct AliasWatchEvent {
    pub alias_id: String,
    pub alias_email: String,
    pub previous_forwards: u64,
    pub current_forwards: u64,
    pub new_forwards: u64,
}

/// Detect increases in forwarded-message counters for explicitly watched aliases.
///
/// Accepted API shapes:
/// - `stats.forwards` (Mailx UI model)
/// - `emails_forwarded` (Addy-compatible API shape)
///
/// Missing previous entries establish the initial baseline and do not notify.
/// Counter decreases are treated as a reset and never produce a notification.
pub fn detect_alias_watch_events(
    previous_aliases: &Value,
    current_aliases: &Value,
    watched_ids: &[String],
) -> Result<Vec<AliasWatchEvent>, String> {
    let previous = previous_aliases
        .as_array()
        .ok_or_else(|| "previous_aliases must be a JSON array".to_string())?;
    let current = current_aliases
        .as_array()
        .ok_or_else(|| "current_aliases must be a JSON array".to_string())?;

    let watched: HashSet<&str> = watched_ids
        .iter()
        .map(String::as_str)
        .filter(|id| !id.trim().is_empty())
        .collect();

    if watched.is_empty() {
        return Ok(Vec::new());
    }

    let previous_counts: HashMap<&str, u64> = previous
        .iter()
        .filter_map(|alias| {
            let id = alias.get("id")?.as_str()?;
            if id.is_empty() || is_deleted(alias) {
                return None;
            }
            Some((id, forwarded_count(alias)))
        })
        .collect();

    let mut events = Vec::new();
    for alias in current {
        let Some(id) = alias.get("id").and_then(Value::as_str) else {
            continue;
        };
        if id.is_empty() || !watched.contains(id) || is_deleted(alias) {
            continue;
        }

        let current_count = forwarded_count(alias);
        let Some(previous_count) = previous_counts.get(id).copied() else {
            continue; // First observation must never generate a false notification.
        };
        if current_count > previous_count {
            events.push(AliasWatchEvent {
                alias_id: id.to_owned(),
                alias_email: alias
                    .get("email")
                    .or_else(|| alias.get("name"))
                    .and_then(Value::as_str)
                    .unwrap_or_default()
                    .to_owned(),
                previous_forwards: previous_count,
                current_forwards: current_count,
                new_forwards: current_count - previous_count,
            });
        }
    }

    Ok(events)
}

fn forwarded_count(alias: &Value) -> u64 {
    alias
        .pointer("/stats/forwards")
        .and_then(Value::as_u64)
        .or_else(|| alias.get("emails_forwarded").and_then(Value::as_u64))
        .unwrap_or(0)
}

fn is_deleted(alias: &Value) -> bool {
    alias
        .get("deleted_at")
        .is_some_and(|value| !value.is_null() && !value.as_str().unwrap_or_default().is_empty())
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn reports_only_forward_increases_for_watched_aliases() {
        let previous = json!([
            {"id":"a","email":"a@mailx.net","stats":{"forwards":4}},
            {"id":"b","email":"b@mailx.net","stats":{"forwards":1}}
        ]);
        let current = json!([
            {"id":"a","email":"a@mailx.net","stats":{"forwards":7}},
            {"id":"b","email":"b@mailx.net","stats":{"forwards":9}}
        ]);
        let events = detect_alias_watch_events(&previous, &current, &["a".into()]).unwrap();

        assert_eq!(events.len(), 1);
        assert_eq!(events[0].alias_id, "a");
        assert_eq!(events[0].new_forwards, 3);
        assert_eq!(events[0].previous_forwards, 4);
        assert_eq!(events[0].current_forwards, 7);
    }

    #[test]
    fn first_observation_does_not_notify() {
        let events = detect_alias_watch_events(
            &json!([]),
            &json!([{"id":"a","name":"a@mailx.net","emails_forwarded":2}]),
            &["a".into()],
        )
        .unwrap();

        assert!(events.is_empty());
    }

    #[test]
    fn ignores_counter_resets_and_deleted_aliases() {
        let previous = json!([
            {"id":"a","stats":{"forwards":10}},
            {"id":"b","stats":{"forwards":0}}
        ]);
        let current = json!([
            {"id":"a","stats":{"forwards":1}},
            {"id":"b","deleted_at":"2026-01-01","stats":{"forwards":5}}
        ]);
        let events =
            detect_alias_watch_events(&previous, &current, &["a".into(), "b".into()]).unwrap();

        assert!(events.is_empty());
    }

    #[test]
    fn empty_watch_list_returns_no_events() {
        let events = detect_alias_watch_events(
            &json!([]),
            &json!([{"id":"a","stats":{"forwards":10}}]),
            &[],
        )
        .unwrap();
        assert!(events.is_empty());
    }

    #[test]
    fn rejects_non_array_input() {
        assert!(detect_alias_watch_events(&json!({}), &json!([]), &[]).is_err());
    }
}
