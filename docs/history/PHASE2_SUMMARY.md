# Mailx architecture migration — Phase 2

This package continues from `Mail-Privacy-RustCore-Phase0-1.zip`.

## Implemented in this phase

- Added UniFFI 0.32.2 as an optional Rust-core feature.
- Added typed `AppState`, `AppEvent`, and `MailxCoreError` API.
- Added a process-scoped UniFFI `MailxCore` object with persistent state and typed dispatch.
- Added the foreign `SecureStorePort` contract and exposed attach/get/put/delete operations on the core.
- Kept the handwritten JNI facade intact; no existing call sites were removed.
- Added UniFFI Kotlin binding generation to both Android CI and Release workflows. The generated Kotlin sources are compiled by the existing Gradle build.
- Replaced per-request ureq constructors with a shared `ureq::Agent` configured with connection/read/write timeouts.
- Updated Material 3 from 1.5.0-rc01 to the 1.5.0 stable version.

## Important source-audit correction

The uploaded project does not contain JNA or `com.sun.jna` references. Therefore no JNA dependency was added. The release workflow already checks every `.so` under `jniLibs` for ELF LOAD alignment.

## Still pending (next migration increments)

- Connect the generated UniFFI `MailxCore` and `SecureStorePort` Kotlin adapter to the Android app runtime.
- Implement the Android Keystore + Tink AEAD + DataStore adapter and versioned migration from existing encrypted preferences.
- Move login/TOTP, API path ownership, alias cache/merge and typed endpoint models into Rust.
- Replace `ureq` with an Android platform-trust-aware, cancellable transport. The shared Agent is an interim improvement, not the final TLS/cancellation solution.
- Consolidate the many ViewModel flows into a single app state and migrate Compose navigation to Navigation 3.
- Move platform-only effects (Passkey, WorkManager, notifications) out of ViewModel Context calls.
- Split oversized UI files, localize user-facing strings, add `rememberSaveable` and Compose previews, and migrate RemoteViews widgets to Glance where parity allows.
- Add fuzz/property tests for parsers and stricter Clippy lints incrementally.

## Validation

- TOML files parsed successfully.
- No local Rust/Cargo or Gradle executable is available in this environment, so compilation, binding generation, tests, and APK assembly have not been run here.
- CI is configured to build the UniFFI feature, generate Kotlin bindings from the arm64 library, and compile the generated source as part of the Android build.
- Cargo.lock has not been regenerated locally; the first CI run will resolve the new UniFFI/thiserror dependencies and should commit the updated lockfile after successful validation.
