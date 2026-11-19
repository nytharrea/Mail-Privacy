# Addy feature port to Mailx

This document tracks the source-level comparison between `Mail-Privacy-main` and
`addy-android-master`. It deliberately separates existing Mailx capabilities,
portable Addy ideas, and features that require server support.

## Already present in Mailx (do not duplicate)

- Alias create/update/delete and server-side pinning.
- Alias bulk enable/disable, pin, delete, restore and forget callbacks.
- Recipients, PGP settings, custom domains, DNS diagnostics and blocklist.
- Rules and rule simulation.
- CSV alias import/export.
- Quick Settings tile and three Android widgets.
- Passkey flows, encrypted preferences and diagnostic logging.

## Addy-derived candidates

| Addy source | Candidate | Mailx status / constraints |
|---|---|---|
| `service/AliasWatcher.kt` | Watch selected aliases for increases in forwarded-message counters | Integrated: alias-detail controls, encrypted watch list, WorkManager polling and actionable notifications. |
| `service/AliasSearchManager.kt` | Android system AppSearch and dynamic shortcuts | Integrated behind an explicit opt-in; disabling clears the local index and shortcuts. |
| `service/BackupHelper.kt` | User-triggered encrypted backup/restore | Integrated using a versioned AES-GCM format and SAF; session credentials are opt-in. |
| `notifications/NotificationHelper.kt` | Actionable, categorized notifications | Alias Watch notification channel and open/stop-watching actions are integrated. |
| `service/BackgroundWorker.kt` | Periodic refresh/check orchestration | Alias Watch uses WorkManager's 15-minute minimum interval and Android background limits. |
| `service/AliasSearchManager.kt` | Per-alias launcher shortcuts | Integrated behind the same explicit privacy opt-in; only active aliases are exposed. |

## Implemented in this change

### Rust alias watch event detector

`crates/mailx-domain/src/alias_watch.rs` adds a pure, deterministic comparison
function. It accepts previous/current alias arrays and a list of watched IDs.
It supports both Mailx's `stats.forwards` and Addy's `emails_forwarded` counter
shapes, ignores deleted aliases, treats missing previous entries as zero, and
does not notify on counter decreases (which may represent a reset).

The function is exposed through `MailxRustBridge.nativeDetectAliasWatchEvents`.
This is the domain/FFI foundation only: it does not yet schedule network checks,
persist the watch list, or post Android notifications.

## Next implementation order

1. Add encrypted watch-list persistence and alias-detail actions.
2. Add a WorkManager-based periodic watcher with a user-configurable interval.
3. Add notification channel and notification actions (open alias / stop watching).
4. Add encrypted backup/restore with a documented, versioned format.
5. Add opt-in AppSearch and pinned-alias shortcuts.
6. Add tests for Android integration and API compatibility.

## Compatibility rule

Do not port Addy-only API operations (subscriptions, usernames, account
notifications, domains, failed deliveries, or webhook/quarantine endpoints)
unless the IVPN Mailx API exposes equivalent documented endpoints.
