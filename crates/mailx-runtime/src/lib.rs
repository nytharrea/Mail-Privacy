pub mod app_shell;
pub mod core;
pub mod skeleton;
pub use skeleton::*;

pub use app_shell::{AppError, AppEvent, AppState, Route, Sheet};
pub use core::MailxCore;
