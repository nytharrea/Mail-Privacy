use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct PhishingRiskReport {
    pub sender_domain: String,
    pub risk_score: u8,
    pub risk_level: String,
    pub is_punycode_idn: bool,
    pub lookalike_brand_target: Option<String>,
    pub reply_to_mismatch: bool,
    pub warnings: Vec<String>,
}

const PROTECTED_BRAND_DOMAINS: &[(&str, &str)] = &[
    ("mailx.net", "IVPN Mailx"),
    ("ivpn.net", "IVPN"),
    ("addy.io", "addy.io"),
    ("simplelogin.io", "SimpleLogin"),
    ("proton.me", "Proton"),
    ("protonmail.com", "Proton Mail"),
    ("tuta.com", "Tuta"),
    ("tutanota.com", "Tutanota"),
    ("google.com", "Google"),
    ("gmail.com", "Gmail"),
    ("apple.com", "Apple"),
    ("icloud.com", "Apple iCloud"),
    ("microsoft.com", "Microsoft"),
    ("outlook.com", "Microsoft Outlook"),
    ("github.com", "GitHub"),
    ("gitlab.com", "GitLab"),
    ("paypal.com", "PayPal"),
    ("stripe.com", "Stripe"),
    ("coinbase.com", "Coinbase"),
    ("kraken.com", "Kraken"),
    ("binance.com", "Binance"),
    ("amazon.com", "Amazon"),
    ("cloudflare.com", "Cloudflare"),
    ("fastmail.com", "Fastmail"),
    ("mailbox.org", "Mailbox.org"),
    ("posteo.de", "Posteo"),
    ("1password.com", "1Password"),
    ("bitwarden.com", "Bitwarden"),
];

pub fn normalize_homoglyphs(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    for ch in input.trim().to_ascii_lowercase().chars() {
        match ch {
            '0' => out.push('o'),
            '1' | 'l' | '!' | '|' => out.push('i'),
            '3' => out.push('e'),
            '4' => out.push('a'),
            '5' => out.push('s'),
            '7' => out.push('t'),
            '8' => out.push('b'),
            c => out.push(c),
        }
    }
    out.replace("rn", "m").replace("vv", "w")
}

pub fn levenshtein_distance(a: &str, b: &str) -> usize {
    let a_chars: Vec<char> = a.chars().collect();
    let b_chars: Vec<char> = b.chars().collect();
    let m = a_chars.len();
    let n = b_chars.len();
    if m == 0 {
        return n;
    }
    if n == 0 {
        return m;
    }

    let mut prev: Vec<usize> = (0..=n).collect();
    let mut curr: Vec<usize> = vec![0; n + 1];

    for i in 1..=m {
        curr[0] = i;
        for j in 1..=n {
            let cost = if a_chars[i - 1] == b_chars[j - 1] {
                0
            } else {
                1
            };
            curr[j] = (prev[j] + 1).min(curr[j - 1] + 1).min(prev[j - 1] + cost);
        }
        std::mem::swap(&mut prev, &mut curr);
    }
    prev[n]
}

