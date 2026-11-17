use crate::alias_engine::{filter_and_sort_alias_ids_json, AliasQuerySpec};
use crate::catalog::format_iso_date_short;
use crate::csv_engine::export_aliases_json_to_csv;
#[cfg(feature = "network")]
use crate::models::{AliasFilter, AliasRequest};
use crate::telemetry::aggregate_user_stats_json;
use crate::utils::{
    compute_privacy_posture, decode_trackers_report, encode_nato_phonetic, encode_qr_matrix,
    generate_random_suffix, generate_reverse_alias, generate_wildcard_on_the_fly,
    validate_access_key,
};
use serde::Serialize;

#[derive(Serialize)]
pub struct JniEnvelope<T: Serialize> {
    pub ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<T>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error_code: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub refreshed_token: Option<String>,
}

impl<T: Serialize> JniEnvelope<T> {
    pub fn success(data: T) -> String {
        Self::success_with_token(data, None)
    }

    pub fn success_with_token(data: T, refreshed_token: Option<String>) -> String {
        serde_json::to_string(&Self {
            ok: true,
            data: Some(data),
            error: None,
            error_code: None,
            refreshed_token,
        })
        .unwrap_or_else(|e| format!(r#"{{"ok":false,"error":"{}"}}"#, e))
    }

    pub fn failure(err: impl ToString) -> String {
        Self::failure_with_code(err, None)
    }

    pub fn failure_with_code(err: impl ToString, error_code: Option<i64>) -> String {
        serde_json::to_string(&Self {
            ok: false,
            data: None,
            error: Some(err.to_string()),
            error_code,
            refreshed_token: None,
        })
        .unwrap_or_else(|_| r#"{"ok":false,"error":"Serialization error"}"#.to_string())
    }
}

#[cfg(feature = "network")]
fn detect_refreshed_token(
    client: &crate::client::MailxClient,
    original_token: &str,
) -> Option<String> {
    client
        .current_token()
        .filter(|current| !current.is_empty() && current != original_token.trim())
}

pub fn handle_validate_access_key(access_key: &str) -> String {
    match validate_access_key(access_key) {
        Ok(()) => JniEnvelope::success(true),
        Err(e) => JniEnvelope::<bool>::failure(e),
    }
}

pub fn handle_generate_reverse_alias(alias_email: &str, target_email: &str) -> String {
    match generate_reverse_alias(alias_email, target_email) {
        Ok(addr) => JniEnvelope::success(addr),
        Err(e) => JniEnvelope::<String>::failure(e),
    }
}

pub fn handle_generate_wildcard(wildcard_alias: &str, prefix: &str) -> String {
    match generate_wildcard_on_the_fly(wildcard_alias, prefix) {
        Ok(addr) => JniEnvelope::success(addr),
        Err(e) => JniEnvelope::<String>::failure(e),
    }
}

pub fn handle_decode_trackers_report(raw_input: &str) -> String {
    match decode_trackers_report(raw_input) {
        Ok(report) => JniEnvelope::success(report),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
    }
}

pub fn handle_encode_qr_matrix(payload: &str) -> String {
    match encode_qr_matrix(payload) {
        Ok(matrix) => JniEnvelope::success(matrix),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
    }
}

pub fn handle_encode_nato_phonetic(email: &str) -> String {
    JniEnvelope::success(encode_nato_phonetic(email))
}

pub fn handle_generate_random_suffix(length: usize) -> String {
    JniEnvelope::success(generate_random_suffix(length))
}

pub fn handle_compute_privacy_posture(
    forwards: u64,
    blocks: u64,
    active_aliases: usize,
    total_aliases: usize,
    pgp_active: bool,
    trackers_blocked: bool,
    header_stripped: bool,
    rules_active: usize,
) -> String {
    JniEnvelope::success(compute_privacy_posture(
        forwards,
        blocks,
        active_aliases,
        total_aliases,
        pgp_active,
        trackers_blocked,
        header_stripped,
        rules_active,
    ))
}

pub fn handle_filter_and_sort_aliases(
    aliases_json: &str,
    chip_key: &str,
    search_query: &str,
    sort_field: &str,
    sort_descending: bool,
    pinned_first: bool,
) -> String {
    let spec = AliasQuerySpec {
        chip_key: chip_key.to_string(),
        search_query: search_query.to_string(),
        sort_field: sort_field.to_string(),
        sort_descending,
        pinned_first,
    };
    match filter_and_sort_alias_ids_json(aliases_json, &spec) {
        Ok(ids) => JniEnvelope::success(ids),
        Err(e) => JniEnvelope::<Vec<String>>::failure(e),
    }
}

pub fn handle_compute_user_stats(raw_stats_json: &str, fallback_alias_count: usize) -> String {
    match aggregate_user_stats_json(raw_stats_json, fallback_alias_count, None) {
        Ok(stats) => JniEnvelope::success(stats),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
    }
}

pub fn handle_export_aliases_csv(aliases_json: &str) -> String {
    match export_aliases_json_to_csv(aliases_json) {
        Ok(csv) => JniEnvelope::success(csv),
        Err(e) => JniEnvelope::<String>::failure(e),
    }
}

pub fn handle_format_iso_date_short(raw_iso: &str) -> String {
    format_iso_date_short(raw_iso)
}

#[cfg(feature = "network")]
pub fn handle_authenticate(base_url: &str, access_key: &str) -> String {
    use crate::client::MailxClient;
    let client = match MailxClient::new(base_url, true) {
        Ok(c) => c,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    match client.authenticate(access_key) {
        Ok(res) => JniEnvelope::success(res),
        Err(e) => JniEnvelope::<serde_json::Value>::failure_with_code(&e, e.error_code()),
    }
}

#[cfg(feature = "network")]
pub fn handle_login(base_url: &str, email: &str, password: &str, otp: &str) -> String {
    use crate::client::MailxClient;
    let client = match MailxClient::new(base_url, true) {
        Ok(c) => c,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    match client.login_with_credentials(email, password, otp) {
        Ok(res) => JniEnvelope::success(res),
        Err(e) => JniEnvelope::<serde_json::Value>::failure_with_code(&e, e.error_code()),
    }
}

#[cfg(feature = "network")]
pub fn handle_api_request(
    base_url: &str,
    access_key: &str,
    bearer_token: &str,
    method: &str,
    path: &str,
    body_json: &str,
) -> String {
    use crate::client::MailxClient;
    let client = match MailxClient::new(base_url, true) {
        Ok(c) => c,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    client.set_credentials(Some(access_key.to_string()), Some(bearer_token.to_string()));
    let body_opt = if body_json.trim().is_empty() {
        None
    } else {
        Some(body_json)
    };
    match client.execute_raw_json(method, path, body_opt) {
        Ok(val) => {
            let refreshed = detect_refreshed_token(&client, bearer_token);
            JniEnvelope::success_with_token(val, refreshed)
        }
        Err(e) => JniEnvelope::<serde_json::Value>::failure_with_code(&e, e.error_code()),
    }
}

#[cfg(feature = "network")]
pub fn handle_dispatch_action(
    base_url: &str,
    access_key: &str,
    bearer_token: &str,
    action: &str,
    params_json: &str,
) -> String {
    use crate::client::MailxClient;
    use crate::controller::execute_controller_action;
    let client = match MailxClient::new(base_url, true) {
        Ok(c) => c,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    client.set_credentials(Some(access_key.to_string()), Some(bearer_token.to_string()));
    match execute_controller_action(&client, action, params_json) {
        Ok(val) => {
            let refreshed = detect_refreshed_token(&client, bearer_token);
            JniEnvelope::success_with_token(val, refreshed)
        }
        Err(e) => JniEnvelope::<serde_json::Value>::failure_with_code(&e, e.error_code()),
    }
}

#[cfg(feature = "network")]
pub fn handle_fetch_defaults(base_url: &str, access_key: &str, bearer_token: &str) -> String {
    use crate::client::MailxClient;
    let client = match MailxClient::new(base_url, true) {
        Ok(c) => c,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    client.set_credentials(Some(access_key.to_string()), Some(bearer_token.to_string()));
    match client.fetch_defaults() {
        Ok(defaults) => {
            let refreshed = detect_refreshed_token(&client, bearer_token);
            JniEnvelope::success_with_token(defaults, refreshed)
        }
        Err(e) => JniEnvelope::<serde_json::Value>::failure_with_code(&e, e.error_code()),
    }
}

#[cfg(feature = "network")]
pub fn handle_fetch_aliases(
    base_url: &str,
    access_key: &str,
    bearer_token: &str,
    search: &str,
    status: &str,
    wildcard: &str,
    page: usize,
) -> String {
    use crate::client::MailxClient;
    let client = match MailxClient::new(base_url, true) {
        Ok(c) => c,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    client.set_credentials(Some(access_key.to_string()), Some(bearer_token.to_string()));
    let filter = AliasFilter {
        page: page.max(1),
        limit: 50,
        search: search.to_string(),
        status: if status.is_empty() {
            "active_inactive".to_string()
        } else {
            status.to_string()
        },
        wildcard: wildcard.to_string(),
        ..Default::default()
    };
    match client.fetch_aliases(&filter) {
        Ok(list) => {
            let refreshed = detect_refreshed_token(&client, bearer_token);
            JniEnvelope::success_with_token(list, refreshed)
        }
        Err(e) => JniEnvelope::<serde_json::Value>::failure_with_code(&e, e.error_code()),
    }
}

#[cfg(feature = "network")]
pub fn handle_create_alias(
    base_url: &str,
    access_key: &str,
    bearer_token: &str,
    request_json: &str,
) -> String {
    use crate::client::MailxClient;
    let client = match MailxClient::new(base_url, true) {
        Ok(c) => c,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    client.set_credentials(Some(access_key.to_string()), Some(bearer_token.to_string()));
    let req: AliasRequest = match serde_json::from_str(request_json) {
        Ok(r) => r,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    match client.create_alias(&req) {
        Ok(res) => {
            let refreshed = detect_refreshed_token(&client, bearer_token);
            JniEnvelope::success_with_token(res, refreshed)
        }
        Err(e) => JniEnvelope::<serde_json::Value>::failure_with_code(&e, e.error_code()),
    }
}

#[cfg(feature = "network")]
pub fn handle_update_alias(
    base_url: &str,
    access_key: &str,
    bearer_token: &str,
    alias_id: &str,
    request_json: &str,
) -> String {
    use crate::client::MailxClient;
    let client = match MailxClient::new(base_url, true) {
        Ok(c) => c,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    client.set_credentials(Some(access_key.to_string()), Some(bearer_token.to_string()));
    let req: AliasRequest = match serde_json::from_str(request_json) {
        Ok(r) => r,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    match client.update_alias(alias_id, &req) {
        Ok(res) => {
            let refreshed = detect_refreshed_token(&client, bearer_token);
            JniEnvelope::success_with_token(res, refreshed)
        }
        Err(e) => JniEnvelope::<serde_json::Value>::failure_with_code(&e, e.error_code()),
    }
}

#[cfg(feature = "network")]
pub fn handle_pin_alias(
    base_url: &str,
    access_key: &str,
    bearer_token: &str,
    alias_id: &str,
    pinned: bool,
) -> String {
    use crate::client::MailxClient;
    let client = match MailxClient::new(base_url, true) {
        Ok(c) => c,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    client.set_credentials(Some(access_key.to_string()), Some(bearer_token.to_string()));
    match client.pin_alias(alias_id, pinned) {
        Ok(res) => {
            let refreshed = detect_refreshed_token(&client, bearer_token);
            JniEnvelope::success_with_token(res, refreshed)
        }
        Err(e) => JniEnvelope::<serde_json::Value>::failure_with_code(&e, e.error_code()),
    }
}

#[cfg(feature = "network")]
pub fn handle_delete_alias(
    base_url: &str,
    access_key: &str,
    bearer_token: &str,
    alias_id: &str,
) -> String {
    use crate::client::MailxClient;
    let client = match MailxClient::new(base_url, true) {
        Ok(c) => c,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    client.set_credentials(Some(access_key.to_string()), Some(bearer_token.to_string()));
    match client.delete_alias(alias_id) {
        Ok(res) => {
            let refreshed = detect_refreshed_token(&client, bearer_token);
            JniEnvelope::success_with_token(res, refreshed)
        }
        Err(e) => JniEnvelope::<serde_json::Value>::failure_with_code(&e, e.error_code()),
    }
}

#[cfg(feature = "network")]
pub fn handle_logout(base_url: &str, bearer_token: &str) -> String {
    use crate::client::MailxClient;
    let client = match MailxClient::new(base_url, true) {
        Ok(c) => c,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    client.set_credentials(None, Some(bearer_token.to_string()));
    match client.logout() {
        Ok(res) => JniEnvelope::success(res),
        Err(e) => JniEnvelope::<serde_json::Value>::failure_with_code(&e, e.error_code()),
    }
}

#[cfg(feature = "jni")]
mod android_exports {
    use super::*;
    use jni::objects::{JClass, JString};
    use jni::sys::{jboolean, jint, jstring, JNI_TRUE};
    use jni::JNIEnv;
    use std::panic::{catch_unwind, AssertUnwindSafe};

    fn jstring_to_rust(env: &mut JNIEnv, input: JString) -> String {
        env.get_string(&input).map(|s| s.into()).unwrap_or_default()
    }

    fn rust_to_jstring(env: &mut JNIEnv, output: String) -> jstring {
        env.new_string(output)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut())
    }

    fn run_jni_safely<F>(mut env: JNIEnv, f: F) -> jstring
    where
        F: FnOnce(&mut JNIEnv) -> String,
    {
        let payload = match catch_unwind(AssertUnwindSafe(|| f(&mut env))) {
            Ok(json) => json,
            Err(_) => JniEnvelope::<serde_json::Value>::failure("Internal native panic recovered"),
        };
        rust_to_jstring(&mut env, payload)
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeValidateAccessKey(
        env: JNIEnv,
        _class: JClass,
        access_key: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let key = jstring_to_rust(env, access_key);
            handle_validate_access_key(&key)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeGenerateReverseAlias(
        env: JNIEnv,
        _class: JClass,
        alias_email: JString,
        target_email: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let alias = jstring_to_rust(env, alias_email);
            let target = jstring_to_rust(env, target_email);
            handle_generate_reverse_alias(&alias, &target)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeGenerateWildcard(
        env: JNIEnv,
        _class: JClass,
        wildcard_alias: JString,
        prefix: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let wc = jstring_to_rust(env, wildcard_alias);
            let pfx = jstring_to_rust(env, prefix);
            handle_generate_wildcard(&wc, &pfx)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeDecodeTrackersReport(
        env: JNIEnv,
        _class: JClass,
        raw_input: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let raw = jstring_to_rust(env, raw_input);
            handle_decode_trackers_report(&raw)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeEncodeQrMatrix(
        env: JNIEnv,
        _class: JClass,
        payload: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let raw = jstring_to_rust(env, payload);
            handle_encode_qr_matrix(&raw)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeEncodeNatoPhonetic(
        env: JNIEnv,
        _class: JClass,
        email: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let raw = jstring_to_rust(env, email);
            handle_encode_nato_phonetic(&raw)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeGenerateRandomSuffix(
        env: JNIEnv,
        _class: JClass,
        length: jint,
    ) -> jstring {
        run_jni_safely(env, |_env| {
            handle_generate_random_suffix(length.clamp(6, 12) as usize)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeComputePrivacyPosture(
        env: JNIEnv,
        _class: JClass,
        forwards: jint,
        blocks: jint,
        active_aliases: jint,
        total_aliases: jint,
        pgp_active: jboolean,
        trackers_blocked: jboolean,
        header_stripped: jboolean,
        rules_active: jint,
    ) -> jstring {
        run_jni_safely(env, |_env| {
            handle_compute_privacy_posture(
                forwards.max(0) as u64,
                blocks.max(0) as u64,
                active_aliases.max(0) as usize,
                total_aliases.max(0) as usize,
                pgp_active == JNI_TRUE,
                trackers_blocked == JNI_TRUE,
                header_stripped == JNI_TRUE,
                rules_active.max(0) as usize,
            )
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeFilterAndSortAliases(
        env: JNIEnv,
        _class: JClass,
        aliases_json: JString,
        chip_key: JString,
        search_query: JString,
        sort_field: JString,
        sort_descending: jboolean,
        pinned_first: jboolean,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let raw = jstring_to_rust(env, aliases_json);
            let chip = jstring_to_rust(env, chip_key);
            let search = jstring_to_rust(env, search_query);
            let field = jstring_to_rust(env, sort_field);
            handle_filter_and_sort_aliases(
                &raw,
                &chip,
                &search,
                &field,
                sort_descending == JNI_TRUE,
                pinned_first == JNI_TRUE,
            )
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeComputeUserStats(
        env: JNIEnv,
        _class: JClass,
        raw_stats_json: JString,
        fallback_alias_count: jint,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let raw = jstring_to_rust(env, raw_stats_json);
            handle_compute_user_stats(&raw, fallback_alias_count.max(0) as usize)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeExportAliasesCsv(
        env: JNIEnv,
        _class: JClass,
        aliases_json: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let raw = jstring_to_rust(env, aliases_json);
            handle_export_aliases_csv(&raw)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeFormatIsoDateShort(
        env: JNIEnv,
        _class: JClass,
        raw_iso: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let raw = jstring_to_rust(env, raw_iso);
            handle_format_iso_date_short(&raw)
        })
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeAuthenticate(
        env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let url = jstring_to_rust(env, base_url);
            let key = jstring_to_rust(env, access_key);
            handle_authenticate(&url, &key)
        })
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeLogin(
        env: JNIEnv,
        _class: JClass,
        base_url: JString,
        email: JString,
        password: JString,
        otp: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let url = jstring_to_rust(env, base_url);
            let em = jstring_to_rust(env, email);
            let pw = jstring_to_rust(env, password);
            let code = jstring_to_rust(env, otp);
            handle_login(&url, &em, &pw, &code)
        })
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeApiRequest(
        env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
        bearer_token: JString,
        method: JString,
        path: JString,
        body_json: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let url = jstring_to_rust(env, base_url);
            let key = jstring_to_rust(env, access_key);
            let tok = jstring_to_rust(env, bearer_token);
            let m = jstring_to_rust(env, method);
            let p = jstring_to_rust(env, path);
            let b = jstring_to_rust(env, body_json);
            handle_api_request(&url, &key, &tok, &m, &p, &b)
        })
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeDispatchAction(
        env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
        bearer_token: JString,
        action: JString,
        params_json: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let url = jstring_to_rust(env, base_url);
            let key = jstring_to_rust(env, access_key);
            let tok = jstring_to_rust(env, bearer_token);
            let act = jstring_to_rust(env, action);
            let prm = jstring_to_rust(env, params_json);
            handle_dispatch_action(&url, &key, &tok, &act, &prm)
        })
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeFetchDefaults(
        env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
        bearer_token: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let url = jstring_to_rust(env, base_url);
            let key = jstring_to_rust(env, access_key);
            let tok = jstring_to_rust(env, bearer_token);
            handle_fetch_defaults(&url, &key, &tok)
        })
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeFetchAliases(
        env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
        bearer_token: JString,
        search: JString,
        status: JString,
        wildcard: JString,
        page: jint,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let url = jstring_to_rust(env, base_url);
            let key = jstring_to_rust(env, access_key);
            let tok = jstring_to_rust(env, bearer_token);
            let srch = jstring_to_rust(env, search);
            let st = jstring_to_rust(env, status);
            let wc = jstring_to_rust(env, wildcard);
            handle_fetch_aliases(&url, &key, &tok, &srch, &st, &wc, page.max(1) as usize)
        })
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeCreateAlias(
        env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
        bearer_token: JString,
        request_json: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let url = jstring_to_rust(env, base_url);
            let key = jstring_to_rust(env, access_key);
            let tok = jstring_to_rust(env, bearer_token);
            let req = jstring_to_rust(env, request_json);
            handle_create_alias(&url, &key, &tok, &req)
        })
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeUpdateAlias(
        env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
        bearer_token: JString,
        alias_id: JString,
        request_json: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let url = jstring_to_rust(env, base_url);
            let key = jstring_to_rust(env, access_key);
            let tok = jstring_to_rust(env, bearer_token);
            let id = jstring_to_rust(env, alias_id);
            let req = jstring_to_rust(env, request_json);
            handle_update_alias(&url, &key, &tok, &id, &req)
        })
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativePinAlias(
        env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
        bearer_token: JString,
        alias_id: JString,
        pinned: jboolean,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let url = jstring_to_rust(env, base_url);
            let key = jstring_to_rust(env, access_key);
            let tok = jstring_to_rust(env, bearer_token);
            let id = jstring_to_rust(env, alias_id);
            handle_pin_alias(&url, &key, &tok, &id, pinned == JNI_TRUE)
        })
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeDeleteAlias(
        env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
        bearer_token: JString,
        alias_id: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let url = jstring_to_rust(env, base_url);
            let key = jstring_to_rust(env, access_key);
            let tok = jstring_to_rust(env, bearer_token);
            let id = jstring_to_rust(env, alias_id);
            handle_delete_alias(&url, &key, &tok, &id)
        })
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeLogout(
        env: JNIEnv,
        _class: JClass,
        base_url: JString,
        bearer_token: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let url = jstring_to_rust(env, base_url);
            let tok = jstring_to_rust(env, bearer_token);
            handle_logout(&url, &tok)
        })
    }
}
