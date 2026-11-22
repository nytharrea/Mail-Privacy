# Increment 3 CI fix

The uploaded CI logs for Increment 3 fail during Kotlin compilation because
`AliasWatchManager` and `MainActivity` call
`SecurePrefsManager.getWatchedAliasIdsDurable()`, which was missing.

Added the missing suspend method. It first waits for pending DataStore writes
through `readDurableSnapshot()`, then parses `watched_alias_ids` from the
durable snapshot. Invalid or malformed values safely produce an empty set,
matching the existing non-throwing synchronous accessor's behavior.

## Validation

The previous CI logs establish that Rust tests, Clippy, and UniFFI generation
passed, but Android compilation failed at the missing method. This fix has not
yet been compiled locally or in CI. Run Android Debug and Release workflows.

The Cargo.lock warning also remains in the supplied logs. They do not prove
that the separate workflow fix was merged and that the generated root
`Cargo.lock` was committed.
