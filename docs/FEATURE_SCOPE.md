# Mailx Android feature scope

## Keep and polish

These capabilities already exist in the Android source and Rust/JNI runtime; the redesign keeps their existing behavior:

- Alias lifecycle: create, search, filter, pin, pause, restore, delete and bulk actions.
- Recipients and custom domains, including DNS checks.
- Rules and blocklist management.
- Reverse-alias sending and wildcard generation.
- Account, subscription, access keys, TOTP and passkeys.
- CSV import/export and offline alias hygiene tools.
- Tracker report inspection, URL sanitization, phishing/header/MIME/PGP tools and MTA diagnostics.
- Android quick settings tile and home-screen widgets.

## Do not add as if supported

The official IVPN Mailx source is a relay/alias service with API, web client and mailserver components. This Android app is an independent API client, not a general-purpose mailbox. Do not add an inbox, message search, mailbox sync, push mail reading, or claims of end-to-end mailbox encryption unless the upstream service exposes and documents those capabilities.

Addy-only features such as username collections, labels, or subscription-specific workflows should not be copied into Mailx until an equivalent IVPN Mailx API contract exists.

## Product principle

Prefer a smaller, truthful interface over decorative controls that imply unsupported server behavior. Any new feature must have:
1. a documented upstream API contract or an explicitly local-only implementation;
2. a Rust domain/use-case implementation where it affects business logic;
3. tests for validation and failure states;
4. a clear UI state for loading, empty, offline and server-error conditions.
