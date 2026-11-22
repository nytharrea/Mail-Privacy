# Project status

Last reviewed: 2026-11-22

This is the single current-state summary. Dated working logs are archived in [history/](history/); decisions are in [adr/](adr/).

## Implemented

### Rust core

- **Randomness.** Alias suffixes, word/passphrase local parts and UUID local parts come from the OS CSPRNG with rejection sampling; entropy figures are computed from the real list sizes ([ADR 0001](adr/0001-os-csprng-for-all-randomness.md)).
- **TOTP.** RFC 6238 on RustCrypto `hmac`/`sha1` with zeroized key material, checked against the RFC 6238 and RFC 2202 vectors.
- **Secrets.** `SecretString` zeroizes on drop and compares in constant time. The unused `SecureStore` trait was removed; key validation remains for the secure-store port.
- **Service client.** Transport abstraction, credentials only to the host the user entered, HTTPS except exact loopback hosts, same-origin redirects only, percent-encoded path segments, timeouts and a response size cap ([ADR 0002](adr/0002-network-host-and-credential-policy.md)).
- **Typed app shell.** Pure state machine for route, sheet, authentication and Back, owned by Rust and exposed through UniFFI ([ADR 0003](adr/0003-typed-rust-core-and-uniffi-boundary.md), [UNIFFI.md](UNIFFI.md)). The JSON reducer and its JNI export were removed.

### Android

- `AppShell.kt` is the only Kotlin entry to the typed core. `MailxViewModel` mirrors Rust's route and sheet into its flows and exposes `canGoBack`; system Back calls `navigateBack()` which dispatches `Back` to Rust.
- Encrypted storage uses Tink AES-256-GCM wrapped by a hardware-backed Android Keystore master key (StrongBox preferred, TEE fallback, software keys rejected) stored in an app-private DataStore file.
- `QuickAliasTileService` (Phase 1 / Increment 5) reads durable snapshots asynchronously, persists refreshed tokens via `writeValuesAndPersist`, separates `CancellationException` from operational errors, and resets tile state in `finally`.
- The recents thumbnail is hidden on Android 13+ (`setRecentsScreenshotEnabled(false)`).
- ProGuard keeps the generated UniFFI package and the JNA members it reaches by reflection.

### Build, CI and release ([ADR 0004](adr/0004-reproducible-toolchain-and-ci.md))

- Rust release pinned in `rust-toolchain.toml`; `cargo fmt --check`; Clippy `-D warnings` for all features and no default features; workspace tests. The release workflow is gated by these checks.
- `Cargo.lock` is committed and validated with `cargo metadata --locked --format-version 1`; `rust-checks.yml` prints an explicit confirmation line when `--locked` is enabled. The manual **Update Cargo.lock** workflow uses `git status --porcelain -- Cargo.lock` so newly generated or modified lockfiles are committed.
- Bindgen version follows the locked `uniffi` crate and is cached.
- Debug CI builds `arm64-v8a` and `x86_64`; Release CI builds all 4 ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) plus a universal APK, verifies 16 KB ELF alignment for native libraries and packaged APKs, and generates `SHA256SUMS.txt`.
- Dependabot, weekly advisory `cargo audit`, and a manual live-account smoke workflow are configured.

## Verification performed, and its limits

| Area | What was done | What it does not prove |
|---|---|---|
| Rust | GitHub Actions (`rust-checks.yml`) green with `--locked`: `cargo fmt --check`, Clippy `-D warnings` (all features and no default features), and workspace tests | Runtime behavior on a physical Android device |
| Android Debug & Release CI | GitHub Actions Debug (`arm64-v8a` + `x86_64`) and Release (4 ABIs + universal APK) builds succeeded with UniFFI Kotlin binding generation, 16 KB alignment checks, and APK signing verification. Increment 5 (`QuickAliasTileService.kt`) is pending its own CI run | Real-device StrongBox/TEE behavior, runtime JNA/UniFFI loading, and live API flows |
| Secure Storage | Durable DataStore API (`readDurableSnapshot`, `writeValuesAndPersist`) implemented and used in backup, Alias Watch, and `QuickAliasTileService` | Hardware Keystore security level (`strongbox` vs `tee`), process death, and corruption recovery on a physical device |
| Device and live account | Not yet verified against the latest release candidate | End-to-end UX, token rotation under concurrency, widgets, and Quick Settings Tile on device |

### Independent review

