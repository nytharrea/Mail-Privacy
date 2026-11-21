# UniFFI boundary

## What is exposed

`rust-core/src/uniffi_api.rs` defines the typed Kotlin-facing API. The records, enums and errors mirror the pure types in `mailx-runtime::app_shell`; every conversion is an exhaustive `match`, so adding a variant on either side fails to compile until the mirror is updated, and unit tests round-trip every variant.

| Rust item | Kotlin shape | Notes |
|---|---|---|
| `MailxCore` (object) | `MailxCore()` | process-scoped state owner; create one per ViewModel and `close()` it in `onCleared` |
| `Route` (enum) | `enum class` | `HOME`, `ALIASES`, `RECIPIENTS`, `RULES`, `SETTINGS` |
| `Sheet` (enum) | `enum class` | one entry per sheet kind |
| `AppState` (record) | data class | `route`, `sheet`, `authenticated`, `canGoBack`, `revision` |
| `AppEvent` (enum with data) | sealed class | `Authenticate`, `SignOut`, `Navigate(route)`, `OpenSheet(sheet)`, `CloseSheet`, `Back` |
| `MailxCoreError` (error) | exception | `AuthenticationRequired`, `NothingToGoBack` |
| `SecureStorePort` (foreign trait) | interface | implemented by Android; not wired yet |

`MailxCore.dispatch(event)` returns the next `AppState` or throws; a rejected event leaves the state untouched. `MailxCore.state()` returns the current snapshot.

On the Kotlin side `ui/viewmodel/AppShell.kt` wraps the object, maps `MainNavTab` and `ActiveSheet` to the generated types and treats any exception as a rejected event.

## Generated package and file

`rust-core/uniffi.toml` sets the Kotlin package to `net.mailx.android.core.uniffi` and the library name to `mailx_core`. The generated Kotlin lives in `android-app/app/src/main/java/net/mailx/android/core/uniffi/`. It is produced in CI, ignored by Git and must not be committed.

The composite action `.github/actions/generate-uniffi-bindings` installs the `uniffi-bindgen` release that matches the `uniffi` crate version in `Cargo.lock`, caches it, generates the bindings from the freshly built arm64 library and fails with a clear error if no Kotlin file appears in that package directory (for example after changing `package_name`).

Manual equivalent:

```bash
cargo ndk -t arm64-v8a -o android-app/app/src/main/jniLibs \
  build --release -p mailx-core --features "network,jni,uniffi"
uniffi-bindgen generate --library android-app/app/src/main/jniLibs/arm64-v8a/libmailx_core.so \
  --language kotlin --no-format --out-dir android-app/app/src/main/java
```

Keep Cargo release `strip` disabled: the bindgen reads the component metadata from the compiled library.

## Android requirements

- JNA (`net.java.dev.jna:jna` AAR) loads the library at runtime.
- `proguard-rules.pro` keeps `net.mailx.android.core.uniffi.**` and the JNA classes; R8 would otherwise remove members that are only reached by reflection.
- The generated sources are compiled together with the app, so any Gradle build must run after the bindings step.

## Security contract for `SecureStorePort`

Android must implement the port with Android Keystore-backed authenticated encryption (Tink AEAD is the intended adapter). Keys are validated in Rust before they reach the adapter (ASCII letters, digits, `.`, `_`, `-`; at most 128 characters), and Rust does not hold a lock while calling into Kotlin. Never pass passwords, access keys, refresh tokens or decrypted secret material through logs. Byte arrays crossing the boundary are transient; callers should clear mutable buffers after use.

## Remaining migration

1. Move authentication/session state behind typed Rust methods so credentials stop being passed per call.
2. Move API calls and local helpers from JSON JNI exports to typed UniFFI methods, feature by feature, with parity tests.
3. Retire the corresponding JNI exports once each feature has parity.
4. Wire a Keystore-backed `SecureStorePort` adapter together with a tested preference migration.

Earlier phase notes are kept in [history/UNIFFI_MIGRATION_PHASE2.md](history/UNIFFI_MIGRATION_PHASE2.md).
