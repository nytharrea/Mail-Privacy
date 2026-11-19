use crate::models::{Alias, AliasRequest, CustomDomain};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CsvImportPlan {
    pub requests: Vec<AliasRequest>,
    pub skipped_count: usize,
}

pub fn sanitize_csv_cell(raw: &str) -> String {
    raw.replace(',', " ")
        .replace(['\r', '\n'], " ")
        .trim()
        .to_string()
}

pub fn export_aliases_to_csv(aliases: &[Alias]) -> String {
    let mut out = String::from("alias,description,enabled,recipients\n");
    for alias in aliases {
        let desc = sanitize_csv_cell(&alias.description);
        let recs = sanitize_csv_cell(&alias.recipients);
        out.push_str(&format!(
            "{},{},{},{}\n",
            alias.name.trim(),
            desc,
            alias.enabled,
            recs
        ));
    }
    out
}

pub fn export_aliases_json_to_csv(aliases_json: &str) -> Result<String, String> {
    let parsed: Vec<Alias> = serde_json::from_str(aliases_json)
        .map_err(|e| format!("Failed to parse aliases JSON for CSV export: {}", e))?;
    Ok(export_aliases_to_csv(&parsed))
}

pub fn parse_aliases_csv_import(
    csv_text: &str,
    default_recipient: &str,
    custom_domains: &[CustomDomain],
    max_rows: usize,
) -> CsvImportPlan {
    let lines: Vec<&str> = csv_text
        .lines()
        .map(|l| l.trim())
        .filter(|l| !l.is_empty())
        .collect();

    if lines.is_empty() {
        return CsvImportPlan {
            requests: Vec::new(),
            skipped_count: 0,
        };
    }

    let start_idx = if lines[0].to_ascii_lowercase().starts_with("alias,") {
        1
    } else {
        0
    };

    let mut requests = Vec::new();
    let mut skipped_count = 0usize;
    let limit = max_rows.clamp(1, 500);

    for line in lines.into_iter().skip(start_idx).take(limit) {
        let cols: Vec<&str> = line.split(',').collect();
        if cols.len() < 4 {
            skipped_count += 1;
            continue;
        }

        let full_alias = cols[0].trim();
        let description = cols[1].trim().to_string();
        let enabled = cols[2].trim().eq_ignore_ascii_case("true");
        let joined_rec = cols[3..].join(",").trim().replace(' ', ",");
        let recipients = if joined_rec.is_empty() {
            default_recipient.trim().to_string()
        } else {
            joined_rec
        };

        let (local_part, domain_name) = match full_alias.split_once('@') {
            Some((l, d)) => (l.trim(), d.trim()),
            None => {
                skipped_count += 1;
                continue;
            }
        };

        if local_part.is_empty() || domain_name.is_empty() || recipients.is_empty() {
            skipped_count += 1;
            continue;
        }

        let domain_param = custom_domains
            .iter()
            .find(|cd| cd.name.eq_ignore_ascii_case(domain_name))
            .map(|cd| {
                if cd.id.trim().is_empty() {
                    domain_name.to_string()
                } else {
                    cd.id.clone()
                }
            })
            .unwrap_or_else(|| domain_name.to_string());

        requests.push(AliasRequest {
            description,
            enabled,
            recipients,
            from_name: String::new(),
            format: "custom".to_string(),
            domain: domain_param,
            wildcard_local_part: None,
            wildcard_delimiter: None,
            local_part: Some(local_part.to_string()),
        });
    }

    CsvImportPlan {
        requests,
        skipped_count,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_csv_parse_and_custom_domain_mapping() {
        let csv = "alias,description,enabled,recipients\nshopping.bot@custom.org,My Store,true,me@example.com\ninvalid_row_only_two_cols,desc\n";
        let domains = vec![CustomDomain {
            id: "dom_123".to_string(),
            created_at: None,
            name: "custom.org".to_string(),
            description: String::new(),
            recipient: String::new(),
            from_name: String::new(),
            enabled: true,
            catch_all: false,
            create_alias: false,
            owner_verified_at: None,
            mx_verified_at: None,
            send_verified_at: None,
        }];

        let plan = parse_aliases_csv_import(csv, "fallback@example.com", &domains, 500);
        assert_eq!(plan.requests.len(), 1);
        assert_eq!(plan.skipped_count, 1);
        assert_eq!(plan.requests[0].domain, "dom_123");
        assert_eq!(plan.requests[0].local_part.as_deref(), Some("shopping.bot"));
    }
}
