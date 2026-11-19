# Mailx Android Design System

## Direction

Mailx uses the **Night Fjord** visual language: deep graphite surfaces, sea-glass mint actions, cool-blue secondary actions, and restrained lilac for specialist privacy tools. Light mode uses a cool pearl background. Status colors remain semantic and are not reused as decoration.

The Android UI remains Jetpack Compose because it owns Android window insets, accessibility semantics, widgets, Credential Manager, passkeys, and platform interactions. The application rules, route transitions, data processing, security tools, and API client remain Rust-first and are exposed through the JNI facade.

## Interaction principles

- Five destinations remain stable: Overview, Aliases, Recipients, Rules, Settings.
- Primary actions are reachable from the current context; create-alias and send-from-alias flows remain bottom sheets.
- Alias lists keep search, filters, pinning, pause/restore, and bulk operations.
- Reduced Motion disables expressive transitions; all navigation and status changes remain understandable without animation.
- Touch targets for compact top-bar actions are at least 38 dp; primary navigation remains label + icon.
- Cards use consistent 16/22/28 dp shape tokens rather than arbitrary radii.
- Do not show simulated account, subscription, security, or relay data as if it came from the server.

## Addy.io inspiration

The Addy Android source was reviewed for its focused alias-management flows, compact list presentation, bottom-sheet editing patterns, and clear bottom navigation. This project uses those general interaction ideas only. No Addy source code, icons, or assets were copied into Mailx.

## Color tokens

See `android-app/app/src/main/java/net/mailx/android/ui/theme/Color.kt`. Brand mint is reserved for the primary action; blue indicates secondary send/relay actions; lilac is reserved for specialist tools; success, warning, and blocked states retain separate semantic colors.
