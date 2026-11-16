//! Mailx Android Rust Çekirdeği (`mailx-core`)
//!
//! - [ivpn/mailx](https://github.com/ivpn/mailx) Go/Fiber REST API modelleri ve istemcisi
//! - Ters-alias (`Send from alias`) ve Wildcard on-the-fly adres üreticisi
//! - Çevrimdışı JSON önbellek yönetimi ve Access Key doğrulaması
//! - Android JNI köprüsü (`Java_net_mailx_android_core_MailxRustBridge_*`)

pub mod client;
pub mod jni_bridge;
pub mod models;
pub mod secret;
pub mod utils;

pub use client::{MailxClient, MailxError};
pub use models::*;
pub use secret::SecretString;
pub use utils::{
    build_aliases_query_path, generate_reverse_alias, generate_wildcard_on_the_fly,
    validate_access_key,
};
