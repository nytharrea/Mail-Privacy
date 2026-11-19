# Upstream relationship

## Sources of truth

- Official service repository: <https://github.com/ivpn/mailx>
- Official service website: <https://mailx.net/>
- This repository: independent Android client built against the Mailx service API.

This Android application is not an official IVPN application and is not endorsed by IVPN. It is an independently maintained client that integrates with the Mailx service. Keep attribution and third-party notices intact.

## Git remote policy

Use two remotes when this repository has its own Git repository:

- `origin`: the user's own Android-client repository.
- `upstream`: `https://github.com/ivpn/mailx.git`, read-only and used to inspect service/API changes.

**Do not merge the upstream repository wholesale.** The official repository contains the Mailx service (API, web app, mailserver and browser extension); it is not the Android-client source tree. Track it as an API and behavior reference. Review endpoint, authentication, response-model and error-contract changes before changing the Android client.

Run:

```bash
# From the Android client repository root
FORK_URL="https://github.com/<your-account>/<your-android-repo>.git" ./scripts/setup-git-remotes.sh

git remote -v
git fetch upstream main
```

The setup script preserves an existing `origin` unless `FORK_URL` is supplied. It disables pushes to `upstream` and never merges or rebases upstream code.

## Upstream review checklist

Before adopting an upstream change, review:

1. Authentication endpoints and token/session lifecycle.
2. API paths, request payloads, response models and pagination.
3. Alias creation, update, pause, pin, delete and reverse-alias behavior.
4. Recipient verification and rules/blocklist semantics.
5. Error codes, retry behavior, rate limits and server-side validation.
6. Mail delivery, forwarding and PGP-related behavior.
7. Security advisories and API deprecations.

Record the upstream commit SHA and the client compatibility change in the relevant pull request. Do not copy server secrets, production configuration or private deployment material into this repository.
