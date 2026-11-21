# Build environment

## Rust toolchain

`rust-toolchain.toml` pins the Rust release (currently 1.97.0) with `rustfmt` and `clippy`. CI installs exactly that release through the `setup-rust` composite action, so local and CI builds use the same compiler and formatter. Android targets are added explicitly by the Android jobs.

Raising the pin is a one-line change in `rust-toolchain.toml`; run `cargo fmt --all`, Clippy and the tests before committing it.

## Cargo.lock

`Cargo.lock` is committed. CI builds with `--locked` whenever the lockfile matches the manifests and prints a warning otherwise. If the warning appears, run the **Update Cargo.lock** workflow (Actions tab, "Run workflow") and keep the commit it creates. Tick `upgrade_all` only when you want every dependency moved to its newest compatible version.

## Java runtime

The Android CI and Release workflows run Gradle on **Eclipse Temurin JDK 27**. The project uses the committed Gradle Wrapper and Gradle 9.9.1. Gradle 9.8.1+ supports running on Java 27; Android Gradle Plugin 9.5.1 requires Gradle 9.6.0 or newer.

The Android Java source/bytecode compatibility remains Java 21 intentionally. The JDK used to run Gradle and the bytecode level emitted for Android are separate settings. Raising `sourceCompatibility`/`targetCompatibility` to 27 would require verification across D8/R8, desugaring, Android runtime API availability, and all Java/Kotlin dependencies; this project does not claim that validation. JDK 27 is not an LTS release; moving the workflows to the current LTS is a reasonable future change once the full build has been verified on it.

## Wrapper

Use `android-app/gradlew` (or `gradlew.bat` on Windows) from the `android-app` directory. The wrapper distribution is pinned to Gradle 9.9.1.

## Android SDK

- compileSdk 37, targetSdk 36 (see [ANDROID_SDK_API37.md](ANDROID_SDK_API37.md))
- Build Tools: 37.0.0
- NDK: 30.0.16248370
- ABIs: arm64-v8a, armeabi-v7a, x86_64, x86 (release); arm64-v8a and x86_64 (CI debug)

## Workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `ci.yml` | push, pull request, manual | Rust checks, debug APK, ELF alignment checks; optional emulator launch test (manual, `run_emulator`) |
| `android-release.yml` | push to main/master, tags, manual | Rust checks gate the signed multi-ABI release; publishes APKs and `SHA256SUMS.txt` |
| `rust-checks.yml` | called by the two workflows above | `cargo fmt --check`, Clippy `-D warnings` (all features and no default features), tests |
| `update-cargo-lock.yml` | manual | Refreshes and commits `Cargo.lock` |
| `audit.yml` | weekly, manual, dependency changes | Advisory-only `cargo audit` |
| `live-smoke.yml` | manual | Runs the ignored live API test against a real account |

Reusable pieces live in `.github/actions/`: `setup-rust` (pinned toolchain plus targets) and `generate-uniffi-bindings` (bindgen matching the locked `uniffi` version, then Kotlin generation; see [UNIFFI.md](UNIFFI.md)).

`scripts/check-elf-alignment.sh` accepts `.so` files, directories or APKs and fails if any LOAD segment is aligned below 16 KB. The workflows run it on the Rust libraries and on every library packaged in the APKs, which also covers JNA's `libjnidispatch.so`.

Keep Cargo release `strip` disabled: UniFFI's bindgen reads component metadata from the compiled library.

## Live smoke test setup

Create the repository secret `MAILX_LIVE_ACCESS_KEY` (a dedicated test account's access key). Optionally create the repository variable `MAILX_LIVE_BASE_URL` for a non-default server. Start with the default read-only run; enable `allow_mutation` only on an account where a throw-away alias may be created and deleted.

## Release signing

Release signing needs the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and optionally `KEY_PASSWORD`. The workflow fails without them; there is no fallback key.
