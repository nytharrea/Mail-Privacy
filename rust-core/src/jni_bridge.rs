//! Android (Kotlin) <-> Rust JNI Köprüsü
//!
//! Kotlin paket yolu: `net.mailx.android.core.MailxRustBridge`
//! Tüm yanıtlar JSON zarfı (`{"ok": true, "data": ...}` veya `{"ok": false, "error": "..."}`)
//! olarak döndürülür; böylece JNI sınırında exception/panic oluşmaz.

#[cfg(feature = "network")]
use crate::models::{AliasFilter, AliasRequest};
use crate::utils::{generate_reverse_alias, generate_wildcard_on_the_fly, validate_access_key};
use serde::Serialize;

#[derive(Serialize)]
pub struct JniEnvelope<T: Serialize> {
    pub ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<T>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

impl<T: Serialize> JniEnvelope<T> {
    pub fn success(data: T) -> String {
        serde_json::to_string(&Self {
            ok: true,
            data: Some(data),
            error: None,
        })
        .unwrap_or_else(|e| format!(r#"{{"ok":false,"error":"{}"}}"#, e))
    }

    pub fn failure(err: impl ToString) -> String {
        serde_json::to_string(&Self {
            ok: false,
            data: None,
            error: Some(err.to_string()),
        })
        .unwrap_or_else(|_| r#"{"ok":false,"error":"Serialization error"}"#.to_string())
    }
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

#[cfg(feature = "network")]
pub fn handle_authenticate(base_url: &str, access_key: &str) -> String {
    use crate::client::MailxClient;
    let client = match MailxClient::new(base_url, true) {
        Ok(c) => c,
        Err(e) => return JniEnvelope::<serde_json::Value>::failure(e),
    };
    match client.authenticate(access_key) {
        Ok(res) => JniEnvelope::success(res),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
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
        Ok(list) => JniEnvelope::success(list),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
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
        Ok(res) => JniEnvelope::success(res),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
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
        Ok(res) => JniEnvelope::success(res),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
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
        Ok(res) => JniEnvelope::success(res),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
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
        Ok(res) => JniEnvelope::success(res),
        Err(e) => JniEnvelope::<serde_json::Value>::failure(e),
    }
}

// Android JNI sembol dışa aktarımları (`feature = "jni"` aktifken derlenir)
#[cfg(feature = "jni")]
mod android_exports {
    use super::*;
    use jni::objects::{JClass, JString};
    use jni::sys::{jboolean, jint, jstring, JNI_TRUE};
    use jni::JNIEnv;

    fn jstring_to_rust(env: &mut JNIEnv, input: JString) -> String {
        env.get_string(&input).map(|s| s.into()).unwrap_or_default()
    }

    fn rust_to_jstring(env: &mut JNIEnv, output: String) -> jstring {
        env.new_string(output)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut())
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeValidateAccessKey(
        mut env: JNIEnv,
        _class: JClass,
        access_key: JString,
    ) -> jstring {
        let key = jstring_to_rust(&mut env, access_key);
        rust_to_jstring(&mut env, handle_validate_access_key(&key))
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeGenerateReverseAlias(
        mut env: JNIEnv,
        _class: JClass,
        alias_email: JString,
        target_email: JString,
    ) -> jstring {
        let alias = jstring_to_rust(&mut env, alias_email);
        let target = jstring_to_rust(&mut env, target_email);
        rust_to_jstring(&mut env, handle_generate_reverse_alias(&alias, &target))
    }

    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeGenerateWildcard(
        mut env: JNIEnv,
        _class: JClass,
        wildcard_alias: JString,
        prefix: JString,
    ) -> jstring {
        let wc = jstring_to_rust(&mut env, wildcard_alias);
        let pfx = jstring_to_rust(&mut env, prefix);
        rust_to_jstring(&mut env, handle_generate_wildcard(&wc, &pfx))
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeAuthenticate(
        mut env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
    ) -> jstring {
        let url = jstring_to_rust(&mut env, base_url);
        let key = jstring_to_rust(&mut env, access_key);
        rust_to_jstring(&mut env, handle_authenticate(&url, &key))
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeFetchAliases(
        mut env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
        bearer_token: JString,
        search: JString,
        status: JString,
        wildcard: JString,
        page: jint,
    ) -> jstring {
        let url = jstring_to_rust(&mut env, base_url);
        let key = jstring_to_rust(&mut env, access_key);
        let tok = jstring_to_rust(&mut env, bearer_token);
        let srch = jstring_to_rust(&mut env, search);
        let st = jstring_to_rust(&mut env, status);
        let wc = jstring_to_rust(&mut env, wildcard);
        rust_to_jstring(
            &mut env,
            handle_fetch_aliases(&url, &key, &tok, &srch, &st, &wc, page.max(1) as usize),
        )
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeCreateAlias(
        mut env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
        bearer_token: JString,
        request_json: JString,
    ) -> jstring {
        let url = jstring_to_rust(&mut env, base_url);
        let key = jstring_to_rust(&mut env, access_key);
        let tok = jstring_to_rust(&mut env, bearer_token);
        let req = jstring_to_rust(&mut env, request_json);
        rust_to_jstring(&mut env, handle_create_alias(&url, &key, &tok, &req))
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeUpdateAlias(
        mut env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
        bearer_token: JString,
        alias_id: JString,
        request_json: JString,
    ) -> jstring {
        let url = jstring_to_rust(&mut env, base_url);
        let key = jstring_to_rust(&mut env, access_key);
        let tok = jstring_to_rust(&mut env, bearer_token);
        let id = jstring_to_rust(&mut env, alias_id);
        let req = jstring_to_rust(&mut env, request_json);
        rust_to_jstring(&mut env, handle_update_alias(&url, &key, &tok, &id, &req))
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativePinAlias(
        mut env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
        bearer_token: JString,
        alias_id: JString,
        pinned: jboolean,
    ) -> jstring {
        let url = jstring_to_rust(&mut env, base_url);
        let key = jstring_to_rust(&mut env, access_key);
        let tok = jstring_to_rust(&mut env, bearer_token);
        let id = jstring_to_rust(&mut env, alias_id);
        rust_to_jstring(
            &mut env,
            handle_pin_alias(&url, &key, &tok, &id, pinned == JNI_TRUE),
        )
    }

    #[cfg(feature = "network")]
    #[no_mangle]
    pub extern "system" fn Java_net_mailx_android_core_MailxRustBridge_nativeDeleteAlias(
        mut env: JNIEnv,
        _class: JClass,
        base_url: JString,
        access_key: JString,
        bearer_token: JString,
        alias_id: JString,
    ) -> jstring {
        let url = jstring_to_rust(&mut env, base_url);
        let key = jstring_to_rust(&mut env, access_key);
        let tok = jstring_to_rust(&mut env, bearer_token);
        let id = jstring_to_rust(&mut env, alias_id);
        rust_to_jstring(&mut env, handle_delete_alias(&url, &key, &tok, &id))
    }
}
