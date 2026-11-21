use std::fmt;

const KEY_MAX_LEN: usize = 128;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SecureStoreError {
    InvalidKey,
    Backend(String),
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

pub fn validate_secure_store_key(key: &str) -> Result<(), SecureStoreError> {
    let valid = !key.is_empty()
        && key.len() <= KEY_MAX_LEN
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
        assert!(validate_secure_store_key("a-b_c.D9").is_ok());
        assert!(validate_secure_store_key(&"k".repeat(KEY_MAX_LEN)).is_ok());
    }

    #[test]
    fn rejects_empty_oversized_or_path_like_keys() {
        assert_eq!(
            validate_secure_store_key(""),
            Err(SecureStoreError::InvalidKey)
        );
        assert_eq!(
            validate_secure_store_key("../token"),
            Err(SecureStoreError::InvalidKey)
        );
        assert_eq!(
            validate_secure_store_key("a/b"),
            Err(SecureStoreError::InvalidKey)
        );
        assert_eq!(
            validate_secure_store_key("white space"),
            Err(SecureStoreError::InvalidKey)
        );
        assert_eq!(
            validate_secure_store_key(&"k".repeat(KEY_MAX_LEN + 1)),
            Err(SecureStoreError::InvalidKey)
        );
    }

    #[test]
    fn errors_render_readable_messages() {
        assert_eq!(
            SecureStoreError::Backend("disk".into()).to_string(),
            "Secure-store backend error: disk"
        );
        assert!(SecureStoreError::CorruptData
            .to_string()
            .contains("corrupt"));
    }
}
