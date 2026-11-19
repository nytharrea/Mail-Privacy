use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ParsedMailtoUri {
    pub primary_to: String,
    pub additional_to: Vec<String>,
    pub cc: Vec<String>,
    pub bcc: Vec<String>,
    pub subject: String,
    pub body: String,
    pub raw_uri: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DecomposedEmailAddress {
    pub raw: String,
    pub display_name: String,
    pub local_part: String,
    pub subaddress_tag: String,
    pub base_local_part: String,
    pub domain: String,
    pub tld: String,
    pub is_valid_syntax: bool,
    pub is_reverse_alias: bool,
    pub reverse_alias_target: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct EmailHeaderSecuritySummary {
    pub from_address: String,
    pub reply_to_address: String,
    pub return_path_address: String,
    pub subject: String,
    pub message_id: String,
    pub spf_pass: bool,
    pub dkim_pass: bool,
    pub dmarc_pass: bool,
    pub has_list_unsubscribe: bool,
    pub one_click_unsubscribe: bool,
    pub unsubscribe_targets: Vec<String>,
    pub mailx_banner_present: bool,
}

pub fn percent_decode(input: &str) -> String {
    let bytes = input.as_bytes();
    let mut out: Vec<u8> = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            let h1 = (bytes[i + 1] as char).to_digit(16);
            let h2 = (bytes[i + 2] as char).to_digit(16);
            if let (Some(d1), Some(d2)) = (h1, h2) {
                out.push(((d1 << 4) | d2) as u8);
                i += 3;
                continue;
            }
        } else if bytes[i] == b'+' {
            out.push(b' ');
            i += 1;
            continue;
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

pub fn parse_mailto_uri(raw_uri: &str) -> Result<ParsedMailtoUri, String> {
    let trimmed = raw_uri.trim();
    let without_scheme = if trimmed.len() >= 7 && trimmed[..7].eq_ignore_ascii_case("mailto:") {
        &trimmed[7..]
    } else {
        trimmed
    };

    let (recipients_part, query_part) = without_scheme
        .split_once('?')
        .unwrap_or((without_scheme, ""));

    let mut to_list: Vec<String> = recipients_part
        .split(',')
        .map(|s| percent_decode(s).trim().to_string())
        .filter(|s| !s.is_empty())
        .collect();

    let mut cc: Vec<String> = Vec::new();
    let mut bcc: Vec<String> = Vec::new();
    let mut subject = String::new();
    let mut body = String::new();

    if !query_part.is_empty() {
        for pair in query_part.split('&') {
            if pair.trim().is_empty() {
                continue;
            }
            let (k, v) = pair.split_once('=').unwrap_or((pair, ""));
            let key = k.trim().to_ascii_lowercase();
            let val = percent_decode(v);
            match key.as_str() {
                "to" => {
                    for addr in val.split(',').map(|s| s.trim()).filter(|s| !s.is_empty()) {
                        to_list.push(addr.to_string());
                    }
                }
                "cc" => {
                    for addr in val.split(',').map(|s| s.trim()).filter(|s| !s.is_empty()) {
                        cc.push(addr.to_string());
                    }
                }
                "bcc" => {
                    for addr in val.split(',').map(|s| s.trim()).filter(|s| !s.is_empty()) {
                        bcc.push(addr.to_string());
                    }
                }
                "subject" => subject = val,
                "body" => body = val,
                _ => {}
            }
        }
    }

    let primary_to = if to_list.is_empty() {
        String::new()
    } else {
        to_list.remove(0)
    };

    Ok(ParsedMailtoUri {
        primary_to,
        additional_to: to_list,
        cc,
        bcc,
        subject,
        body,
        raw_uri: trimmed.to_string(),
    })
}

pub fn decompose_email_address(raw_input: &str) -> DecomposedEmailAddress {
    let trimmed = raw_input.trim();
    let (display_name, addr_spec) = if let (Some(lt), Some(gt)) = (trimmed.find('<'), trimmed.rfind('>')) {
        if lt < gt {
            let disp = trimmed[..lt].trim().trim_matches('"').trim().to_string();
            let inner = trimmed[lt + 1..gt].trim();
            (disp, inner)
        } else {
            (String::new(), trimmed)
        }
    } else {
        (String::new(), trimmed)
    };

    let (local_part, domain_raw) = match addr_spec.split_once('@') {
        Some((l, d)) => (l.trim().to_string(), d.trim().to_ascii_lowercase()),
        None => (addr_spec.to_string(), String::new()),
    };

    let (base_local_part, subaddress_tag) = match local_part.split_once('+') {
        Some((base, tag)) => (base.to_string(), tag.to_string()),
        None => (local_part.clone(), String::new()),
    };

    let (is_reverse_alias, reverse_alias_target) = if !subaddress_tag.is_empty() && subaddress_tag.contains('=') {
        if let Some((target_local, target_dom)) = subaddress_tag.split_once('=') {
            if !target_local.is_empty() && target_dom.contains('.') {
                (true, Some(format!("{}@{}", target_local, target_dom)))
            } else {
                (false, None)
            }
        } else {
            (false, None)
        }
    } else {
        (false, None)
    };

    let tld = domain_raw
        .rsplit_once('.')
        .map(|(_, t)| t.to_string())
        .unwrap_or_default();

    let is_valid_syntax = !local_part.is_empty()
        && local_part.len() <= 64
        && !domain_raw.is_empty()
        && domain_raw.len() <= 253
        && domain_raw.contains('.')
        && !tld.is_empty();

    DecomposedEmailAddress {
        raw: trimmed.to_string(),
        display_name,
        local_part,
        subaddress_tag,
        base_local_part,
        domain: domain_raw,
        tld,
        is_valid_syntax,
        is_reverse_alias,
        reverse_alias_target,
    }
}

pub fn analyze_raw_email_headers(raw_headers: &str) -> EmailHeaderSecuritySummary {
    let mut unfolded_lines: Vec<String> = Vec::new();
    for line in raw_headers.lines() {
        if (line.starts_with(' ') || line.starts_with('\t')) && !unfolded_lines.is_empty() {
            if let Some(last) = unfolded_lines.last_mut() {
                last.push(' ');
                last.push_str(line.trim());
            }
        } else {
            unfolded_lines.push(line.trim().to_string());
        }
    }

    let mut summary = EmailHeaderSecuritySummary {
        from_address: String::new(),
        reply_to_address: String::new(),
        return_path_address: String::new(),
        subject: String::new(),
        message_id: String::new(),
        spf_pass: false,
        dkim_pass: false,
        dmarc_pass: false,
        has_list_unsubscribe: false,
        one_click_unsubscribe: false,
        unsubscribe_targets: Vec::new(),
        mailx_banner_present: false,
    };

    for line in unfolded_lines {
        let (name, value) = match line.split_once(':') {
            Some((n, v)) => (n.trim().to_ascii_lowercase(), v.trim()),
            None => continue,
        };

        match name.as_str() {
            "from" => summary.from_address = value.to_string(),
            "reply-to" => summary.reply_to_address = value.to_string(),
            "return-path" => summary.return_path_address = value.trim_matches(&['<', '>'][..]).to_string(),
            "subject" => summary.subject = value.to_string(),
            "message-id" => summary.message_id = value.to_string(),
            "authentication-results" => {
                let lower = value.to_ascii_lowercase();
                if lower.contains("spf=pass") {
                    summary.spf_pass = true;
                }
                if lower.contains("dkim=pass") {
                    summary.dkim_pass = true;
                }
                if lower.contains("dmarc=pass") {
                    summary.dmarc_pass = true;
                }
            }
            "received-spf" => {
                if value.to_ascii_lowercase().starts_with("pass") {
                    summary.spf_pass = true;
                }
            }
            "list-unsubscribe" => {
                summary.has_list_unsubscribe = true;
                for part in value.split(',') {
                    let clean = part.trim().trim_start_matches('<').trim_end_matches('>').trim();
                    if !clean.is_empty() {
                        summary.unsubscribe_targets.push(clean.to_string());
                    }
                }
            }
            "list-unsubscribe-post" => {
                if value.to_ascii_lowercase().contains("list-unsubscribe=one-click") {
                    summary.one_click_unsubscribe = true;
                }
            }
            "x-mailx-alias" | "x-anonaddy-original-sender" | "x-simplelogin-alias" => {
                summary.mailx_banner_present = true;
            }
            _ => {}
        }
    }

    summary
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_mailto_and_decompose_reverse_alias() {
        let parsed = parse_mailto_uri("mailto:support@github.com?subject=Account%20Help&cc=sec@github.com").unwrap();
        assert_eq!(parsed.primary_to, "support@github.com");
        assert_eq!(parsed.subject, "Account Help");
        assert_eq!(parsed.cc, vec!["sec@github.com".to_string()]);

        let decomp = decompose_email_address("Mailx User <quiet.fox15+support=github.com@mailx.net>");
        assert!(decomp.is_valid_syntax);
        assert!(decomp.is_reverse_alias);
        assert_eq!(decomp.reverse_alias_target.as_deref(), Some("support@github.com"));
    }

    #[test]
    fn test_analyze_raw_email_headers() {
        let raw = "From: Sender <news@example.com>\nAuthentication-Results: mx.mailx.net; spf=pass; dkim=pass; dmarc=pass\nList-Unsubscribe: <mailto:unsub@example.com>, <https://example.com/unsub>\nList-Unsubscribe-Post: List-Unsubscribe=One-Click\n";
        let sec = analyze_raw_email_headers(raw);
        assert!(sec.spf_pass);
        assert!(sec.dkim_pass);
        assert!(sec.dmarc_pass);
        assert!(sec.one_click_unsubscribe);
        assert_eq!(sec.unsubscribe_targets.len(), 2);
    }
}
