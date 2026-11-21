# UniFFI migration — Phase 2

## Scope in this phase

- Adds a typed UniFFI API alongside the existing handwritten JNI API.
- Exposes `MailxCore` as a process-scoped UniFFI object.
- Exposes typed `AppState`, `AppEvent`, and `MailxCoreError` values.
- Defines a foreign `SecureStorePort` implemented by the Android layer; Rust owns only the contract and never persists secrets itself.
- Keeps all legacy JNI exports intact. Existing Kotlin call sites are not switched in this phase.

## Binding generation

The Kotlin binding is generated from the Android arm64 library after the Rust build:

```bash
cargo ndk -t arm64-v8a -o android-app/app/src/main/jniLibs \\
  build --release -p mailx-core --features "network,jni,uniffi"
uniffi-bindgen generate --library android-app/app/src/main/jniLibs/arm64-v8a/libmailx_core.so \\
  --language kotlin \\
  --out-dir android-app/app/src/main/java
```

The generated package is `net.mailx.android.core.uniffi`.

## Security contract

`SecureStorePort` is a foreign interface. Android must implement it with Android Keystore-backed authenticated encryption (Tink AEAD is the intended adapter). Never pass passwords, access keys, refresh tokens, or decrypted secret material through logs. The byte arrays crossing UniFFI are transient; callers should clear mutable buffers after use.

## Migration order

1. Generate bindings and compile them in Android CI.
2. Add a Kotlin adapter and a single process-scoped `MailxCore` instance.
3. Move state and navigation events to `MailxCore`.
4. Move authentication and API calls behind typed Rust methods.
5. Retire old JNI functions only after each feature has parity tests.

## Not claimed complete

This phase does not migrate the 39 legacy JNI calls, the network client, authentication/TOTP, alias cache, Compose navigation, or the large ViewModel. It establishes and CI-builds the typed boundary first.

## Source audit notes

The inspected upload does not contain JNA dependencies or `com.sun.jna` references, so no JNA version bump was made. The 16 KB check remains generalized to every native `.so` file. The Compose code still has a large ViewModel and multiple independently collected flows; those are intentionally not rewritten in this boundary-only phase because doing so together with a new FFI ABI would make regressions difficult to isolate.
