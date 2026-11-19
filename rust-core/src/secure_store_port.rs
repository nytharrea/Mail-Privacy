//! Foreign platform port used by the UniFFI boundary.
use std::sync::Arc;

/// Errors returned by Android's Keystore-backed adapter.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum SecureStorePortError {
    #[error("Invalid secure-store key")]
    InvalidKey,
    #[error("Secure-store backend failed: {detail}")]
    Backend { detail: String },
    #[error("Stored value failed authentication")]
    CorruptData,
}

/// Android implements this interface; Rust never stores platform secrets itself.
#[uniffi::export(with_foreign)]
pub trait SecureStorePort: Send + Sync {
    fn get(&self, key: String) -> Result<Option<Vec<u8>>, SecureStorePortError>;
    fn put(&self, key: String, value: Vec<u8>) -> Result<(), SecureStorePortError>;
    fn delete(&self, key: String) -> Result<(), SecureStorePortError>;
}

/// Keep a strong reference to the Kotlin implementation for the lifetime of the core.
pub type SecureStoreHandle = Arc<dyn SecureStorePort>;
