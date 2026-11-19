# Architecture

## Current architecture

The Rust core is now split into seven Cargo crates. The Android application keeps the existing Kotlin/Jetpack Compose presentation and Android platform integration. The `mailx-core` package remains the Android-facing facade and preserves the native library name `libmailx_core.so`.

| Crate | Responsibility | Main contents |
|---|---|---|
| `mailx-domain` | Domain types and pure domain operations | models, alias engine, CSV, telemetry, tracker DB, offline portfolio audit, catalog |
| `mailx-utils` | Shared low-level helpers and secret wrapper | validation/encoding helpers, `SecretString` |
| `mailx-security` | Local security inspection and generators | phishing, MIME, PGP, DNS, RFC email, MTA, TOTP, disposable domains, wordlists, rules |
| `mailx-network` | Mailx HTTP client and transport | authentication, API requests, token handling |
| `mailx-application` | Application use cases and API action orchestration | controller and action endpoint mapping |
| `mailx-runtime` | Composition of local domain/security capabilities | skeleton runtime and JSON-facing local use cases |
| `mailx-core` | Android JNI facade / FFI boundary | stable `mailx_core` library name and JNI exports |

## Dependency direction

Dependencies are intentionally acyclic:

- `mailx-domain` has no dependency on networking, Android or JNI.
- `mailx-utils` depends on `mailx-domain` for shared data types.
- `mailx-security` depends on `mailx-utils` for shared helpers.
- `mailx-network` depends on `mailx-domain` and `mailx-utils`.
- `mailx-application` composes `mailx-domain` and `mailx-network`.
- `mailx-runtime` composes `mailx-domain`, `mailx-utils` and `mailx-security`.
- `mailx-core` is the outer facade and composes the other crates for JNI.

The facade composes the crates; domain and security crates must not depend on Android/JNI or the network client. Keep JNI glue thin and do not place business rules in it.

## Ownership rules

- Rust owns canonical business rules, domain validation, deterministic transformations, local analysis and API client behavior.
- Kotlin owns Android lifecycle, permissions, system UI, widgets, navigation and rendering.
- ViewModels adapt Rust results to UI state; they must not become a second business-logic implementation.
- JNI is a boundary only. Do not put business rules in JNI glue.
- Network calls must be cancellable from the Android lifecycle and must not run on the main thread.
- Sensitive values must not be logged. Keep credentials in the existing protected storage path and review key lifecycle before changing it.

## Storage boundary

Encrypted preferences and Android Keystore remain in the Android layer. No Rust storage crate or SQLCipher migration is claimed by this refactor. If persistent Rust-side storage is introduced later, define an explicit storage port and migration plan first.

## Offline-first boundary

Local-only analysis (URL sanitizing, header inspection, PGP parsing, TOTP preview, local rule simulation and portfolio audits) should remain usable without network access. Server-backed operations (authentication, alias CRUD, recipients, rules and delivery telemetry) must expose explicit loading, stale-data and offline/error states. Do not imply that server mutations have succeeded until the server confirms them.

## Validation

The workspace CI runs formatting, Clippy and tests for all crates/features. A separate Android CI job cross-compiles the Rust JNI library for all four configured Android ABIs and runs `assembleDebug`. A successful CI build is still not a substitute for testing installation, login, lifecycle, offline behavior and JNI calls on physical Android devices.
