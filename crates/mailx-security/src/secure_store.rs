//! Platform-backed secret storage contract.
//!
//! The Rust core never persists secrets itself. Android supplies an adapter
//! backed by Android Keystore (and an authenticated-encryption primitive).

use mailx_utils::SecretString;
use std::fmt;

/// Errors returned by a platform secure-store implementation.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SecureStoreError {
    /// The requested key is malformed or outside the supported namespace.
    InvalidKey,
    /// The platform storage operation failed.
    Backend(String),
    /// Stored data could not be authenticated or decoded.
    CorruptData,
}

impl fmt::Display for SecureStoreError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::InvalidKey => f.write_str("Invalid secure-store key"),
            Self::Backend(message) => write!(f, "Secure-store backend error: {message}"),
            Self::CorruptData => f.write_str("Secure-store data is corrupt or unauthenticated"),
        }
    }
}

impl std::error::Error for SecureStoreError {}

/// Platform port for credentials and other sensitive values.
///
/// Implementations must use authenticated encryption, must not log values,
/// and should make `delete` idempotent. `get` returns a zeroizing
/// [`SecretString`] wrapper rather than a plain `String`.
pub trait SecureStore: Send + Sync {
    fn get(&self, key: &str) -> Result<Option<SecretString>, SecureStoreError>;
    fn put(&self, key: &str, value: &SecretString) -> Result<(), SecureStoreError>;
    fn delete(&self, key: &str) -> Result<(), SecureStoreError>;
}

/// Validate the stable key namespace before calling a platform adapter.
pub fn validate_secure_store_key(key: &str) -> Result<(), SecureStoreError> {
    let valid = !key.is_empty()
        && key.len() <= 128
        && key
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-'));
    if valid {
        Ok(())
    } else {
        Err(SecureStoreError::InvalidKey)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_stable_namespaced_keys() {
        assert!(validate_secure_store_key("auth.refresh_token").is_ok());
    }

    #[test]
    fn rejects_empty_or_path_like_keys() {
        assert_eq!(
            validate_secure_store_key(""),
            Err(SecureStoreError::InvalidKey)
        );
        assert_eq!(
            validate_secure_store_key("../token"),
            Err(SecureStoreError::InvalidKey)
        );
    }
}
