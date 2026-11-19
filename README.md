# Mailx for Android

An independent Android client for the [IVPN Mailx](https://github.com/ivpn/mailx) email-aliasing service. This repository contains the Android application and its Rust core; it is **not an official IVPN application** and is not endorsed by IVPN.

**Slogan:** Your inbox. Your privacy.

The client keeps the `Mailx` product identity and integrates with the official Mailx service. The official repository is tracked as an API/behavior upstream reference, not as a codebase to merge wholesale. See [docs/UPSTREAM.md](docs/UPSTREAM.md).

## Project characteristics

- Android-native UI built with Jetpack Compose and Material 3 Expressive.
- Rust-first business logic, security analysis and service client.
- Existing Android package identity: `net.mailx.android` (kept for install/update compatibility).
- Offline-capable local analysis with server-backed Mailx account operations.
- Dark obsidian surfaces with a restrained teal accent and a geometric web/envelope app icon.

## Architecture today

The current implementation is a Rust-first **modular monolith** in `rust-core` (one Cargo crate with focused modules), with Kotlin responsible for Android lifecycle, Compose rendering, widgets, platform services and JNI integration. The proposed multi-crate split is documented but not claimed as complete. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Main source areas

- `rust-core/`: Rust domain models, Mailx API client, orchestration, alias/rules engines and local security analyzers.
- `android-app/app/`: Compose screens, ViewModel, Android services, widgets, secure preferences and JNI bridge.
- `assets/branding/`: editable brand artwork.
- `docs/`: upstream policy, architecture, branding, licensing and migration notes.

## Build

### Rust

```bash
cargo test --manifest-path rust-core/Cargo.toml --all-features
cargo clippy --manifest-path rust-core/Cargo.toml --all-targets --all-features -- -D warnings
```

### Android

Open `android-app/` in Android Studio with the Android SDK and NDK versions configured in `android-app/app/build.gradle.kts` and `.github/workflows/android-release.yml`.

The release workflow builds the Rust JNI library for Android ABIs before assembling APKs. Release signing requires the following GitHub Actions secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD` (optional; defaults to the keystore password)

The workflow intentionally fails if the private signing keystore is not configured. Never use a predictable or shared fallback signing key for release builds.

## Keep the official service connected

```bash
FORK_URL="https://github.com/<your-account>/<your-android-repo>.git" \
  ./scripts/setup-git-remotes.sh
```

This configures `origin` to your own Android repository and `upstream` to `https://github.com/ivpn/mailx.git`. The upstream project is the service (API, web app, mailserver and browser extension); review its API changes, but do not merge its repository tree into this Android repository. Full procedure: [docs/UPSTREAM.md](docs/UPSTREAM.md).

## Licensing and attribution

The official IVPN Mailx repository is GPL-3.0 licensed. The supplied Android snapshot has no top-level license file and previously contained conflicting Cargo license metadata. This preparation removes the unverified Cargo `MIT` declaration but does not invent a project-wide license. Complete the provenance review before redistributing; see [docs/LICENSING_AND_ATTRIBUTION.md](docs/LICENSING_AND_ATTRIBUTION.md).

## Security

- Never commit `keystore.properties`, signing keys, access keys, session tokens or real account data.
- Do not log authentication material or message contents.
- Keep local-only analyzers usable without network access.
- Treat upstream API responses as untrusted input and validate them at the Rust boundary.
- Preserve existing secure-storage and Keystore behavior unless a separately tested migration is prepared.

## Brand assets

![Mailx geometric web and envelope mark](assets/branding/mailx-web-mark.png)

The launcher icon uses a geometric spider-web motif with an envelope at its center. Editable source: `assets/branding/mailx-web-mark.svg`. Brand tokens: [docs/BRANDING.md](docs/BRANDING.md).

## Migration status

See [docs/MIGRATION_PLAN.md](docs/MIGRATION_PLAN.md) for completed preparation and remaining work. This archive patch has not been compiled in an Android SDK environment.
