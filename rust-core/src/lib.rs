#![allow(clippy::all)]

pub mod client;
pub mod jni_bridge;
pub mod models;
pub mod secret;
pub mod utils;

pub use client::{MailxClient, MailxError};
pub use models::*;
pub use secret::SecretString;
pub use utils::{
    build_aliases_query_path, decode_trackers_report, generate_reverse_alias,
    generate_wildcard_on_the_fly, validate_access_key,
};
