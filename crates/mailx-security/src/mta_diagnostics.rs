use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MtaBounceAnalysis {
    pub smtp_code: String,
    pub enhanced_status: String,
    pub classification: String,
    pub is_permanent_failure: bool,
    pub root_cause_summary: String,
    pub recommended_action: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub struct Rfc3463CodeEntry {
    pub code: &'static str,
    pub title: &'static str,
    pub explanation: &'static str,
    pub remediation: &'static str,
}

pub const RFC3463_STATUS_CATALOG: &[Rfc3463CodeEntry] = &[
    Rfc3463CodeEntry {
        code: "2.0.0",
        title: "Success",
        explanation: "Message accepted by remote MTA for final delivery or forwarding.",
        remediation: "No action needed.",
    },
    Rfc3463CodeEntry {
        code: "2.1.0",
        title: "Address Status Valid",
        explanation: "Originator or destination address validated by remote server.",
        remediation: "No action needed.",
    },
    Rfc3463CodeEntry {
        code: "2.1.5",
        title: "Destination Address Valid",
        explanation: "Recipient address accepted by remote MX host.",
        remediation: "No action needed.",
    },
    Rfc3463CodeEntry {
        code: "2.5.0",
        title: "SMTP Protocol Success",
        explanation: "TLS and ESMTP command pipeline completed cleanly.",
        remediation: "No action needed.",
    },
    Rfc3463CodeEntry {
        code: "2.6.0",
        title: "Message Content Accepted",
        explanation: "MIME body and OpenPGP payload accepted by remote queue.",
        remediation: "No action needed.",
    },
    Rfc3463CodeEntry {
        code: "4.0.0",
        title: "Transient Failure",
        explanation: "Temporary unspecified MTA condition; delivery remains queued.",
        remediation: "Wait for automatic Mailx MTA retry.",
    },
    Rfc3463CodeEntry {
        code: "4.1.1",
        title: "Temporary Mailbox Unavailable",
        explanation: "Destination mailbox is temporarily busy or undergoing maintenance.",
        remediation: "Automatic retry scheduled.",
    },
    Rfc3463CodeEntry {
        code: "4.1.2",
        title: "Temporary Bad Destination System",
        explanation: "DNS or MX resolution for recipient domain timed out transiently.",
        remediation: "Check custom domain DNS health if persistent.",
    },
    Rfc3463CodeEntry {
        code: "4.1.7",
        title: "Transient Sender Address Check",
        explanation: "Remote server temporarily deferred sender verification.",
        remediation: "Ensure SPF record is reachable.",
    },
    Rfc3463CodeEntry {
        code: "4.1.8",
        title: "Transient Sender Domain Check",
        explanation: "Originating domain DNS lookup temporarily timed out.",
        remediation: "Verify DNS nameserver responsiveness.",
    },
    Rfc3463CodeEntry {
        code: "4.2.0",
        title: "Transient Mailing List Expansion Issue",
        explanation: "Recipient group or alias expansion deferred temporarily.",
        remediation: "Automatic retry scheduled.",
    },
    Rfc3463CodeEntry {
        code: "4.2.1",
        title: "Mailbox Temporarily Disabled",
        explanation: "Recipient mailbox is temporarily locked or greylisted.",
        remediation: "Wait for standard greylisting window (5-15 minutes).",
    },
    Rfc3463CodeEntry {
        code: "4.2.2",
        title: "Recipient Inbox Over Quota (Soft Bounce)",
        explanation: "Destination mailbox is currently full and deferring new mail.",
        remediation: "Clear space in the recipient mailbox.",
    },
    Rfc3463CodeEntry {
        code: "4.3.0",
        title: "Mail System Temporarily Full",
        explanation: "Remote MX host storage or queue is temporarily saturated.",
        remediation: "Mailx MTA will retry automatically.",
    },
    Rfc3463CodeEntry {
        code: "4.3.1",
        title: "Mail System Storage Full",
        explanation: "Remote MTA disk spool has reached capacity.",
        remediation: "Wait for remote provider recovery.",
    },
    Rfc3463CodeEntry {
        code: "4.3.2",
        title: "System Not Accepting Network Messages",
        explanation: "Remote MX server is in maintenance mode or draining connections.",
        remediation: "Automatic retry scheduled.",
    },
    Rfc3463CodeEntry {
        code: "4.4.1",
        title: "No Answer From Host",
        explanation: "Connection to remote MX host timed out on port 25.",
        remediation: "Verify recipient domain MX records.",
    },
    Rfc3463CodeEntry {
        code: "4.4.2",
        title: "Bad Network Connection",
        explanation: "TCP connection dropped mid-transaction with remote MTA.",
        remediation: "Mailx queue will re-attempt delivery.",
    },
    Rfc3463CodeEntry {
        code: "4.4.3",
        title: "Directory Server Temp Failure",
        explanation: "Remote LDAP/directory lookup timed out.",
        remediation: "No action required.",
    },
    Rfc3463CodeEntry {
        code: "4.4.5",
        title: "Mail System Congestion",
        explanation: "Remote MTA is rate-limiting incoming connections.",
        remediation: "Delivery will complete once rate window resets.",
    },
    Rfc3463CodeEntry {
        code: "4.4.7",
        title: "Delivery Queue Expired",
        explanation: "Message remained deferred in queue past maximum retry window.",
        remediation: "Check recipient server availability.",
    },
    Rfc3463CodeEntry {
        code: "4.5.0",
        title: "Transient Protocol Error",
        explanation: "Temporary SMTP command state mismatch (often greylisting).",
        remediation: "Automatic retry scheduled.",
    },
    Rfc3463CodeEntry {
        code: "4.5.3",
        title: "Too Many Recipients in Transaction",
        explanation: "Remote MTA deferred excess recipients in a single envelope.",
        remediation: "Split recipient routing if needed.",
    },
    Rfc3463CodeEntry {
        code: "4.7.0",
        title: "Temporary Security / Greylist Deferral",
        explanation: "Remote anti-spam filter temporarily deferred message for reputation check.",
        remediation: "Ensure SPF, DKIM, and DMARC pass.",
    },
    Rfc3463CodeEntry {
        code: "4.7.1",
        title: "Greylisted / Try Again Later",
        explanation: "Remote MTA applied temporary greylisting to new sender IP/domain.",
        remediation: "Mailx will automatically retry and pass greylisting.",
    },
    Rfc3463CodeEntry {
        code: "4.7.5",
        title: "Temporary Cryptographic Verification Deferral",
        explanation: "Remote DKIM key DNS fetch timed out transiently.",
        remediation: "Verify DKIM CNAME records in Domains tab.",
    },
    Rfc3463CodeEntry {
        code: "4.7.6",
        title: "Temporary DANE / TLS Certificate Check",
        explanation: "Remote TLSA / DANE verification deferred.",
        remediation: "Automatic retry scheduled.",
    },
    Rfc3463CodeEntry {
        code: "4.7.15",
        title: "Temporary Priority Policy Deferral",
        explanation: "Remote provider rate-limited message tier.",
        remediation: "Automatic retry scheduled.",
    },
    Rfc3463CodeEntry {
        code: "4.7.25",
        title: "Reverse DNS (PTR) Temporary Deferral",
        explanation: "Remote server deferred due to transient PTR lookup timeout.",
        remediation: "No action needed.",
    },
    Rfc3463CodeEntry {
        code: "4.7.28",
        title: "Gmail / Workspace Rate Limit Deferral",
        explanation: "Destination mailbox is receiving mail at a rate that prevents immediate delivery.",
        remediation: "Reduce burst volume to this recipient.",
    },
    Rfc3463CodeEntry {
        code: "5.0.0",
        title: "Permanent Delivery Failure",
        explanation: "Remote MTA permanently rejected the message.",
        remediation: "Inspect raw bounce log for exact provider reason.",
    },
    Rfc3463CodeEntry {
        code: "5.1.0",
        title: "Other Address Status Failure",
        explanation: "Permanent addressing policy rejection.",
        remediation: "Verify alias and recipient addresses.",
    },
    Rfc3463CodeEntry {
        code: "5.1.1",
        title: "Bad Destination Mailbox Address",
        explanation: "The recipient email address does not exist on the destination server.",
        remediation: "Update or remove the invalid recipient in the Recipients tab.",
    },
    Rfc3463CodeEntry {
        code: "5.1.2",
        title: "Bad Destination System Address",
        explanation: "The recipient domain does not exist (NXDOMAIN) or has no MX records.",
        remediation: "Verify the recipient domain name.",
    },
    Rfc3463CodeEntry {
        code: "5.1.3",
        title: "Bad Destination Mailbox Syntax",
        explanation: "Recipient address violates RFC 5321 syntax rules.",
        remediation: "Correct the recipient email address format.",
    },
    Rfc3463CodeEntry {
        code: "5.1.6",
        title: "Destination Mailbox Has Moved",
        explanation: "Recipient account was permanently closed or migrated without forwarding.",
        remediation: "Update alias routing to the new recipient address.",
    },
    Rfc3463CodeEntry {
        code: "5.1.7",
        title: "Bad Sender Mailbox Syntax",
        explanation: "Envelope sender address syntax rejected.",
        remediation: "Verify custom domain From Name and local part.",
    },
    Rfc3463CodeEntry {
        code: "5.1.8",
        title: "Bad Sender System Address",
        explanation: "Envelope sender domain lacks valid MX/A records.",
        remediation: "Verify custom domain DNS records.",
    },
    Rfc3463CodeEntry {
        code: "5.1.10",
        title: "Null MX (Domain Does Not Accept Mail)",
        explanation: "Destination domain publishes RFC 7505 Null MX ('0 .').",
        remediation: "Route alias to a domain configured to receive email.",
    },
    Rfc3463CodeEntry {
        code: "5.2.0",
        title: "Permanent Mailbox Status Failure",
        explanation: "Destination mailbox is unable to receive messages.",
        remediation: "Check recipient account status.",
    },
    Rfc3463CodeEntry {
        code: "5.2.1",
        title: "Mailbox Disabled / Not Accepting Messages",
        explanation: "Recipient account is disabled, frozen, or deactivated.",
        remediation: "Activate the recipient inbox or switch to an active recipient.",
    },
    Rfc3463CodeEntry {
        code: "5.2.2",
        title: "Mailbox Full (Hard Bounce)",
        explanation: "Recipient mailbox exceeded storage quota and permanently rejected delivery.",
        remediation: "Free up storage space in destination mailbox.",
    },
    Rfc3463CodeEntry {
        code: "5.2.3",
        title: "Message Length Exceeds Administrative Limit",
        explanation: "Email + attachments exceed recipient provider size limit (typically 25 MB).",
        remediation: "Enable 'Remove attachments' rule or send smaller attachments.",
    },
    Rfc3463CodeEntry {
        code: "5.2.4",
        title: "Mailing List Expansion Problem",
        explanation: "Destination list address could not expand members.",
        remediation: "Check destination mailing list settings.",
    },
    Rfc3463CodeEntry {
        code: "5.3.0",
        title: "Other Mail System Status Failure",
        explanation: "Remote mail system permanently rejected the message.",
        remediation: "Inspect full MTA diagnostic message.",
    },
    Rfc3463CodeEntry {
        code: "5.3.4",
        title: "Message Too Big for System",
        explanation: "Message exceeds maximum size supported by remote MTA.",
        remediation: "Strip attachments via Mailx Rules.",
    },
    Rfc3463CodeEntry {
        code: "5.3.5",
        title: "Mail Loop Detected",
        explanation: "Remote server detected a forwarding loop back to the same alias or inbox.",
        remediation: "Ensure recipient does not auto-forward back to your Mailx alias.",
    },
    Rfc3463CodeEntry {
        code: "5.4.0",
        title: "Network & Routing Failure",
        explanation: "Permanent network routing failure to destination domain.",
        remediation: "Verify recipient MX configuration.",
    },
    Rfc3463CodeEntry {
        code: "5.4.1",
        title: "No Answer From Host (Permanent)",
        explanation: "Destination MX hosts permanently unreachable.",
        remediation: "Check recipient domain DNS health.",
    },
    Rfc3463CodeEntry {
        code: "5.4.4",
        title: "Unable to Route",
        explanation: "No valid mail route exists for destination domain.",
        remediation: "Check recipient domain MX records.",
    },
    Rfc3463CodeEntry {
        code: "5.4.6",
        title: "Routing Loop Detected (Hop Count Exceeded)",
        explanation: "Too many Received headers; forwarding loop detected.",
        remediation: "Break the forwarding loop between your inbox and Mailx alias.",
    },
    Rfc3463CodeEntry {
        code: "5.5.0",
        title: "SMTP Protocol Failure",
        explanation: "Permanent protocol command rejection.",
        remediation: "Inspect raw bounce transcript.",
    },
    Rfc3463CodeEntry {
        code: "5.5.1",
        title: "Invalid SMTP Command",
        explanation: "Remote MTA rejected command sequence.",
        remediation: "Inspect raw bounce transcript.",
    },
    Rfc3463CodeEntry {
        code: "5.5.2",
        title: "Syntax Error in SMTP Parameters",
        explanation: "Envelope parameter rejected by remote server.",
        remediation: "Check recipient and sender address formatting.",
    },
    Rfc3463CodeEntry {
        code: "5.5.4",
        title: "Invalid Command Arguments",
        explanation: "Malformed HELO/EHLO or MAIL FROM parameters.",
        remediation: "Verify custom domain hostname.",
    },
    Rfc3463CodeEntry {
        code: "5.6.0",
        title: "Message Media / Content Error",
        explanation: "Remote MTA rejected MIME structure or character encoding.",
        remediation: "Check if strict PGP mode is required by recipient.",
    },
    Rfc3463CodeEntry {
        code: "5.6.1",
        title: "Media Not Supported",
        explanation: "Attachment MIME type blocked by recipient server (e.g. executable).",
        remediation: "Use a Mailx Rule to remove attachments if needed.",
    },
    Rfc3463CodeEntry {
        code: "5.6.2",
        title: "Conversion Required and Prohibited",
        explanation: "8BITMIME conversion rejected by remote server.",
        remediation: "Enable PGP/MIME encryption to encapsulate content cleanly.",
    },
    Rfc3463CodeEntry {
        code: "5.7.0",
        title: "Security or Policy Rejection",
        explanation: "Remote server blocked message due to security or anti-spam policy.",
        remediation: "Verify SPF, DKIM, and DMARC records.",
    },
    Rfc3463CodeEntry {
        code: "5.7.1",
        title: "Delivery Not Authorized / Message Refused",
        explanation: "Remote server blocked the sender or message content as spam/unauthorized.",
        remediation: "Add sender to Blocklist if spam, or verify custom domain SPF/DKIM.",
    },
    Rfc3463CodeEntry {
        code: "5.7.2",
        title: "Mailing List Expansion Prohibited",
        explanation: "Sender is not authorized to post to destination group.",
        remediation: "Use Reverse-Alias to send from the authorized alias address.",
    },
    Rfc3463CodeEntry {
        code: "5.7.5",
        title: "Cryptographic Failure",
        explanation: "DKIM or S/MIME/PGP signature verification failed at destination.",
        remediation: "Re-verify custom domain DKIM CNAME records.",
    },
    Rfc3463CodeEntry {
        code: "5.7.6",
        title: "Cryptographic Algorithm Not Supported",
        explanation: "Remote server rejected legacy cipher or key algorithm.",
        remediation: "Use modern Ed25519 or RSA-4096 PGP keys.",
    },
    Rfc3463CodeEntry {
        code: "5.7.7",
        title: "Message Integrity Failure",
        explanation: "DKIM body hash mismatch after intermediate modification.",
        remediation: "Check if downstream filter altered message headers.",
    },
    Rfc3463CodeEntry {
        code: "5.7.13",
        title: "User Account Disabled by Provider",
        explanation: "Destination email provider suspended the recipient account.",
        remediation: "Switch alias forwarding to an active recipient.",
    },
    Rfc3463CodeEntry {
        code: "5.7.23",
        title: "SPF Validation Hard Fail",
        explanation: "Sender IP is explicitly prohibited by domain SPF '-all' record.",
        remediation: "Include 'include:spf.mailx.net' in your custom domain SPF record.",
    },
    Rfc3463CodeEntry {
        code: "5.7.25",
        title: "Reverse DNS (PTR) Required",
        explanation: "Remote server requires valid PTR record for connecting MTA.",
        remediation: "Contact support if using a self-hosted MTA without PTR.",
    },
    Rfc3463CodeEntry {
        code: "5.7.26",
        title: "Unauthenticated Email Rejected (SPF/DKIM Required)",
        explanation: "Gmail/Yahoo/Cloudflare rejected message lacking aligned SPF or DKIM.",
        remediation: "Verify all 3 DKIM CNAME selectors and SPF in Domains tab.",
    },
    Rfc3463CodeEntry {
        code: "5.7.27",
        title: "Sender Address Has Null MX",
        explanation: "Originating domain publishes Null MX and cannot send mail.",
        remediation: "Publish valid MX records on your custom domain.",
    },
];

pub fn lookup_rfc3463_code(code: &str) -> Option<&'static Rfc3463CodeEntry> {
    let trimmed = code.trim();
    RFC3463_STATUS_CATALOG.iter().find(|e| e.code == trimmed)
}

