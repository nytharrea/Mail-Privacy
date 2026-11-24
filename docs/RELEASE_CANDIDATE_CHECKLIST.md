# Release Candidate Sign-Off Checklist (v0.5.0-rc1)

All automated CI gates (Phases 1–10: 201 Rust tests, 12 Android JVM unit tests, Debug + Signed Release 4-ABI + universal APKs, 16 KB ELF & `zipalign`) are green. Complete the following manual checks on a physical Android device before public distribution.

## 1. Installation & Startup
- [ ] Install the signed `app-arm64-v8a-release.apk` (or `app-universal-release.apk`) from GitHub Release and verify its SHA-256 against `SHA256SUMS.txt`.
- [ ] Cold-start the app: verify `libmailx_core.so` loads cleanly via UniFFI/JNA with zero crashes.
- [ ] Verify the default **Graphite Lime** theme renders crisply in Dark and Light modes, and switching to **Night Fjord** in `Settings -> Appearance & Display` persists across app restart.

## 2. Authentication & Live API Flows
- [ ] Sign in with an **Access Key** (`/v1/api/authenticate`): list aliases, create an alias, toggle/pin/update, and delete an alias.
- [ ] Sign in with **Email + Password** (and TOTP if enabled) or **Passkey** (`/v1/login`): verify web-session routes (Recipients, Custom Domains, Rules, Blocklist, Account/Settings) load and mutate cleanly.
- [ ] Trigger biometric lock (if enabled) and verify recents thumbnail protection on Android 13+.

## 3. Secure Storage & Process Death
- [ ] Force-stop the app and reopen: verify active session restores cleanly from hardware-backed Keystore/Tink encrypted DataStore.
- [ ] Sign out: force-stop and reopen; verify the session stays cleared and does not reopen.
- [ ] Optional instrumentation run on a connected device:
  `./gradlew connectedDebugAndroidTest` (verifies `SecureStorageDeviceSecurityTest` hardware StrongBox/TEE key level and `EncryptedBackupCodecTest`).

## 4. Platform Integrations
- [ ] Add the **Quick Alias**, **Privacy Stats**, and **Pinned Aliases** home-screen widgets and verify refresh/copy/create actions.
- [ ] Add the **Quick Alias** Quick Settings Tile and verify alias creation + clipboard copy.
- [ ] Test `mailto:` / selected-text share into `ReverseAliasSendActivity`.

## 5. Licensing & Attribution (Before Public Publishing)
- [ ] Complete file-level provenance review and add a top-level `LICENSE` file ([LICENSING_AND_ATTRIBUTION.md](LICENSING_AND_ATTRIBUTION.md)).
- [ ] Keep clear repository and release-note disclosure that this Android app is unofficial and not endorsed by IVPN.
