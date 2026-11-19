// Stable Android-facing facade. Domain and feature modules live in focused crates.
pub use mailx_application::controller;
pub use mailx_domain::{
    alias_engine, catalog, csv_engine, models, offline_audit, telemetry, tracker_db,
};
pub use mailx_network::{client, MailxClient, MailxError};
pub use mailx_runtime::skeleton;
pub use mailx_security::{
    disposable_domains, dns_security, mime_inspector, mta_diagnostics, pgp_inspector,
    phishing_guard, rfc_email, rules_engine, totp_engine, wordlist,
};
pub use mailx_utils::{secret, utils, SecretString};

pub mod jni_bridge;

pub use mailx_domain::models::*;
pub use mailx_runtime::skeleton::{
    analyze_email_headers, analyze_mta_bounce_log, audit_alias_hygiene,
    compute_seven_day_telemetry, decode_and_audit_trackers, evaluate_phishing_and_domain_risk,
    filter_and_sort_aliases, format_aliases_csv_export, generate_contextual_aliases,
    generate_passphrase_and_candidate, generate_totp_token, get_skeleton_info,
    inspect_openpgp_key_armor, parse_aliases_csv_import, sanitize_tracking_urls,
    simulate_rule_evaluation, MailxSkeletonRuntime,
};
pub use mailx_utils::utils::{
    build_aliases_query_path, compute_privacy_posture, decode_trackers_report,
    encode_nato_phonetic, encode_qr_matrix, generate_random_suffix, generate_reverse_alias,
    generate_wildcard_on_the_fly, validate_access_key,
};
