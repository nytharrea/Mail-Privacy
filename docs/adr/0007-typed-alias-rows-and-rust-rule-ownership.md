# ADR 0007: Typed alias rows and Rust rule ownership

Status: accepted, 2026-11-27

## Context

In `v0.5.0-rc1`, filtering and sorting the alias list in Compose converted every `UiAlias` back into a `JSONArray` string, sent it across UniFFI to `skeleton.rs` (which duplicated the filter/sort logic already implemented in `mailx-domain::alias_engine`), returned JSON, and deserialized every `UiAlias` again on every search keystroke or chip change. In addition, the `High spam` status threshold (`blocks >= 3 && blocks >= forwards`) lived inside `AliasCard.kt`.

## Decision

- `crates/mailx-domain/src/alias_engine.rs` is the single source of truth for alias filtering, sorting, status chip priority (`HighSpam` -> `Paused { days_left }` -> `Disabled` -> `Wildcard`), pause countdown calculation, and deterministic avatar tint (`0..=7`, FNV-1a over lowercased address without fetching favicons).
- `crates/mailx-runtime/src/skeleton.rs` delegates filtering and sorting directly to `mailx_domain::alias_engine` rather than maintaining a second copy.
- `rust-core/src/uniffi_api.rs` mirrors `AliasChip`, `AliasKind`, `AliasRow`, `AliasQuery`, and `AliasPage` via UniFFI and caches the active `Vec<Alias>` inside `MailxCore`.
- Kotlin syncs raw alias data into Rust once on fetch/update (`set_cached_aliases_json`) and queries typed `AliasPage` rows (`alias_rows(query, now_unix)`) with zero JSON conversions on search, filter, or sort changes.
- Compose reads Rust's `AliasChip` directly instead of recomputing spam/status thresholds in UI code.

## Consequences

- Search, filter, and sort changes perform zero JSON serialization or deserialization across the FFI boundary.
- Adding a new `AliasChip` variant in Rust causes a compile-time error until exhaustive mappings are updated.
