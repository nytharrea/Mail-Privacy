# Mailx API compatibility

The Android client integrates with the service at `https://mailx.net` by default (see `rust-core/src/client.rs`). Keep API paths and payload models aligned with the official service repository: <https://github.com/ivpn/mailx>.

When changing API integration:

- Prefer explicit request/response structs over untyped JSON for stable contracts.
- Preserve server error status and safe error messages; do not leak credentials or message content into logs.
- Distinguish authentication failure, authorization failure, validation failure, rate limiting, server failure and network failure.
- Use bounded retries only for operations that are safe to retry; do not blindly retry alias creation or other non-idempotent mutations.
- Add fixtures for success and failure responses without real personal data.
- Verify login, access-key authentication, token refresh/logout and alias CRUD against a test account before release.

The service repository is not a Rust crate dependency. Keep the integration at the HTTP/API contract boundary.
