# ADR 0003: Typed Rust app shell behind a UniFFI boundary

Status: accepted, 2026-11-20

## Context

The Rust-owned navigation state crossed the JNI boundary as JSON strings (`{"type":"navigate","route":"aliases"}`), and the Kotlin ViewModel kept parallel state that could drift: some paths changed the Kotlin tab or sheet without telling Rust, and the sign-out path did not reset the Kotlin tab. A typed UniFFI object existed but nothing used it.

## Decision

- `mailx-runtime::app_shell` holds a pure, dependency-free state machine with typed `Route`, `Sheet`, `AppEvent`, `AppState` and `AppError`. `MailxCore` wraps it in a poison-tolerant mutex.
- `rust-core/src/uniffi_api.rs` mirrors those types for UniFFI. UniFFI derives must live in the crate that calls `setup_scaffolding!`, so the runtime stays free of FFI dependencies and exhaustive `From` conversions keep the two sides in sync.
- Rust is authoritative for route, open sheet, authentication and back behavior. Sheet payloads (for example the alias being managed) stay in Kotlin because they are presentation data.
- Kotlin reaches the object only through `AppShell`, which maps `MainNavTab`/`ActiveSheet` to the generated types. The ViewModel mirrors every accepted state into its flows; system Back calls `navigateBack()`, which dispatches `Back` to Rust.
- The JSON reducer and its JNI export were removed instead of kept as a second path.
- Compose Navigation 3 was not adopted: the app has five tabs and sheets, no pushed destinations, so Rust-owned routes plus `when(route)` is smaller and keeps navigation logic testable in Rust. Revisit if detail screens appear.

## Consequences

- Illegal transitions are impossible to express (no string routes) and are covered by an exhaustive reachability test that checks the invariant "unauthenticated implies Home and no sheet" over all reachable states.
- The generated Kotlin package is `net.mailx.android.core.uniffi` (from `rust-core/uniffi.toml`); bindings are generated in CI, ignored by Git and kept from R8 by ProGuard rules.
- Adding a route or sheet means editing the runtime enum, the UniFFI mirror and the Kotlin mapping; the compiler rejects an incomplete change.
- The first runtime use of UniFFI/JNA happens at ViewModel creation. The optional emulator launch test in CI fails on JNA or UniFFI loading errors.
