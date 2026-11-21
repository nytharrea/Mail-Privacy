# Android feature coverage — 2026-11-19

This matrix records the current Android client implementation against the official
Mailx API contract. “Implemented” means a corresponding Android flow exists in the
source; it does not mean the flow has been exercised against a live account.

## Existing client features

| Area | Current implementation | Verification still required |
|---|---|---|
| Authentication | Password/TOTP, access-key and passkey login paths | Live account tests for each path and session expiry |
| Passkeys | Credential Manager assertion/creation, registration finish, credential listing and deletion | Android Digital Asset Links/RP-ID association and device-provider matrix |
| Aliases | List/search/status filtering, create/update/delete, pin, pause/unpause, restore/forget, bulk operations | Large-account pagination and all filter combinations |
| Alias import/export | CSV import/export paths | Large CSV, malformed CSV and partial server result handling |
| Recipients | List, add, OTP send/verify, PGP settings and deletion | Deactivated recipient and stale-state scenarios |
| Domains | CRUD, DNS config and DNS verification | Provider-specific DNS response variations |
| Rules | List, create/update, active toggle, reorder and delete | Complex condition/action combinations |
| Blocklist | List/add/delete | Large-list pagination and duplicate-page handling |
| Account/security | User details, TOTP, password/email flows, passkeys, access keys | Step-up authentication and session expiry |
| Subscription | `GET /v1/sub` read-only display | Validate all fields returned by the deployed service |
| Diagnostics | Logs, individual log detail and delete-all | Large logs and server error states |
| Dashboard | User stats and announcements | Empty, stale and partial-response handling |
| Local utilities | Encrypted backup, alias search/watch, widgets and quick tile | Restore compatibility and Android-version matrix |

## This delivery's hardening

- Alias pagination now tracks unique IDs rather than raw row count. A repeated page,
  premature empty page, or excessive page count fails closed and preserves the
  previously displayed list instead of replacing it with incomplete data.
- Blocklist pagination applies the same unique-ID/progress checks and safety limit.
- Blocklist token rotation is synchronized by the shared `requestApi` helper; the
  duplicate per-page synchronization call was removed.

## Explicitly out of scope

- Mail inbox/reading, message content, SMTP or email sending.
- Server-side mail processing, delivery/routing, or MIME/HTML processing.
- Subscription purchase, renewal, plan changes, payments, account-management
  redirects, or calls to `PUT /v1/sub/update`.

## Release validation still required

This editing environment has JDK 21 but no configured Android SDK/NDK or Rust
 toolchain, so it cannot reproduce the repository's JDK 27 + Android SDK 37 CI build.
Run the existing GitHub Actions CI and Release workflows after applying this archive.
Then test authentication, passkeys, alias and blocklist pagination, recipients,
security settings and backup restore on a real Android device.
