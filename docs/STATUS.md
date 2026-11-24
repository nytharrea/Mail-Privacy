# Project status

Last reviewed: 2026-11-24

This is the single current-state summary for **Mailx v0.5.0-rc1** (unofficial Android client for the open-source `mailx` email-alias server). Dated working logs are archived in [history/](history/); architectural decisions are in [adr/](adr/).

## Implemented & CI-verified (Phases 1–10)

### Rust core
- **Randomness.** OS CSPRNG with rejection sampling across alias suffixes, word/passphrase and UUID local parts ([ADR 0001](adr/0001-os-csprng-for-all-randomness.md)).
- **TOTP & local security.** RFC 6238 on RustCrypto `hmac`/`sha1` with zeroized key material; phishing, MIME, PGP, DNS, RFC email, MTA, and rule evaluation modules.
- **Secrets & session ownership.** `SecretString` zeroizes on drop and compares in constant time. `MailxCore` owns active session state, `SecureStorePort` persistence, and atomic token rotation.
- **Service client.** Transport abstraction, credentials restricted to the user-entered host, HTTPS-only except exact loopback hosts, same-origin redirects only, public-endpoint credential stripping, percent-encoded path segments, bounded timeouts, strict 10 MiB response-size rejection, HTTP status error-code propagation, and single-flight 401 token refresh ([ADR 0002](adr/0002-network-host-and-credential-policy.md), [ADR 0006](adr/0006-certificate-pinning-and-rotation-policy.md)).
- **Single UniFFI boundary.** Legacy handwritten JNI (`jni_bridge.rs`, `external fun`, `System.loadLibrary`, and the `jni` crate) has been completely removed. `MailxCore` and all local helpers are exposed exclusively through `uniffi 0.32.2` ([ADR 0003](adr/0003-typed-rust-core-and-uniffi-boundary.md), [UNIFFI.md](UNIFFI.md)).

### Android
- **Single FFI entry & pure async storage.** `AppShell.kt` and `AndroidSecureStoreAdapter.kt` wire `MailxCore` to `SecurePrefsManager` (pure DataStore `Flow` + `suspend`, Tink AES-256-GCM, hardware-backed Android Keystore preferring StrongBox with TEE fallback and software-key rejection). All legacy synchronous storage wrappers (`runBlocking` getters/setters) have been removed.
- **Performance.** Single-pass metrics in `HomeDashboardTab` and `RecipientsTab`, `@Immutable` UI state models including `ActiveSheet`, and `key` + `contentType` across all `LazyColumn` lists.
- **UI & theme.** **Graphite Lime** is the default matte dark/light theme with **Night Fjord** selectable in Settings (`Appearance & Display`), `MotionScheme.standard()` as the default motion scheme, `LocalReducedMotion` support, and 100% English UI copy.
- **Release security.** `android:allowBackup="false"`, `android:fullBackupContent="false"`, `android:usesCleartextTraffic="false"`, full `data_extraction_rules.xml` exclusions for cloud backup and device transfer, `QuickAliasWidgetProvider` active widget ID validation, `setRecentsScreenshotEnabled(false)` on Android 13+, and ProGuard log stripping (`Log.v/d/i`). Details: [RELEASE_SECURITY_AUDIT.md](RELEASE_SECURITY_AUDIT.md).

### Build, CI and release ([ADR 0004](adr/0004-reproducible-toolchain-and-ci.md))
- Rust toolchain pinned in `rust-toolchain.toml`; `Cargo.lock` verified with `--locked`; `cargo fmt --check`, Clippy `-D warnings` (`--all-features` and `--no-default-features`), and **201/201** Rust workspace tests green.
- Android Debug CI (`arm64-v8a`, `x86_64`) runs **12/12** JVM unit tests (`SecureStorageSecurityUnitTest`, `AndroidSecureStoreAdapterUnitTest`), compiles `assembleDebugAndroidTest`, and verifies 16 KB ELF alignment.
- Android Release CI builds all 4 ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) plus a universal signed APK (Signature Scheme v2 + v3), verifies 16 KB ELF LOAD alignment and 16 KB `zipalign`, and generates `SHA256SUMS.txt`.

## Verification performed, and its limits

| Area | Verified in CI | Still requires physical device / owner action |
|---|---|---|
| Rust workspace | `cargo fmt --check`, Clippy `-D warnings` (all/no-default features), 201/201 tests (`--locked`) | Live-account runtime behavior on device |
| Android Debug & JVM tests | 12/12 JVM security & adapter unit tests, `assembleDebugAndroidTest`, Debug APKs, 16 KB alignment | Running `SecureStorageDeviceSecurityTest` on a physical Android device |
| Android Signed Release | 4 ABIs + universal APK, R8 minification, v2+v3 signing, 16 KB ELF & `zipalign` | Physical-device install, login, widgets, Quick Settings Tile, and process-death checks |
| Licensing & distribution | `GPL-3.0-only` added in top-level `LICENSE` and `Cargo.toml`; unofficial client notice in [LICENSING_AND_ATTRIBUTION.md](LICENSING_AND_ATTRIBUTION.md) | Verify third-party notices before store listing |

## Release Candidate gate (Phase 11)
Complete the physical-device smoke tests and licensing sign-off in [RELEASE_CANDIDATE_CHECKLIST.md](RELEASE_CANDIDATE_CHECKLIST.md) before publishing public binaries.

## Intentionally deferred (post-v1)
- **Certificate pinning:** Deferred until a host-scoped SPKI pin-rotation and emergency recovery runbook is verified on physical devices ([ADR 0006](adr/0006-certificate-pinning-and-rotation-policy.md)).
- **Cooperative request cancellation:** Blocking calls are bounded by timeouts; async UniFFI cancellation is post-v1.
- **Diagnostics Engine & Localization:** Explicitly deferred for post-v1 (English-only for v1).
- **Navigation 3, Glance widgets, Baseline Profiles:** Deferred until post-release measurement.
