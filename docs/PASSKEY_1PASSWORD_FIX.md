# 1Password passkey app-domain mismatch

Observed 1Password message: "unable to save passkey for security reasons ... associated url ... does not match the selected app".

This is a WebAuthn / Digital Asset Links association error, **not a Rust or APK build failure**.

## Release app identity, extracted from supplied 2026-11-19 CI logs

- Android applicationId: `net.mailx.android`
- APK signing certificate SHA-256: `F2:C5:92:E3:0B:10:05:CB:0F:07:EC:AB:A0:AD:BD:A8:E0:02:88:3A:66:9A:C5:CF:B0:D2:B7:97:91:21:08:8D`
- All five signed APK variants in the supplied run showed this same digest.

## Required server-side deployment

1. Inspect the **actual registration options** returned by `/v1/register/add`. Find `publicKey.rp.id` or `rp.id` depending on wrapper shape. For sign-in inspect `rpId` from `/v1/login/passkey/begin`. **Do not replace these values client-side.**
2. On **that exact RP ID's HTTPS host**, publish `docs/passkey-assetlinks.json` at `https://<RP-ID>/.well-known/assetlinks.json` (HTTP 200, JSON, no authentication, publicly reachable). If the RP ID is `mailx.net`, deploy on `mailx.net`; if `api.mailx.net`, deploy on `api.mailx.net`. Publishing on a different subdomain is not sufficient.
3. If the host already serves assetlinks, merge the entry with existing statements rather than overwriting them.
4. For a debug APK (`net.mailx.android.debug`) add a **separate** target entry using the actual debug signing fingerprint. Never use the release fingerprint for debug builds.
5. Verify the installed APK was signed with the same release key. If the signing key changes, update assetlinks accordingly.
6. Reopen the app and retry 1Password registration. Credential providers can cache domain association; allow time for refresh.

Android app code can surface a better error but cannot create a valid trust association without a server change. No origin spoofing, RP-ID rewriting, or bypass is attempted.