pub fn evaluate_sender_phishing_risk(
    from_email: &str,
    reply_to_email: &str,
    spf_pass: bool,
    dkim_pass: bool,
) -> PhishingRiskReport {
    let from_domain = from_email
        .trim()
        .rsplit_once('@')
        .map(|(_, d)| d.trim_end_matches('>').to_ascii_lowercase())
        .unwrap_or_else(|| from_email.trim().to_ascii_lowercase());

    let reply_domain = reply_to_email
        .trim()
        .rsplit_once('@')
        .map(|(_, d)| d.trim_end_matches('>').to_ascii_lowercase())
        .unwrap_or_default();

    let mut risk_score: u8 = 0;
    let mut warnings: Vec<String> = Vec::new();

    let is_punycode = from_domain.split('.').any(|lbl| lbl.starts_with("xn--"));
    if is_punycode {
        risk_score = risk_score.saturating_add(45);
        warnings.push("Sender domain uses IDN Punycode ('xn--'), which is frequently used in homograph phishing attacks.".to_string());
    }

    let reply_to_mismatch = !reply_domain.is_empty() && reply_domain != from_domain;
    if reply_to_mismatch {
        risk_score = risk_score.saturating_add(25);
        warnings.push(format!(
            "Reply-To domain ('{}') differs from From domain ('{}').",
            reply_domain, from_domain
        ));
    }

    if !spf_pass || !dkim_pass {
        risk_score = risk_score.saturating_add(20);
        warnings.push("Message lacks full SPF and DKIM cryptographic alignment.".to_string());
    }

    let mut lookalike_brand_target: Option<String> = None;
    let norm_sender = normalize_homoglyphs(&from_domain);
    for (brand_dom, brand_name) in PROTECTED_BRAND_DOMAINS {
        if from_domain == *brand_dom || from_domain.ends_with(&format!(".{}", brand_dom)) {
            continue;
        }
        let norm_brand = normalize_homoglyphs(brand_dom);
        let dist = levenshtein_distance(&from_domain, brand_dom);
        if norm_sender == norm_brand || dist == 1 {
            lookalike_brand_target = Some((*brand_name).to_string());
            risk_score = risk_score.saturating_add(60);
            warnings.push(format!(
                "Domain '{}' closely impersonates protected brand '{}' ('{}').",
                from_domain, brand_name, brand_dom
            ));
            break;
        }
    }

    let risk_score = risk_score.min(100);
    let risk_level = match risk_score {
        70..=100 => "Critical",
        40..=69 => "High",
        15..=39 => "Medium",
        _ => "Low",
    }
    .to_string();

    PhishingRiskReport {
        sender_domain: from_domain,
        risk_score,
        risk_level,
        is_punycode_idn: is_punycode,
        lookalike_brand_target,
        reply_to_mismatch,
        warnings,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_phishing_lookalike_detection() {
        let r =
            evaluate_sender_phishing_risk("security@pr0ton.me", "evil@attacker.org", false, true);
        assert!(r.risk_score >= 70);
        assert_eq!(r.lookalike_brand_target.as_deref(), Some("Proton"));
        assert!(r.reply_to_mismatch);
    }

    #[test]
    fn test_clean_aligned_brand_domain_is_low_risk() {
        let r = evaluate_sender_phishing_risk("noreply@mailx.net", "noreply@mailx.net", true, true);
        assert_eq!(r.risk_score, 0);
        assert_eq!(r.risk_level, "Low");
        assert!(r.lookalike_brand_target.is_none());
        assert!(!r.reply_to_mismatch);
        assert!(r.warnings.is_empty());

        let sub = evaluate_sender_phishing_risk("alerts@notify.proton.me", "", true, true);
        assert_eq!(sub.risk_score, 0);
        assert!(sub.lookalike_brand_target.is_none());
    }

    #[test]
    fn test_punycode_idn_domain_is_flagged() {
        let r = evaluate_sender_phishing_risk("support@xn--pple-43d.com", "", true, true);
        assert!(r.is_punycode_idn);
        assert!(r.risk_score >= 45);
        assert_eq!(r.risk_level, "High");
    }

    #[test]
    fn test_homoglyph_normalization_and_levenshtein() {
        assert_eq!(normalize_homoglyphs("pr0t0n"), "proton");
        assert_eq!(normalize_homoglyphs("rnai1x"), "maiix");
        assert_eq!(normalize_homoglyphs("vvorld"), "world");
        assert_eq!(levenshtein_distance("", "abc"), 3);
        assert_eq!(levenshtein_distance("abc", ""), 3);
        assert_eq!(levenshtein_distance("mailx.net", "mailx.net"), 0);
        assert_eq!(levenshtein_distance("mailx.net", "mailz.net"), 1);
    }

    #[test]
    fn test_missing_spf_or_dkim_increases_risk() {
        let r = evaluate_sender_phishing_risk("hello@example.org", "", false, true);
        assert_eq!(r.risk_score, 20);
        assert_eq!(r.risk_level, "Medium");
        assert!(!r.warnings.is_empty());
    }
}
