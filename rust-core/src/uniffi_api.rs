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

#[derive(Clone)]
struct ActiveSession {
    base_url: String,
    access_key: Option<SecretString>,
    bearer_token: SecretString,
    account_email: String,
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
    let trimmed = raw.trim().trim_end_matches('/');
    match trimmed.to_ascii_lowercase().as_str() {
        "" | "https://mailx.net" | "https://www.mailx.net" | "https://app.mailx.net" => {
            DEFAULT_API_BASE_URL.to_string()
        }
        _ => trimmed.to_string(),
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
}
