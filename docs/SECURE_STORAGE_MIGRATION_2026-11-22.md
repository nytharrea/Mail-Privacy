# Secure Storage Migration — Increment 1

Date: 2026-11-22

## Changes

- Added `SecurePrefsManager.readDurableSnapshot()`, a suspend API that first
  waits for pending queued writes and then reads the current DataStore value.
- Migrated encrypted backup export to use this durable snapshot instead of
  reading the synchronous in-memory compatibility properties.
- Backup restore now obtains its default domain fallback from the durable
  snapshot rather than the synchronous property getter.

## Why this is a first increment

This is deliberately a narrow migration step. It removes synchronous reads
from the backup export path and ensures the exported backup reflects data
committed to DataStore after pending writes have completed.

The legacy synchronous property API and write queue still exist because many
other application components depend on them. They are not claimed to be
removed in this increment.

## Validation

- Source-level diff reviewed.
- Gradle compile attempted using `android-app/gradlew :app:compileDebugKotlin --offline`.
- Build could not start: Gradle wrapper distribution `9.9.1` was not cached and
  this environment cannot resolve `services.gradle.org`.
- No new Android build or device test is claimed.

## Next increment

Migrate one contained caller group at a time to suspend/Flow APIs, beginning
with backup restore and alias-watch operations. Once all consumers are moved,
remove synchronous properties, process snapshot, Channel writer, and
`runBlocking` initialization.
