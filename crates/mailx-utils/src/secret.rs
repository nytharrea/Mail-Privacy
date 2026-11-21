use std::fmt;
use zeroize::Zeroize;

#[derive(Clone)]
pub struct SecretString {
    inner: String,
}

impl SecretString {
    pub fn new(val: impl Into<String>) -> Self {
        Self { inner: val.into() }
    }

    pub fn expose(&self) -> &str {
        &self.inner
    }

    pub fn len(&self) -> usize {
        self.inner.len()
    }

    pub fn is_empty(&self) -> bool {
        self.inner.is_empty()
    }
}

fn constant_time_eq(left: &[u8], right: &[u8]) -> bool {
    if left.len() != right.len() {
        return false;
    }
    let mut diff = 0u8;
    for (a, b) in left.iter().zip(right.iter()) {
        diff |= a ^ b;
    }
    std::hint::black_box(diff) == 0
}

impl PartialEq for SecretString {
    fn eq(&self, other: &Self) -> bool {
        constant_time_eq(self.inner.as_bytes(), other.inner.as_bytes())
    }
}

impl Eq for SecretString {}

impl fmt::Debug for SecretString {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "SecretString([REDACTED; {} chars])", self.inner.len())
    }
}

impl fmt::Display for SecretString {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "[REDACTED]")
    }
}

impl Drop for SecretString {
    fn drop(&mut self) {
        self.inner.zeroize();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn debug_and_display_never_reveal_the_secret() {
        let secret = SecretString::new("hunter2-token");
        assert!(!format!("{secret:?}").contains("hunter2"));
        assert!(!format!("{secret}").contains("hunter2"));
        assert_eq!(secret.expose(), "hunter2-token");
        assert_eq!(secret.len(), 13);
        assert!(!secret.is_empty());
    }

    #[test]
    fn equality_compares_full_contents() {
        assert_eq!(SecretString::new("abc"), SecretString::new("abc"));
        assert_ne!(SecretString::new("abc"), SecretString::new("abd"));
        assert_ne!(SecretString::new("abc"), SecretString::new("abcd"));
        assert_eq!(SecretString::new(""), SecretString::new(""));
    }

    #[test]
    fn clones_are_independent() {
        let original = SecretString::new("value");
        let copy = original.clone();
        drop(original);
        assert_eq!(copy.expose(), "value");
    }
}
