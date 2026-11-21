# ADR 0005: Deferred platform security work

Status: accepted, 2026-11-20

Some hardening items were reviewed and deliberately not changed in this delivery because they cannot be verified without a device or a real build, and a mistake would lock users out or lose stored credentials.

## Decisions

1. **Platform trust store and certificate pinning.** Needs a Kotlin trust-store provider plus JNI initialization, and a wrong pin or an incomplete chain would make every request fail with no way to recover short of an update. Revisit with a device test plan and a pin-rotation strategy.
2. **Keystore + Tink + DataStore replacing `EncryptedSharedPreferences`.** The current library is deprecated, but migrating stored credentials needs a tested one-way migration (read old store, write new store, verify, delete) on real devices, including upgrade from existing installs. The Rust `SecureStorePort` contract is in place so the adapter can be added without further Rust changes.
3. **Rust-owned session.** Credentials are still passed explicitly on each JNI call (about 66 call sites). Moving them behind a Rust session object is the right direction but touches every API call and should be done together with the typed UniFFI API migration.
4. **Request cancellation.** The client is blocking with timeouts. An async UniFFI gateway can follow once the session work lands.
5. **Localization.** The UI contains several hundred hard-coded English strings, many outside composable scope. Converting them without a compiler in the loop would risk a half-translated UI or a broken build. Plan: extract strings in small batches with CI compile feedback, English first, then Turkish.
6. **Glance widgets, Baseline Profile, screenshot tests.** Each needs an Android build environment and device verification; AGP 9 compatibility of the screenshot-test tooling was not verified.

## Consequences

Until these land, the documented guarantees are limited to what the code and tests establish: host and credential policy in Rust, honest randomness, typed shell state. Nothing in the documentation should claim pinning, hardware-backed storage migration or localization.
