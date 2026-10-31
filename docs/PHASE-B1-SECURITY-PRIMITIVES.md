# Phase B1 — identity and security primitives

Status: implementation baseline; validation results are recorded in the handoff TODO.

## Frozen wire compatibility

B1 adds **no AIDL methods, AIDL files, Parcelable fields, or wire DTOs**. In particular,
`ClientHello` remains byte/order compatible with v2.0 and still contains only its frozen
protocol range, requested capability IDs, and client nonce. The nonce is not authentication.
Pairing Scope requests and challenges remain Runtime-owned internal records in B1. Any later
wire surface must be separately versioned and reviewed before implementation; it must not
silently extend `ClientHello`.

## Caller identity

The verifier derives identity from Binder UID, the complete sorted package set returned by
PackageManager, a reverse package-to-UID ownership check, and the exact current signing
certificate set. It rejects empty, malformed, and shared-UID identities. SHA-256 fingerprints
are lowercase hexadecimal and sorted, so multiple current signers have deterministic identity.
API 26–27 use `GET_SIGNATURES`; API 28+ use `SigningInfo.apkContentsSigners`.

Signing lineage policy is deliberately strict: only current signers are considered;
`signingCertificateHistory` is not an authorization input. A changed signer set therefore
does not inherit a prior pairing and requires explicit re-pairing. Multiple simultaneous
signers are represented as a sorted exact set; any set change invalidates identity equality.

The existing signature-protected `RuntimeV2Service` remains protected by the same manifest
permission. Its legacy path now requires exactly one verified package and exact equality with
the Runtime's current SHA-256 signer set, and repeats this check on each Session method.

## Pairing persistence

`PairingRecord` schema v1 is keyed by package name plus exact current signer digest set and
stores only explicitly granted known Scopes, a monotonically increasing pairing version, and
creation time. UID is intentionally not a persistent identity key. The binary file has a
magic header, explicit file schema, bounded record/string/signer/scope counts, canonical
ordering, and strict validation. `AtomicPairingStore` uses Android `AtomicFile`; unreadable,
truncated, corrupt, or unknown-version state throws and must be treated as no authorization,
not as an empty or permissive store.

## Challenges

Challenge schema v1 uses 256 bits from `SecureRandom`, URL-safe Base64 encoding, Android
`SystemClock.elapsedRealtime()` through `AndroidMonotonicClock`, bounded pending requests, and a
server-held binding to verified UID/package/current signer set and the exact requested Scope set. Approval accepts only a subset of the server-held
request and consumes the challenge; denial also consumes it. Reuse, expiry, and Scope
escalation fail closed. Pending challenges are intentionally in-memory: process death
invalidates them rather than resurrecting a consent request. The consent decision is an
internal Runtime operation, not a client-provided `approved` field.

## Deferred to B2/B3

No exported pairing or paired-client service, consent Activity, persistent pairing lifecycle,
or Scope-authorized business API is exposed by B1. Those are B2/B3 work and remain out of
scope until B1 tests and compatibility checks pass.