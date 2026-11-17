# Mailx Android (Rust Core + Jetpack Compose Material 3)

Modern Android client for [IVPN Mailx](https://github.com/ivpn/mailx) ([mailx.net](https://mailx.net/)) powered by a **Rust native core (`mailx-core`)**, **Android 16 KB page size alignment**, **multi-ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`)** support, and [anonaddy/addy-android](https://github.com/anonaddy/addy-android) system integrations.

## Key Features

1. **Rust Native Core (`rust-core` / `libmailx_core.so`)**:
   - Supports both **Email & Password + 2FA TOTP** web sessions (`POST /v1/login`) and **85-character Access Key** API sessions (`POST /v1/api/authenticate`) over `ureq` + `rustls`.
   - Zeroizes sensitive tokens in memory on `Drop` via `SecretString`.
   - Generates Reverse-Alias (`Send from alias`: `alias+recipient=domain.com@mailx.net`) and Wildcard on-the-fly (`prefix+wildcard@domain`) addresses natively in Rust.
   - Includes a client-side **Removed Trackers Report Decoder** (`nativeDecodeTrackersReport`) that decodes base64url `#fragment` tracker reports locally without contacting any server.
2. **Complete Mailx Feature Set**:
   - **Aliases & Bulk Operations**: Create, edit, pin, pause/unpause (`1-10 hours/days/weeks`), soft-delete, restore, permanently forget, and bulk-manage aliases (`/v1/aliases/bulk/*`).
   - **Wildcard Aliases**: Per-domain wildcard usage info (`/v1/alias/wildcard-domain-info`), custom delimiter (`*.` or `*+`), and 6-12 character alphanumeric suffix generator.
   - **Recipients & PGP Encryption**: Add recipients, verify 6-digit OTP codes, upload PGP public keys, toggle PGP encryption, and switch between `PGP/MIME` and `PGP/Inline`.
   - **Custom Domains & DNS Verification**: Add custom domains, inspect `TXT`, `MX`, `SPF`, `DKIM`, and `DMARC` records, run live DNS verification (`/v1/domain/:id/verify-dns`), and configure `Catch-All`.
   - **Rules & Blocklist**: Multi-condition (`up to 5`) and multi-action (`up to 5`) email rules with `AND`/`OR` operators, recipient UUID mapping for `forward_to`, rule duplication, rule reordering, and sender/domain blocklist.
   - **Account, Security & Diagnostics**: Primary email OTP verification, password reset & change, 2FA TOTP setup & backup codes, Passkey credential management, Access Key creation/revocation, Alias CSV Import & Export, delivery/bounce log viewer, and `/livez` server health check.
3. **Android 16 KB Page Size & Multi-ABI Support**:
   - Configured with `-Wl,-z,max-page-size=16384` and `-Wl,-z,common-page-size=16384` in `.cargo/config.toml` across `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86`.
   - Built with **NDK r30 LTS (`30.0.16248370`)**, `useLegacyPackaging = false`, and verified via `llvm-objdump -p` and `zipalign -c -v -P 16 4`.
4. **Jetpack Compose (BOM `2026.09.00`) & Material Design 3**:
   - Pure **AMOLED Black (`#000000`)** dark mode surface hierarchy and Android 12+ **Material You Dynamic Color** support.
   - **Quick Settings Tile (`QuickAliasTileService.kt`)** and **Reverse-Alias Intent Handler (`ReverseAliasSendActivity.kt`)** for `mailto:` links and text selection.

## Building & Signing via GitHub Actions

Add the following repository secrets under **Settings → Secrets and variables → Actions → New repository secret**:

| Secret Name | Description |
| :--- | :--- |
| `KEYSTORE_BASE64` | Base64 output of your `.jks` or `.keystore` file (`base64 -w 0 release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |
