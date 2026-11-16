use std::fmt;

/// Hassas verileri (Access Key ve Bearer Token) loglarda sızdırmamak ve
/// bellekten düşerken sıfırlamak (zeroize-on-drop) için sarmalayıcı.
#[derive(Clone, PartialEq, Eq)]
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
        // Bellek bölgesini sıfırla
        unsafe {
            for b in self.inner.as_bytes_mut() {
                std::ptr::write_volatile(b, 0);
            }
        }
    }
}
