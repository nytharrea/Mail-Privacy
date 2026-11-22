use mailx_core::{get_skeleton_info, validate_access_key};

#[test]
fn android_facade_preserves_16kb_alignment_contract() {
    let info = get_skeleton_info();
    assert_eq!(info.page_alignment_kb, 16);
}

#[test]
fn access_key_validation_rejects_empty_input() {
    assert!(validate_access_key("").is_err());
}

#[cfg(feature = "uniffi")]
#[test]
fn uniffi_envelope_keeps_stable_error_shape() {
    let raw = mailx_core::uniffi_api::uniffi_validate_access_key(String::new());
    let response: serde_json::Value =
        serde_json::from_str(&raw).expect("UniFFI response must be valid JSON");

    assert_eq!(response["ok"], false);
    assert!(response["error"].is_string());
}
