# ADR 0001: OS CSPRNG for all randomness

Status: accepted, 2026-11-20

## Context

Alias suffixes, word/passphrase local parts and UUID-style local parts were produced from a generator seeded with the clock and a process counter. Such values are predictable to an observer who knows roughly when an alias was created, which defeats the purpose of an unguessable alias. The entropy figures shown to users were also not derived from the real list sizes and overstated what the generators delivered.

## Decision

- Every random value comes from the operating-system CSPRNG through `mailx_utils::random` (`getrandom`).
- Bounded integers use rejection sampling (`random_below`, `random_index`), so there is no modulo bias.
- `random_uuid_v4` sets the version and variant bits explicitly; `random_hex` is the only hex source.
- Entropy figures are computed from the real pool sizes (`entropy_bits_floor`, `words_local_part_entropy_bits`, `random_suffix_entropy_bits`) and rounded down, so the UI never claims more than the generator provides.
- If the OS RNG fails, `fill_random` panics with a clear message instead of falling back to a weaker source. The JNI layer runs under `catch_unwind` (`panic = "unwind"`), so the failure surfaces as an error envelope.
- TOTP uses RustCrypto `hmac` and `sha1` instead of hand-written primitives, and key material is held in zeroizing buffers.

## Consequences

- New dependencies: `getrandom 0.3`, `hmac 0.12`, `sha1 0.10`, `zeroize 1`.
- Tests cover the sampler with scripted input (including rejection), bucket coverage, UUID layout, honest entropy values, and the RFC 6238 / RFC 2202 vectors for TOTP.
- Any new feature that needs randomness must call `mailx_utils::random`; do not reintroduce time-seeded generators.
