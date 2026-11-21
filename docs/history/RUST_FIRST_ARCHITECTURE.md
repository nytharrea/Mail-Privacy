# Rust-first Android architecture

## Runtime responsibilities

The workspace is split into focused crates:

- `mailx-domain`: domain models, alias processing, CSV, telemetry, local audit.
- `mailx-utils`: shared utilities and secret handling.
- `mailx-security`: phishing, MIME, PGP, TOTP, DNS, RFC and rules security logic.
- `mailx-network`: Mailx API client and authentication.
- `mailx-application`: application use cases and API action mapping.
- `mailx-runtime`: runtime skeleton and Rust-owned app-shell state reducer.
- `mailx-core`: stable Android JNI facade and native library (`mailx_core`).

`mailx-runtime::app_shell` validates navigation and sheet events. The Kotlin ViewModel delegates route transitions to the Rust reducer and retains Android lifecycle/state-flow responsibilities.

## Platform boundary

Jetpack Compose is intentionally retained for the Android presentation layer. Android-only capabilities such as Credential Manager, passkeys, app widgets, system UI insets, and accessibility are platform concerns and should not be reimplemented in Rust. Rust owns application/domain/security logic; Compose renders state and invokes the JNI facade.

## Compatibility

- Android namespace and application ID remain `net.mailx.android`.
- Native library remains `mailx_core`.
- Existing JNI methods and API paths remain in place.
- The Rust app-shell reducer is additive and does not replace existing API operations.

## Upstream sources

The official IVPN Mailx repository is used as the API and behavior reference. It is a service repository (API, web app, mailserver and browser extension), not an Android application project. The Android project should consume its documented API contracts rather than merge the server repository into the Android source tree.
