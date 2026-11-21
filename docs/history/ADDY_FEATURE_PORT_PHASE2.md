# Addy feature port — Phase 2

## Fixed from CI logs

The uploaded GitHub Actions log reported:

`error[E0425]: cannot find function handle_detect_alias_watch_events in this scope`

The JNI export called the handler, but the handler definition was missing from
`rust-core/src/jni_bridge.rs`. The missing public handler is now defined before
the existing JNI handlers and uses the existing `JniEnvelope` response contract.

## Added in this phase

### Alias Watch runtime
- Encrypted watch-list IDs and previous baseline are stored in the existing
  `SecurePrefsManager` (Tink AES-256-GCM encrypted DataStore).
- Maximum watch list is capped at 25 IDs, matching the Addy watcher limit.
- `AliasWatchManager` exposes add/remove/query/enable operations.
- WorkManager periodic worker checks the Mailx aliases endpoint no more often
   than Android's 15-minute periodic-work minimum.
- Rust compares counters and emits events; Kotlin handles scheduling and
  Android notifications.
- Notifications open Mailx and carry the alias ID as an intent extra.
- Work is cancelled when watching is disabled or the list becomes empty.

### Encrypted backup primitive
- Added `EncryptedBackupCodec` with a versioned binary envelope.
- Uses a random 16-byte salt, random 12-byte nonce, PBKDF2-HMAC-SHA-256
  (310,000 iterations), and AES-256-GCM authenticated encryption.
- Header fields are authenticated as AAD; decryption validates version,
  iteration bounds and maximum input size.
- This is a codec, not yet a complete backup/restore screen or SAF workflow.
  The caller must decide which settings/data are exportable; API credentials
  should not be included by default.

## Deliberately not auto-enabled

- AppSearch/system search and dynamic launcher shortcuts can expose private
  alias addresses outside Mailx. They should only be added behind an explicit
  privacy opt-in, with a clear-index action. No system indexing is enabled in
  this phase.
- Addy API-specific subscription, username, failed-delivery and account
  notification endpoints are not imported because they are not verified as
  part of the Mailx API.

## Validation status

- Source-level checks confirm the missing Rust handler and JNI declaration/export
  are present.
- ZIP integrity is checked.
- Cargo and Gradle executables are not available in this execution environment;
  therefore no claim is made that Rust/Android compilation or device tests pass.
- CI should be rerun. If another compiler error appears, use that log to make the
  next correction rather than assuming the package builds.
