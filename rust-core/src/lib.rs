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
pub mod skeleton;
pub mod telemetry;
pub mod totp_engine;
pub mod tracker_db;
pub mod utils;
pub mod wordlist;

pub use client::{MailxClient, MailxError};
pub use models::*;
pub use secret::SecretString;
pub use skeleton::{
    analyze_email_headers, analyze_mta_bounce_log, audit_alias_hygiene,
    compute_seven_day_telemetry, decode_and_audit_trackers, evaluate_phishing_and_domain_risk,
    filter_and_sort_aliases, format_aliases_csv_export, generate_contextual_aliases,
    generate_passphrase_and_candidate, generate_totp_token, get_skeleton_info,
    inspect_openpgp_key_armor, parse_aliases_csv_import, sanitize_tracking_urls,
    simulate_rule_evaluation, MailxSkeletonRuntime,
};
pub use utils::{
    build_aliases_query_path, compute_privacy_posture, decode_trackers_report,
    encode_nato_phonetic, encode_qr_matrix, generate_random_suffix, generate_reverse_alias,
    generate_wildcard_on_the_fly, validate_access_key,
};


