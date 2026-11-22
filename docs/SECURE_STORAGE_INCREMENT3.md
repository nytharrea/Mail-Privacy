# Secure Storage Increment 3

## Changes

- Added durable suspend APIs for reading and changing the alias watch list.
- `AliasWatchManager` now exposes suspend methods and no longer uses synchronous
  watch-list getters/setters.
- `AliasWatchWorker` reads its configuration and prior baseline from one durable
  DataStore snapshot, persists refreshed tokens before continuing, and writes the
  next baseline through an awaited batch mutation.
- `StopAliasWatchReceiver` now uses `goAsync()` and a coroutine to complete the
  durable watch-list update before finishing the broadcast.
- `MainActivity` schedules alias watch work from durable storage and loads the
  selected alias watch state asynchronously.
- `ManageAliasBottomSheet` now accepts a suspend watch callback and updates its
  local state only after the storage operation succeeds.

## Scope intentionally not changed

Other consumers (including Quick Settings tile, widgets, and the ViewModel) still
use the synchronous compatibility properties. They will be migrated in separate
increments before the compatibility API is removed.

## Validation status

Static delimiter checks passed for all modified Kotlin files. Gradle compilation
was not run in this environment. The CI results from Increment 2 do not validate
this increment; run Android Debug and Release CI before installing it.
