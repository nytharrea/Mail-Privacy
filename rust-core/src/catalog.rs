use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CatalogEntry {
    pub key: String,
    pub label: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RuleCatalogSpec {
    pub condition_types: Vec<CatalogEntry>,
    pub boolean_condition_types: Vec<String>,
    pub match_types: Vec<CatalogEntry>,
    pub action_types: Vec<CatalogEntry>,
    pub text_value_actions: Vec<String>,
    pub unsubscribe_behaviours: Vec<(i32, String)>,
}

pub fn build_rule_catalog() -> RuleCatalogSpec {
    RuleCatalogSpec {
        condition_types: vec![
            CatalogEntry {
                key: "sender".to_string(),
                label: "Sender email address".to_string(),
            },
            CatalogEntry {
                key: "sender_domain".to_string(),
                label: "Sender domain".to_string(),
            },
            CatalogEntry {
                key: "sender_name".to_string(),
                label: "Sender display name".to_string(),
            },
            CatalogEntry {
                key: "subject".to_string(),
                label: "Email subject".to_string(),
            },
            CatalogEntry {
                key: "alias".to_string(),
                label: "Alias email address".to_string(),
            },
            CatalogEntry {
                key: "alias_description".to_string(),
                label: "Alias description".to_string(),
            },
            CatalogEntry {
                key: "is_mailing_list".to_string(),
                label: "Email is from a mailing list".to_string(),
            },
            CatalogEntry {
                key: "has_attachments".to_string(),
                label: "Email has attachments".to_string(),
            },
            CatalogEntry {
                key: "alias_is_wildcard".to_string(),
                label: "Alias is a wildcard alias".to_string(),
            },
        ],
        boolean_condition_types: vec![
            "is_mailing_list".to_string(),
            "has_attachments".to_string(),
            "alias_is_wildcard".to_string(),
        ],
        match_types: vec![
            CatalogEntry {
                key: "is".to_string(),
                label: "is exactly".to_string(),
            },
            CatalogEntry {
                key: "is_not".to_string(),
                label: "is not".to_string(),
            },
            CatalogEntry {
                key: "contains".to_string(),
                label: "contains".to_string(),
            },
            CatalogEntry {
                key: "not_contains".to_string(),
                label: "does not contain".to_string(),
            },
            CatalogEntry {
                key: "starts_with".to_string(),
                label: "starts with".to_string(),
            },
            CatalogEntry {
                key: "not_starts_with".to_string(),
                label: "does not start with".to_string(),
            },
            CatalogEntry {
                key: "ends_with".to_string(),
                label: "ends with".to_string(),
            },
            CatalogEntry {
                key: "not_ends_with".to_string(),
                label: "does not end with".to_string(),
            },
            CatalogEntry {
                key: "regex".to_string(),
                label: "matches regex".to_string(),
            },
            CatalogEntry {
                key: "not_regex".to_string(),
                label: "does not match regex".to_string(),
            },
        ],
        action_types: vec![
            CatalogEntry {
                key: "block".to_string(),
                label: "Block the email".to_string(),
            },
            CatalogEntry {
                key: "blocklist_sender".to_string(),
                label: "Add sender email to the blocklist".to_string(),
            },
            CatalogEntry {
                key: "blocklist_domain".to_string(),
                label: "Add sender domain to the blocklist".to_string(),
            },
            CatalogEntry {
                key: "replace_subject".to_string(),
                label: "Replace the subject with".to_string(),
            },
            CatalogEntry {
                key: "set_display_name".to_string(),
                label: "Set the sender name to".to_string(),
            },
            CatalogEntry {
                key: "forward_to".to_string(),
                label: "Forward to recipient(s)".to_string(),
            },
            CatalogEntry {
                key: "disable_encryption".to_string(),
                label: "Turn off PGP encryption".to_string(),
            },
            CatalogEntry {
                key: "hide_banner".to_string(),
                label: "Hide the Mailx banner".to_string(),
            },
            CatalogEntry {
                key: "show_banner".to_string(),
                label: "Show the Mailx banner".to_string(),
            },
            CatalogEntry {
                key: "deactivate_alias".to_string(),
                label: "Deactivate the alias".to_string(),
            },
            CatalogEntry {
                key: "set_alias_description".to_string(),
                label: "Set the alias description to".to_string(),
            },
            CatalogEntry {
                key: "remove_attachments".to_string(),
                label: "Remove attachments".to_string(),
            },
            CatalogEntry {
                key: "stop".to_string(),
                label: "Stop processing more rules".to_string(),
            },
        ],
        text_value_actions: vec![
            "replace_subject".to_string(),
            "set_display_name".to_string(),
            "set_alias_description".to_string(),
        ],
        unsubscribe_behaviours: vec![
            (
                0,
                "Unsubscribe from sender; if that fails, deactivate alias".to_string(),
            ),
            (1, "Deactivate alias".to_string()),
            (2, "Delete alias".to_string()),
            (3, "Block sender email address".to_string()),
            (4, "Block sender domain".to_string()),
        ],
    }
}

pub fn format_iso_date_short(raw: &str) -> String {
    let clean = raw.trim();
    if clean.is_empty() || clean.eq_ignore_ascii_case("null") {
        return "Unknown".to_string();
    }
    let (date_part, rest) = clean.split_once('T').unwrap_or((clean, ""));
    let time_part: String = rest.chars().take(5).collect();
    let parts: Vec<&str> = date_part.split('-').collect();
    if parts.len() == 3 {
        let formatted_date = format!("{}.{}.{}", parts[2], parts[1], parts[0]);
        if !time_part.is_empty() {
            format!("{} {}", formatted_date, time_part)
        } else {
            formatted_date
        }
    } else {
        clean.chars().take(16).collect()
    }
}

pub fn format_alias_type_label(format_key: &str) -> String {
    match format_key.trim().to_ascii_lowercase().as_str() {
        "words" => "Words (e.g. quiet.haze16)".to_string(),
        "random" => "Random (e.g. uf1h0xi)".to_string(),
        "uuid" => "UUID".to_string(),
        "wildcard" => "Wildcard (*)".to_string(),
        "custom" => "Custom".to_string(),
        other => other.to_string(),
    }
}

pub fn validate_pgp_armored_key(raw_key: &str) -> Result<bool, String> {
    let trimmed = raw_key.trim();
    if trimmed.is_empty() {
        return Ok(false);
    }
    if !trimmed.contains("-----BEGIN PGP PUBLIC KEY BLOCK-----")
        || !trimmed.contains("-----END PGP PUBLIC KEY BLOCK-----")
    {
        return Err(
            "PGP key must be ASCII-armored with BEGIN/END PGP PUBLIC KEY BLOCK headers."
                .to_string(),
        );
    }
    Ok(true)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_format_iso_date_short() {
        assert_eq!(
            format_iso_date_short("2026-11-17T16:26:45Z"),
            "17.11.2026 16:26"
        );
        assert_eq!(format_iso_date_short("null"), "Unknown");
    }

    #[test]
    fn test_validate_pgp_armored_key() {
        let valid = "-----BEGIN PGP PUBLIC KEY BLOCK-----\nabc\n-----END PGP PUBLIC KEY BLOCK-----";
        assert_eq!(validate_pgp_armored_key(valid), Ok(true));
        assert!(validate_pgp_armored_key("invalid key").is_err());
    }
}
