#[cfg(feature = "network")]
use crate::models::{AliasFilter, AliasRequest};
use crate::skeleton::{
    analyze_email_headers, audit_alias_hygiene, compute_seven_day_telemetry,
    filter_and_sort_aliases, format_aliases_csv_export, generate_contextual_aliases,
    get_skeleton_info, parse_aliases_csv_import, sanitize_tracking_urls,
};
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
    search_query: &str,
    selected_chip: &str,
    sort_field: &str,
    sort_descending: bool,
    pinned_first: bool,
) -> String {
    match filter_and_sort_aliases(
        aliases_json,
        search_query,
        selected_chip,
        sort_field,
        sort_descending,
        pinned_first,
    ) {
        Ok(list) => JniEnvelope::success(list),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
    }
}

pub fn handle_compute_seven_day_telemetry(
    raw_stats_json: &str,
    aliases_json: &str,
    reference_epoch_secs: i64,
) -> String {
    match compute_seven_day_telemetry(raw_stats_json, aliases_json, reference_epoch_secs) {
        Ok(summary) => JniEnvelope::success(summary),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
    }
}

pub fn handle_format_aliases_csv(aliases_json: &str) -> String {
    match format_aliases_csv_export(aliases_json) {
        Ok(csv) => JniEnvelope::success(csv),
        Err(e) => JniEnvelope::<String>::failure(e),
    }
}

pub fn handle_parse_aliases_csv_import(
    csv_text: &str,
    custom_domains_json: &str,
    default_recipient: &str,
) -> String {
    match parse_aliases_csv_import(csv_text, custom_domains_json, default_recipient) {
        Ok(plan) => JniEnvelope::success(plan),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
    }
}

pub fn handle_get_skeleton_info() -> String {
    JniEnvelope::success(get_skeleton_info())
}

pub fn handle_sanitize_tracking_urls(raw_text: &str) -> String {
    JniEnvelope::success(sanitize_tracking_urls(raw_text))
}

pub fn handle_analyze_email_headers(raw_headers: &str) -> String {
    JniEnvelope::success(analyze_email_headers(raw_headers))
}

pub fn handle_generate_contextual_aliases(
    service_input: &str,
    domain: &str,
    count: usize,
) -> String {
    JniEnvelope::success(generate_contextual_aliases(service_input, domain, count))
}

pub fn handle_audit_alias_hygiene(aliases_json: &str) -> String {
    JniEnvelope::success(audit_alias_hygiene(aliases_json))
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
    use jni::sys::{jboolean, jint, jlong, jstring, JNI_TRUE};
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
        search_query: JString,
        selected_chip: JString,
        sort_field: JString,
        sort_descending: jboolean,
        pinned_first: jboolean,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let aliases = jstring_to_rust(env, aliases_json);
            let query = jstring_to_rust(env, search_query);
            let chip = jstring_to_rust(env, selected_chip);
            let field = jstring_to_rust(env, sort_field);
            handle_filter_and_sort_aliases(
                &aliases,
                &query,
                &chip,
                &field,
                sort_descending == JNI_TRUE,
                pinned_first == JNI_TRUE,
            )
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeComputeSevenDayTelemetry(
        env: JNIEnv,
        _class: JClass,
        raw_stats_json: JString,
        aliases_json: JString,
        reference_epoch_secs: jlong,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let raw_stats = jstring_to_rust(env, raw_stats_json);
            let aliases = jstring_to_rust(env, aliases_json);
            handle_compute_seven_day_telemetry(&raw_stats, &aliases, reference_epoch_secs)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeFormatAliasesCsv(
        env: JNIEnv,
        _class: JClass,
        aliases_json: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let aliases = jstring_to_rust(env, aliases_json);
            handle_format_aliases_csv(&aliases)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeParseAliasesCsvImport(
        env: JNIEnv,
        _class: JClass,
        csv_text: JString,
        custom_domains_json: JString,
        default_recipient: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let csv = jstring_to_rust(env, csv_text);
            let domains = jstring_to_rust(env, custom_domains_json);
            let def_rec = jstring_to_rust(env, default_recipient);
            handle_parse_aliases_csv_import(&csv, &domains, &def_rec)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeGetSkeletonInfo(
        env: JNIEnv,
        _class: JClass,
    ) -> jstring {
        run_jni_safely(env, |_env| handle_get_skeleton_info())
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeSanitizeTrackingUrls(
        env: JNIEnv,
        _class: JClass,
        raw_text: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let raw = jstring_to_rust(env, raw_text);
            handle_sanitize_tracking_urls(&raw)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeAnalyzeEmailHeaders(
        env: JNIEnv,
        _class: JClass,
        raw_headers: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let raw = jstring_to_rust(env, raw_headers);
            handle_analyze_email_headers(&raw)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeGenerateContextualAliases(
        env: JNIEnv,
        _class: JClass,
        service_input: JString,
        domain: JString,
        count: jint,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let srv = jstring_to_rust(env, service_input);
            let dom = jstring_to_rust(env, domain);
            handle_generate_contextual_aliases(&srv, &dom, count.clamp(1, 8) as usize)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeAuditAliasHygiene(
        env: JNIEnv,
        _class: JClass,
        aliases_json: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let aliases = jstring_to_rust(env, aliases_json);
            handle_audit_alias_hygiene(&aliases)
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
