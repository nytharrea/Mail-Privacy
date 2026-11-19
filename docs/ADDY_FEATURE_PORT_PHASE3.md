# Mailx Android — Addy feature port, phase 3

## Changes

- Alias Watch: Watch/unwatch switch in Alias Inspector, Android 13+ notification permission request, background pagination for watched aliases, no false notifications on first baseline, shortcut/notification deep links.
- Backup & Restore: encrypted `.mailxbkp` file export/import through Android Storage Access Framework, optional inclusion of sign-in credentials, explicit restore confirmation, size limits, authenticated encryption, settings-only by default.
- App lock: opt-in biometric/device credential gate when reopening Mailx. User must authenticate to enable/disable. Biometric/device credentials are never stored in backups.
- System Search: off by default; explicit settings toggle enables AppSearch index on Android 12+ and launcher dynamic shortcuts; disabling or signing out clears dynamic shortcuts and local index. Alias email addresses may be visible to device search/launcher when enabled.
- Passkeys: server RP ID is validated but never rewritten, error reports the Digital Asset Links requirement. A release-signature `assetlinks.json` example and deployment instructions are in `PASSKEY_1PASSWORD_FIX.md`.
- Tests: Android instrumentation test suite for encrypted backup roundtrip, incorrect password, and tampering; existing Rust alias-watch unit tests adjusted to baseline behavior.

## Limits and integration requirements

- The app **cannot** resolve 1Password's security mismatch on its own. The RP ID's website owner must deploy the matching Digital Asset Links statement. No app-side bypass is possible.
- Android WorkManager's 15-minute interval is a *minimum* and is not a real-time notification guarantee.
- Backup restores local settings and optional login credentials; it is **not** a server-side export of all aliases, rules, or recipients. Those are fetched again after sign-in. CSV export remains separate.
- AppSearch indexing covers aliases loaded by the UI. For full indexing on large accounts, a dedicated all-pages sync is recommended; partial alias lists may omit older aliases.
- Android instrumentation tests require an emulator/device and are not run in the existing release workflow.
- Local environment lacks the Android SDK/Gradle/Rust toolchain; the changes have not been compiled or tested end-to-end here. Run GitHub Actions CI before using the package as a release.
