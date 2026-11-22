# CI follow-up — 2026-11-22

## Cargo.lock

The supplied source archive now contains the root `Cargo.lock` (185 package
entries). The Refresh Cargo.lock logs show that one run successfully committed
it (`8a03373`), while a concurrent run created its own commit and was rejected
by GitHub with `fetch first`. The Rust workspace check log predates the
successful lockfile push, so its `cargo metadata --locked` warning is stale
relative to the later lockfile commit.

The workflow now serializes refresh runs with a GitHub Actions concurrency group
and fetches/rebases onto the latest branch before pushing. If an unrelated merge
races with the job, rebase preserves it; a true Cargo.lock conflict will fail
visibly and can be resolved by rerunning the workflow against the latest branch.

## Android

The previous Android CI failure was caused by `AliasWatchManager` calling
`getWatchedAliasIdsDurable` and `setAliasWatchedDurable` while neither API
existed in `SecurePrefsManager`. Both suspend methods are now implemented using
the durable DataStore snapshot and awaited batch write API.

## Validation status

These source changes have not been compiled in this environment. Run the
Update Cargo.lock workflow and Android Debug/Release CI on this exact archive
before treating it as validated.
