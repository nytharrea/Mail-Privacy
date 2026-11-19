//! Typed UniFFI boundary. The legacy JNI API remains available during migration.
use std::sync::{Arc, Mutex};
use crate::secure_store_port::{SecureStorePort, SecureStorePortError};

use serde_json::json;
use thiserror::Error;

#[derive(Debug, Clone, uniffi::Record)]
pub struct AppState {
    pub route: String,
    pub authenticated: bool,
    pub active_sheet: Option<String>,
    pub revision: u64,
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum AppEvent {
    Authenticate,
    SignOut,
    Navigate { route: String },
    OpenSheet { sheet: String },
    CloseSheet,
}

#[derive(Debug, Error, uniffi::Error)]
pub enum MailxCoreError {
    #[error("Invalid application event: {message}")]
    InvalidEvent { message: String },
    #[error("Application core failure: {message}")]
    Internal { message: String },
}

impl From<String> for MailxCoreError {
    fn from(message: String) -> Self {
        Self::InvalidEvent { message }
    }
}

/// Process-scoped Rust state owner exposed to Kotlin as a UniFFI object.
#[derive(uniffi::Object)]
pub struct MailxCore {
    inner: mailx_runtime::MailxCore,
    secure_store: Mutex<Option<Arc<dyn SecureStorePort>>>,
}

#[uniffi::export]
impl MailxCore {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            inner: mailx_runtime::MailxCore::new(),
            secure_store: Mutex::new(None),
        })
    }

    /// Attach the Android Keystore/Tink adapter for this process.
    pub fn attach_secure_store(&self, store: Arc<dyn SecureStorePort>) -> Result<(), MailxCoreError> {
        let mut slot = self.secure_store.lock()
            .map_err(|_| MailxCoreError::Internal { message: "Secure-store lock is poisoned".into() })?;
        *slot = Some(store);
        Ok(())
    }

    pub fn secure_store_get(&self, key: String) -> Result<Option<Vec<u8>>, SecureStorePortError> {
        let store = self.secure_store.lock()
            .map_err(|_| SecureStorePortError::Backend { message: "Secure-store lock is poisoned".into() })?;
        let adapter = store.as_ref()
            .ok_or_else(|| SecureStorePortError::Backend { message: "Secure store is not attached".into() })?;
        adapter.get(key)
    }

    pub fn secure_store_put(&self, key: String, value: Vec<u8>) -> Result<(), SecureStorePortError> {
        let store = self.secure_store.lock()
            .map_err(|_| SecureStorePortError::Backend { message: "Secure-store lock is poisoned".into() })?;
        let adapter = store.as_ref()
            .ok_or_else(|| SecureStorePortError::Backend { message: "Secure store is not attached".into() })?;
        adapter.put(key, value)
    }

    pub fn secure_store_delete(&self, key: String) -> Result<(), SecureStorePortError> {
        let store = self.secure_store.lock()
            .map_err(|_| SecureStorePortError::Backend { message: "Secure-store lock is poisoned".into() })?;
        let adapter = store.as_ref()
            .ok_or_else(|| SecureStorePortError::Backend { message: "Secure store is not attached".into() })?;
        adapter.delete(key)
    }

    pub fn state(&self) -> Result<AppState, MailxCoreError> {
        let snapshot = self.inner.state_snapshot()
            .map_err(|message| MailxCoreError::Internal { message })?;
        parse_state(&snapshot)
    }

    pub fn dispatch(&self, event: AppEvent) -> Result<AppState, MailxCoreError> {
        let payload = match event {
            AppEvent::Authenticate => json!({"type":"authenticate"}),
            AppEvent::SignOut => json!({"type":"sign_out"}),
            AppEvent::Navigate { route } => json!({"type":"navigate","route":route}),
            AppEvent::OpenSheet { sheet } => json!({"type":"open_sheet","sheet":sheet}),
            AppEvent::CloseSheet => json!({"type":"close_sheet"}),
        };
        let encoded = serde_json::to_string(&payload)
            .map_err(|error| MailxCoreError::Internal { message: error.to_string() })?;
        let snapshot = self.inner.dispatch(&encoded)
            .map_err(|message| MailxCoreError::InvalidEvent { message })?;
        parse_state(&snapshot)
    }
}

fn parse_state(snapshot: &str) -> Result<AppState, MailxCoreError> {
    serde_json::from_str(snapshot)
        .map_err(|error| MailxCoreError::Internal { message: error.to_string() })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn typed_api_preserves_state_between_calls() {
        let core = MailxCore::new();
        let authenticated = core.dispatch(AppEvent::Authenticate).unwrap();
        assert!(authenticated.authenticated);
        assert_eq!(authenticated.revision, 1);

        let aliases = core.dispatch(AppEvent::Navigate { route: "aliases".into() }).unwrap();
        assert_eq!(aliases.route, "aliases");
        assert_eq!(aliases.revision, 2);
    }

    #[test]
    fn invalid_navigation_does_not_change_state() {
        let core = MailxCore::new();
        assert!(core.dispatch(AppEvent::Navigate { route: "unknown".into() }).is_err());
        assert_eq!(core.state().unwrap().revision, 0);
    }
}
