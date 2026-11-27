# Third-party licenses and notices

Mailx Android (`Mail-Privacy`) is licensed under **GPL-3.0-only** ([LICENSE](LICENSE)). It bundles no proprietary binaries, no Google Play Services (`com.google.android.gms`), no telemetry/analytics SDKs, and no custom font files (UI typography uses Android system sans and system monospace fonts).

Because `android-app/app/build.gradle.kts` excludes duplicate `/META-INF/{AL2.0,LGPL2.1}` files during APK packaging, third-party open-source attributions are documented here and in [NOTICE](NOTICE).

## Android & Kotlin dependencies
- **AndroidX / Jetpack Compose / Material 3 / DataStore / Biometric / WorkManager / AppSearch / Credentials (`androidx.*`)** — Apache-2.0
- **Kotlin Standard Library & Coroutines (`org.jetbrains.kotlinx`)** — Apache-2.0
- **Google Tink (`com.google.crypto.tink:tink-android`)** — Apache-2.0
- **Java Native Access (`net.java.dev.jna:jna`)** — Dual-licensed Apache-2.0 / LGPL-2.1 (used under Apache-2.0)

## Rust workspace dependencies (locked in `Cargo.lock`)
- **UniFFI (`uniffi`)** — MPL-2.0
- **Serde & `serde_json`** — MIT OR Apache-2.0
- **`ureq` & `rustls` / `webpki-roots`** — MIT OR Apache-2.0 / ISC
- **RustCrypto (`hmac`, `sha1`, `zeroize`, `subtle`)** — MIT OR Apache-2.0
- **`getrandom`** — MIT OR Apache-2.0

## Original brand & icon assets
- Adaptive launcher icon (`ic_mailx_web_mark.xml`, `ic_mailx_web_monochrome.xml`), notification glyph (`ic_stat_mailx.xml`), and all 51+ `Mailx Line` icons (`ic_mx_*.xml` and `MailxIcons.kt`) are original works distributed under **GPL-3.0-only**.
