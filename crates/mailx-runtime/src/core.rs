//! Long-lived Rust application core.
//!
//! The stable stateful boundary behind the UniFFI facade. It contains no
//! Android, JNI or serialization types.

use crate::app_shell::{AppError, AppEvent, AppState};
use std::sync::{Mutex, MutexGuard, PoisonError};

pub struct MailxCore {
    state: Mutex<AppState>,
}

impl Default for MailxCore {
    fn default() -> Self {
        Self::new()
    }
}

impl MailxCore {
    pub fn new() -> Self {
        Self {
            state: Mutex::new(AppState::default()),
        }
    }

    pub fn state(&self) -> AppState {
        *self.lock()
    }

    pub fn dispatch(&self, event: AppEvent) -> Result<AppState, AppError> {
        let mut guard = self.lock();
        let next = guard.reduce(event)?;
        *guard = next;
        Ok(next)
    }

    fn lock(&self) -> MutexGuard<'_, AppState> {
        self.state.lock().unwrap_or_else(PoisonError::into_inner)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::app_shell::{Route, Sheet};
    use std::sync::Arc;

    #[test]
    fn core_keeps_state_between_events() {
        let core = MailxCore::new();
        core.dispatch(AppEvent::Authenticate).unwrap();
        let state = core.dispatch(AppEvent::Navigate(Route::Aliases)).unwrap();
        assert_eq!(state.route, Route::Aliases);
        assert_eq!(state.revision, 2);
        assert_eq!(core.state(), state);
    }

    #[test]
    fn rejected_event_does_not_mutate_state() {
        let core = MailxCore::new();
        assert_eq!(
            core.dispatch(AppEvent::Navigate(Route::Aliases)),
            Err(AppError::AuthenticationRequired)
        );
        assert_eq!(core.state(), AppState::default());
    }

    #[test]
    fn a_poisoned_lock_still_yields_the_last_committed_state() {
        let core = Arc::new(MailxCore::new());
        core.dispatch(AppEvent::Authenticate).unwrap();

        let poisoner = Arc::clone(&core);
        let _ = std::thread::spawn(move || {
            let _guard = poisoner.state.lock().unwrap();
            panic!("poison the lock");
        })
        .join();

        assert!(core.state.is_poisoned());
        assert!(core.state().authenticated);
        let next = core.dispatch(AppEvent::OpenSheet(Sheet::Account)).unwrap();
        assert_eq!(next.sheet, Some(Sheet::Account));
    }

    #[test]
    fn concurrent_dispatch_never_loses_a_revision() {
        let core = Arc::new(MailxCore::new());
        core.dispatch(AppEvent::Authenticate).unwrap();

        let workers: Vec<_> = (0..8)
            .map(|_| {
                let core = Arc::clone(&core);
                std::thread::spawn(move || {
                    for _ in 0..250 {
                        core.dispatch(AppEvent::CloseSheet).unwrap();
                    }
                })
            })
            .collect();
        for worker in workers {
            worker.join().unwrap();
        }

        assert_eq!(core.state().revision, 1 + 8 * 250);
    }
}
