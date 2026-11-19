use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ExpectedDnsRecord {
    pub record_type: String,
    pub host: String,
    pub value: String,
    pub priority: Option<u16>,
    pub purpose: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DnsSetupTemplate {
    pub domain: String,
    pub is_valid_hostname: bool,
    pub records: Vec<ExpectedDnsRecord>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct PgpKeyInspection {
    pub is_valid_armor: bool,
    pub key_type_hint: String,
    pub version_header: String,
    pub comment_header: String,
    pub base64_body_bytes: usize,
    pub has_crc24_footer: bool,
    pub crc24_checksum: String,
}

pub fn validate_custom_domain_hostname(raw_domain: &str) -> Result<String, String> {
    let clean = raw_domain.trim().trim_end_matches('.').to_ascii_lowercase();
    if clean.is_empty() {
        return Err("Domain name cannot be empty.".to_string());
    }
    if clean.len() > 253 {
        return Err("Domain name exceeds maximum RFC 1035 length of 253 characters.".to_string());
    }
    if !clean.contains('.') {
        return Err("Domain name must include a valid top-level domain (e.g. example.com).".to_string());
    }

    for label in clean.split('.') {
        if label.is_empty() || label.len() > 63 {
            return Err("Each domain label must be between 1 and 63 characters.".to_string());
        }
        if label.starts_with('-') || label.ends_with('-') {
            return Err("Domain labels cannot start or end with a hyphen.".to_string());
        }
        if !label.chars().all(|c| c.is_ascii_alphanumeric() || c == '-') {
            return Err("Domain contains invalid characters.".to_string());
        }
    }

    Ok(clean)
}

pub fn build_dns_setup_template(
    domain_name: &str,
    verification_token: &str,
    service_domain: &str,
    mx_hosts: &[String],
    dkim_selectors: &[String],
) -> DnsSetupTemplate {
    let valid_dom = validate_custom_domain_hostname(domain_name);
    let clean_dom = valid_dom
        .as_ref()
        .cloned()
        .unwrap_or_else(|_| domain_name.trim().to_ascii_lowercase());
    let svc = if service_domain.trim().is_empty() {
        "mailx.net"
    } else {
        service_domain.trim()
    };

    let mut records = Vec::new();

    if !verification_token.trim().is_empty() {
        records.push(ExpectedDnsRecord {
            record_type: "TXT".to_string(),
            host: "@".to_string(),
            value: format!("mailx-verification={}", verification_token.trim()),
            priority: None,
            purpose: "Domain ownership verification".to_string(),
        });
    }

    let effective_mx: Vec<String> = if mx_hosts.is_empty() {
        vec![format!("mx1.{}", svc), format!("mx2.{}", svc)]
    } else {
        mx_hosts.to_vec()
    };

    for (idx, mx_host) in effective_mx.iter().enumerate() {
        records.push(ExpectedDnsRecord {
            record_type: "MX".to_string(),
            host: "@".to_string(),
            value: mx_host.trim().to_string(),
            priority: Some(((idx + 1) * 10) as u16),
            purpose: format!("Inbound mail routing (MX #{})", idx + 1),
        });
    }

    records.push(ExpectedDnsRecord {
        record_type: "TXT".to_string(),
        host: "@".to_string(),
        value: format!("v=spf1 include:spf.{} ~all", svc),
        priority: None,
        purpose: "Sender Policy Framework (SPF) authentication".to_string(),
    });

    records.push(ExpectedDnsRecord {
        record_type: "TXT".to_string(),
        host: "_dmarc".to_string(),
        value: "v=DMARC1; p=quarantine; pct=100".to_string(),
        priority: None,
        purpose: "Domain-based Message Authentication (DMARC)".to_string(),
    });

    let effective_dkim: Vec<String> = if dkim_selectors.is_empty() {
        vec!["mx1".to_string(), "mx2".to_string(), "mx3".to_string()]
    } else {
        dkim_selectors.to_vec()
    };

    for sel in effective_dkim {
        let s = sel.trim();
        if !s.is_empty() {
            records.push(ExpectedDnsRecord {
                record_type: "CNAME".to_string(),
                host: format!("{}._domainkey", s),
                value: format!("{}._domainkey.{}.", s, svc),
                priority: None,
                purpose: format!("DKIM signing selector ({})", s),
            });
        }
    }

    DnsSetupTemplate {
        domain: clean_dom,
        is_valid_hostname: valid_dom.is_ok(),
        records,
    }
}

pub fn inspect_pgp_public_key_armor(raw_armor: &str) -> PgpKeyInspection {
    let trimmed = raw_armor.trim();
    let begin_marker = "-----BEGIN PGP PUBLIC KEY BLOCK-----";
    let end_marker = "-----END PGP PUBLIC KEY BLOCK-----";

    if !trimmed.starts_with(begin_marker) || !trimmed.ends_with(end_marker) {
        return PgpKeyInspection {
            is_valid_armor: false,
            key_type_hint: "Invalid or unarmored PGP key".to_string(),
            version_header: String::new(),
            comment_header: String::new(),
            base64_body_bytes: 0,
            has_crc24_footer: false,
            crc24_checksum: String::new(),
        };
    }

    let inner = trimmed
        .strip_prefix(begin_marker)
        .unwrap_or("")
        .strip_suffix(end_marker)
        .unwrap_or("")
        .trim();

    let mut version_header = String::new();
    let mut comment_header = String::new();
    let mut crc24_checksum = String::new();
    let mut body_chars = 0usize;
    let mut in_headers = true;

    for line in inner.lines() {
        let l = line.trim();
        if l.is_empty() {
            in_headers = false;
            continue;
        }
        if in_headers && l.contains(':') {
            if let Some((k, v)) = l.split_once(':') {
                match k.trim().to_ascii_lowercase().as_str() {
                    "version" => version_header = v.trim().to_string(),
                    "comment" => comment_header = v.trim().to_string(),
                    _ => {}
                }
            }
            continue;
        }
        in_headers = false;
        if let Some(crc) = l.strip_prefix('=') {
            if crc.len() == 4 {
                crc24_checksum = crc.to_string();
                continue;
            }
        }
        body_chars += l.len();
    }

    let estimated_bytes = (body_chars * 3) / 4;
    let key_type_hint = if estimated_bytes < 256 {
        "Ed25519 / Curve25519 (Modern ECC)".to_string()
    } else if estimated_bytes < 900 {
        "RSA-2048 / NIST P-256".to_string()
    } else {
        "RSA-4096 / Multi-Subkey OpenPGP Keyring".to_string()
    };

    PgpKeyInspection {
        is_valid_armor: body_chars >= 32,
        key_type_hint,
        version_header,
        comment_header,
        base64_body_bytes: estimated_bytes,
        has_crc24_footer: !crc24_checksum.is_empty(),
        crc24_checksum,
    }
}

pub fn format_totp_backup_codes_document(account_email: &str, raw_codes: &str) -> String {
    let mut out = String::from("Mailx - 2FA Backup Recovery Codes\n");
    if !account_email.trim().is_empty() {
        out.push_str(&format!("Account: {}\n", account_email.trim()));
    }
    out.push_str("----------------------------------------\n");
    for (idx, code) in raw_codes
        .split_whitespace()
        .map(|s| s.trim())
        .filter(|s| !s.is_empty())
        .enumerate()
    {
        out.push_str(&format!("{}. {}\n", idx + 1, code));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_validate_custom_domain_and_dns_template() {
        assert!(validate_custom_domain_hostname("privacy.example.org").is_ok());
        assert!(validate_custom_domain_hostname("-bad.com").is_err());

        let tpl = build_dns_setup_template(
            "privacy.example.org",
            "tok_abc123",
            "mailx.net",
            &[],
            &[],
        );
        assert!(tpl.is_valid_hostname);
        assert!(tpl.records.len() >= 7);
    }

    #[test]
    fn test_inspect_pgp_armor_and_backup_codes() {
        let sample_key = "-----BEGIN PGP PUBLIC KEY BLOCK-----\nVersion: OpenPGP.js v5\n\nmDMEZ1234567890abcdefghijklmnopqrstuvwxyz1234567890==\n=abcd\n-----END PGP PUBLIC KEY BLOCK-----";
        let info = inspect_pgp_public_key_armor(sample_key);
        assert!(info.is_valid_armor);
        assert!(info.has_crc24_footer);
        assert_eq!(info.crc24_checksum, "abcd");

        let doc = format_totp_backup_codes_document("user@mailx.net", "1111-2222 3333-4444");
        assert!(doc.contains("1. 1111-2222"));
        assert!(doc.contains("2. 3333-4444"));
    }
}