pub fn extract_smtp_and_enhanced_codes(raw_message: &str) -> (String, String) {
    let mut smtp_code = String::new();
    let mut enhanced_status = String::new();

    for token in raw_message.split_whitespace() {
        let clean = token.trim_matches(|c: char| !c.is_ascii_digit() && c != '.');
        if smtp_code.is_empty()
            && clean.len() == 3
            && clean.chars().all(|c| c.is_ascii_digit())
            && matches!(clean.as_bytes()[0], b'2' | b'4' | b'5')
        {
            smtp_code = clean.to_string();
        }
        if enhanced_status.is_empty() {
            let parts: Vec<&str> = clean.split('.').collect();
            if parts.len() == 3
                && matches!(parts[0], "2" | "4" | "5")
                && parts.iter().all(|p| !p.is_empty() && p.chars().all(|c| c.is_ascii_digit()))
            {
                enhanced_status = clean.to_string();
            }
        }
    }

    (smtp_code, enhanced_status)
}

pub fn analyze_mta_diagnostic_log(status: &str, message: &str, remote_mta: &str) -> MtaBounceAnalysis {
    let (smtp_code, enhanced_status) = extract_smtp_and_enhanced_codes(message);
    let lower_msg = message.to_ascii_lowercase();
    let lower_status = status.to_ascii_lowercase();

    let is_permanent = smtp_code.starts_with('5')
        || enhanced_status.starts_with("5.")
        || lower_status.contains("bounced")
        || lower_status.contains("rejected");

    let (classification, root_cause, recommendation) = if enhanced_status == "5.1.1"
        || lower_msg.contains("user unknown")
        || lower_msg.contains("does not exist")
        || lower_msg.contains("no such user")
    {
        (
            "Invalid Recipient Mailbox (5.1.1)",
            "The destination mail server reported that the recipient mailbox does not exist.",
            "Verify the recipient email address spelling in your Mailx Recipients tab or switch the alias to another verified recipient.",
        )
    } else if enhanced_status == "5.2.2"
        || lower_msg.contains("quota exceeded")
        || lower_msg.contains("mailbox full")
    {
        (
            "Recipient Mailbox Full (5.2.2)",
            "The destination inbox has exceeded its storage quota and cannot accept new messages.",
            "Free up storage space in your destination email account or route messages to an alternate recipient.",
        )
    } else if enhanced_status == "5.7.1"
        || enhanced_status == "5.7.26"
        || lower_msg.contains("dmarc")
        || lower_msg.contains("spf")
        || lower_msg.contains("dkim")
        || lower_msg.contains("blocked")
        || lower_msg.contains("spam")
    {
        (
            "Policy / Reputation Rejection (5.7.x)",
            "The remote MTA rejected the message due to SPF/DKIM/DMARC policy or spam filtering rules.",
            "If using a custom domain, run Live DNS Verification in the Domains tab to ensure SPF, DKIM, and DMARC records are valid.",
        )
    } else if smtp_code.starts_with('4')
        || enhanced_status.starts_with("4.")
        || lower_status.contains("deferred")
        || lower_msg.contains("greylist")
        || lower_msg.contains("temporarily")
    {
        (
            "Temporary Deferral / Greylisting (4.x.x)",
            "The receiving MTA temporarily deferred delivery (rate limiting or greylisting).",
            "No immediate action required; the Mailx MTA queue automatically retries deferred deliveries.",
        )
    } else if is_permanent {
        (
            "Permanent MTA Delivery Failure",
            "The remote mail server permanently rejected the delivery attempt.",
            "Inspect the raw bounce log details for the remote MTA response and check recipient verification status.",
        )
    } else {
        (
            "Standard MTA Diagnostic Event",
            "Routine mail transfer agent status record.",
            "Monitor server health via /livez if delivery delays persist.",
        )
    };

    let mta_suffix = if remote_mta.trim().is_empty() {
        String::new()
    } else {
        format!(" (Remote MTA: {})", remote_mta.trim())
    };

    MtaBounceAnalysis {
        smtp_code: if smtp_code.is_empty() {
            "N/A".to_string()
        } else {
            smtp_code
        },
        enhanced_status: if enhanced_status.is_empty() {
            "N/A".to_string()
        } else {
            enhanced_status
        },
        classification: classification.to_string(),
        is_permanent_failure: is_permanent,
        root_cause_summary: format!("{}{}", root_cause, mta_suffix),
        recommended_action: recommendation.to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_analyze_mta_diagnostic_log() {
        let res = analyze_mta_diagnostic_log(
            "bounced",
            "host mx.example.com said: 550 5.1.1 <user@example.com>: Recipient address rejected: User unknown",
            "mx.example.com",
        );
        assert_eq!(res.smtp_code, "550");
        assert_eq!(res.enhanced_status, "5.1.1");
        assert!(res.is_permanent_failure);
        assert!(res.classification.contains("5.1.1"));
    }
}
