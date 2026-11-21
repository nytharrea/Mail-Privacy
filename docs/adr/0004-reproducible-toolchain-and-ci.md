# ADR 0004: Reproducible toolchain and CI structure

Status: accepted, 2026-11-20

## Context

CI installed whatever "stable" Rust was current, ran `cargo fmt --all` immediately before `cargo fmt --all -- --check` (so the check could never fail and the repository drifted out of rustfmt compliance), did not gate releases on tests, installed `uniffi-bindgen` at a version typed by hand, and verified 16 KB alignment only for the Rust libraries.

## Decision

- `rust-toolchain.toml` pins the Rust release; a composite action installs exactly that release and the Android targets.
- A reusable `rust-checks` workflow runs a real `cargo fmt --check`, Clippy with `-D warnings` (all features and no default features) and the tests. CI and the release both call it, and the release job `needs` it.
- Builds use `--locked` when `Cargo.lock` matches the manifests. Otherwise they warn and continue, and the manual **Update Cargo.lock** workflow refreshes the lock. This avoids breaking releases while the committed lock is stale and still pushes the project toward fully locked builds.
- The bindgen version is read from the resolved `uniffi` crate, so the generator and the library cannot drift apart. The installed binary is cached.
- 16 KB ELF alignment is checked for the Rust libraries after cross-compilation and for every library inside the built APKs, which includes JNA's native dispatcher.
- Releases publish `SHA256SUMS.txt` next to the APKs and record the commit in the nightly notes.
- Dependabot watches Cargo, GitHub Actions and Gradle weekly; `cargo audit` runs weekly and is advisory only.
- The JDK stays at 27 as documented in `BUILD_ENVIRONMENT.md`; actions are pinned by major tag, not by commit SHA.

## Consequences

- A formatting or Clippy regression now fails CI and blocks releases.
- Rust upgrades are deliberate one-line changes.
- Pinning actions by commit SHA and moving to an LTS JDK are possible follow-ups that need a verified full build first.
