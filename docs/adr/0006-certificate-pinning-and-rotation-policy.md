# ADR 0006: Certificate pinning and rotation policy

Status: accepted, 2026-11-22

## Context

Mailx is an unofficial Android client for the open-source `mailx` email-alias server. Users may connect to the hosted service (`https://api.mailx.net`) or to a self-hosted `mailx` instance on their own domain.

Enabling static leaf or intermediate certificate pinning without an operational pin-rotation and recovery plan creates a severe lockout risk:

1. Because this project does not operate `api.mailx.net`, upstream TLS certificates or issuing CAs can rotate without advance notice to this client.
2. Self-hosted users bring arbitrary valid CA certificates (Let's Encrypt, ZeroSSL, private CAs). Hard-coding pins would break self-hosted deployments unless scoped strictly by host.
3. Without a backup SPKI pin, an expiration deadline, and a tested recovery path, a single unexpected rotation bricks every installed client until users download a new APK.

## Decision

- **Do not enable certificate pinning in v1.** Production TLS relies on strict `rustls` certificate chain and hostname verification (`ureq 3.5.2` with `webpki-roots`, TLS 1.2/1.3, zero verification bypasses) plus `android:usesCleartextTraffic="false"` in `AndroidManifest.xml`.
- **Prerequisites before enabling pinning in a future release:**
  1. Scope pins strictly to `api.mailx.net` (never self-hosted domains) and pin **SPKI hashes**, never leaf certificates.
  2. Require at least **two independent backup SPKI pins** (primary issuing/root CA + backup CA) in every pinned set.
  3. Attach an explicit **pin expiration date** aligned with the release cadence so stale builds fail open to standard WebPKI verification instead of permanently locking users out.
  4. Document and test a **rotation & emergency recovery runbook** on physical Android devices (valid chain, rotated backup pin, and expired pin set) before shipping to production.

## Consequences

- Zero risk of self-inflicted lockout on upstream certificate rotation or self-hosted deployments.
- All transport security guarantees remain enforced and verifiable in CI: HTTPS-only (except explicit loopback opt-in), strict `rustls` TLS verification, same-origin redirect blocking, public-endpoint credential stripping, strict 10 MiB body limit rejection, bounded timeouts, and single-flight 401 token refresh.
