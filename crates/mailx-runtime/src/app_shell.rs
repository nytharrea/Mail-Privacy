//! Typed, pure application-shell state machine owned by Rust.
//! Compose renders `AppState`; every transition is decided here.

use std::fmt;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Route {
    Home,
    Aliases,
    Recipients,
    Rules,
    Settings,
}

impl Route {
    pub const START: Route = Route::Home;
    pub const ALL: [Route; 5] = [
        Route::Home,
        Route::Aliases,
        Route::Recipients,
        Route::Rules,
        Route::Settings,
    ];
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
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

impl Sheet {
    pub const ALL: [Sheet; 14] = [
        Sheet::CreateAlias,
        Sheet::FilterOptions,
        Sheet::ProfileMenu,
        Sheet::Appearance,
        Sheet::Diagnostics,
        Sheet::Account,
        Sheet::Announcements,
        Sheet::TrackersReport,
        Sheet::PrivacyLab,
        Sheet::ManageAlias,
        Sheet::WildcardGen,
        Sheet::ReverseSend,
        Sheet::ConfirmDelete,
        Sheet::ConfirmLogout,
    ];
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum AppEvent {
    Authenticate,
    SignOut,
    Navigate(Route),
    OpenSheet(Sheet),
    CloseSheet,
    Back,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum AppError {
    AuthenticationRequired,
    NothingToGoBack,
}

impl fmt::Display for AppError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(match self {
            AppError::AuthenticationRequired => "Authentication is required",
            AppError::NothingToGoBack => "There is nothing to go back to",
        })
    }
}

impl std::error::Error for AppError {}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct AppState {
    pub route: Route,
    pub sheet: Option<Sheet>,
    pub authenticated: bool,
    pub revision: u64,
}

impl Default for AppState {
    fn default() -> Self {
        Self {
            route: Route::START,
            sheet: None,
            authenticated: false,
            revision: 0,
        }
    }
}

impl AppState {
    pub fn can_go_back(&self) -> bool {
        self.sheet.is_some() || self.route != Route::START
    }

    pub fn reduce(self, event: AppEvent) -> Result<AppState, AppError> {
        let next = match event {
            AppEvent::Authenticate => Self {
                authenticated: true,
                ..self
            },
            AppEvent::SignOut => Self {
                route: Route::START,
                sheet: None,
                authenticated: false,
                ..self
            },
            AppEvent::Navigate(route) => {
                self.require_authentication()?;
                Self {
                    route,
                    sheet: None,
                    ..self
                }
            }
            AppEvent::OpenSheet(sheet) => {
                self.require_authentication()?;
                Self {
                    sheet: Some(sheet),
                    ..self
                }
            }
            AppEvent::CloseSheet => Self {
                sheet: None,
                ..self
            },
            AppEvent::Back => {
                if self.sheet.is_some() {
                    Self {
                        sheet: None,
                        ..self
                    }
                } else if self.route != Route::START {
                    Self {
                        route: Route::START,
                        ..self
                    }
                } else {
                    return Err(AppError::NothingToGoBack);
                }
            }
        };
        Ok(Self {
            revision: self.revision.saturating_add(1),
            ..next
        })
    }

