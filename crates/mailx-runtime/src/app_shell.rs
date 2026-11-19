//! Rust-owned application shell state and navigation reducer.
//! Android/Compose renders the state; Rust validates and transitions it.

use serde_json::{json, Value};

const ROUTES: &[&str] = &["home", "aliases", "recipients", "rules", "settings"];

fn route_or_default(value: Option<&Value>) -> &'static str {
    let candidate = value.and_then(Value::as_str);

    ROUTES
        .iter()
        .copied()
        .find(|route| Some(*route) == candidate)
        .unwrap_or("home")
}

/// Reduce an app-shell event into a new serializable state.
///
/// Supported events:
/// - `{"type":"navigate","route":"aliases"}`
/// - `{"type":"open_sheet","sheet":"create_alias"}`
/// - `{"type":"close_sheet"}`
/// - `{"type":"authenticate"}`
/// - `{"type":"sign_out"}`
pub fn reduce_app_shell_state(state_json: &str, event_json: &str) -> Result<Value, String> {
    let state: Value = if state_json.trim().is_empty() {
        json!({})
    } else {
        serde_json::from_str(state_json)
            .map_err(|error| format!("Invalid app shell state: {error}"))?
    };
    let event: Value = serde_json::from_str(event_json)
        .map_err(|error| format!("Invalid app shell event: {error}"))?;

    let event_type = event
        .get("type")
        .and_then(Value::as_str)
        .ok_or_else(|| "App shell event is missing type".to_string())?;

    let mut route = route_or_default(state.get("route")).to_string();
    let mut authenticated = state
        .get("authenticated")
        .and_then(Value::as_bool)
        .unwrap_or(false);
    let mut active_sheet = state
        .get("active_sheet")
        .and_then(Value::as_str)
        .map(str::to_string);

    match event_type {
        "navigate" => {
            let next_route = event
                .get("route")
                .and_then(Value::as_str)
                .ok_or_else(|| "Navigate event is missing route".to_string())?;
            if !ROUTES.contains(&next_route) {
                return Err(format!("Unknown app route: {next_route}"));
            }
            if !authenticated {
                return Err("Authentication is required before navigation".to_string());
            }
            route = next_route.to_string();
            active_sheet = None;
        }
        "open_sheet" => {
            if !authenticated {
                return Err("Authentication is required before opening a sheet".to_string());
            }
            let sheet = event
                .get("sheet")
                .and_then(Value::as_str)
                .filter(|value| !value.trim().is_empty())
                .ok_or_else(|| "Open-sheet event is missing sheet".to_string())?;
            active_sheet = Some(sheet.to_string());
        }
        "close_sheet" => active_sheet = None,
        "authenticate" => authenticated = true,
        "sign_out" => {
            authenticated = false;
            route = "home".to_string();
            active_sheet = None;
        }
        other => return Err(format!("Unsupported app shell event: {other}")),
    }

    let revision = state
        .get("revision")
        .and_then(Value::as_u64)
        .unwrap_or(0)
        .saturating_add(1);

    Ok(json!({
        "route": route,
        "authenticated": authenticated,
        "active_sheet": active_sheet,
        "revision": revision
    }))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn navigation_requires_authentication() {
        let error = reduce_app_shell_state(
            r#"{"route":"home","authenticated":false}"#,
            r#"{"type":"navigate","route":"aliases"}"#,
        )
        .unwrap_err();
        assert!(error.contains("Authentication"));
    }

    #[test]
    fn authenticated_navigation_updates_route_and_closes_sheet() {
        let result = reduce_app_shell_state(
            r#"{"route":"home","authenticated":true,"active_sheet":"profile","revision":4}"#,
            r#"{"type":"navigate","route":"aliases"}"#,
        )
        .unwrap();
        assert_eq!(result["route"], "aliases");
        assert_eq!(result["active_sheet"], Value::Null);
        assert_eq!(result["revision"], 5);
    }

    #[test]
    fn sign_out_resets_route_and_sheet() {
        let result = reduce_app_shell_state(
            r#"{"route":"settings","authenticated":true,"active_sheet":"account"}"#,
            r#"{"type":"sign_out"}"#,
        )
        .unwrap();
        assert_eq!(result["route"], "home");
        assert!(!result["authenticated"].as_bool().unwrap_or(true));
        assert_eq!(result["active_sheet"], Value::Null);
    }

    #[test]
    fn unknown_routes_are_rejected() {
        let error = reduce_app_shell_state(
            r#"{"authenticated":true}"#,
            r#"{"type":"navigate","route":"inbox"}"#,
        )
        .unwrap_err();
        assert!(error.contains("Unknown app route"));
    }
}
