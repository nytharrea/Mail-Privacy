use crate::liquid_glass::{
    evaluate_nav_bar_liquid_state, generate_agsl_shader, generate_wgsl_shader,
    resolve_surface_material, Appearance, Material, Shape,
};
#[cfg(feature = "network")]
use crate::models::{AliasFilter, AliasRequest};
use crate::utils::{
    analyze_email_headers, audit_alias_hygiene, compute_privacy_posture, decode_trackers_report,
    encode_nato_phonetic, encode_qr_matrix, generate_contextual_aliases, generate_random_suffix,
    generate_reverse_alias, generate_wildcard_on_the_fly, sanitize_tracking_urls,
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

pub fn handle_resolve_liquid_material(
    preset: &str,
    tint_argb: u32,
    corner_radius_pt: f32,
    width_pt: f32,
    height_pt: f32,
    transparency: f32,
    dark_mode: bool,
    reduce_transparency: bool,
    increase_contrast: bool,
    backdrop_luma: f32,
) -> String {
    let mat = Material::from_preset_name(preset, tint_argb);
    let appearance = Appearance {
        transparency,
        dark_mode,
        reduce_transparency,
        increase_contrast,
        reduce_motion: false,
        adapt_to_content: true,
        light_angle_rad: -0.7853982,
    };
    let surface = resolve_surface_material(
        &mat,
        Shape::Rounded(corner_radius_pt.max(0.0)),
        width_pt.max(1.0),
        height_pt.max(1.0),
        &appearance,
        backdrop_luma,
    );
    JniEnvelope::success(surface)
}

pub fn handle_evaluate_nav_bar_liquid(
    tab_count: usize,
    current_index: usize,
    target_index: usize,
    progress: f32,
    pointer_x_norm: f32,
    is_pressed: bool,
    spacing_pt: f32,
    transparency: f32,
    dark_mode: bool,
) -> String {
    let state = evaluate_nav_bar_liquid_state(
        tab_count,
        current_index,
        target_index,
        progress,
        pointer_x_norm,
        is_pressed,
        spacing_pt,
        transparency,
        dark_mode,
    );
    JniEnvelope::success(state)
}

pub fn handle_get_liquid_glass_agsl_shader() -> String {
    JniEnvelope::success(generate_agsl_shader())
}

pub fn handle_get_liquid_glass_wgsl_shader() -> String {
    JniEnvelope::success(generate_wgsl_shader())
}

pub fn handle_sanitize_tracking_urls(raw_text: &str) -> String {
    JniEnvelope::success(sanitize_tracking_urls(raw_text))
}

pub fn handle_analyze_email_headers(raw_headers: &str) -> String {
    match analyze_email_headers(raw_headers) {
        Ok(res) => JniEnvelope::success(res),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
    }
}

pub fn handle_audit_alias_hygiene(aliases_json: &str) -> String {
    match audit_alias_hygiene(aliases_json) {
        Ok(res) => JniEnvelope::success(res),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
    }
}

pub fn handle_generate_contextual_aliases(service_name: &str, domain: &str) -> String {
    match generate_contextual_aliases(service_name, domain) {
        Ok(res) => JniEnvelope::success(res),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
    }
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
    use jni::sys::{jboolean, jfloat, jint, jstring, JNI_TRUE};
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
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeResolveLiquidMaterial(
        env: JNIEnv,
        _class: JClass,
        preset: JString,
        tint_argb: jint,
        corner_radius_pt: jfloat,
        width_pt: jfloat,
        height_pt: jfloat,
        transparency: jfloat,
        dark_mode: jboolean,
        reduce_transparency: jboolean,
        increase_contrast: jboolean,
        backdrop_luma: jfloat,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let preset_str = jstring_to_rust(env, preset);
            handle_resolve_liquid_material(
                &preset_str,
                tint_argb as u32,
                corner_radius_pt,
                width_pt,
                height_pt,
                transparency,
                dark_mode == JNI_TRUE,
                reduce_transparency == JNI_TRUE,
                increase_contrast == JNI_TRUE,
                backdrop_luma,
            )
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeEvaluateNavBarLiquid(
        env: JNIEnv,
        _class: JClass,
        tab_count: jint,
        current_index: jint,
        target_index: jint,
        progress: jfloat,
        pointer_x_norm: jfloat,
        is_pressed: jboolean,
        spacing_pt: jfloat,
        transparency: jfloat,
        dark_mode: jboolean,
    ) -> jstring {
        run_jni_safely(env, |_env| {
            handle_evaluate_nav_bar_liquid(
                tab_count.max(1) as usize,
                current_index.max(0) as usize,
                target_index.max(0) as usize,
                progress,
                pointer_x_norm,
                is_pressed == JNI_TRUE,
                spacing_pt,
                transparency,
                dark_mode == JNI_TRUE,
            )
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeGetLiquidGlassAgslShader(
        env: JNIEnv,
        _class: JClass,
    ) -> jstring {
        run_jni_safely(env, |_env| handle_get_liquid_glass_agsl_shader())
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeGetLiquidGlassWgslShader(
        env: JNIEnv,
        _class: JClass,
    ) -> jstring {
        run_jni_safely(env, |_env| handle_get_liquid_glass_wgsl_shader())
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeSanitizeTrackingUrls(
        env: JNIEnv,
        _class: JClass,
        raw_text: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let txt = jstring_to_rust(env, raw_text);
            handle_sanitize_tracking_urls(&txt)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeAnalyzeEmailHeaders(
        env: JNIEnv,
        _class: JClass,
        raw_headers: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let hdrs = jstring_to_rust(env, raw_headers);
            handle_analyze_email_headers(&hdrs)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeAuditAliasHygiene(
        env: JNIEnv,
        _class: JClass,
        aliases_json: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let json = jstring_to_rust(env, aliases_json);
            handle_audit_alias_hygiene(&json)
        })
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeGenerateContextualAliases(
        env: JNIEnv,
        _class: JClass,
        service_name: JString,
        domain: JString,
    ) -> jstring {
        run_jni_safely(env, |env| {
            let srv = jstring_to_rust(env, service_name);
            let dom = jstring_to_rust(env, domain);
            handle_generate_contextual_aliases(&srv, &dom)
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
