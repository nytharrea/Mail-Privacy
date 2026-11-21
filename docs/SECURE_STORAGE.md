# Secure storage

## Current design

- Application preferences are stored in an app-private Preferences DataStore file.
- The serialized JSON payload is encrypted and authenticated with Tink AES-256-GCM.
- Tink's keyset is wrapped by a master key in Android Keystore.
- New installations request a StrongBox-backed AES-256-GCM master key on Android 9+
  when StrongBox is available.
- If StrongBox is unavailable, the app falls back to a hardware-backed TEE key.
  A software-backed master key is rejected.
- API 31+ reports the Keystore security level as StrongBox, TEE, or software.
  API 26-30 can only report hardware-backed versus software-backed status.

## Existing installations

An already-provisioned hardware-backed master key is retained so the existing
Tink keyset and encrypted DataStore remain readable. This means an installation
that already has a TEE key remains on TEE; it is not silently replaced with a
new StrongBox key. A fresh installation provisions StrongBox where supported.

## Persistence guarantees

Legacy property accessors remain temporarily for Android platform call sites.
Writes are serialized through a single DataStore writer. Authentication and
backup-restore flows use `setSessionSecretsAndPersist` / `persistPendingWrites`
to wait for the DataStore transaction before reporting completion.

The compatibility property setters remain asynchronous. Callers that require
read-after-write durability must call `persistPendingWrites` or use the
suspend session API.

## Failure behavior

- Tink initialization fails closed.
- A software-backed Android Keystore master key is rejected.
- Decryption/authentication failures are surfaced as DataStore corruption.
- DataStore write failures are propagated by the explicit persistence barrier.

## Remaining validation

CI compilation does not prove the actual device's security level. Verify on
physical hardware using `getMasterKeySecurityLevel()` and exercise StrongBox,
TEE fallback, key invalidation, process death during writes, and corrupted
ciphertext scenarios.
