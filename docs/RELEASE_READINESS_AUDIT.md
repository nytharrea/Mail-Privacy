# Release Readiness Audit

Audit basis: `Mail-Privacy-main(3).zip` supplied in this conversation.

## Scope and evidence

This is a source inspection of the supplied archive. It is not a successful build,
device-security test, penetration test, or release certification.

## Findings

### 1. Secure storage still has a synchronous compatibility surface

`android-app/app/src/main/java/net/mailx/android/core/SecurePrefsManager.kt`
currently exposes synchronous properties backed by an in-memory `AtomicReference`
snapshot. It initializes that snapshot using `runBlocking(Dispatchers.IO)` and
serializes writes through a process-wide `Channel` and coroutine writer.

The class also exposes explicit persistence barriers for session writes and
backup restore. This is an improvement over unawaited writes, but the overall
API is not yet a fully asynchronous DataStore/Flow design.

The class is referenced by 12 other Kotlin source files. A safe conversion must
update those call sites together, including widgets, services, workers,
activities, backup/restore, and the ViewModel. Replacing only the manager would
break callers or introduce blocking work elsewhere.

### 2. Rust and UniFFI coexist with JNI

The supplied source contains:
- `rust-core/src/uniffi_api.rs`
- `rust-core/src/secure_store_port.rs`
- `rust-core/src/jni_bridge.rs`

The JNI bridge is still present (1,254 lines in this archive), and Kotlin still
has `MailxRustBridge.kt`. UniFFI is therefore present but is not yet the sole
Android-facing boundary. Removing JNI safely requires a method-by-method
inventory and parity tests before deleting the old bridge.

### 3. Network transport has useful security controls

`crates/mailx-network/src/client.rs` shows:
- HTTPS-only URL validation.
- Automatic redirects disabled in the underlying `ureq` agent.
- Redirect handling performed by the application layer.
- Cross-origin redirects rejected so credentials are not forwarded.
- Same-origin redirects bounded by `MAX_SAME_ORIGIN_REDIRECTS`.
- Connect, read, write, and request timeouts.
- Unit tests covering cross-origin redirects, scheme downgrade, protocol-relative
  redirects, redirect loops, and credential handling.

The inspected transport does not show custom certificate pinning. It uses the
transport library's default TLS certificate validation. Pinning should not be
added automatically: it introduces certificate/key rotation and outage risks.
If required by the threat model, it needs a documented rotation and recovery
strategy first.

## Validation performed in this environment

- The Gradle wrapper was invoked with `:app:compileDebugKotlin`.
- It could not start because the wrapper attempted to download Gradle
  `9.9.1` and this environment could not resolve `services.gradle.org`.
- Therefore no new Kotlin/Android compile result is claimed.
- No Cargo executable is available in this environment, so Rust tests and
  Clippy were not rerun here.
- Previously supplied CI logs are separate evidence and do not prove that
  this archive was built in this environment.

## Recommended implementation order

1. Convert `SecurePrefsManager` consumers to suspend/Flow access, then remove
   `runBlocking`, synchronous properties, and the write queue.
2. Add tests for persistence failure, process recreation, corrupted ciphertext,
   logout durability, and backup-restore durability.
3. Complete the UniFFI API surface and migrate Kotlin callers with parity tests.
4. Remove JNI only after no production caller depends on it.
5. Run CI and physical-device tests on the exact release candidate.
