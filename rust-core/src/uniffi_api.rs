//! Typed UniFFI boundary.
//!
//! The records, enums and errors below mirror the pure `mailx_runtime` types so
//! that the state machine itself stays free of FFI dependencies. Every mapping
//! is an exhaustive `match`, so adding a variant on either side fails to compile
//! until the mirror is updated.
use crate::secure_store_port::{SecureStorePort, SecureStorePortError};
use mailx_runtime::app_shell as shell;
use mailx_security::secure_store::validate_secure_store_key;
use mailx_utils::SecretString;
use std::fmt;
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum Route {
    Home,
    Aliases,
    Recipients,
    Rules,
    Settings,
}

impl From<shell::Route> for Route {
    fn from(route: shell::Route) -> Self {
        match route {
            shell::Route::Home => Self::Home,
            shell::Route::Aliases => Self::Aliases,
            shell::Route::Recipients => Self::Recipients,
            shell::Route::Rules => Self::Rules,
            shell::Route::Settings => Self::Settings,
        }
    }
}

impl From<Route> for shell::Route {
    fn from(route: Route) -> Self {
        match route {
            Route::Home => Self::Home,
            Route::Aliases => Self::Aliases,
            Route::Recipients => Self::Recipients,
            Route::Rules => Self::Rules,
            Route::Settings => Self::Settings,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum Sheet {
    CreateAlias,
    FilterOptions,
    ProfileMenu,
    Appearance,
    Diagnostics,
    Account,
    Announcements,
    TrackersReport,
    PrivacyLab,
    ManageAlias,
    WildcardGen,
    ReverseSend,
    ConfirmDelete,
    ConfirmLogout,
}

impl From<shell::Sheet> for Sheet {
    fn from(sheet: shell::Sheet) -> Self {
        match sheet {
            shell::Sheet::CreateAlias => Self::CreateAlias,
            shell::Sheet::FilterOptions => Self::FilterOptions,
            shell::Sheet::ProfileMenu => Self::ProfileMenu,
            shell::Sheet::Appearance => Self::Appearance,
            shell::Sheet::Diagnostics => Self::Diagnostics,
            shell::Sheet::Account => Self::Account,
            shell::Sheet::Announcements => Self::Announcements,
            shell::Sheet::TrackersReport => Self::TrackersReport,
            shell::Sheet::PrivacyLab => Self::PrivacyLab,
            shell::Sheet::ManageAlias => Self::ManageAlias,
            shell::Sheet::WildcardGen => Self::WildcardGen,
            shell::Sheet::ReverseSend => Self::ReverseSend,
            shell::Sheet::ConfirmDelete => Self::ConfirmDelete,
            shell::Sheet::ConfirmLogout => Self::ConfirmLogout,
        }
    }
}

impl From<Sheet> for shell::Sheet {
    fn from(sheet: Sheet) -> Self {
        match sheet {
            Sheet::CreateAlias => Self::CreateAlias,
            Sheet::FilterOptions => Self::FilterOptions,
            Sheet::ProfileMenu => Self::ProfileMenu,
            Sheet::Appearance => Self::Appearance,
            Sheet::Diagnostics => Self::Diagnostics,
            Sheet::Account => Self::Account,
            Sheet::Announcements => Self::Announcements,
            Sheet::TrackersReport => Self::TrackersReport,
            Sheet::PrivacyLab => Self::PrivacyLab,
            Sheet::ManageAlias => Self::ManageAlias,
            Sheet::WildcardGen => Self::WildcardGen,
            Sheet::ReverseSend => Self::ReverseSend,
            Sheet::ConfirmDelete => Self::ConfirmDelete,
            Sheet::ConfirmLogout => Self::ConfirmLogout,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct AppState {
    pub route: Route,
    pub sheet: Option<Sheet>,
    pub authenticated: bool,
    pub can_go_back: bool,
    pub revision: u64,
}

impl From<shell::AppState> for AppState {
    fn from(state: shell::AppState) -> Self {
        Self {
            route: state.route.into(),
            sheet: state.sheet.map(Sheet::from),
            authenticated: state.authenticated,
            can_go_back: state.can_go_back(),
            revision: state.revision,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AppEvent {
    Authenticate,
    SignOut,
    Navigate { route: Route },
    OpenSheet { sheet: Sheet },
    CloseSheet,
    Back,
}

impl From<AppEvent> for shell::AppEvent {
    fn from(event: AppEvent) -> Self {
        match event {
            AppEvent::Authenticate => Self::Authenticate,
            AppEvent::SignOut => Self::SignOut,
            AppEvent::Navigate { route } => Self::Navigate(route.into()),
            AppEvent::OpenSheet { sheet } => Self::OpenSheet(sheet.into()),
            AppEvent::CloseSheet => Self::CloseSheet,
            AppEvent::Back => Self::Back,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Error)]
pub enum MailxCoreError {
    AuthenticationRequired,
    NothingToGoBack,
}

impl fmt::Display for MailxCoreError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::AuthenticationRequired => f.write_str("Authentication is required"),
            Self::NothingToGoBack => f.write_str("There is nothing to go back to"),
        }
    }
}

impl std::error::Error for MailxCoreError {}

impl From<shell::AppError> for MailxCoreError {
    fn from(error: shell::AppError) -> Self {
        match error {
            shell::AppError::AuthenticationRequired => Self::AuthenticationRequired,
            shell::AppError::NothingToGoBack => Self::NothingToGoBack,
        }
    }
}

/// Process-scoped Rust state owner exposed to Kotlin as a UniFFI object.
const KEY_BASE_URL: &str = "base_url";
const KEY_ACCESS_KEY: &str = "access_key";
const KEY_BEARER_TOKEN: &str = "bearer_token";
const KEY_ACCOUNT_EMAIL: &str = "account_email";
const DEFAULT_API_BASE_URL: &str = "https://api.mailx.net";

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SessionSnapshot {
    pub authenticated: bool,
    pub is_web_session: bool,
    pub has_access_key: bool,
    pub base_url: String,
    pub account_email: String,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ApiCallResult {
    pub ok: bool,
    pub data_json: String,
    pub error: Option<String>,
    pub error_code: Option<i64>,
}

impl ApiCallResult {
    #[cfg(feature = "network")]
    fn success<T: serde::Serialize>(data: &T) -> Self {
        match serde_json::to_string(data) {
            Ok(data_json) => Self {
                ok: true,
                data_json,
                error: None,
                error_code: None,
            },
            Err(err) => Self::failure(err.to_string(), None),
        }
    }

    fn failure(error: impl Into<String>, error_code: Option<i64>) -> Self {
        Self {
            ok: false,
            data_json: String::new(),
            error: Some(error.into()),
            error_code,
        }
    }

    #[cfg(feature = "network")]
    fn from_mailx_error(error: &mailx_network::MailxError) -> Self {
        Self::failure(error.to_string(), error.error_code())
    }
}

#[derive(Clone)]
struct ActiveSession {
    base_url: String,
    access_key: Option<SecretString>,
    bearer_token: SecretString,
    account_email: String,
    #[cfg(feature = "network")]
    client: Option<mailx_network::MailxClient>,
}

impl ActiveSession {
    fn snapshot(&self) -> SessionSnapshot {
        let token = self.bearer_token.expose();
        SessionSnapshot {
            authenticated: !token.is_empty(),
            is_web_session: token.starts_with("cookie:"),
            has_access_key: self.access_key.is_some(),
            base_url: self.base_url.clone(),
            account_email: self.account_email.clone(),
        }
    }
}

fn normalize_base_url(raw: &str) -> String {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return DEFAULT_API_BASE_URL.to_string();
    }
    #[cfg(feature = "network")]
    {
        mailx_network::MailxClient::normalize_base_url(trimmed)
    }
    #[cfg(not(feature = "network"))]
    {
        let stripped = trimmed.trim_end_matches('/');
        match stripped.to_ascii_lowercase().as_str() {
            "https://mailx.net" | "https://www.mailx.net" | "https://app.mailx.net" => {
                DEFAULT_API_BASE_URL.to_string()
            }
            _ => stripped.to_string(),
        }
    }
}

fn empty_session_snapshot(base_url: String) -> SessionSnapshot {
    SessionSnapshot {
        authenticated: false,
        is_web_session: false,
        has_access_key: false,
        base_url,
        account_email: String::new(),
    }
}

#[derive(uniffi::Object)]
pub struct MailxCore {
    inner: mailx_runtime::MailxCore,
    secure_store: Mutex<Option<Arc<dyn SecureStorePort>>>,
    session: Mutex<Option<ActiveSession>>,
}

#[uniffi::export]
impl MailxCore {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            inner: mailx_runtime::MailxCore::new(),
            secure_store: Mutex::new(None),
            session: Mutex::new(None),
        })
    }

    pub fn state(&self) -> AppState {
        self.inner.state().into()
    }

    pub fn dispatch(&self, event: AppEvent) -> Result<AppState, MailxCoreError> {
        let next = self.inner.dispatch(event.into())?;
        Ok(next.into())
    }

    /// Attach the Android Keystore/Tink adapter for this process.
    pub fn attach_secure_store(&self, store: Arc<dyn SecureStorePort>) {
        *self.store_slot() = Some(store);
    }

    pub fn secure_store_get(&self, key: String) -> Result<Option<Vec<u8>>, SecureStorePortError> {
        validate_secure_store_key(&key)?;
        self.attached_store()?.get(key)
    }

    pub fn secure_store_put(
        &self,
        key: String,
        value: Vec<u8>,
    ) -> Result<(), SecureStorePortError> {
        validate_secure_store_key(&key)?;
        self.attached_store()?.put(key, value)
    }

    pub fn secure_store_delete(&self, key: String) -> Result<(), SecureStorePortError> {
        validate_secure_store_key(&key)?;
        self.attached_store()?.delete(key)
    }

    pub fn session_snapshot(&self) -> SessionSnapshot {
        let guard = self.session_slot();
        match guard.as_ref() {
            Some(active) => active.snapshot(),
            None => empty_session_snapshot(DEFAULT_API_BASE_URL.to_string()),
        }
    }

    pub fn restore_session(&self) -> Result<SessionSnapshot, SecureStorePortError> {
        let store = self.attached_store()?;
        let base_url = normalize_base_url(&read_utf8_key(&store, KEY_BASE_URL)?);
        let access_key = read_utf8_key(&store, KEY_ACCESS_KEY)?.trim().to_string();
        let bearer_token = read_utf8_key(&store, KEY_BEARER_TOKEN)?.trim().to_string();
        let account_email = read_utf8_key(&store, KEY_ACCOUNT_EMAIL)?.trim().to_string();

        if bearer_token.is_empty() {
            *self.session_slot() = None;
            let _ = self.inner.dispatch(shell::AppEvent::SignOut);
            return Ok(empty_session_snapshot(base_url));
        }

        let active = ActiveSession {
            base_url,
            access_key: (!access_key.is_empty()).then(|| SecretString::new(access_key)),
            bearer_token: SecretString::new(bearer_token),
            account_email,
            #[cfg(feature = "network")]
            client: None,
        };
        let snapshot = active.snapshot();
        *self.session_slot() = Some(active);
        let _ = self.inner.dispatch(shell::AppEvent::Authenticate);
        Ok(snapshot)
    }

    pub fn save_session(
        &self,
        base_url: String,
        access_key: String,
        bearer_token: String,
        account_email: String,
    ) -> Result<SessionSnapshot, SecureStorePortError> {
        let store = self.attached_store()?;
        let normalized_url = normalize_base_url(&base_url);
        let trimmed_key = access_key.trim().to_string();
        let trimmed_token = bearer_token.trim().to_string();
        let trimmed_email = account_email.trim().to_string();

        store.put(KEY_BASE_URL.to_string(), normalized_url.as_bytes().to_vec())?;
        store.put(KEY_ACCESS_KEY.to_string(), trimmed_key.as_bytes().to_vec())?;
        store.put(
            KEY_BEARER_TOKEN.to_string(),
            trimmed_token.as_bytes().to_vec(),
        )?;
        store.put(
            KEY_ACCOUNT_EMAIL.to_string(),
            trimmed_email.as_bytes().to_vec(),
        )?;

        if trimmed_token.is_empty() {
            *self.session_slot() = None;
            let _ = self.inner.dispatch(shell::AppEvent::SignOut);
            return Ok(empty_session_snapshot(normalized_url));
        }

        let active = ActiveSession {
            base_url: normalized_url,
            access_key: (!trimmed_key.is_empty()).then(|| SecretString::new(trimmed_key)),
            bearer_token: SecretString::new(trimmed_token),
            account_email: trimmed_email,
            #[cfg(feature = "network")]
            client: None,
        };
        let snapshot = active.snapshot();
        *self.session_slot() = Some(active);
        let _ = self.inner.dispatch(shell::AppEvent::Authenticate);
        Ok(snapshot)
    }

    pub fn rotate_session_token(
        &self,
        expected_token: String,
        new_token: String,
    ) -> Result<bool, SecureStorePortError> {
        let trimmed_new = new_token.trim().to_string();
        if trimmed_new.is_empty() {
            return Ok(false);
        }
        let store = self.attached_store()?;
        let mut guard = self.session_slot();
        let Some(active) = guard.as_mut() else {
            return Ok(false);
        };
        let expected_secret = SecretString::new(expected_token.trim());
        let next_secret = SecretString::new(trimmed_new.clone());
        if active.bearer_token != expected_secret || active.bearer_token == next_secret {
            return Ok(false);
        }
        store.put(KEY_BEARER_TOKEN.to_string(), trimmed_new.into_bytes())?;
        active.bearer_token = next_secret;
        #[cfg(feature = "network")]
        if let Some(client) = &active.client {
            let key = active.access_key.as_ref().map(|k| k.expose().to_string());
            client.set_credentials(key, Some(active.bearer_token.expose().to_string()));
        }
        Ok(true)
    }

    pub fn clear_session(&self) -> Result<SessionSnapshot, SecureStorePortError> {
        let store = self.attached_store()?;
        let base_url = self
            .session_slot()
            .as_ref()
            .map(|s| s.base_url.clone())
            .unwrap_or_else(|| DEFAULT_API_BASE_URL.to_string());
        store.delete(KEY_ACCESS_KEY.to_string())?;
        store.delete(KEY_BEARER_TOKEN.to_string())?;
        store.delete(KEY_ACCOUNT_EMAIL.to_string())?;
        *self.session_slot() = None;
        let _ = self.inner.dispatch(shell::AppEvent::SignOut);
        Ok(empty_session_snapshot(base_url))
    }

    pub fn login_with_access_key(&self, base_url: String, access_key: String) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let client = match mailx_network::MailxClient::new(&base_url, true) {
                Ok(c) => c,
                Err(e) => return ApiCallResult::from_mailx_error(&e),
            };
            let res = match client.authenticate(&access_key) {
                Ok(r) => r,
                Err(e) => return ApiCallResult::from_mailx_error(&e),
            };
            let email = self.session_snapshot().account_email;
            let saved = self.save_session(base_url, access_key, res.token.clone(), email);
            if let Err(e) = saved {
                return ApiCallResult::failure(e.to_string(), None);
            }
            ApiCallResult::success(&res)
        }
        #[cfg(not(feature = "network"))]
        {
            let _ = (base_url, access_key);
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn login_with_password(
        &self,
        base_url: String,
        email: String,
        password: String,
        otp: String,
    ) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let client = match mailx_network::MailxClient::new(&base_url, true) {
                Ok(c) => c,
                Err(e) => return ApiCallResult::from_mailx_error(&e),
            };
            let res = match client.login_with_credentials(&email, &password, &otp) {
                Ok(r) => r,
                Err(e) => return ApiCallResult::from_mailx_error(&e),
            };
            let saved = self.save_session(base_url, String::new(), res.token.clone(), email);
            if let Err(e) = saved {
                return ApiCallResult::failure(e.to_string(), None);
            }
            ApiCallResult::success(&res)
        }
        #[cfg(not(feature = "network"))]
        {
            let _ = (base_url, email, password, otp);
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn passkey_login_begin(&self, base_url: String, email: String) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let client = match mailx_network::MailxClient::new(&base_url, true) {
                Ok(c) => c,
                Err(e) => return ApiCallResult::from_mailx_error(&e),
            };
            match client.passkey_login_begin(&email) {
                Ok(res) => ApiCallResult::success(&res),
                Err(e) => ApiCallResult::from_mailx_error(&e),
            }
        }
        #[cfg(not(feature = "network"))]
        {
            let _ = (base_url, email);
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn passkey_login_finish(
        &self,
        base_url: String,
        ceremony_cookie: String,
        credential_json: String,
        discoverable: bool,
        fallback_email: String,
    ) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let client = match mailx_network::MailxClient::new(&base_url, true) {
                Ok(c) => c,
                Err(e) => return ApiCallResult::from_mailx_error(&e),
            };
            let res = match client.passkey_login_finish(
                &ceremony_cookie,
                &credential_json,
                discoverable,
                &fallback_email,
            ) {
                Ok(r) => r,
                Err(e) => return ApiCallResult::from_mailx_error(&e),
            };
            let email = if res.recipient.trim().is_empty() {
                fallback_email
            } else {
                res.recipient.clone()
            };
            let saved = self.save_session(base_url, String::new(), res.token.clone(), email);
            if let Err(e) = saved {
                return ApiCallResult::failure(e.to_string(), None);
            }
            ApiCallResult::success(&res)
        }
        #[cfg(not(feature = "network"))]
        {
            let _ = (
                base_url,
                ceremony_cookie,
                credential_json,
                discoverable,
                fallback_email,
            );
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn passkey_register_begin(&self) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let (client, orig) = match self.build_session_client() {
                Ok(pair) => pair,
                Err(err) => return err,
            };
            let res = client.passkey_register_begin();
            self.sync_client_rotation(&client, &orig);
            match res {
                Ok(val) => ApiCallResult::success(&val),
                Err(e) => ApiCallResult::from_mailx_error(&e),
            }
        }
        #[cfg(not(feature = "network"))]
        {
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn passkey_register_finish(
        &self,
        ceremony_cookie: String,
        credential_json: String,
    ) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let (client, orig) = match self.build_session_client() {
                Ok(pair) => pair,
                Err(err) => return err,
            };
            let res = client.passkey_register_finish(&ceremony_cookie, &credential_json);
            self.sync_client_rotation(&client, &orig);
            match res {
                Ok(val) => ApiCallResult::success(&val),
                Err(e) => ApiCallResult::from_mailx_error(&e),
            }
        }
        #[cfg(not(feature = "network"))]
        {
            let _ = (ceremony_cookie, credential_json);
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn api_request(&self, method: String, path: String, body_json: String) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let (client, orig) = match self.build_session_client() {
                Ok(pair) => pair,
                Err(err) => return err,
            };
            let body_opt = if body_json.trim().is_empty() {
                None
            } else {
                Some(body_json.as_str())
            };
            let res = client.execute_raw_json(&method, &path, body_opt);
            self.sync_client_rotation(&client, &orig);
            match res {
                Ok(val) => ApiCallResult::success(&val),
                Err(e) => ApiCallResult::from_mailx_error(&e),
            }
        }
        #[cfg(not(feature = "network"))]
        {
            let _ = (method, path, body_json);
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn import_aliases_csv(&self, csv_text: String) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let (client, orig) = match self.build_session_client() {
                Ok(pair) => pair,
                Err(err) => return err,
            };
            let res = client.import_aliases_csv(&csv_text);
            self.sync_client_rotation(&client, &orig);
            match res {
                Ok(val) => ApiCallResult::success(&val),
                Err(e) => ApiCallResult::from_mailx_error(&e),
            }
        }
        #[cfg(not(feature = "network"))]
        {
            let _ = csv_text;
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn fetch_defaults(&self) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let (client, orig) = match self.build_session_client() {
                Ok(pair) => pair,
                Err(err) => return err,
            };
            let res = client.fetch_defaults();
            self.sync_client_rotation(&client, &orig);
            match res {
                Ok(val) => ApiCallResult::success(&val),
                Err(e) => ApiCallResult::from_mailx_error(&e),
            }
        }
        #[cfg(not(feature = "network"))]
        {
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn fetch_aliases(
        &self,
        search: String,
        status: String,
        wildcard: String,
        page: u32,
    ) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let (client, orig) = match self.build_session_client() {
                Ok(pair) => pair,
                Err(err) => return err,
            };
            let filter = mailx_domain::models::AliasFilter {
                page: page.max(1) as usize,
                status,
                wildcard,
                search,
                ..Default::default()
            };
            let res = client.fetch_aliases(&filter);
            self.sync_client_rotation(&client, &orig);
            match res {
                Ok(val) => ApiCallResult::success(&val),
                Err(e) => ApiCallResult::from_mailx_error(&e),
            }
        }
        #[cfg(not(feature = "network"))]
        {
            let _ = (search, status, wildcard, page);
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn create_alias(&self, request_json: String) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let req: mailx_domain::models::AliasRequest = match serde_json::from_str(&request_json)
            {
                Ok(r) => r,
                Err(e) => return ApiCallResult::failure(e.to_string(), None),
            };
            let (client, orig) = match self.build_session_client() {
                Ok(pair) => pair,
                Err(err) => return err,
            };
            let res = client.create_alias(&req);
            self.sync_client_rotation(&client, &orig);
            match res {
                Ok(val) => ApiCallResult::success(&val),
                Err(e) => ApiCallResult::from_mailx_error(&e),
            }
        }
        #[cfg(not(feature = "network"))]
        {
            let _ = request_json;
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn update_alias(&self, alias_id: String, request_json: String) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let req: mailx_domain::models::AliasRequest = match serde_json::from_str(&request_json)
            {
                Ok(r) => r,
                Err(e) => return ApiCallResult::failure(e.to_string(), None),
            };
            let (client, orig) = match self.build_session_client() {
                Ok(pair) => pair,
                Err(err) => return err,
            };
            let res = client.update_alias(&alias_id, &req);
            self.sync_client_rotation(&client, &orig);
            match res {
                Ok(val) => ApiCallResult::success(&val),
                Err(e) => ApiCallResult::from_mailx_error(&e),
            }
        }
        #[cfg(not(feature = "network"))]
        {
            let _ = (alias_id, request_json);
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn pin_alias(&self, alias_id: String, pinned: bool) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let (client, orig) = match self.build_session_client() {
                Ok(pair) => pair,
                Err(err) => return err,
            };
            let res = client.pin_alias(&alias_id, pinned);
            self.sync_client_rotation(&client, &orig);
            match res {
                Ok(val) => ApiCallResult::success(&val),
                Err(e) => ApiCallResult::from_mailx_error(&e),
            }
        }
        #[cfg(not(feature = "network"))]
        {
            let _ = (alias_id, pinned);
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn delete_alias(&self, alias_id: String) -> ApiCallResult {
        #[cfg(feature = "network")]
        {
            let (client, orig) = match self.build_session_client() {
                Ok(pair) => pair,
                Err(err) => return err,
            };
            let res = client.delete_alias(&alias_id);
            self.sync_client_rotation(&client, &orig);
            match res {
                Ok(val) => ApiCallResult::success(&val),
                Err(e) => ApiCallResult::from_mailx_error(&e),
            }
        }
        #[cfg(not(feature = "network"))]
        {
            let _ = alias_id;
            ApiCallResult::failure("Network support is disabled in this build.", None)
        }
    }

    pub fn logout_session(&self) -> ApiCallResult {
        #[cfg(feature = "network")]
        if let Ok((client, _)) = self.build_session_client() {
            let _ = client.logout();
        }
        match self.clear_session() {
            Ok(_) => ApiCallResult {
                ok: true,
                data_json: "{}".to_string(),
                error: None,
                error_code: None,
            },
            Err(e) => ApiCallResult::failure(e.to_string(), None),
        }
    }
}

impl MailxCore {
    fn store_slot(&self) -> MutexGuard<'_, Option<Arc<dyn SecureStorePort>>> {
        self.secure_store
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
    }

    fn session_slot(&self) -> MutexGuard<'_, Option<ActiveSession>> {
        self.session.lock().unwrap_or_else(PoisonError::into_inner)
    }

    fn attached_store(&self) -> Result<Arc<dyn SecureStorePort>, SecureStorePortError> {
        self.store_slot()
            .clone()
            .ok_or(SecureStorePortError::NotAttached)
    }

    #[cfg(feature = "network")]
    fn build_session_client(&self) -> Result<(mailx_network::MailxClient, String), ApiCallResult> {
        if self.session_slot().is_none() && self.store_slot().is_some() {
            if let Err(e) = self.restore_session() {
                return Err(ApiCallResult::failure(e.to_string(), None));
            }
        }
        let mut guard = self.session_slot();
        let active = guard
            .as_mut()
            .ok_or_else(|| ApiCallResult::failure("Authentication is required", Some(401)))?;
        let token = active.bearer_token.expose().to_string();
        if let Some(client) = &active.client {
            return Ok((client.clone(), token));
        }
        let client = mailx_network::MailxClient::new(&active.base_url, true)
            .map_err(|e| ApiCallResult::from_mailx_error(&e))?;
        let key = active.access_key.as_ref().map(|k| k.expose().to_string());
        client.set_credentials(key, Some(token.clone()));
        active.client = Some(client.clone());
        Ok((client, token))
    }

    #[cfg(feature = "network")]
    fn sync_client_rotation(&self, client: &mailx_network::MailxClient, original_token: &str) {
        if let Some(current) = client.current_token() {
            if !current.is_empty() && current != original_token {
                let _ = self.rotate_session_token(original_token.to_string(), current);
            }
        }
    }
}

fn read_utf8_key(
    store: &Arc<dyn SecureStorePort>,
    key: &str,
) -> Result<String, SecureStorePortError> {
    match store.get(key.to_string())? {
        Some(bytes) => String::from_utf8(bytes).map_err(|_| SecureStorePortError::CorruptData),
        None => Ok(String::new()),
    }
}

fn json_ok<T: serde::Serialize>(data: T) -> String {
    serde_json::to_string(&serde_json::json!({
        "ok": true,
        "data": data,
    }))
    .unwrap_or_else(|_| r#"{"ok":false,"error":"Serialization error"}"#.to_string())
}

fn json_err(err: impl fmt::Display) -> String {
    serde_json::to_string(&serde_json::json!({
        "ok": false,
        "error": err.to_string(),
    }))
    .unwrap_or_else(|_| r#"{"ok":false,"error":"Serialization error"}"#.to_string())
}

#[uniffi::export]
pub fn uniffi_detect_alias_watch_events(
    previous_aliases_json: String,
    current_aliases_json: String,
    watched_ids_json: String,
) -> String {
    let previous: serde_json::Value = match serde_json::from_str(&previous_aliases_json) {
        Ok(value) => value,
        Err(error) => return json_err(error),
    };
    let current: serde_json::Value = match serde_json::from_str(&current_aliases_json) {
        Ok(value) => value,
        Err(error) => return json_err(error),
    };
    let watched: Vec<String> = match serde_json::from_str(&watched_ids_json) {
        Ok(value) => value,
        Err(error) => return json_err(error),
    };
    match mailx_domain::alias_watch::detect_alias_watch_events(&previous, &current, &watched) {
        Ok(events) => json_ok(events),
        Err(error) => json_err(error),
    }
}

#[uniffi::export]
pub fn uniffi_validate_access_key(access_key: String) -> String {
    match mailx_utils::utils::validate_access_key(&access_key) {
        Ok(()) => json_ok(true),
        Err(error) => json_err(error),
    }
}

#[uniffi::export]
pub fn uniffi_generate_reverse_alias(alias_email: String, target_email: String) -> String {
    match mailx_utils::utils::generate_reverse_alias(&alias_email, &target_email) {
        Ok(addr) => json_ok(addr),
        Err(error) => json_err(error),
    }
}

#[uniffi::export]
pub fn uniffi_generate_wildcard(wildcard_alias: String, prefix: String) -> String {
    match mailx_utils::utils::generate_wildcard_on_the_fly(&wildcard_alias, &prefix) {
        Ok(addr) => json_ok(addr),
        Err(error) => json_err(error),
    }
}

#[uniffi::export]
pub fn uniffi_decode_trackers_report(raw_input: String) -> String {
    match mailx_runtime::skeleton::decode_and_audit_trackers(&raw_input) {
        Ok(report) => json_ok(report),
        Err(error) => json_err(error),
    }
}

#[uniffi::export]
pub fn uniffi_encode_qr_matrix(payload: String) -> String {
    match mailx_utils::utils::encode_qr_matrix(&payload) {
        Ok(matrix) => json_ok(matrix),
        Err(error) => json_err(error),
    }
}

#[uniffi::export]
pub fn uniffi_encode_nato_phonetic(email: String) -> String {
    json_ok(mailx_utils::utils::encode_nato_phonetic(&email))
}

#[uniffi::export]
pub fn uniffi_generate_random_suffix(length: i32) -> String {
    json_ok(mailx_utils::utils::generate_random_suffix(
        length.max(0) as usize
    ))
}

#[allow(clippy::too_many_arguments)]
#[uniffi::export]
pub fn uniffi_compute_privacy_posture(
    forwards: i32,
    blocks: i32,
    active_aliases: i32,
    total_aliases: i32,
    pgp_active: bool,
    trackers_blocked: bool,
    header_stripped: bool,
    rules_active: i32,
) -> String {
    let input = mailx_utils::utils::PrivacyPostureInput {
        forwards: forwards.max(0) as u64,
        blocks: blocks.max(0) as u64,
        active_aliases: active_aliases.max(0) as usize,
        total_aliases: total_aliases.max(0) as usize,
        pgp_active,
        trackers_blocked,
        header_stripped,
        rules_active: rules_active.max(0) as usize,
    };
    json_ok(mailx_utils::utils::compute_privacy_posture(input))
}

#[uniffi::export]
pub fn uniffi_filter_and_sort_aliases(
    aliases_json: String,
    search_query: String,
    selected_chip: String,
    sort_field: String,
    sort_descending: bool,
    pinned_first: bool,
) -> String {
    let res = mailx_runtime::skeleton::filter_and_sort_aliases(
        &aliases_json,
        &search_query,
        &selected_chip,
        &sort_field,
        sort_descending,
        pinned_first,
    );
    match res {
        Ok(list) => json_ok(list),
        Err(error) => json_err(error),
    }
}

#[uniffi::export]
pub fn uniffi_compute_seven_day_telemetry(
    raw_stats_json: String,
    aliases_json: String,
    reference_epoch_secs: i64,
) -> String {
    let res = mailx_runtime::skeleton::compute_seven_day_telemetry(
        &raw_stats_json,
        &aliases_json,
        reference_epoch_secs,
    );
    match res {
        Ok(summary) => json_ok(summary),
        Err(error) => json_err(error),
    }
}

#[uniffi::export]
pub fn uniffi_format_aliases_csv(aliases_json: String) -> String {
    match mailx_runtime::skeleton::format_aliases_csv_export(&aliases_json) {
        Ok(csv) => json_ok(csv),
        Err(error) => json_err(error),
    }
}

#[uniffi::export]
pub fn uniffi_parse_aliases_csv_import(
    csv_text: String,
    custom_domains_json: String,
    default_recipient: String,
) -> String {
    let res = mailx_runtime::skeleton::parse_aliases_csv_import(
        &csv_text,
        &custom_domains_json,
        &default_recipient,
    );
    match res {
        Ok(plan) => json_ok(plan),
        Err(error) => json_err(error),
    }
}

#[uniffi::export]
pub fn uniffi_get_skeleton_info() -> String {
    json_ok(mailx_runtime::skeleton::get_skeleton_info())
}

#[uniffi::export]
pub fn uniffi_sanitize_tracking_urls(raw_text: String) -> String {
    json_ok(mailx_runtime::skeleton::sanitize_tracking_urls(&raw_text))
}

#[uniffi::export]
pub fn uniffi_analyze_email_headers(raw_headers: String) -> String {
    json_ok(mailx_runtime::skeleton::analyze_email_headers(&raw_headers))
}

#[uniffi::export]
pub fn uniffi_generate_contextual_aliases(
    service_input: String,
    domain: String,
    count: i32,
) -> String {
    let aliases = mailx_runtime::skeleton::generate_contextual_aliases(
        &service_input,
        &domain,
        count.max(0) as usize,
    );
    json_ok(aliases)
}

#[uniffi::export]
pub fn uniffi_audit_alias_hygiene(aliases_json: String) -> String {
    json_ok(mailx_runtime::skeleton::audit_alias_hygiene(&aliases_json))
}

#[uniffi::export]
pub fn uniffi_inspect_openpgp_key(armored_key: String) -> String {
    json_ok(mailx_runtime::skeleton::inspect_openpgp_key_armor(
        &armored_key,
    ))
}

#[uniffi::export]
pub fn uniffi_evaluate_phishing_risk(
    from_header: String,
    reply_to_header: String,
    return_path_header: String,
) -> String {
    let report = mailx_runtime::skeleton::evaluate_phishing_and_domain_risk(
        &from_header,
        &reply_to_header,
        &return_path_header,
    );
    json_ok(report)
}

#[uniffi::export]
pub fn uniffi_simulate_rule(rule_json: String, email_context_json: String) -> String {
    match mailx_runtime::skeleton::simulate_rule_evaluation(&rule_json, &email_context_json) {
        Ok(res) => json_ok(res),
        Err(error) => json_err(error),
    }
}

#[uniffi::export]
pub fn uniffi_analyze_mta_bounce(
    log_type: String,
    status_text: String,
    message_text: String,
    remote_mta: String,
    raw_bounce: String,
) -> String {
    let report = mailx_runtime::skeleton::analyze_mta_bounce_log(
        &log_type,
        &status_text,
        &message_text,
        &remote_mta,
        &raw_bounce,
    );
    json_ok(report)
}

#[uniffi::export]
pub fn uniffi_generate_totp_token(base32_secret: String, unix_seconds: i64) -> String {
    let secs = unix_seconds.max(0) as u64;
    match mailx_runtime::skeleton::generate_totp_token(&base32_secret, secs) {
        Ok(res) => json_ok(res),
        Err(error) => json_err(error),
    }
}

#[uniffi::export]
pub fn uniffi_generate_passphrase_and_candidate(
    format: String,
    domain: String,
    word_count: i32,
) -> String {
    let res = mailx_runtime::skeleton::generate_passphrase_and_candidate(
        &format,
        &domain,
        word_count.max(0) as usize,
    );
    json_ok(res)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;
    use std::sync::atomic::{AtomicUsize, Ordering};

    #[derive(Default)]
    struct MemoryStore {
        entries: Mutex<HashMap<String, Vec<u8>>>,
        calls: AtomicUsize,
        fail_with: Mutex<Option<SecureStorePortError>>,
        reenter: Mutex<Option<Arc<MailxCore>>>,
    }

    impl MemoryStore {
        fn begin_call(&self) -> Result<(), SecureStorePortError> {
            self.calls.fetch_add(1, Ordering::SeqCst);
            if let Some(core) = self.reenter.lock().unwrap().take() {
                core.attach_secure_store(Arc::new(MemoryStore::default()));
            }
            match self.fail_with.lock().unwrap().clone() {
                Some(error) => Err(error),
                None => Ok(()),
            }
        }
    }

    impl SecureStorePort for MemoryStore {
        fn get(&self, key: String) -> Result<Option<Vec<u8>>, SecureStorePortError> {
            self.begin_call()?;
            Ok(self.entries.lock().unwrap().get(&key).cloned())
        }

        fn put(&self, key: String, value: Vec<u8>) -> Result<(), SecureStorePortError> {
            self.begin_call()?;
            self.entries.lock().unwrap().insert(key, value);
            Ok(())
        }

        fn delete(&self, key: String) -> Result<(), SecureStorePortError> {
            self.begin_call()?;
            self.entries.lock().unwrap().remove(&key);
            Ok(())
        }
    }

    #[test]
    fn typed_api_preserves_state_between_calls() {
        let core = MailxCore::new();
        let authenticated = core.dispatch(AppEvent::Authenticate).unwrap();
        assert!(authenticated.authenticated);
        assert_eq!(authenticated.revision, 1);

        let aliases = core
            .dispatch(AppEvent::Navigate {
                route: Route::Aliases,
            })
            .unwrap();
        assert_eq!(aliases.route, Route::Aliases);
        assert_eq!(aliases.revision, 2);
        assert!(aliases.can_go_back);
        assert_eq!(core.state(), aliases);
    }

    #[test]
    fn rejected_events_surface_typed_errors_and_keep_state() {
        let core = MailxCore::new();
        assert_eq!(
            core.dispatch(AppEvent::Navigate {
                route: Route::Aliases
            }),
            Err(MailxCoreError::AuthenticationRequired)
        );
        assert_eq!(
            core.dispatch(AppEvent::Back),
            Err(MailxCoreError::NothingToGoBack)
        );
        assert_eq!(core.state().revision, 0);
    }

    #[test]
    fn back_unwinds_sheet_then_route_through_the_boundary() {
        let core = MailxCore::new();
        core.dispatch(AppEvent::Authenticate).unwrap();
        core.dispatch(AppEvent::Navigate {
            route: Route::Settings,
        })
        .unwrap();
        core.dispatch(AppEvent::OpenSheet {
            sheet: Sheet::Account,
        })
        .unwrap();

        let without_sheet = core.dispatch(AppEvent::Back).unwrap();
        assert_eq!(without_sheet.sheet, None);
        assert_eq!(without_sheet.route, Route::Settings);

        let at_home = core.dispatch(AppEvent::Back).unwrap();
        assert_eq!(at_home.route, Route::Home);
        assert!(!at_home.can_go_back);
    }

    #[test]
    fn mirror_enums_round_trip_every_variant() {
        for route in shell::Route::ALL {
            assert_eq!(shell::Route::from(Route::from(route)), route);
        }
        for sheet in shell::Sheet::ALL {
            assert_eq!(shell::Sheet::from(Sheet::from(sheet)), sheet);
        }
    }

    #[test]
    fn mirror_enums_keep_variant_names_in_sync() {
        for route in shell::Route::ALL {
            assert_eq!(format!("{route:?}"), format!("{:?}", Route::from(route)));
        }
        for sheet in shell::Sheet::ALL {
            assert_eq!(format!("{sheet:?}"), format!("{:?}", Sheet::from(sheet)));
        }
    }

    #[test]
    fn events_map_to_the_matching_runtime_events() {
        assert_eq!(
            shell::AppEvent::from(AppEvent::Navigate {
                route: Route::Rules
            }),
            shell::AppEvent::Navigate(shell::Route::Rules)
        );
        assert_eq!(
            shell::AppEvent::from(AppEvent::OpenSheet {
                sheet: Sheet::PrivacyLab
            }),
            shell::AppEvent::OpenSheet(shell::Sheet::PrivacyLab)
        );
        assert_eq!(shell::AppEvent::from(AppEvent::Back), shell::AppEvent::Back);
        assert_eq!(
            shell::AppEvent::from(AppEvent::SignOut),
            shell::AppEvent::SignOut
        );
    }

    #[test]
    fn secure_store_requires_an_attached_adapter() {
        let core = MailxCore::new();
        assert_eq!(
            core.secure_store_get("auth.token".into()),
            Err(SecureStorePortError::NotAttached)
        );
        assert_eq!(
            core.secure_store_put("auth.token".into(), vec![1]),
            Err(SecureStorePortError::NotAttached)
        );
        assert_eq!(
            core.secure_store_delete("auth.token".into()),
            Err(SecureStorePortError::NotAttached)
        );
    }

    #[test]
    fn secure_store_round_trips_through_the_attached_adapter() {
        let core = MailxCore::new();
        core.attach_secure_store(Arc::new(MemoryStore::default()));

        assert_eq!(core.secure_store_get("auth.token".into()), Ok(None));
        core.secure_store_put("auth.token".into(), vec![9, 8, 7])
            .unwrap();
        assert_eq!(
            core.secure_store_get("auth.token".into()),
            Ok(Some(vec![9, 8, 7]))
        );
        core.secure_store_delete("auth.token".into()).unwrap();
        assert_eq!(core.secure_store_get("auth.token".into()), Ok(None));
    }

    #[test]
    fn invalid_keys_never_reach_the_adapter() {
        let store = Arc::new(MemoryStore::default());
        let core = MailxCore::new();
        core.attach_secure_store(store.clone());

        for key in ["", "../token", "a/b", "white space", &"k".repeat(129)] {
            assert_eq!(
                core.secure_store_get(key.to_string()),
                Err(SecureStorePortError::InvalidKey)
            );
            assert_eq!(
                core.secure_store_put(key.to_string(), vec![1]),
                Err(SecureStorePortError::InvalidKey)
            );
            assert_eq!(
                core.secure_store_delete(key.to_string()),
                Err(SecureStorePortError::InvalidKey)
            );
        }
        assert_eq!(store.calls.load(Ordering::SeqCst), 0);
    }

    #[test]
    fn adapter_failures_are_passed_through_unchanged() {
        let store = Arc::new(MemoryStore::default());
        *store.fail_with.lock().unwrap() = Some(SecureStorePortError::Backend {
            detail: "keystore locked".into(),
        });
        let core = MailxCore::new();
        core.attach_secure_store(store);

        assert_eq!(
            core.secure_store_get("auth.token".into()),
            Err(SecureStorePortError::Backend {
                detail: "keystore locked".into()
            })
        );
    }

    #[test]
    fn adapter_may_reenter_the_core_without_deadlock() {
        let core = MailxCore::new();
        let store = Arc::new(MemoryStore::default());
        *store.reenter.lock().unwrap() = Some(core.clone());
        core.attach_secure_store(store.clone());

        assert_eq!(core.secure_store_get("auth.token".into()), Ok(None));
        assert_eq!(store.calls.load(Ordering::SeqCst), 1);
    }

    #[test]
    fn session_save_restore_and_clear_round_trip_through_secure_store() {
        let store = Arc::new(MemoryStore::default());
        let core = MailxCore::new();
        core.attach_secure_store(store.clone());

        let saved = core
            .save_session(
                "https://mailx.net".into(),
                "ak_test".into(),
                "tok_initial".into(),
                "user@example.com".into(),
            )
            .unwrap();
        assert!(saved.authenticated);
        assert!(!saved.is_web_session);
        assert_eq!(saved.base_url, DEFAULT_API_BASE_URL);
        assert_eq!(saved.account_email, "user@example.com");
        assert!(core.state().authenticated);

        let restarted = MailxCore::new();
        restarted.attach_secure_store(store);
        let restored = restarted.restore_session().unwrap();
        assert_eq!(restored, saved);
        assert!(restarted.state().authenticated);

        let cleared = restarted.clear_session().unwrap();
        assert!(!cleared.authenticated);
        assert!(!restarted.state().authenticated);
        assert_eq!(
            restarted.secure_store_get(KEY_BEARER_TOKEN.into()),
            Ok(None)
        );
    }

    #[test]
    fn session_token_rotation_prevents_stale_token_overwrite() {
        let store = Arc::new(MemoryStore::default());
        let core = MailxCore::new();
        core.attach_secure_store(store);

        core.save_session(
            "https://api.mailx.net".into(),
            String::new(),
            "cookie:sess_1".into(),
            "web@example.com".into(),
        )
        .unwrap();
        assert!(core.session_snapshot().is_web_session);

        assert_eq!(
            core.rotate_session_token("cookie:sess_1".into(), "cookie:sess_2".into()),
            Ok(true)
        );
        assert_eq!(
            core.rotate_session_token("cookie:sess_1".into(), "cookie:stale".into()),
            Ok(false)
        );
        assert_eq!(
            core.secure_store_get(KEY_BEARER_TOKEN.into()),
            Ok(Some(b"cookie:sess_2".to_vec()))
        );
    }

    #[test]
    fn session_operations_require_attached_secure_store() {
        let core = MailxCore::new();
        assert_eq!(
            core.restore_session(),
            Err(SecureStorePortError::NotAttached)
        );
        assert_eq!(core.clear_session(), Err(SecureStorePortError::NotAttached));
    }

    #[test]
    fn api_request_requires_active_session() {
        let core = MailxCore::new();
        let res = core.api_request("GET".into(), "/v1/user".into(), String::new());
        assert!(!res.ok);
        assert!(res.error.is_some());
    }

    #[test]
    fn uniffi_local_helpers_return_json_envelopes() {
        let info: serde_json::Value = serde_json::from_str(&uniffi_get_skeleton_info()).unwrap();
        assert_eq!(info["ok"], true);
        let nato: serde_json::Value =
            serde_json::from_str(&uniffi_encode_nato_phonetic("a@b.com".into())).unwrap();
        assert_eq!(nato["ok"], true);
    }

    #[test]
    fn concurrent_token_rotation_only_succeeds_once() {
        use std::sync::Barrier;

        let store = Arc::new(MemoryStore::default());
        let core = MailxCore::new();
        core.attach_secure_store(store);
        core.save_session(
            "https://api.mailx.net".into(),
            "ak_key".into(),
            "tok_initial".into(),
            "user@example.com".into(),
        )
        .unwrap();

        let barrier = Arc::new(Barrier::new(8));
        let handles: Vec<_> = (0..8)
            .map(|idx| {
                let c = core.clone();
                let b = barrier.clone();
                std::thread::spawn(move || {
                    b.wait();
                    let next = format!("tok_winner_{idx}");
                    let rotated = c
                        .rotate_session_token("tok_initial".into(), next.clone())
                        .unwrap();
                    (rotated, next)
                })
            })
            .collect();

        let mut winners = Vec::new();
        for h in handles {
            let (rotated, token) = h.join().unwrap();
            if rotated {
                winners.push(token);
            }
        }
        assert_eq!(winners.len(), 1);
        assert_eq!(
            core.secure_store_get(KEY_BEARER_TOKEN.into()),
            Ok(Some(winners[0].as_bytes().to_vec()))
        );
    }

    #[test]
    fn uniffi_logout_session_clears_state_and_store() {
        let store = Arc::new(MemoryStore::default());
        let core = MailxCore::new();
        core.attach_secure_store(store);
        core.save_session(
            "http://127.0.0.1:1".into(),
            String::new(),
            "cookie:sess".into(),
            "me@example.com".into(),
        )
        .unwrap();
        let res = core.logout_session();
        assert!(res.ok);
        assert!(!core.session_snapshot().authenticated);
        assert!(!core.state().authenticated);
        assert_eq!(core.secure_store_get(KEY_BEARER_TOKEN.into()), Ok(None));
    }

    #[test]
    fn uniffi_rotate_session_token_rejects_empty_and_identical() {
        let store = Arc::new(MemoryStore::default());
        let core = MailxCore::new();
        core.attach_secure_store(store);
        core.save_session(
            "https://api.mailx.net".into(),
            "ak".into(),
            "tok_1".into(),
            "u@example.com".into(),
        )
        .unwrap();
        let blank = core.rotate_session_token("tok_1".into(), "   ".into());
        let same = core.rotate_session_token("tok_1".into(), "tok_1".into());
        assert_eq!(blank, Ok(false));
        assert_eq!(same, Ok(false));
    }

    #[test]
    fn uniffi_local_helpers_handle_invalid_inputs_safely() {
        let bad_key: serde_json::Value =
            serde_json::from_str(&uniffi_validate_access_key("short".into())).unwrap();
        assert_eq!(bad_key["ok"], false);

        let bad_totp: serde_json::Value =
            serde_json::from_str(&uniffi_generate_totp_token("!!!".into(), 1000)).unwrap();
        assert_eq!(bad_totp["ok"], false);

        let bad_watch: serde_json::Value = serde_json::from_str(&uniffi_detect_alias_watch_events(
            "not-json".into(),
            "[]".into(),
            "[]".into(),
        ))
        .unwrap();
        assert_eq!(bad_watch["ok"], false);

        let bad_csv: serde_json::Value =
            serde_json::from_str(&uniffi_format_aliases_csv("not-json".into())).unwrap();
        assert_eq!(bad_csv["ok"], false);
    }

    #[test]
    fn uniffi_local_helpers_valid_round_trips() {
        let valid_key = concat!(
            "550e8400-e29b-41d4-a716-446655440000.",
            "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6"
        );
        let key_res: serde_json::Value =
            serde_json::from_str(&uniffi_validate_access_key(valid_key.into())).unwrap();
        assert_eq!(key_res["ok"], true);

        let suffix: serde_json::Value =
            serde_json::from_str(&uniffi_generate_random_suffix(8)).unwrap();
        assert_eq!(suffix["ok"], true);
        assert_eq!(suffix["data"].as_str().unwrap().len(), 8);

        let sanitized: serde_json::Value = serde_json::from_str(&uniffi_sanitize_tracking_urls(
            "https://example.com/?utm_source=test&id=42".into(),
        ))
        .unwrap();
        assert_eq!(sanitized["ok"], true);

        let totp: serde_json::Value =
            serde_json::from_str(&uniffi_generate_totp_token("JBSWY3DPEHPK3PXP".into(), 59))
                .unwrap();
        assert_eq!(totp["ok"], true);
    }
}
