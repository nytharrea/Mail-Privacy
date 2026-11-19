//! Long-lived Rust application core.
//!
//! This object is the stable stateful boundary intended for the future UniFFI
//! facade. It deliberately contains no Android or JNI types.

use crate::app_shell::reduce_app_shell_state;
use serde_json::{json, Value};
use std::sync::Mutex;

/// A process-scoped state owner. Create one instance per signed-in app process.
pub struct MailxCore {
    state: Mutex<Value>,
}

impl Default for MailxCore {
    fn default() -> Self {
        Self::new()
    }
}

impl MailxCore {
    /// Create a fresh unauthenticated application state.
    pub fn new() -> Self {
        Self {
            state: Mutex::new(json!({
                "route": "home",
                "authenticated": false,
                "active_sheet": null,
                "revision": 0
            })),
        }
    }

    /// Return an immutable JSON snapshot of the current app-shell state.
    pub fn state_snapshot(&self) -> Result<String, String> {
        let state = self
            .state
            .lock()
            .map_err(|_| "MailxCore state lock is poisoned".to_string())?;
        serde_json::to_string(&*state).map_err(|error| error.to_string())
    }

    /// Apply one event atomically and return the committed state snapshot.
    pub fn dispatch(&self, event_json: &str) -> Result<String, String> {
        let mut state = self
            .state
            .lock()
            .map_err(|_| "MailxCore state lock is poisoned".to_string())?;
        let current = serde_json::to_string(&*state).map_err(|error| error.to_string())?;
        let next = reduce_app_shell_state(&current, event_json)?;
        *state = next;
        serde_json::to_string(&*state).map_err(|error| error.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn core_keeps_state_between_events() {
        let core = MailxCore::new();
        core.dispatch(r#"{"type":"authenticate"}"#).unwrap();
        let state = core
            .dispatch(r#"{"type":"navigate","route":"aliases"}"#)
            .unwrap();
        let parsed: Value = serde_json::from_str(&state).unwrap();
        assert_eq!(parsed["route"], "aliases");
        assert_eq!(parsed["revision"], 2);
    }

    #[test]
    fn rejected_event_does_not_mutate_state() {
        let core = MailxCore::new();
        assert!(core
            .dispatch(r#"{"type":"navigate","route":"aliases"}"#)
            .is_err());
        let state: Value = serde_json::from_str(&core.state_snapshot().unwrap()).unwrap();
        assert_eq!(state["revision"], 0);
    }
}
