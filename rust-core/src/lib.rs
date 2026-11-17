#![allow(clippy::all)]

pub mod client;
pub mod jni_bridge;
pub mod models;
pub mod secret;
pub mod skeleton;
pub mod utils;

pub use client::{MailxClient, MailxError};
pub use models::*;
pub use secret::SecretString;
pub use skeleton::{
    compute_seven_day_telemetry, filter_and_sort_aliases, format_aliases_csv_export,
    get_skeleton_info, parse_aliases_csv_import, MailxSkeletonRuntime,
};
pub use utils::{
    build_aliases_query_path, compute_privacy_posture, decode_trackers_report,
    encode_nato_phonetic, encode_qr_matrix, generate_random_suffix, generate_reverse_alias,
    generate_wildcard_on_the_fly, validate_access_key,
};
