# Mailx Android implementation status — 2026-11-19

## Source baseline

This package is based on `Mail-Privacy-Feature-Hardening-Phase1.zip` supplied in this conversation.
It retains the Rust-first application architecture and the existing Compose screens.

## Changes in this consolidated pass

- GitHub Actions release publishing now checks whether a release exists before choosing create vs. upload.
- Existing release assets with matching names are replaced using `gh release upload --clobber`.
- The workflow no longer treats every `gh release create` failure as evidence that a release already exists.
- A concurrency group prevents simultaneous runs for the same Git ref from racing to publish.
- Existing nightly release metadata is refreshed after asset upload.

## Existing functionality retained

The project already contains implementations for alias management, recipients, rules/blocklist,
custom domains, account/security, access keys, diagnostics, announcements, statistics, passkey
ceremonies, and subscription status display. This pass does not duplicate those modules.

## Important product constraints retained

- Subscription remains read-only. No subscription update, payment, purchase, renewal, plan-change,
  or external account-management redirect is introduced.
- No inbox/email-reading, SMTP, mail delivery, or server-side mail processing is added.
- The official Mailx API remains the source of truth; no invented backend routes are introduced.

## Verification status

The supplied GitHub Actions log `logs_120439362215.zip` shows:
- Rust JNI build completed.
- Kotlin/Java release compilation completed.
- `assembleRelease` completed with `BUILD SUCCESSFUL`.
- The release publish step reported that the `nightly` release tag already existed and entered
  its upload fallback. The log excerpt does not provide an explicit asset-upload confirmation.

The updated workflow has been statically inspected, but it has not been executed against GitHub
from this environment. A fresh Actions run is required to verify upload permissions and final
release assets. Passkey, authentication expiry/refresh, pagination edge cases, accessibility,
and live API behavior still require device/account-level tests; a successful APK build does not
prove those runtime scenarios.
