# Secure Storage Increment 2

## Changes

- Added `SecurePrefsManager.writeValuesAndPersist`, an ordered batch-write API.
- The batch is submitted through the existing serialized DataStore writer and awaited.
- The in-memory snapshot is updated in the same queue order as the durable mutation.
- Backup restore now validates its payload first, builds one settings/session mutation,
  and waits for that mutation to persist.
- Restore no longer performs a sequence of independent synchronous property writes.
- Existing behavior is preserved when a backup omits session data: the current session
  is not overwritten. When session data is explicitly restored, all three session
  fields are included in the same batch.
- Device-local biometric enablement remains disabled after restore; alias search index
  remains opt-in and is not restored.

## Validation status

This increment is based on the CI-passing Increment 1 archive. The newly changed Kotlin
sources have not yet been compiled by Gradle in this environment. Run Android Debug and
Release CI before installing this increment. Do not treat the prior CI result as proof
that this new increment compiles.
