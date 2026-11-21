# Architecture

## Current architecture

The Rust core is split into seven Cargo crates: six capability crates and the `mailx-core` facade. The Android application keeps Kotlin and Jetpack Compose for presentation and for Android platform integration (lifecycle, permissions, system UI, widgets, Credential Manager). Rust owns application state, domain rules, security logic and the service client. The facade preserves the native library name `libmailx_core.so`.

| Crate | Responsibility | Main contents |
|---|---|---|
| `mailx-domain` | Domain types and pure domain operations | models, alias engine, CSV, telemetry, tracker DB, offline portfolio audit, catalog |
| `mailx-utils` | Shared low-level helpers and secret handling | validation/encoding helpers, OS-CSPRNG random helpers, path-segment encoding, `SecretString` |
| `mailx-security` | Local security inspection and generators | phishing, MIME, PGP, DNS, RFC email, MTA, TOTP (RustCrypto HMAC-SHA1), disposable domains, wordlists, rules, secure-store key validation |
| `mailx-network` | Mailx HTTP client | transport abstraction, host/credential policy, authentication, API requests, token handling |
| `mailx-application` | Application use cases and API action orchestration | controller and action endpoint mapping |
| `mailx-runtime` | Runtime state and composition of local capabilities | typed app-shell state machine (`app_shell`, `core`), skeleton runtime and JSON-facing local use cases |
| `mailx-core` | Android FFI facade | UniFFI object for the typed shell and secure-store port, JNI exports for stateless helpers and API calls |

## Dependency direction

Dependencies are intentionally acyclic:

- `mailx-domain` depends on nothing in the workspace.
- `mailx-utils` depends on `mailx-domain`.
- `mailx-security` depends on `mailx-utils`.
- `mailx-network` depends on `mailx-domain` and `mailx-utils`.
- `mailx-application` depends on `mailx-domain`, `mailx-network` and `mailx-utils`.
- `mailx-runtime` depends on `mailx-domain`, `mailx-security` and `mailx-utils`.
- `mailx-core` is the outer facade and composes every other crate.

Domain, security, runtime and network crates must not depend on Android, JNI or UniFFI. Only `mailx-core` knows about the FFI layers; keep that glue thin and do not place business rules in it.

## Boundaries between Kotlin and Rust

Two bridges exist and each has a single job:

- **UniFFI (typed, stateful).** `MailxCore` is a process-scoped object that owns the app-shell state: current route, open sheet, authentication flag and a revision counter. Kotlin sends typed `AppEvent` values and receives typed `AppState` records. Details: [UNIFFI.md](UNIFFI.md).
- **JNI (JSON, stateless).** The existing handwritten exports for API calls and local analysis helpers remain in `rust-core/src/jni_bridge.rs`. Every call returns the envelope `{ok, data, error, error_code, refreshed_token}` and is wrapped in `catch_unwind`.

New stateful behavior belongs behind the UniFFI object. Migrating the JNI calls to typed UniFFI methods is planned but not claimed complete; see [STATUS.md](STATUS.md).

## App shell

`mailx-runtime::app_shell` is a pure state machine: `AppState::reduce(event) -> Result<AppState, AppError>`.

- Routes: `Home`, `Aliases`, `Recipients`, `Rules`, `Settings`.
- Sheets: a typed kind per bottom sheet (14 kinds). Sheet payloads such as the alias being managed stay in Kotlin; Rust tracks only which kind is open.
- Navigation and sheets require authentication. Signing out resets the route to `Home` and closes any sheet.
- `Back` follows Android's fixed start-destination rule: close the open sheet first, otherwise return to `Home`, otherwise the event is rejected and the system handles Back.
- Every accepted event increments `revision`; rejected events change nothing.

Compose renders the mirrored state (`currentTab`, `activeSheet`, `canGoBack`) and forwards user intent as events. The ViewModel does not decide whether a transition is legal.

## Ownership rules

- Rust owns canonical business rules, domain validation, deterministic transformations, local analysis, shell state and API client behavior.
- Kotlin owns Android lifecycle, permissions, system UI, widgets and rendering.
- ViewModels adapt Rust results to UI state; they must not become a second business-logic implementation.
- FFI layers are boundaries only. Do not put business rules in them.
- Network calls must not run on the main thread. Calls are bounded by timeouts; cooperative cancellation is future work.
- Sensitive values must not be logged. Credentials stay in the existing protected storage path.

## Randomness, secrets and network policy

- All randomness comes from the operating-system CSPRNG ([ADR 0001](adr/0001-os-csprng-for-all-randomness.md)).
- Credentials are sent only to the host the user entered, over HTTPS, with same-origin redirects only ([ADR 0002](adr/0002-network-host-and-credential-policy.md)).
- `SecretString` zeroizes on drop and compares in constant time.

## Storage boundary

Encrypted preferences and Android Keystore remain in the Android layer. The foreign `SecureStorePort` contract exists so a Keystore-backed adapter can be attached later, but no Kotlin adapter is wired yet and no Rust storage crate or SQLCipher migration is claimed. If persistent Rust-side storage is introduced, define an explicit storage port and a migration plan first ([ADR 0005](adr/0005-deferred-platform-security-work.md)).

## Offline-first boundary

Local-only analysis (URL sanitizing, header inspection, PGP parsing, TOTP preview, local rule simulation and portfolio audits) stays usable without network access. Server-backed operations (authentication, alias CRUD, recipients, rules and delivery telemetry) expose explicit loading, stale-data and offline/error states. Do not imply that a server mutation succeeded until the server confirms it.

## Validation

The reusable `rust-checks` workflow runs `cargo fmt --check`, Clippy with `-D warnings` (all features and no default features) and the full test suite on the toolchain pinned in `rust-toolchain.toml`. The CI and release workflows both call it, and the release is gated by it. The Android jobs cross-compile the JNI/UniFFI library, verify 16 KB ELF alignment for the Rust libraries and for every library inside the built APKs, generate the Kotlin bindings and run Gradle. A passing build is still not a substitute for testing installation, login, lifecycle, offline behavior and FFI calls on a device; see [STATUS.md](STATUS.md) for the verification checklist.
