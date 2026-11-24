# Release Security Audit (Phase 10)

Date: 2026-11-23

## 1. AndroidManifest & Exported Components
- `android:allowBackup="false"`, `android:fullBackupContent="false"`, `android:usesCleartextTraffic="false"`.
- `MainActivity`: `exported="true"` (`MAIN`/`LAUNCHER` only).
- `ReverseAliasSendActivity`: `exported="true"` (`mailto:` `SENDTO` and `text/plain` `PROCESS_TEXT`; no silent mutations or secret exposure).
- `QuickAliasTileService`: `exported="true"`, guarded by `android.permission.BIND_QUICK_SETTINGS_TILE`.
- `StopAliasWatchReceiver`: `exported="false"`.
- `PinnedAliasesWidgetService`: `exported="false"`, guarded by `android.permission.BIND_REMOTEVIEWS`.
- `QuickAliasWidgetProvider`, `PrivacyStatsWidgetProvider`, `PinnedAliasesWidgetProvider`: `exported="true"` (required for `APPWIDGET_UPDATE`). `QuickAliasWidgetProvider` validates `EXTRA_APPWIDGET_ID` against active widget IDs before handling create/copy broadcasts.
- Permissions limited to `INTERNET`, `POST_NOTIFICATIONS`, and `USE_BIOMETRIC`.

## 2. Cloud Backup & Device-to-Device Transfer (`data_extraction_rules.xml`)
- Both `<cloud-backup>` and `<device-transfer>` explicitly exclude `root`, `file`, `database`, `sharedpref`, and `external`.
- Encrypted DataStore (`mailx_secure_store.bin`) and Keystore-wrapped Tink keysets cannot leave the device via backup or D2D transfer.

## 3. Release Build, Obfuscation & Signing
- `isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-android-optimize.txt` + `proguard-rules.pro`.
- `proguard-rules.pro` strips `android.util.Log.v/d/i` in release builds and preserves only `androidx.credentials`, `com.sun.jna`/`net.java.dev.jna`, and `net.mailx.android.core.uniffi`.
- Release signing enforces APK Signature Scheme v2 + v3 (`enableV1Signing = false`).
- Native libraries (`libmailx_core.so`, `libjnidispatch.so`, `libdatastore_shared_counter.so`, `libandroidx.graphics.path.so`) are verified for 16 KB ELF LOAD alignment and 16 KB `zipalign` across all 4 ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) and the universal APK.

## 4. Logging & Secret Leak Scan
- Zero `Log.d`/`Log.v`/`Log.i`/`println`/`printStackTrace` calls in production Kotlin code (only one non-secret `Log.w` on rejected `AppEvent` in `AppShell.kt`).
- Zero `println!`/`eprintln!`/`dbg!` calls in production Rust library code.
- `HttpRequest` / `HttpResponse` `Debug` implementations redact `Authorization`, `Proxy-Authorization`, `Cookie`, and `Set-Cookie` headers and omit bodies.
- `SecretString` zeroizes on drop and uses constant-time equality.

## 5. Licensing & Attribution (Owner Action Required Before Public Distribution)
- No top-level `LICENSE` file has been added automatically; see [LICENSING_AND_ATTRIBUTION.md](LICENSING_AND_ATTRIBUTION.md).
- Before publishing APKs or source publicly, complete provenance review and add the chosen top-level `LICENSE` file, keeping clear attribution that this Android client is unofficial and not endorsed by IVPN.
