# Architecture

## Current architecture

The current snapshot is a **Rust-first modular monolith**: one Cargo crate (`mailx-core`) exposes focused Rust modules, and the Android application uses Kotlin/Jetpack Compose for presentation and Android platform integration. The existing JNI bridge is the active FFI boundary.

The Rust modules already present include:

- Domain and data: `models`, `alias_engine`, `rules_engine`, `csv_engine`, `catalog`.
- Application orchestration: `skeleton`, `controller`.
- Service integration: `client`.
- Security and analysis: `secret`, `phishing_guard`, `mime_inspector`, `rfc_email`, `pgp_inspector`, `dns_security`, `mta_diagnostics`, `totp_engine`, `tracker_db`, `disposable_domains`, `offline_audit`, `telemetry`.
- Platform boundary: `jni_bridge`.

Do not describe the current source as seven independent Rust crates: that split is a target architecture, not implemented in this snapshot.

## Ownership rules

- Rust owns canonical business rules, domain validation, deterministic transformations, local analysis and API client behavior.
- Kotlin owns Android lifecycle, permissions, system UI, widgets, navigation and rendering.
- ViewModels adapt Rust results to UI state; they must not become a second business-logic implementation.
- JNI is a boundary only. Do not put business rules in JNI glue.
- Network calls must be cancellable from the Android lifecycle and must not run on the main thread.
- Sensitive values must not be logged. Keep credentials in the existing protected storage path and review key lifecycle before changing it.

## Target modularization

Split the current crate only after tests establish stable boundaries:

1. `mailx-domain`: models and pure domain rules.
2. `mailx-application`: use cases, orchestration and ports.
3. `mailx-security`: local security inspection and secret handling.
4. `mailx-network`: Mailx API client and transport.
5. `mailx-storage`: persistence abstractions and encrypted local storage.
6. `mailx-runtime`: application runtime/state coordination.
7. `mailx-ffi`: narrow Android-facing FFI surface.

Migration order: add tests around public behavior; extract pure domain modules; extract security modules; define application ports; isolate network; introduce storage behind an interface; narrow JNI/consider UniFFI only after the API contract is stable. Do not combine a crate split with a behavior rewrite in one change.

## Offline-first boundary

Local-only analysis (URL sanitizing, header inspection, PGP parsing, TOTP preview, local rule simulation and portfolio audits) should remain usable without network access. Server-backed operations (authentication, alias CRUD, recipients, rules and delivery telemetry) must expose explicit loading, stale-data and offline/error states. Do not imply that server mutations have succeeded until the server confirms them.
