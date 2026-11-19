# Migration and maintenance plan

## Completed in this preparation

- Kept the visible app name `Mailx`.
- Kept `namespace` and `applicationId` at `net.mailx.android` to avoid breaking update identity.
- Added a teal/obsidian visual identity and geometric web/envelope launcher icon.
- Added upstream relationship and review policy for `ivpn/mailx`.
- Added a safe Git remote setup script; it does not merge upstream code.
- Removed the unverified `MIT` Cargo metadata declaration pending provenance review.
- Removed the predictable hard-coded release-signing fallback. Release signing now requires repository secrets.

## Next implementation stages

### 1. Establish repository remotes

Initialize or use the existing Git repository, set `origin` to the user's Android-client repository, and add `upstream` as the official service repository. See `docs/UPSTREAM.md`.

### 2. Stabilize behavior before restructuring

Add Rust unit and integration tests for authentication, alias CRUD, recipients, rules, serialization, offline behavior, and security analyzers. Add API contract fixtures from documented or observed responses; do not store real account data in fixtures.

### 3. Separate Rust responsibilities

Extract modules incrementally into crates only after tests pass. Keep the JNI ABI stable during each extraction. The current source remains one crate until that work is completed.

### 4. Strengthen Android state ownership

Keep Compose as presentation, ViewModels as UI adapters, and Rust as the business-rule owner. Move blocking JNI/network calls off the main thread and add lifecycle cancellation. Preserve Android-specific services and widgets in Kotlin.

### 5. Storage and secrets

Audit the existing encrypted preferences implementation and Android Keystore key lifecycle. Do not rename encryption aliases or migrate stored secrets as part of branding changes. Any database or preference migration must be versioned, tested with backup/restore, and fail safely.

### 6. Release readiness

Require a private release keystore in GitHub Actions secrets. Never generate a known-password fallback key. Verify APK signatures and 16 KB ELF alignment without ignoring failures. Publish source and corresponding build instructions with every distributed binary.

## Not done by this archive patch

- No new applicationId or Kotlin package migration.
- No automatic merge of IVPN's service repository.
- No seven-crate Rust split yet.
- No replacement of JNI with UniFFI.
- No production SQLCipher integration or storage rewrite.
- No compile/test claim: the execution environment used to prepare this archive did not have Cargo, Gradle or an Android SDK installed.
