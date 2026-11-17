use crate::alias_engine::is_alias_deleted;
use crate::models::{Alias, CustomDomain};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SanitizedUrlReport {
    pub original_url: String,
    pub sanitized_url: String,
    pub removed_parameters: Vec<String>,
    pub was_modified: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct PortfolioAuditFinding {
    pub code: String,
    pub severity: String,
    pub title: String,
    pub detail: String,
    pub affected_items: Vec<String>,
    pub remediation: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct PortfolioAuditReport {
    pub hygiene_score: u8,
    pub total_aliases_scanned: usize,
    pub findings: Vec<PortfolioAuditFinding>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SpfDmarcInspection {
    pub mechanism_type: String,
    pub is_strict_policy: bool,
    pub includes: Vec<String>,
    pub dmarc_policy: String,
    pub summary: String,
}

pub const TRACKING_QUERY_PARAMETERS: &[&str] = &[
    "_hsenc", "_hsmi", "_ke", "_kx", "activehosted_id", "adset_id", "aff_id", "aff_sub",
    "aff_sub2", "aff_sub3", "aff_sub4", "aff_sub5", "affiliate_id", "at_campaign", "at_custom1",
    "at_custom2", "at_custom3", "at_custom4", "at_medium", "attentive_id", "awc", "beacon_id",
    "bluecore_id", "braze_ext_id", "braze_id", "bsft_aaid", "bsft_clkid", "bsft_eid", "bsft_ek",
    "bsft_lx", "bsft_mid", "bsft_mime_type", "bsft_tv", "bsft_uid", "campaign_id", "cid",
    "cio_campaign_id", "cio_id", "cjdata", "cjevent", "ck_subscriber_id", "click_id", "cm_lm",
    "cm_mmc", "cm_mmca1", "cm_mmca2", "cmpid", "cordial_id", "creative_id", "customerio_id",
    "cv_och", "cv_ocp", "cv_oct", "cv_osrc", "dclid", "drip_email_id", "drip_subscriber_id",
    "eloqua_id", "elq", "elqah", "elqaid", "elqak", "elqat", "elqcampaignid", "elqtrack",
    "elqtrackid", "emarsys_id", "epik", "et_cid", "et_rid", "fbclid", "gclid", "gclsrc",
    "hsa_acc", "hsa_ad", "hsa_cam", "hsa_grp", "hsa_kw", "hsa_la", "hsa_mt", "hsa_net",
    "hsa_ol", "hsa_src", "hsa_tgt", "hsa_ver", "hsctatracking", "icid", "igsh", "igshid",
    "intercom_uid", "irclickid", "irgwc", "itc", "iterable_campaign", "iterable_template", "ito",
    "justuno_id", "klaviyo_id", "li_fat_id", "mailjet_id", "mailpoet_id", "mandrill_id",
    "matomo_campaign", "matomo_kwd", "mbid", "mc_cid", "mc_eid", "mc_tc", "mkt_hm", "mkt_si",
    "mkt_tok", "ml_subscriber", "ml_subscriber_hash", "msclkid", "mtm_campaign", "mtm_cid",
    "mtm_content", "mtm_group", "mtm_keyword", "mtm_medium", "mtm_placement", "mtm_source",
    "ncid", "newsletter_id", "nr_email_referer", "oly_anon_id", "oly_enc_id", "omn_id",
    "omnisendcontactid", "open_id", "optinmonster_id", "otc", "pardot_id", "partner_id",
    "pi_list_email", "piwik_campaign", "piwik_kwd", "pixel_id", "pk_campaign", "pk_cid",
    "pk_content", "pk_kwd", "pk_medium", "pk_source", "postscript_id", "privy_id", "ran_eaid",
    "ran_mid", "ran_siteid", "rb_clickid", "rdt_cid", "ref_campaign", "ref_src", "ref_url",
    "s_cid", "s_kwcid", "sailthru_id", "sc_cid", "sc_customer", "sc_eh", "sc_lid", "sc_llid",
    "sc_src", "sc_uid", "sccid", "selligent_id", "sendgrid_id", "sfmc_activityid", "sfmc_id",
    "sfmc_jb", "sfmc_l", "sfmc_mid", "sfmc_sub", "sfmc_u", "sg_wid", "spjobid", "spmailingid",
    "spreportid", "spuserid", "sr_share", "sscid", "st_cid", "stamped_id", "subid", "subid1",
    "subid2", "subid3", "subscriber_id", "sumo_id", "tduid", "tracking_id", "trk_contact",
    "trk_module", "trk_msg", "trk_sid", "ttclid", "twclid", "utm_brand", "utm_campaign",
    "utm_channel", "utm_cid", "utm_content", "utm_creative", "utm_device", "utm_id", "utm_medium",
    "utm_name", "utm_network", "utm_placement", "utm_pubreferrer", "utm_reader", "utm_social",
    "utm_social-type", "utm_source", "utm_swu", "utm_term", "utm_viz_id", "vero_conv", "vero_id",
    "vgo_ee", "wickedid", "wt.mc_id", "wt.nav", "wt.seg_1", "wt.tsrc", "yclid", "yotpo_id",
    "zanpid",
];

pub fn is_tracking_query_parameter(param_name: &str) -> bool {
    let lower = param_name.trim().to_ascii_lowercase();
    if lower.starts_with("utm_")
        || lower.starts_with("hsa_")
        || lower.starts_with("mtm_")
        || lower.starts_with("pk_")
        || lower.starts_with("bsft_")
        || lower.starts_with("sfmc_")
        || lower.starts_with("elq")
    {
        return true;
    }
    TRACKING_QUERY_PARAMETERS.iter().any(|p| *p == lower)
}

pub fn sanitize_tracking_url(raw_url: &str) -> SanitizedUrlReport {
    let trimmed = raw_url.trim();
    let (before_hash, hash_part) = match trimmed.split_once('#') {
        Some((b, h)) => (b, Some(h)),
        None => (trimmed, None),
    };

    let (base_path, query_str) = match before_hash.split_once('?') {
        Some((b, q)) => (b, q),
        None => {
            return SanitizedUrlReport {
                original_url: trimmed.to_string(),
                sanitized_url: trimmed.to_string(),
                removed_parameters: Vec::new(),
                was_modified: false,
            };
        }
    };

    let mut kept_pairs: Vec<&str> = Vec::new();
    let mut removed_parameters: Vec<String> = Vec::new();

    for pair in query_str.split('&') {
        if pair.is_empty() {
            continue;
        }
        let key = pair.split_once('=').map(|(k, _)| k).unwrap_or(pair);
        if is_tracking_query_parameter(key) {
            removed_parameters.push(key.to_string());
        } else {
            kept_pairs.push(pair);
        }
    }

    let mut sanitized = base_path.to_string();
    if !kept_pairs.is_empty() {
        sanitized.push('?');
        sanitized.push_str(&kept_pairs.join("&"));
    }
    if let Some(h) = hash_part {
        if !h.is_empty() {
            sanitized.push('#');
            sanitized.push_str(h);
        }
    }

    let was_modified = !removed_parameters.is_empty();
    SanitizedUrlReport {
        original_url: trimmed.to_string(),
        sanitized_url: sanitized,
        removed_parameters,
        was_modified,
    }
}

pub fn audit_alias_portfolio(
    aliases: &[Alias],
    custom_domains: &[CustomDomain],
    pgp_enabled_any: bool,
    trackers_blocked: bool,
    header_stripped: bool,
) -> PortfolioAuditReport {
    let mut findings: Vec<PortfolioAuditFinding> = Vec::new();
    let mut score: i32 = 100;

    let active_aliases: Vec<&Alias> = aliases
        .iter()
        .filter(|a| !is_alias_deleted(a) && a.enabled)
        .collect();

    if !trackers_blocked {
        score -= 18;
        findings.push(PortfolioAuditFinding {
            code: "TRACKER_SHIELD_OFF".to_string(),
            severity: "High".to_string(),
            title: "Email Tracker Blocking Disabled".to_string(),
            detail: "Incoming emails are not being stripped of 1x1 spy pixels and behavioral tracking links.".to_string(),
            affected_items: Vec::new(),
            remediation: "Enable 'Block Known Email Trackers' in the Settings tab.".to_string(),
        });
    }

    if !pgp_enabled_any {
        score -= 15;
        findings.push(PortfolioAuditFinding {
            code: "PGP_ENCRYPTION_INACTIVE".to_string(),
            severity: "Medium".to_string(),
            title: "End-to-End OpenPGP Encryption Not Enabled".to_string(),
            detail: "Forwarded emails are delivered using standard transport encryption without recipient OpenPGP public-key encryption.".to_string(),
            affected_items: Vec::new(),
            remediation: "Upload an ASCII-armored OpenPGP public key for your primary recipient in the Recipients tab.".to_string(),
        });
    }

    if !header_stripped {
        score -= 5;
        findings.push(PortfolioAuditFinding {
            code: "MAILX_HEADER_VISIBLE".to_string(),
            severity: "Low".to_string(),
            title: "Mailx Diagnostic Header Included".to_string(),
            detail: "Forwarded messages include the Mailx routing header.".to_string(),
            affected_items: Vec::new(),
            remediation: "Toggle off 'Include Mailx Header' in Settings if you prefer minimal header metadata.".to_string(),
        });
    }

    let high_concentration: Vec<String> = active_aliases
        .iter()
        .filter(|a| a.stats.forwards > 250 && a.description.trim().is_empty())
        .map(|a| a.name.clone())
        .collect();

    if !high_concentration.is_empty() {
        score -= 8;
        findings.push(PortfolioAuditFinding {
            code: "UNTAGGED_HIGH_TRAFFIC_ALIAS".to_string(),
            severity: "Medium".to_string(),
            title: "High-Traffic Aliases Missing Description Notes".to_string(),
            detail: "One or more aliases have forwarded over 250 messages without an identifying service description.".to_string(),
            affected_items: high_concentration,
            remediation: "Add a service description to identify which vendor holds each high-volume alias.".to_string(),
        });
    }

    let unverified_domains: Vec<String> = custom_domains
        .iter()
        .filter(|d| {
            d.owner_verified_at.as_deref().unwrap_or("").is_empty()
                || d.mx_verified_at.as_deref().unwrap_or("").is_empty()
                || d.send_verified_at.as_deref().unwrap_or("").is_empty()
        })
        .map(|d| d.name.clone())
        .collect();

    if !unverified_domains.is_empty() {
        score -= 10;
        findings.push(PortfolioAuditFinding {
            code: "INCOMPLETE_CUSTOM_DOMAIN_DNS".to_string(),
            severity: "High".to_string(),
            title: "Custom Domains Pending Full DNS Verification".to_string(),
            detail: "One or more custom domains are missing verified TXT, MX, SPF, DKIM, or DMARC records.".to_string(),
            affected_items: unverified_domains,
            remediation: "Open the Domains tab and run 'Verify DNS Records' after publishing the required records.".to_string(),
        });
    }

    PortfolioAuditReport {
        hygiene_score: score.clamp(0, 100) as u8,
        total_aliases_scanned: aliases.len(),
        findings,
    }
}

pub fn inspect_spf_or_dmarc_record(raw_txt: &str) -> SpfDmarcInspection {
    let trimmed = raw_txt.trim();
    let lower = trimmed.to_ascii_lowercase();
    if lower.starts_with("v=spf1") {
        let mut includes = Vec::new();
        for tok in trimmed.split_whitespace() {
            if let Some(inc) = tok.strip_prefix("include:") {
                includes.push(inc.to_string());
            }
        }
        let is_strict = lower.ends_with("-all") || lower.ends_with("~all");
        SpfDmarcInspection {
            mechanism_type: "SPF (RFC 7208)".to_string(),
            is_strict_policy: is_strict,
            includes,
            dmarc_policy: String::new(),
            summary: if is_strict {
                "Valid SPF record with authorized outbound senders and fail/softfail qualifier.".to_string()
            } else {
                "SPF record is missing a strict '-all' or '~all' terminal mechanism.".to_string()
            },
        }
    } else if lower.starts_with("v=dmarc1") {
        let mut policy = "none".to_string();
        for part in lower.split(';') {
            let p = part.trim();
            if let Some(val) = p.strip_prefix("p=") {
                policy = val.trim().to_string();
            }
        }
        let is_strict = matches!(policy.as_str(), "quarantine" | "reject");
        SpfDmarcInspection {
            mechanism_type: "DMARC (RFC 7489)".to_string(),
            is_strict_policy: is_strict,
            includes: Vec::new(),
            dmarc_policy: policy.clone(),
            summary: format!("DMARC policy is set to '{}'.", policy),
        }
    } else {
        SpfDmarcInspection {
            mechanism_type: "Generic DNS TXT".to_string(),
            is_strict_policy: false,
            includes: Vec::new(),
            dmarc_policy: String::new(),
            summary: "Non-SPF/DMARC TXT record.".to_string(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sanitize_tracking_url() {
        let dirty = "https://example.com/deals?item=42&utm_source=newsletter&mc_eid=998877&fbclid=abc#top";
        let report = sanitize_tracking_url(dirty);
        assert!(report.was_modified);
        assert_eq!(report.sanitized_url, "https://example.com/deals?item=42#top");
        assert_eq!(report.removed_parameters.len(), 3);
    }

    #[test]
    fn test_inspect_spf_and_dmarc() {
        let spf = inspect_spf_or_dmarc_record("v=spf1 include:spf.mailx.net ~all");
        assert!(spf.is_strict_policy);
        assert_eq!(spf.includes, vec!["spf.mailx.net".to_string()]);

        let dmarc = inspect_spf_or_dmarc_record("v=DMARC1; p=quarantine; pct=100");
        assert!(dmarc.is_strict_policy);
        assert_eq!(dmarc.dmarc_policy, "quarantine");
    }
}
