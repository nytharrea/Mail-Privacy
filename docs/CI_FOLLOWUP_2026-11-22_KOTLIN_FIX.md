# CI Follow-up — 2026-11-22

## Source of the failure

Both Android Debug and Android Release jobs failed while compiling
`MailxViewModel.kt`:

- Line 747: `Expecting 'catch' or 'finally'`
- Line 2276: `Missing '}'`
- `MailxViewModelFactory` was then reported unresolved as a downstream parser error.

The underlying issue was an incomplete `try` statement in
`loadAliasesInternal()`: the `else if (showIndicator)` branch was not closed
before `finally`.

## Change made

- Closed the `else if` branch correctly.
- Added an exception handler before `finally`.
- Re-threw coroutine cancellation rather than swallowing it.
- Kept loading-state cleanup in `finally`.

This is a source-level repair only. Android compilation was not run locally,
so the fix still requires GitHub Actions confirmation.

## Other CI observations

- Rust workspace tests passed in the supplied logs (62, 20, 33 and 19 tests
  in the reported suites; one test was ignored).
- `cargo fmt` and both Clippy configurations completed successfully.
- The Cargo.lock check still warns that the lockfile is stale. Run the
  repository's `Update Cargo.lock` workflow and commit its result.
- 16 KB ELF alignment checks ran for the listed Rust libraries.
- Cache reservation warnings are non-fatal and unrelated to the Kotlin
  compilation failure.
