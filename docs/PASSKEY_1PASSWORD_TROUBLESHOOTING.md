# 1Password: associated URL does not match the selected app

This error is a WebAuthn / Android Digital Asset Links association failure, **not** an invalid passkey password. Mailx uses Android Credential Manager; the credential provider checks whether the RP domain trusts the installed app's package name and signing certificate.

1. Inspect the actual server `publicKey.rp.id` from the **registration begin** response (do not log the challenge, user ID, bearer token, or credentials). Do not assume that the API hostname `api.mailx.net` is the RP ID.
2. Host `https://<RP_ID>/.well-known/assetlinks.json` with HTTPS, HTTP 200, and JSON content type. The file must include the **installed release APK's** signing certificate SHA-256 and package `net.mailx.android` (debug uses `net.mailx.android.debug`).
3. Example (replace placeholder with actual signing SHA-256):

```json
[{
  "relation": ["delegate_permission/common.get_login_creds"],
  "target": {
    "namespace": "android_app",
    "package_name": "net.mailx.android",
    "sha256_cert_fingerprints": ["REPLACE_WITH_RELEASE_APK_SIGNING_CERT_SHA256"]
  }
}]
```

4. If multiple APK signing keys are distributed, add all legitimate signing fingerprints. A GitHub Actions keystore and a locally installed APK may differ.
5. Verify the Digital Asset Links statement on the RP host, then retry 1Password after propagation. A native-only change cannot establish domain ownership.

The client intentionally **does not** override `rp.id` or bypass Credential Manager's origin checks. Android association is enforced by the credential provider. The app cannot force 1Password to save an untrusted passkey.
