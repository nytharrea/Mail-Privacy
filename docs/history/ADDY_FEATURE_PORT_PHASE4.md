# Addy feature port — Phase 4

This phase completes the remaining local Android integrations that were left as
platform primitives in earlier phases. The 1Password-specific configuration
has intentionally been removed; generic Android Credential Manager/passkey
support remains unchanged.

## Feature status

- Alias Watch: watch/unwatch controls are available in the alias management
  sheet; encrypted preferences hold the watch list and baseline; WorkManager
  checks all pages; notifications open the selected alias and now include a
  Stop watching action.
- Encrypted backup/restore: Settings opens a SAF-based dialog. The backup is
  password-encrypted. Session credentials are excluded unless explicitly
  selected. Restore requires confirmation; biometric lock and system search
  opt-ins are deliberately not restored as enabled.
- Biometric app lock: Settings toggle uses Android biometric/device credential
  authentication. The preference is not restored from backups.
- Alias system search and dynamic shortcuts: Settings toggle is off by default.
  Enabling it indexes active aliases locally and publishes dynamic shortcuts;
  disabling it clears the AppSearch namespace and dynamic shortcuts. Re-indexing
  now removes stale documents before adding the current alias set.
- 1Password-specific association files, setup notes and error handling are not
  included. General passkey flows are retained.

## CI evidence supplied with this phase

The uploaded 2026-11-19 workflow log shows:
- Rust JNI release compilation completed for arm64-v8a, armeabi-v7a, x86_64
  and x86.
- `:app:assembleRelease` completed with `BUILD SUCCESSFUL`.
- APK alignment verification succeeded.
- APK signature verification succeeded (v2 and v3).
- The release publication step reported that the `nightly` tag already exists.
  The log excerpt does not show a successful fallback upload, so publication
  should be checked separately.
- The Gradle cache reservation message is a cache warning, not a compile error.

## Validation limits

The CI log predates the Phase 4 notification action and AppSearch stale-index
cleanup changes. Those two final changes must be run through CI. A successful
release build is not a substitute for physical-device checks of notification
permission, WorkManager scheduling, biometric lock lifecycle, SAF restore, or
AppSearch visibility.
