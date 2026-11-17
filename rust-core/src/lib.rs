#![allow(clippy::all)]

pub mod alias_engine;
pub mod catalog;
pub mod client;
pub mod controller;
pub mod csv_engine;
pub mod disposable_domains;
pub mod dns_security;
pub mod jni_bridge;
pub mod mime_inspector;
pub mod models;
pub mod mta_diagnostics;
pub mod offline_audit;
pub mod pgp_inspector;
pub mod phishing_guard;
pub mod rfc_email;
pub mod rules_engine;
pub mod secret;
pub mod telemetry;
pub mod totp_engine;
pub mod tracker_db;
pub mod utils;
pub mod wordlist;

pub use alias_engine::{
    compute_dashboard_metrics, filter_and_sort_alias_ids_json, filter_and_sort_aliases,
    merge_alias_collections, AliasDashboardMetrics, AliasQuerySpec, AliasSortKey,
};
pub use catalog::{
    build_rule_catalog, format_alias_type_label, format_iso_date_short, validate_pgp_armored_key,
    CatalogEntry, RuleCatalogSpec,
};
pub use client::{MailxClient, MailxError};
pub use controller::{build_action_endpoint, build_expiry_iso_from_days, ControllerActionResult};
pub use csv_engine::{
    export_aliases_json_to_csv, export_aliases_to_csv, parse_aliases_csv_import, CsvImportPlan,
};
pub use disposable_domains::{assess_domain_trust, DomainTrustAssessment};
pub use dns_security::{
    build_dns_setup_template, format_totp_backup_codes_document, inspect_pgp_public_key_armor,
    validate_custom_domain_hostname, DnsSetupTemplate, ExpectedDnsRecord, PgpKeyInspection,
};
pub use models::*;
pub use mta_diagnostics::{
    analyze_mta_diagnostic_log, extract_smtp_and_enhanced_codes, lookup_rfc3463_code,
    MtaBounceAnalysis, Rfc3463CodeEntry,
};
pub use offline_audit::{
    audit_alias_portfolio, inspect_spf_or_dmarc_record, is_tracking_query_parameter,
    sanitize_tracking_url, PortfolioAuditFinding, PortfolioAuditReport, SanitizedUrlReport,
    SpfDmarcInspection,
};
pub use pgp_inspector::{
    compute_openpgp_crc24, parse_openpgp_armored_public_key, pgp_packet_tag_name,
    pgp_public_key_algorithm_name, OpenPgpKeyringReport, OpenPgpPacketSummary,
};
pub use phishing_guard::{
    evaluate_sender_phishing_risk, levenshtein_distance, normalize_homoglyphs, PhishingRiskReport,
};
pub use rfc_email::{
    analyze_raw_email_headers, decompose_email_address, parse_mailto_uri, DecomposedEmailAddress,
    EmailHeaderSecuritySummary, ParsedMailtoUri,
};
pub use rules_engine::{
    evaluate_condition, evaluate_rule, normalize_rule_payload_json, MailxRuleSpec,
    RuleActionSpec, RuleConditionSpec, RuleEvaluationOutcome, SimulatedEmailContext,
};
pub use secret::SecretString;
pub use telemetry::{
    aggregate_user_stats_json, civil_from_days, current_utc_epoch_days, days_from_civil,
    AggregatedUserStats, DailyStatPoint,
};
pub use totp_engine::{
    decode_base32_rfc4648, generate_totp_code_at, hmac_sha1, sha1_digest, OtpAuthConfig,
};
pub use tracker_db::{
    audit_trackers_report, enrich_tracker_item, lookup_tracker_vendor, EnrichedTrackerFinding,
    EnrichedTrackersAudit, TrackerVendorProfile,
};
pub use utils::{
    build_aliases_query_path, compute_privacy_posture, decode_trackers_report,
    encode_nato_phonetic, encode_qr_matrix, generate_random_suffix, generate_reverse_alias,
    generate_wildcard_on_the_fly, validate_access_key,
};
pub use wordlist::{
    generate_alias_preview_candidate, generate_passphrase, generate_uuid_v4_local_part,
    generate_words_local_part, GeneratedAliasCandidate, GeneratedPassphrase,
};

pub use mime_inspector::{
    inspect_attachment_filename, inspect_mime_message, AttachmentRiskEntry, MimeStructureReport,
    DANGEROUS_ATTACHMENT_CATALOG,
};
