# CI follow-up — 2026-11-22

Based on the supplied GitHub Actions logs for commit
`7165eefa324a418ce34d87db6535fedd8da3a32c`.

## Confirmed CI results

- Android Release: `BUILD SUCCESSFUL`.
- Rust test suites: all executed tests passed; one test was ignored.
- Clippy with all features: passed.
- Clippy without default features: passed, with an unused dependency warning.
- The Cargo lock check warned that `Cargo.lock` was missing or stale.
- The nightly GitHub Release upload completed.

## Source changes in this follow-up

1. Made `mailx-network` an optional dependency of `mailx-application` and
   explicitly connected it to the `network` feature. This matches the existing
   `#[cfg(feature = "network")]` imports in `controller.rs` and avoids retaining
   a dependency in `--no-default-features` builds.
2. Removed a redundant Elvis fallback from `MailxViewModel` where CI reported
   `fetchError` as a non-null `String`.

## Not changed

- No generated `Cargo.lock` is included. This environment has no Cargo binary,
  so the lockfile must be generated and committed by the existing
  `.github/workflows/update-cargo-lock.yml` workflow.
- Deprecated Compose and widget APIs were not mechanically replaced because
  they need API-level review and UI/device regression checks.
- The secure-storage and JNI/UniFFI migrations remain separate follow-up work.

## Validation boundary

These edits have not been compiled locally. Run the Cargo lock refresh workflow,
then rerun Rust checks and Android Debug/Release CI against this exact commit
before treating this ZIP as validated.
