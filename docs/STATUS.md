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
| Rust | `cargo check`, Clippy `-D warnings` (all features and no default features), `cargo fmt --check` and 169 tests (1 ignored live test) on Rust 1.97.0 | Third-party crates were replaced by offline stand-ins (serde, serde_json, ureq, getrandom, hmac, sha1, zeroize, jni, uniffi macros, qrcode). Real dependency resolution, real UniFFI macro expansion and Android cross-compilation have only been exercised by GitHub Actions |
| Kotlin | Parsed with the Kotlin 2.0.21 compiler: no new syntax diagnostics. `AppShell.kt` compiles with `-Werror` against hand-written stand-ins shaped like UniFFI's generated API | The stand-ins encode UniFFI naming conventions (enum entries in UPPER_SNAKE, data enums as sealed classes, package from `uniffi.toml`). The first CI compile confirms them |
| Workflows | Parsed as YAML; `scripts/check-elf-alignment.sh` tested on 16 KB-aligned and 4 KB-aligned libraries and on APK, directory and empty inputs | Not executed on GitHub |
| Device and live account | Not tested | JNA/UniFFI loading at runtime, UI behavior, and real API behavior |

### Independent review

A separate reviewer went through the network client, the random generation and the app shell and reproduced their findings with throw-away probes. Eight defects were confirmed and fixed with tests: a backslash in the URL authority that bypassed host validation, `.` and `..` ids collapsing the request path, an authentication response without a token being accepted, unbounded JSON error messages, JSON mentioning `<body>` being mistaken for HTML, credentials and bodies appearing in `Debug` output, PUT and DELETE being turned into a GET on 301 and 302, and the emulator alias `10.0.2.2` being accepted as cleartext. Details are in [ADR 0002](adr/0002-network-host-and-credential-policy.md). The reviewer found the redirect handling, the host policy, the random sampling and the app-shell invariants sound.

## First-run checklist

1. Run **Update Cargo.lock** once and keep its commit; later runs then build with `--locked`.
2. Push and watch **Rust checks** and **Android debug build**. If Kotlin reports unresolved references in `AppShell.kt`, the generated names differ from the assumption above; the error text is enough to fix it.
3. Run **Mailx Android CI** manually with `run_emulator` enabled. The launch test fails on JNA or UniFFI loading errors and on any crash at startup.
4. Install the nightly APK and exercise tabs, system Back, sheets, sign-in and sign-out.
5. Optional: add the secret `MAILX_LIVE_ACCESS_KEY` and run **Live API smoke test** against a dedicated test account.

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

## Decisions that are yours

- **License.** There is no top-level `LICENSE` file and the workspace license metadata was removed earlier because it was unverified. Choosing the project license requires a file-level provenance review; see [LICENSING_AND_ATTRIBUTION.md](LICENSING_AND_ATTRIBUTION.md). This is not something to settle in code.
- **Repository secrets and variables** for the live smoke test and for release signing ([BUILD_ENVIRONMENT.md](BUILD_ENVIRONMENT.md)).