    fn require_authentication(&self) -> Result<(), AppError> {
        if self.authenticated {
            Ok(())
        } else {
            Err(AppError::AuthenticationRequired)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashSet;

    fn signed_in() -> AppState {
        AppState::default()
            .reduce(AppEvent::Authenticate)
            .expect("authenticate is always accepted")
    }

    fn all_events() -> Vec<AppEvent> {
        let mut events = vec![
            AppEvent::Authenticate,
            AppEvent::SignOut,
            AppEvent::CloseSheet,
            AppEvent::Back,
        ];
        events.extend(Route::ALL.into_iter().map(AppEvent::Navigate));
        events.extend(Sheet::ALL.into_iter().map(AppEvent::OpenSheet));
        events
    }

    #[test]
    fn navigation_requires_authentication() {
        let error = AppState::default()
            .reduce(AppEvent::Navigate(Route::Aliases))
            .unwrap_err();
        assert_eq!(error, AppError::AuthenticationRequired);
        assert!(error.to_string().contains("Authentication"));
    }

    #[test]
    fn opening_a_sheet_requires_authentication() {
        let error = AppState::default()
            .reduce(AppEvent::OpenSheet(Sheet::CreateAlias))
            .unwrap_err();
        assert_eq!(error, AppError::AuthenticationRequired);
    }

    #[test]
    fn authenticated_navigation_updates_route_and_closes_sheet() {
        let state = AppState {
            route: Route::Home,
            sheet: Some(Sheet::ProfileMenu),
            authenticated: true,
            revision: 4,
        };
        let next = state.reduce(AppEvent::Navigate(Route::Aliases)).unwrap();
        assert_eq!(next.route, Route::Aliases);
        assert_eq!(next.sheet, None);
        assert_eq!(next.revision, 5);
        assert!(next.authenticated);
    }

    #[test]
    fn opening_a_sheet_replaces_the_previous_one() {
        let next = signed_in()
            .reduce(AppEvent::OpenSheet(Sheet::ProfileMenu))
            .unwrap()
            .reduce(AppEvent::OpenSheet(Sheet::Account))
            .unwrap();
        assert_eq!(next.sheet, Some(Sheet::Account));
    }

    #[test]
    fn sign_out_resets_route_and_sheet() {
        let state = AppState {
            route: Route::Settings,
            sheet: Some(Sheet::Account),
            authenticated: true,
            revision: 9,
        };
        let next = state.reduce(AppEvent::SignOut).unwrap();
        assert_eq!(next.route, Route::Home);
        assert_eq!(next.sheet, None);
        assert!(!next.authenticated);
        assert_eq!(next.revision, 10);
    }

    #[test]
    fn back_closes_the_sheet_before_leaving_the_route() {
        let state = signed_in()
            .reduce(AppEvent::Navigate(Route::Rules))
            .unwrap()
            .reduce(AppEvent::OpenSheet(Sheet::FilterOptions))
            .unwrap();
        assert!(state.can_go_back());

        let after_first = state.reduce(AppEvent::Back).unwrap();
        assert_eq!(after_first.sheet, None);
        assert_eq!(after_first.route, Route::Rules);
        assert!(after_first.can_go_back());

        let after_second = after_first.reduce(AppEvent::Back).unwrap();
        assert_eq!(after_second.route, Route::Home);
        assert!(!after_second.can_go_back());
    }

    #[test]
    fn back_at_the_start_route_is_rejected() {
        let state = signed_in();
        assert_eq!(
            state.reduce(AppEvent::Back).unwrap_err(),
            AppError::NothingToGoBack
        );
        assert_eq!(
            AppState::default().reduce(AppEvent::Back).unwrap_err(),
            AppError::NothingToGoBack
        );
    }

    #[test]
    fn closing_without_a_sheet_is_accepted_and_idempotent() {
        let state = signed_in();
        let next = state.reduce(AppEvent::CloseSheet).unwrap();
        assert_eq!(next.sheet, None);
        assert_eq!(next.route, state.route);
        assert_eq!(next.revision, state.revision + 1);
    }

    #[test]
    fn revision_saturates_instead_of_overflowing() {
        let state = AppState {
            revision: u64::MAX,
            ..AppState::default()
        };
        assert_eq!(
            state.reduce(AppEvent::Authenticate).unwrap().revision,
            u64::MAX
        );
    }

    #[test]
    fn route_and_sheet_catalogs_are_complete_and_distinct() {
        assert_eq!(Route::ALL.into_iter().collect::<HashSet<_>>().len(), 5);
        assert_eq!(Sheet::ALL.into_iter().collect::<HashSet<_>>().len(), 14);
        assert_eq!(Route::ALL[0], Route::START);
    }

    #[test]
    fn reachable_states_always_satisfy_the_shell_invariants() {
        let events = all_events();
        let mut seen: HashSet<(Route, Option<Sheet>, bool)> = HashSet::new();
        let mut frontier = vec![AppState::default()];
        seen.insert((Route::Home, None, false));

        while let Some(state) = frontier.pop() {
            if !state.authenticated {
                assert_eq!(state.route, Route::START);
                assert_eq!(state.sheet, None);
                assert!(!state.can_go_back());
            }
            for event in &events {
                match state.reduce(*event) {
                    Ok(next) => {
                        assert_eq!(next.revision, state.revision + 1);
                        if seen.insert((next.route, next.sheet, next.authenticated)) {
                            frontier.push(next);
                        }
                    }
                    Err(AppError::AuthenticationRequired) => assert!(!state.authenticated),
                    Err(AppError::NothingToGoBack) => {
                        assert!(!state.can_go_back());
                        assert_eq!(*event, AppEvent::Back);
                    }
                }
            }
        }

        assert_eq!(seen.len(), 1 + 5 * 15);
    }
}
