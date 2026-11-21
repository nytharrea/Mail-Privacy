//! Foreign platform port used by the UniFFI boundary.
use mailx_security::secure_store::SecureStoreError;
use std::fmt;
use std::sync::Arc;

/// Errors returned by Android's Keystore-backed adapter.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Error)]
pub enum SecureStorePortError {
    InvalidKey,
    NotAttached,
    Backend { detail: String },
    CorruptData,
}

impl fmt::Display for SecureStorePortError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::InvalidKey => f.write_str("Invalid secure-store key"),
            Self::NotAttached => f.write_str("Secure store is not attached"),
            Self::Backend { detail } => write!(f, "Secure-store backend failed: {detail}"),
            Self::CorruptData => f.write_str("Stored value failed authentication"),
        }
    }
}

impl std::error::Error for SecureStorePortError {}

impl From<SecureStoreError> for SecureStorePortError {
    fn from(error: SecureStoreError) -> Self {
        match error {
            SecureStoreError::InvalidKey => Self::InvalidKey,
            SecureStoreError::Backend(detail) => Self::Backend { detail },
            SecureStoreError::CorruptData => Self::CorruptData,
        }
    }
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn messages_are_stable_and_descriptive() {
        assert_eq!(
            SecureStorePortError::InvalidKey.to_string(),
            "Invalid secure-store key"
        );
        assert_eq!(
            SecureStorePortError::NotAttached.to_string(),
            "Secure store is not attached"
        );
        assert_eq!(
            SecureStorePortError::Backend {
                detail: "keystore locked".into()
            }
            .to_string(),
            "Secure-store backend failed: keystore locked"
        );
        assert_eq!(
            SecureStorePortError::CorruptData.to_string(),
            "Stored value failed authentication"
        );
    }

    #[test]
    fn security_crate_errors_map_one_to_one() {
        assert_eq!(
            SecureStorePortError::from(SecureStoreError::InvalidKey),
            SecureStorePortError::InvalidKey
        );
        assert_eq!(
            SecureStorePortError::from(SecureStoreError::Backend("x".into())),
            SecureStorePortError::Backend { detail: "x".into() }
        );
        assert_eq!(
            SecureStorePortError::from(SecureStoreError::CorruptData),
            SecureStorePortError::CorruptData
        );
    }

    #[test]
    fn port_errors_are_std_errors() {
        fn assert_error<E: std::error::Error + Send + Sync + 'static>() {}
        assert_error::<SecureStorePortError>();
    }
}
