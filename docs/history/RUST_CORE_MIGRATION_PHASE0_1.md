# Rust core migration — Phase 0 and Phase 1 foundation

This change is deliberately incremental. Existing JNI entry points remain in
place; no Kotlin call sites are migrated in this phase.

## Phase 0 — safety and CI

- Release panic strategy is `unwind`, so the existing JNI `catch_unwind`
  boundary can recover Rust panics instead of being bypassed by `abort`.
- OpenPGP packet body boundaries use checked addition before slicing. A
  regression test covers an oversized packet length.
- Rust formatting is a check (`cargo fmt --all -- --check`) rather than a
  mutating CI step.
- CI and Release both use JDK 21.
- Release alignment verification examines every `.so` under `jniLibs`, not
  only `libmailx_core.so`.

## Phase 1 — typed core and storage port

- `mailx_runtime::MailxCore` owns a persistent app-shell state behind a mutex.
  `state_snapshot` reads it and `dispatch` atomically applies reducer events.
- `mailx_security::secure_store::SecureStore` defines the platform storage
  boundary and returns `SecretString` for secret reads.
- Android is expected to implement this port with Android Keystore-backed
  authenticated encryption. Rust does not persist credentials itself.

## Deliberately not migrated yet

- The current JNI facade and JSON contracts are retained for compatibility.
- `MailxCore` is UniFFI-ready in shape, but generated UniFFI bindings are not
  included in this phase. Add the UniFFI dependency, derive/export annotations,
  Kotlin bindings, and CI generation in a dedicated follow-up after the Rust
  API surface is stable. This avoids checking in an unverified generated API
  or a stale Cargo.lock.
- No Kotlin `SecureStore` adapter is wired yet; this phase only establishes the
  Rust port contract.

## Validation status

The source tree was inspected and edited, but this execution environment does
not have `rustc`, `cargo`, or Gradle installed. Run the repository CI before
merging. In particular, confirm that Android NDK targets support the selected
`panic = "unwind"` release profile and that all native ELF LOAD segments meet
16 KB alignment.
