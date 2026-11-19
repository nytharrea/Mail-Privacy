# Rust-first / Compose-first implementation status

This delivery includes the previous UniFFI/Rust-core groundwork, fixes the known
`AppState: Deserialize` compile error, switches CI and Release to JDK 27, commits
a Gradle Wrapper, and makes the Rust app-shell reducer state persistent across
Kotlin ViewModel navigation events.

## Implemented in source

- `AppState` derives Serde `Serialize` and `Deserialize` for the UniFFI adapter.
- UniFFI `MailxCore` and `SecureStorePort` boundaries remain available alongside
  the handwritten JNI bridge for staged migration.
- `MailxViewModel` now retains the Rust app-shell snapshot and revision. Route
  changes, sheet opening/closing, successful authentication, and logout dispatch
  events through Rust. A rejected transition no longer silently falls back to the
  requested Kotlin route.
- JNI string conversion failures no longer silently become empty strings; they
  are caught by the JNI panic boundary and returned as native failures.
- Release panic strategy remains `unwind`, matching the JNI `catch_unwind` policy.
- PGP packet body offset calculations use checked arithmetic.
- `ureq` is pinned to 2.12.1, matching the existing lockfile; the shared Agent and
  connect/read/write timeouts remain in place.
- CI and Release use Temurin JDK 27. Gradle Wrapper 9.9.1 is committed.
- Android compileSdk/targetSdk and SDK installation are aligned to API 36 / Build
  Tools 36.0.0; emulator smoke test uses API 36.
- Release validates every native `.so` for 16 KB ELF LOAD alignment and verifies
  APK alignment/signatures.

## Deliberately not marked complete

The following are still substantial application migrations, not safe one-line
configuration changes. They are not represented as finished in this archive:

1. **Full UniFFI runtime cutover:** Android does not yet instantiate `MailxCore`
   as the primary application service. Legacy JNI calls remain in the UI,
   ViewModel, widgets, and intent activities.
2. **Keystore + Tink + DataStore:** The current `EncryptedPrefsManager` still uses
   `EncryptedSharedPreferences`. No Tink-backed Kotlin `SecureStorePort` adapter
   or verified versioned data migration/rollback has been wired. The deprecated
   store is intentionally not removed to avoid losing existing credentials.
3. **Rust-owned business workflows:** Login/TOTP, alias cache/merge, settings
   synchronization, typed DTOs for every endpoint, and API orchestration are not
   all migrated into the persistent Rust core.
4. **HTTP transport:** The shared ureq Agent has timeouts, but requests are still
   blocking. Android platform CA integration, custom-CA policy, async cancellation,
   and transport trait substitution are not complete.
5. **Single Compose AppState:** Existing screen-specific StateFlows and the large
   ViewModel remain. The Rust app-shell state is now persistent, but it is not the
   complete screen model.
6. **Navigation 3:** The current screen navigation still uses the existing tab and
   animated-content implementation. Navigation 3 has not been substituted.
7. **Compose decomposition/localization:** Large screen/sheet files, hard-coded
   UI strings, previews, and saveable-state coverage still need behavior-preserving
   refactoring.
8. **Glance:** Existing RemoteViews widgets have not yet been migrated. Quick
   Settings Tile remains a platform component.
9. **Rust formatting:** The source archive's last CI log reports rustfmt differences
   in multiple workspace files. This environment has no Rust toolchain, so those
   diffs could not be safely applied or revalidated here. CI's format check remains
   strict rather than being weakened.

## Validation boundary

The editing environment does not contain Cargo/rustfmt, Gradle, Android SDK, or NDK,
and outbound package downloads are unavailable. ZIP integrity and source-level
changes can be checked here, but Rust compilation, Kotlin compilation, instrumentation
tests, APK signing, and runtime migration tests must pass in GitHub Actions before
this build should be treated as release-ready.