A separate reviewer went through the network client, the random generation and the app shell and reproduced their findings with throw-away probes. Eight defects were confirmed and fixed with tests: a backslash in the URL authority that bypassed host validation, `.` and `..` ids collapsing the request path, an authentication response without a token being accepted, unbounded JSON error messages, JSON mentioning `<body>` being mistaken for HTML, credentials and bodies appearing in `Debug` output, PUT and DELETE being turned into a GET on 301 and 302, and the emulator alias `10.0.2.2` being accepted as cleartext. Details are in [ADR 0002](adr/0002-network-host-and-credential-policy.md).

## Secure Storage migration inventory (Phase 1.3)

| Component | Status | Notes |
|---|---|---|
| `MailxBackupRepository.kt` | Migrated | Uses `readDurableSnapshot` and `writeValuesAndPersist` |
| `AliasWatchManager.kt` | Migrated (Phase 2.5) | Verified in Debug & Release CI |
| `QuickAliasTileService.kt` | Migrated (Phase 1 / Increment 5) | Verified in Debug & Release CI |
| `AliasWatchWorker.kt` | Migrated (Phase 2.6) | Verified in Debug & Release CI |
| `MainActivity.kt` | Migrated (Phase 2.8) | Verified in Debug & Release CI |
| `MailxViewModel.kt` | Migrated (Phase 2.10) | Verified in Debug & Release CI |
| `QuickAliasWidgetProvider.kt` | Migrated (Phase 2.1) | Verified in Debug & Release CI |
| `PrivacyStatsWidgetProvider.kt` | Migrated (Phase 2.2) | Verified in Debug & Release CI |
| `PinnedAliasesWidgetService.kt` | Migrated (Phase 2.3) | Verified in Debug & Release CI |
| `AliasSearchIndex.kt` | Migrated (Phase 2.4) | Verified in Debug & Release CI |
| `ReverseAliasSendActivity.kt` | Migrated (Phase 2.7) | Verified in Debug & Release CI |
| `SecurityBackupDialog.kt` | Migrated (Phase 2.9) | Verified in Debug & Release CI |
| `SecurePrefsManager.kt` | Migrated (Phase 2.11) | Verified in Debug & Release CI; Phase 3 security tests added (pending CI + device verification) |

## Next steps (Pre-release roadmap)

1. **Phase 1.1:** Run Debug and Release CI on this Phase 1 / Increment 5 archive and confirm zero regressions.
2. **Phase 2:** Complete Secure Storage async migration across widgets, background services, activities, `MailxViewModel`, and remove the legacy sync compatibility layer in `SecurePrefsManager.kt`.
3. **Phase 3:** Execute Secure Storage security and device tests (StrongBox/TEE, corruption, wrong key, logout durability, process death).
4. **Phase 4–5:** Move session, token refresh, and API orchestration into Rust and replace `jni_bridge.rs` with a single UniFFI boundary.
5. **Phase 6–11:** Network/API hardening, full test coverage, measured performance, UI finalization, release security audit, and Release Candidate sign-off.

## Deferred

| Item | Why it is not done | Needed to proceed |
|---|---|---|
| Platform trust store, certificate pinning | A wrong pin or incomplete chain locks every user out; cannot be verified offline | Device test plan and a pin-rotation strategy |
| Request cancellation | Client is blocking with timeouts | Async UniFFI gateway after the session work |
| Localization (English and Turkish) | Several hundred hard-coded strings, many outside composable scope | Batches with CI compile feedback after Phase 9 |
| Navigation 3 | No pushed destinations exist; Rust-owned routes are enough | Revisit only if detail screens appear |
| Glance widgets, Baseline Profile, screenshot tests | Need an Android build environment and a device | Post-Phase 7/8 measured work |
| LTS JDK in CI | Full build not verified on JDK 21; JDK 27 is documented as deliberate | A verified run |
| `jni` 0.22 migration | Superseded by Phase 5 goal to remove `jni_bridge.rs` in favor of UniFFI | Phase 5 completion |

## Decisions that are yours

- **License.** There is no top-level `LICENSE` file and the workspace license metadata was removed earlier because it was unverified. Choosing the project license requires a file-level provenance review; see [LICENSING_AND_ATTRIBUTION.md](LICENSING_AND_ATTRIBUTION.md). This is not something to settle in code.
- **Repository secrets and variables** for the live smoke test and for release signing ([BUILD_ENVIRONMENT.md](BUILD_ENVIRONMENT.md)).
