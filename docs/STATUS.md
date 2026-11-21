# Project status

Last reviewed: 2026-11-21

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
- The recents thumbnail is hidden on Android 13+ (`setRecentsScreenshotEnabled(false)`).
- ProGuard keeps the generated UniFFI package and the JNA members it reaches by reflection.

### Build, CI and release ([ADR 0004](adr/0004-reproducible-toolchain-and-ci.md))

- Rust release pinned in `rust-toolchain.toml`; a real `cargo fmt --check`; Clippy `-D warnings` for all features and no default features; tests. The release is gated by these checks.
- `--locked` builds when `Cargo.lock` matches, otherwise a warning plus the manual **Update Cargo.lock** workflow.
- Bindgen version follows the locked `uniffi` crate and is cached.
- 16 KB alignment is verified for the Rust libraries and for every library inside the built APKs.
- Releases include `SHA256SUMS.txt`. Dependabot, weekly advisory `cargo audit`, and a manual live-account smoke workflow are configured.
- The Rust sources were formatted with the pinned rustfmt; the earlier CI step that formatted before checking had hidden about 200 unformatted files.

## Verification performed, and its limits

| Area | What was done | What it does not prove |
|---|---|---|
| Rust | Offline against stand-in crates: `cargo check`, Clippy `-D warnings` (all features and no default features), `cargo fmt --check` and 169 tests (1 ignored live test) on Rust 1.97.0. Then on GitHub Actions with the real crates (2026-11-21): the same 169 tests passed, Clippy and rustfmt were clean, `cargo ndk` built `arm64-v8a` and `x86_64` and the 16 KB alignment check passed | Runtime behavior of the cross-compiled library on a device |
| Kotlin | Parsed with the Kotlin 2.0.21 compiler: no new syntax diagnostics. `AppShell.kt` compiles with `-Werror` against hand-written stand-ins shaped like UniFFI's generated API | The stand-ins encode UniFFI naming conventions (enum entries in UPPER_SNAKE, data enums as sealed classes, package from `uniffi.toml`). The Android job has not yet reached the Kotlin compile, so the real generated names are still unconfirmed |
| Workflows | Parsed as YAML and executed on GitHub on 2026-11-21: toolchain setup, caching, Rust checks, cross-compilation and the alignment check worked. The binding step failed once and was fixed (see below) | Binding generation, Gradle build, APK checks, emulator test and signed release have not run yet |
| Device and live account | Not tested | JNA/UniFFI loading at runtime, UI behavior, and real API behavior |

### Independent review

A separate reviewer went through the network client, the random generation and the app shell and reproduced their findings with throw-away probes. Eight defects were confirmed and fixed with tests: a backslash in the URL authority that bypassed host validation, `.` and `..` ids collapsing the request path, an authentication response without a token being accepted, unbounded JSON error messages, JSON mentioning `<body>` being mistaken for HTML, credentials and bodies appearing in `Debug` output, PUT and DELETE being turned into a GET on 301 and 302, and the emulator alias `10.0.2.2` being accepted as cleartext. Details are in [ADR 0002](adr/0002-network-host-and-credential-policy.md). The reviewer found the redirect handling, the host policy, the random sampling and the app-shell invariants sound.

## First CI run (2026-11-21)

Twenty workflow logs were reviewed.

- **Rust checks** were green on the real crates in every run that did not change dependencies.
- **Android debug build and release** stopped at the step that resolves the `uniffi` version, with `StopIteration`. `cargo metadata` leaves out optional dependencies, and `uniffi` and `jni` sit behind features, so the package list did not contain `uniffi`. The step now passes `--all-features`; the cause was reproduced locally. The same step now prints the generated declarations that `AppShell.kt` depends on, so a naming mismatch is visible in the log.
- **Dependabot** opened pull requests for `jni 0.22.4` and for several Gradle and Actions updates. `jni 0.22` is a breaking API change (`JNIEnv` is split into `Env` and `EnvUnowned`; the pull request failed with 46 compile errors), so `dependabot.yml` ignores it until it is migrated on purpose. The other pull requests are ordinary version bumps to review one by one.
- **cargo audit** reported no vulnerabilities and one advisory notice: `basic-toml` is unmaintained and is pulled in by UniFFI's own tooling.
- **Release** is expected to fail at the keystore step until the signing secrets are configured; it refuses to create a fallback key.
- **Cargo.lock** was stale as predicted. The **Update Cargo.lock** workflow added 67 packages and committed the result. Zip deliveries do not contain `Cargo.lock`, so unpacking one over the repository cannot revert the refreshed lock.

## Next steps

1. Push and watch **Android debug build**. The next expected stop is the Kotlin compile of `AppShell.kt` against the generated bindings; the error text is enough to fix any naming difference.
2. Run **Mailx Android CI** manually with `run_emulator` enabled. The launch test fails on JNA or UniFFI loading errors and on any crash at startup.
3. Install the nightly APK and exercise tabs, system Back, sheets, sign-in and sign-out.
4. Optional: add the secret `MAILX_LIVE_ACCESS_KEY` and run **Live API smoke test** against a dedicated test account.

## Deferred

| Item | Why it is not done | Needed to proceed |
|---|---|---|
| Platform trust store, certificate pinning | A wrong pin or incomplete chain locks every user out; cannot be verified offline | Device test plan and a pin-rotation strategy |
| Keystore + Tink + DataStore replacing `EncryptedSharedPreferences` | Credential migration risks data loss | Tested one-way migration on real devices, including upgrades |
| Rust-owned session (credentials still passed per JNI call) | Touches about 66 call sites | Do together with typed UniFFI methods for API calls |
| Request cancellation | Client is blocking with timeouts | Async UniFFI gateway after the session work |
| Localization (English and Turkish) | Several hundred hard-coded strings, many outside composable scope; a bulk rewrite without a compiler risks a broken or half-translated UI | Batches with CI compile feedback |
| Navigation 3 | No pushed destinations exist; Rust-owned routes are enough | Revisit if detail screens appear |
| Glance widgets, Baseline Profile, screenshot tests | Need an Android build environment and a device; AGP 9 compatibility of screenshot tooling unverified | Android-capable environment |
| LTS JDK in CI | Full build not verified on JDK 21; JDK 27 is documented as deliberate | A verified run |
| Material 3 `1.5.0-rc01` pin | Kept as is | Move to the stable release when available |
| `jni` 0.22 migration | Breaking API change in the JNI crate; 46 compile errors in `jni_bridge.rs`, and the offline stand-in cannot validate the new API | A dedicated migration of `jni_bridge.rs` with CI feedback |

## Decisions that are yours

- **License.** There is no top-level `LICENSE` file and the workspace license metadata was removed earlier because it was unverified. Choosing the project license requires a file-level provenance review; see [LICENSING_AND_ATTRIBUTION.md](LICENSING_AND_ATTRIBUTION.md). This is not something to settle in code.
- **Repository secrets and variables** for the live smoke test and for release signing ([BUILD_ENVIRONMENT.md](BUILD_ENVIRONMENT.md)).
