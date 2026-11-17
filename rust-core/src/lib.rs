#![allow(clippy::all)]

pub mod client;
pub mod jni_bridge;
pub mod liquid_glass;
pub mod models;
pub mod secret;
pub mod utils;

pub use client::{MailxClient, MailxError};
pub use liquid_glass::{
    evaluate_nav_bar_liquid_state, generate_agsl_shader, generate_wgsl_shader,
    resolve_surface_material, smin_polynomial, Appearance, Color, ContainerId, Glass, GlassId,
    LiquidGlassFramePacket, LiquidGlassItemSnapshot, LiquidNeckBridge, Material, NavBarLiquidState,
    Rect, ResolvedGlassSurface, Scene, Shape, Spring,
};
pub use models::*;
pub use secret::SecretString;
pub use utils::{
    analyze_email_headers, audit_alias_hygiene, build_aliases_query_path, compute_privacy_posture,
    decode_trackers_report, encode_nato_phonetic, encode_qr_matrix, generate_contextual_aliases,
    generate_random_suffix, generate_reverse_alias, generate_wildcard_on_the_fly,
    sanitize_tracking_urls, validate_access_key,
};
