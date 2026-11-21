# ADR 0002: Network host and credential policy

Status: accepted, 2026-11-20; amended 2026-11-21 after an independent review of the network layer

## Context

The HTTP client was a single block that was only reachable through a real network stack, so its security behavior (where credentials may be sent, how redirects are handled) could not be unit tested. It also tried guessed `api.<host>` variants of the server the user typed, which could send an access key to a host the user never chose.

## Decision

- `mailx_network::client` talks to a `Transport` trait. `UreqTransport` is the production implementation behind the `network` feature; tests use an in-memory transport.
- Credentials are sent only to the host the user entered. There is no guessed `api.<host>` fallback. The single rewrite is first-party: `mailx.net`, `www.mailx.net` and `app.mailx.net` (web hosts of the official service) are mapped to `api.mailx.net`.
- The server URL is validated before any request. The authority must be an ASCII host name (letters, digits, `-` and `.`, no empty labels) or a bracketed IPv6 literal, with an optional numeric port from 1 to 65535, so credentials, percent signs and backslashes inside the authority are rejected. The URL as a whole may not contain backslashes, whitespace, control characters, a query or a fragment. URL parsers disagree about characters such as `\` inside the authority, so anything outside this grammar is refused instead of interpreted. Internationalized host names must be entered in punycode form.
- HTTPS is required. Plain HTTP is accepted only for the loopback hosts `localhost`, `127.0.0.1` and `::1`, and only when the caller enables it. `10.0.2.2`, the emulator's alias for the development machine, is not accepted: on a real device it is an ordinary LAN address. Emulator development can use `adb reverse` together with `localhost`.
- The transport never follows redirects itself. The client follows at most two redirects, only to the same origin, never to a different scheme, and refuses protocol-relative targets and targets that contain backslashes or control characters. A cross-origin redirect is refused and credentials are not forwarded. Method handling follows the Fetch standard: 303 turns any method except GET and HEAD into a body-less GET, 301 and 302 do so only for POST, and every other combination, including 307 and 308, keeps method and body. A PUT or DELETE is therefore never replaced by a GET that could report a false success.
- Every identifier interpolated into a URL path is percent-encoded as a single segment (`encode_path_segment`). Because `%2E%2E` is itself a dot segment for URL parsers and for servers that normalize paths, request-path validation also rejects empty segments, `.` and `..` segments (including their percent-encoded forms) and backslashes before anything is sent. Identifiers that are empty or consist of dots only therefore fail locally.
- Calls have timeouts (liveness 10 s, API 15 s, raw 20 s, CSV import 60 s), response bodies are capped at 10 MiB, and server error messages are truncated to 300 characters whether they arrive as JSON `error` or `message` fields, plain text or HTML.
- An authentication response without a usable session token is reported as unauthorized instead of storing a session that cannot be used.
- A body counts as the web frontend (HTML) only when it starts with markup, so JSON that merely mentions `<body>` is not discarded.
- When both candidate routes answer 404 or 405 and only the first carries a JSON error message, that message is reported instead of the bare 404 of the alternate route.
- `Debug` output of requests and responses redacts credential headers and omits bodies; `AuthResponse` redacts its token.
- Request methods passed to the raw call must be HTTP tokens.

## Consequences

- 62 contract tests exercise host policy, authority grammar, redirects, path validation, error mapping, redaction and the alternate-path behavior through the mock transport. An `#[ignore]` live test and the manual `live-smoke` workflow check the same flows against a real account.
- Certificate pinning and the platform trust store are not part of this decision; see [ADR 0005](0005-deferred-platform-security-work.md).
- Requests are blocking and bounded by timeouts. Cooperative cancellation would need an async transport and is future work.
- Plain HTTP to loopback addresses stays available in every build because the user has to type such a server URL deliberately and the traffic never leaves the device.
- Login falls back to built-in defaults (`mailx.net`, word aliases) when the defaults endpoint cannot be fetched; the session itself is valid in that case.
