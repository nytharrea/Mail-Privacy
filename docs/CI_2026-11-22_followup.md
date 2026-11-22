# CI follow-up — 2026-11-22

## Android Kotlin failures

The supplied CI logs report:
- `SecurePrefsManager.getWatchedAliasIdsDurable` unresolved in `AliasWatchManager` and `MainActivity`.
- `SecurePrefsManager.setAliasWatchedDurable` unresolved in `AliasWatchManager`.
- `kotlinx.coroutines.launch` unresolved in `ManageAliasBottomSheet`, followed by suspend-call errors.

The source archive `Mail-Privacy-main(5).zip` did not contain the two durable methods and the bottom sheet lacked the `kotlinx.coroutines.launch` import. This increment adds both methods using the existing durable snapshot/batch-write API and adds the missing import.

## Cargo.lock diagnostic

The archive contains a root `Cargo.lock`, but CI still reports that `cargo metadata --locked` fails. The previous workflow redirected both stdout and stderr to `/dev/null`, hiding the actual Cargo diagnostic. `rust-checks.yml` now keeps JSON stdout suppressed but prints Cargo stderr on the failure path, then continues with the existing unlocked fallback behavior.

This does not assert that the lockfile is now valid. The next CI run will reveal the exact cause if `--locked` still fails.

## Validation

- Source-level checks only; Android Gradle compilation and Cargo commands were not run in this environment.
- Run Debug, Release, and Rust workspace CI against this archive before treating it as verified.
