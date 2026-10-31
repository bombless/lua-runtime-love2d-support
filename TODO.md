# IPC v2 — Development Handoff

> Workspace: `D:\mcp-agent-workspace\love2d-runtime-bridge`
> Runtime project: `D:\mcp-agent-workspace\ipc-android`
> Authoritative contract: `docs/IPC-PROTOCOL-V2-DESIGN.md`
> Last updated: 2026-10-31

## 0. Read first in a fresh context

**Handoff status (2026-10-31): Phase B1 and B2 host implementation are complete and verified. The next authorized task is finishing B2 Runtime-consent device acceptance only. Do not repeat B0/B1/B2 implementation or start B3/Phase C.**

Read these files before editing:

1. `docs/IPC-PROTOCOL-V2-DESIGN.md` — authoritative protocol proposal.
2. `upstream/love/src/main/java/org/love2d/android/GameActivity.java` — Companion Binder client and lifecycle.
3. `upstream/love/src/main/aidl/com/bombless/luaandroid/ipc/v2/` and `upstream/love/src/main/java/com/bombless/luaandroid/ipc/v2/` — Companion contract and DTOs.
4. `ipc-android/app/src/main/AndroidManifest.xml`, `app/src/main/aidl/com/bombless/luaandroid/ipc/v2/`, and `app/src/main/java/com/bombless/luaandroid/ipc/v2/` — Runtime service, contract, and DTOs.
5. Runtime JobManager, execution engine, result provider, and package manager before designing adapters.

Do not assume the design proposal is fully implemented. Verify each feature against source and tests.

## 1. Architecture boundaries

- **LÖVE2D Companion** owns the LÖVE Android host, native libraries, LuaJIT VM, game lifecycle, rendering, input, audio, and game-private files.
- **Generic Lua Runtime** (`ipc-android`) owns LuaJ execution, generic jobs, durable job records, package catalog/management, authorization, and runtime policy.
- The Lua VMs are separate. Exchange typed protocol data and validated artifacts only—not VM state, native pointers, remote function calls, or arbitrary filesystem paths.
- Do not add LÖVE-specific behavior to the generic Runtime.
- Native Lua modules are not portable just because both engines support Lua 5.1 language semantics. Native delivery/loading needs separate ABI, signing, linker, provenance, and in-process design.

## 2. Verified current state

### Implemented foundation

- [x] Both projects contain matching v2 AIDL bootstrap/session methods: `getProtocolInfo`, `openSession`, `getSessionInfo`, and `getCapabilities`.
- [x] Both projects contain base Parcelable DTOs: `ProtocolInfo`, `ClientHello`, `SessionInfo`, and `CapabilityInfo`.
- [x] Companion explicitly binds to Runtime, performs handshake on a background executor, displays status, and handles timeout/disconnect/retry.
- [x] Runtime v2 service is exported behind a signature-level permission.
- [x] Runtime legacy signature-protected path now uses strict complete-UID package-set validation, reverse package ownership, exact current signer SHA-256 set comparison, and per-Session-call identity revalidation. B1 also adds pairing record/challenge primitives, but no pairing Service/UI or Scope-authorized business API exists yet.
- [x] Runtime validates basic ClientHello lengths, protocol range, requested capability count, and capability ID lengths.
- [x] Session expiry is 30 minutes.
- [x] Runtime reports installed app version/build and LuaJ engine metadata; `luaAbi` is empty because LuaJ is a JVM engine without a native Lua C ABI.
- [x] Unimplemented limits are zero and Runtime returns no capabilities rather than claiming unsupported features.
- [x] Initial protocol/auth error code constants exist.

### Last verified builds

- [x] `ipc-android`: `gradlew.bat testDebugUnitTest` — BUILD SUCCESSFUL.
- [x] Companion upstream: `:love:compileNormalDebugJavaWithJavac` — BUILD SUCCESSFUL.
- [x] Latest Runtime unit tests after B1: 30 tests, 0 failures, 0 errors, 0 skipped (`gradlew.bat testDebugUnitTest --no-daemon`; BUILD SUCCESSFUL).
- [x] Cross-project checker: `python tools/verify-ipc-v2-contract.py` — 7 AIDL files and 5 Parcelable read/write orders match; both manifest permission declarations verified.
- [x] Runtime and Companion debug signing reports use the same keystore and identical SHA-256 fingerprint (see Known gaps).
- [x] `git diff --check` passed in both working trees.

### Known gaps — do not overlook

- [x] Companion declares `<uses-permission android:name="com.bombless.luaandroid.permission.USE_LUA_RUNTIME" />`; Runtime retains signature protection. Runtime and Companion Gradle debug signing reports both use the same per-user debug keystore and identical SHA-256 fingerprint: BE:9B:E5:4F:AC:7D:0B:CC:97:BC:7B:73:39:E0:FE:B0:73:1B:45:35:34:C8:F0:AD:AF:E9:53:3F:D7:D2:2C:B5.
- [x] Release signing policy decided: use the same organization-controlled certificate; if separate release certificates are unavoidable, mandatory user-mediated pairing is required. Do not weaken the signature permission.
- [ ] Companion currently checks namespace/major but does not fully negotiate minor versions or capability IDs/schema versions/limits.
- [x] ProtocolInfo now carries supported-capability descriptors with stable ID, schema version, and limits JSON. The advertised list intentionally remains empty until a capability is implemented and authorized.
- [ ] Session scopes and capability list are empty. No v2 Job, Transfer, Event, Package, or Artifact API exists yet.
- [x] B1 challenge primitives now provide 256-bit SecureRandom tokens, server-held verified-identity/Scope binding, Android monotonic expiry adapter, bounded pending requests, and one-time consume semantics. They are not yet exposed or integrated into a pairing Service; a client-generated `ClientHello.nonce` remains non-authentication.
- [ ] Review same-certificate package trust and release identity assumptions before production. Never trust a package name supplied by ClientHello as proof of identity.

## 3. Ordered development plan

Work incrementally. Keep Companion and Runtime AIDL/Parcelable wire contracts synchronized. Add tests before exposing new service methods.

### Phase A — freeze and test the protocol contract

**Status: Complete — prior phase; do not repeat.**

- [x] Finalize exact AIDL/Parcelable schemas: fields, nullability, enum values, bounds, defaults, and serialization order.
- [x] Add capability descriptor schema: stable capability IDs, schema versions, and concrete limits. No unimplemented capability is advertised.
- [x] Define structured errors: domain, code, safe message, retryable, optional field path, bounded details, retry-after, correlation ID.
- [x] Specify major/minor negotiation and unknown optional capability behavior.
- [x] Define canonical Job states and legal transitions before implementing jobs.
- [x] Add Parcelable round-trip tests and cross-project contract tests.
- [x] Add the Companion signature-permission declaration; Runtime and Companion debug signing fingerprints match.
- [x] Verify protected binding, protocol 2.0 handshake, session creation, overlay, Binder connection, and both APK installs on Realme RMX3800 (Android 16). See the user's Phase A handoff evidence.
- [ ] Negative Phase A cases (missing app, incompatible version, service death, and permission denial) were not all individually evidenced; do not repeat Phase A as part of Phase B unless a Phase B regression directly requires a targeted check.
- [x] Decide release signing: same certificate preferred; otherwise mandatory user-mediated pairing.

**Exit:** accepted based on the Realme RMX3800 Phase A evidence and cross-project contract checks. Phase A is closed; proceed only with Phase B.

### Phase B — identity, pairing, authorization

**Approved security architecture (2026-10-31):** preserve the existing signature-protected `RuntimeV2Service` unchanged as the same-signature path. Add separate, narrowly limited exported entry points for user-mediated pairing and already-paired clients. The pairing entry point must expose no Runtime information, create no Session, and invoke no business operation. A paired-client entry point must authenticate the caller and enforce persisted pairing and Scope authorization on every call. Never weaken or remove the existing `signature` permission.

**Frozen v2.0 compatibility constraint:** do not add `requestedScopes` or otherwise alter the field order/shape of v2.0 `ClientHello`, existing AIDL methods, or existing Parcelable DTOs. `ClientHello` currently contains `nonce` and `requestedCapabilities`, but no `requestedScopes`. `requestedCapabilities` are not user authorization. Carry pairing Scope requests in separate, explicitly versioned pairing DTOs/AIDL. Any future Session-bootstrap protocol extension requires a separately reviewed compatibility design and tests against old clients; do not guess or silently extend v2.0.

**Identity and fail-closed rules:**

- Authenticated identity is derived from `Binder.getCallingUid()`, the complete package set returned by PackageManager for that UID, and Android signing-certificate identity. Client-supplied package names are display/request data only, never proof.
- Revalidate UID ownership, package set, signer digest, and pairing status at every Binder entry point. Default-deny shared UIDs containing more than one package; do not pick a trusted package out of an ambiguous UID.
- Use SHA-256 signing-certificate digests with deterministic handling of multiple signers. Support Android API 26–27 signing APIs as well as API 28+ `SigningInfo`. Define and test signing-certificate rotation/lineage policy explicitly; a changed signer must not silently inherit trust.
- Persist pairing records keyed by verified package identity and signer digest. Use versioned, atomic persistence (Android `AtomicFile` or a separately justified equivalent); corrupt, unknown-version, or unreadable state fails closed.
- Runtime UI must display PackageManager-derived application identity, signer fingerprint, and exact requested Scopes. Only an explicit Runtime-side user approval creates a pairing record.
- Keep the seven least-privilege Scopes distinct from capability IDs: `runtime.read`, `jobs.submit`, `jobs.read`, `jobs.cancel`, `packages.read`, `packages.install`, `artifacts.read`. Approval grants only the user-selected subset. Do not advertise or expose business capabilities that are not implemented.
- Bind each Session to the verified identity, pairing record/version, granted Scopes, and expiry. Check authorization and revocation on every method. Use a monotonic clock for Session lifetime; wall-clock changes must not extend a Session.
- Revocation and authorization changes invalidate all Sessions associated with the affected pairing. Reinstall/signing identity changes require explicit re-pairing unless a reviewed signing-lineage rule proves continuity.
- Do not leak stack traces, private paths, signing material, challenges, nonces, or secrets in remote errors or logs. Normalize Binder-facing errors to bounded, safe structured errors.

**Replay-resistant pairing:**

- A client-generated `ClientHello.nonce` is not authentication; current code only checks its length. Do not treat it as a replay defense.
- Runtime creates a cryptographically random, short-lived, one-time server challenge bound to the Binder UID, complete verified package identity, signer digest, and requested Scopes.
- Challenge state is consumed exactly once on approval/rejection/expiry; duplicate, expired, mismatched, or replayed requests are rejected. The consent result must be bound to Runtime-held pending state, never to a client-supplied `approved` flag.

**B0 — audit and approved design baseline**

- [x] Read `TODO.md`, `DEBUG.md` before its explicitly approved removal, and `docs/IPC-PROTOCOL-V2-DESIGN.md`; transfer the approved architecture and B0 findings into this TODO so the next context does not depend on `DEBUG.md`.
- [x] Audit both repositories' AIDL/DTOs, manifests, V2 service/session code, PackageManager identity checks, tests, and Gradle configuration.
- [x] Record the implementation gaps: incomplete UID/package-set verification; no SHA-256 signer-digest policy; no pairing store or consent UI; no Scope enforcement; no pairing/session revocation; no server challenge/one-time replay protection; incomplete remote error normalization; no corresponding negative security test suite.
- [x] Confirm the existing Runtime V2 Service uses a `signature` permission and must remain protected.
- [x] Confirm v2.0 `ClientHello` has `nonce` and `requestedCapabilities` but no `requestedScopes`; freeze the existing wire contract.
- [x] Approve the separate restricted Pairing Service and paired-client Service architecture, Runtime-side consent, identity/signer-bound persistent pairing, seven Scopes, and fail-closed rules above.
- [x] Baseline checks: cross-project contract checker passed; Runtime Gradle unit-test task reported BUILD SUCCESSFUL (existing reports: 19 tests, 0 failures/errors; task was up-to-date); both `git diff --check` passed. No project source was modified during B0.

**B0 exit:** audit and architecture decision complete. B1 may begin in a fresh context after its preflight checks.

**B1 — identity and security primitives**

- [x] Reinspect both Git states and relevant diffs; preserve all pre-existing user changes, untracked files, and local commits.
- [x] Implement a testable caller-identity verifier: Binder UID, complete UID package set, package ownership, current signer SHA-256 digest, API 26–27 compatibility, API 28+ signing info, and explicit signing-lineage policy (current signer set only; rotation requires re-pairing).
- [x] Reject ambiguous/shared UID identities by default and reject package ownership or signer mismatches; the legacy signature-protected Session path revalidates the exact identity on every call.
- [x] Define versioned pairing/challenge records and atomic persistence semantics; unit tests cover corrupt/unknown records and fail-closed behavior.
- [x] Define and test 256-bit SecureRandom challenges, verified-identity/exact-Scope binding, monotonic expiry, bounded pending requests, one-time approval/denial, and replay/Scope-escalation rejection without changing v2.0 `ClientHello`.
- [x] Document wire compatibility before adding any wire surface. B1 adds no AIDL methods/files or Parcelable fields; see `docs/PHASE-B1-SECURITY-PRIMITIVES.md`.

**B1 exit: PASS (2026-10-31).** Runtime unit tests: 30 tests, 0 failures, 0 errors, 0 skipped. Cross-project checker: 7 AIDL files and 5 Parcelable serialization orders match; Companion signature permission and Runtime protection verified. `git diff --check` passed in both repositories. No v2.0 wire DTO/AIDL contract was changed by B1. API-level signing branches compile; device/API-specific signing behavior remains part of later security acceptance.

**B2 — restricted pairing entry and Runtime consent**

- [x] Add a separate exported pairing AIDL/Service with submit/status/cancel only; it reveals no Runtime metadata, creates no Session, and exposes no business API.
- [x] Keep `RuntimeV2Service`'s existing `signature` permission and behavior intact; its bootstrap AIDL and implementation were not changed by B2.
- [x] Add Runtime-owned consent UI showing PackageManager-derived app label/package, current signer SHA-256 digest, and exact requested Scopes; all Scope choices default unchecked and approval/denial consumes Runtime-held one-time challenge state.
- [x] Persist only explicitly approved pairings through B1 `AtomicPairingStore`; enforce 32 pending challenges, two-minute monotonic expiry, per-UID rate limits, bounded request history, caller-bound cancellation, and denial.
- [x] Add B2 AIDL Stub-boundary negative regression tests (8 tests): caller identity unavailable, UID/package mismatch, shared UID, no client-supplied package identity, malformed/unknown/duplicate Scopes, signer change before consent, expiry/replay, caller-bound status/cancel, denial, and persistence-write failure. These are Robolectric local-Stub tests with injected PackageIdentitySource; they are not represented as cross-process device Binder tests.
- [x] Install and run the separately signed probe on Realme RMX3800 (Android API 36); restricted pairing Binder bind succeeded and `submitPairingRequest` returned `PENDING` with an opaque request ID.
- [x] From the separately signed probe, attempt the signature-protected Runtime v2 path; Android correctly denied it (`DENIED_BY_SIGNATURE_PERMISSION`).
- [x] Query request status from the same caller (`PENDING`), cancel it, then refresh caller-bound status (`CANCELLED`).
- [ ] Complete Runtime-side consent lifecycle on device: visibly launch/locate the consent UI or notification, explicitly approve a selected Scope subset, verify approved state and persisted grant, then cover denial, expiry, and re-application. No approval was performed or inferred from PENDING.
- [ ] Add/execute cross-process negative tests beyond the device probe as practical; local Robolectric Stub tests are not a substitute for a full hostile-client matrix.

**B2 host implementation:** see `docs/PHASE-B2-PAIRING-ENTRY.md`. Runtime final forced verification: `testDebugUnitTest`, `compileDebugKotlin`, and `assembleDebug` all BUILD SUCCESSFUL; 40 tests, 0 failures, 0 errors, 0 skipped (including 8 B2 Stub-boundary tests). Companion `:love:compileNormalDebugJavaWithJavac` BUILD SUCCESSFUL with tasks forced. Both v2.0 and B2 cross-project checkers pass; `git diff --check` passes in both repositories.

**B2 exit:** PENDING Runtime consent approval and remaining lifecycle acceptance. On RMX3800 (Android 16/API 36), a separately signed probe successfully bound to the restricted pairing Binder, submitted a request (`PENDING`), was denied access to RuntimeV2Service by the signature permission (`DENIED_BY_SIGNATURE_PERMISSION`), queried status, cancelled the request, and confirmed `CANCELLED`. The probe was then uninstalled. The consent approval surface was not successfully exercised; do not claim selected-Scope approval, persisted grant, denial, expiry, or re-application device PASS. Do not start B3 or Phase C. Existing signature-protected path remains protected.

**B3 — paired-client entry, Scopes, and Session authorization**

- [ ] Add the separate paired-client AIDL/Service; authenticate every Binder call against current UID/package/signer and a persisted, active pairing.
- [ ] Enforce the seven Scopes independently and least-privilege; no empty/default/unknown Scope may grant access.
- [ ] Bind Session to pairing identity/version, granted Scopes, and monotonic expiry; re-check authorization on every method.
- [ ] Ensure the existing v2.0 bootstrap contract remains byte/order compatible; any required extension must be a separately versioned interface and reviewed before implementation.
- [ ] Normalize all remote errors to safe, bounded structured errors without stack traces, private paths, or secrets.
- [ ] Test missing Scope, Scope escalation, expired Session, UID change, package-set change, signer change, and Session isolation across pairings.

**B3 exit:** only an actively paired identity with the required Scope can use the paired-client interface; every Session operation revalidates identity, expiry, and authorization.

**B4 — revocation, persistence lifecycle, and recovery**

- [ ] Implement user-visible pairing listing and explicit per-client revocation in Runtime.
- [ ] Revocation atomically changes pairing state/version and immediately invalidates every associated Session, including already-open Binder Session objects.
- [ ] Revalidate persisted pairing identity against PackageManager on every use; uninstall/reinstall, package-set change, signing mismatch, corrupt state, and unsupported schema fail closed.
- [ ] Define behavior for Runtime process death/restart and device reboot; do not resurrect revoked or expired authority.
- [ ] Test revocation races with in-flight calls, restart/reboot persistence, atomic-write interruption, corrupted records, and signer/package changes.

**B4 exit:** revocation is effective immediately and survives process restart; persistence failures cannot restore authorization.

**B5 — security acceptance and device verification**

- [ ] Add a complete negative security suite: wrong UID; forged package identity; mixed/shared UID; untrusted/mismatched signer; signer rotation; no pairing; denied/revoked pairing; missing/unknown Scope; Scope escalation; expired Session; replayed/expired challenge; malformed/oversized DTO; corrupted persistence; Runtime restart; and remote-error leakage.
- [ ] Run Runtime unit tests and Companion/Runtime contract and Parcelable compatibility tests; report exact executed test counts and distinguish UP-TO-DATE from actually executed tests.
- [ ] Build both APKs without cleaning or overwriting user outputs; inspect build diffs and preserve local artifacts.
- [ ] Verify on the already-connected Realme RMX3800 first. Do not switch devices/emulators unless that device is unavailable or a documented technical reason requires it.
- [ ] Verify same-signature legacy path still works, separately signed pairing cannot access data before consent, approval grants only selected Scopes, and revocation blocks existing Sessions.
- [ ] Record device/API, commands, test results, APK hashes, and relevant sanitized logs. Do not claim complete acceptance from build/install success alone.

**B5 / Phase B exit:** all identity, pairing, Scope, expiry, replay, revocation, compatibility, and negative-test criteria pass on unit/instrumentation tests and the target device. Only then may the next phase be considered; do not begin Phase C without separate scope authorization.

### Phase C — transfer data plane

- [ ] Define transfer spec/handle/commit DTOs, purpose, declared size, media type, checksum, expiry, and ownership.
- [ ] Use bounded file-descriptor streams (prefer service-returned `ParcelFileDescriptor`) or narrowly scoped short-lived URI grants.
- [ ] Never accept/expose another app's absolute filesystem path.
- [ ] Enforce per-session/global quotas, max size, metadata limits, concurrency, expiry, and cleanup.
- [ ] Verify actual byte count and checksum before atomically publishing uploads.
- [ ] Keep incomplete uploads invisible; downloads are read-only and scoped to the owning session/job.
- [ ] Audit ResultContentProvider; if used, implement per-URI grants and ownership checks, not global readability.
- [ ] Test oversized/truncated uploads, checksum mismatch, expiry, cross-session access, duplicate finalize, process death, and cleanup.

**Exit:** bounded, authenticated, integrity-checked transfer without large Binder payloads or path-based authority.

### Phase D — durable Job API

- [ ] Define typed JobSpec/Receipt/Snapshot/Query, pagination, and cancellation result.
- [ ] Job source is a transfer/artifact ID, not a large inline script. Bound args, allow-list typed environment, use workspace IDs, and define time/memory/output limits.
- [ ] Reject arbitrary Bundle maps, shell commands, reflection targets, and absolute paths.
- [ ] Implement idempotency using client request ID scoped to pairing; duplicate submissions return original receipt.
- [ ] Persist job state/revision/timestamps/owner/idempotency/result/error/artifact metadata transactionally.
- [ ] Enforce queue/concurrency limits and per-client quotas.
- [ ] States: ACCEPTED → QUEUED → STARTING → RUNNING → terminal. Terminals: SUCCEEDED, FAILED, CANCELLED, TIMED_OUT, REJECTED, INTERRUPTED. CANCEL_REQUESTED is intermediate.
- [ ] Cancellation acknowledgement is not terminal cancellation; exactly one terminal state wins races.
- [ ] Recover persisted records after process death; mark non-resumable active jobs INTERRUPTED.
- [ ] Jobs continue after Companion disconnect by default; isolate workspaces per job.
- [ ] Enforce authorization on submit/query/cancel.
- [ ] Test idempotency, queue full, timeout, cancellation races, process restart, ownership isolation, output truncation, and retention.

**Exit:** durable, typed, bounded Job API with recovery and authorization tests.

### Phase E — events and replay

- [ ] Persist monotonically increasing event sequence numbers as defined by the final contract.
- [ ] Define event envelope/types/revisions/timestamps and ordering guarantees.
- [ ] Implement bounded `getEvents(afterSequence, limit)` and pagination/replay behavior.
- [ ] Binder callbacks are advisory and may be duplicated/lost; snapshots + replay are authoritative.
- [ ] Companion resynchronizes after reconnect using snapshots and event replay.
- [ ] Define retention and response when requested sequence is no longer available.
- [ ] Test gaps, duplicates, ordering, reconnect, service restart, and replay limits.

**Exit:** client can reconstruct job state after disconnect/restart without relying on callbacks.

### Phase F — package catalog and compatibility

- [ ] Implement read-only catalog/details first. Defer installation until resolver, provenance, dependency, and rollback semantics exist.
- [ ] Describe package ID/version/publisher/license/source/checksum/signature/dependencies/file manifest and per-file hashes.
- [ ] Classify pure-Lua, native, and unsupported/system-dependent packages.
- [ ] Record consumer compatibility: Lua language/API target, Lua C ABI, Android ABI, min API, native dependencies, entry points.
- [ ] Evaluate compatibility against the LÖVE consumer VM, not Runtime LuaJ.
- [ ] Explicitly reject/label incompatible native rocks; Lua 5.1 language compatibility does not imply LuaJIT C ABI compatibility.
- [ ] Do not advertise the current rudimentary package manager as complete LuaRocks resolution.
- [ ] Add pagination, scope enforcement, metadata validation, and compatibility tests.

**Exit:** accurate read-only package discovery. Installation/native module delivery remains a separately approved future phase.

### Phase G — Companion client integration

- [ ] Move Binder operations into a typed v2 client/repository layer rather than expanding `GameActivity`.
- [ ] Negotiate major/minor versions and capability IDs/schema versions/limits.
- [ ] Request only capabilities actually needed.
- [ ] Keep Binder and file operations off the LÖVE/render thread.
- [ ] Add job/transfer/event/package UI only after corresponding Runtime APIs are complete.
- [ ] Distinguish permission denied, pairing required/revoked, protocol mismatch, unsupported capability, service unavailable, session expiry, and retryable errors.
- [ ] Keep games runnable without Runtime; imported modules/assets live in Companion private storage, never via Runtime paths.
- [ ] Test reconnect, service death, long-running jobs, progress, artifact retrieval, and app lifecycle.

**Exit:** Companion consumes only the frozen documented contract and degrades gracefully without Runtime.

### Phase H — security and release verification

- [ ] Hostile-input/fuzz tests for every DTO and operation.
- [ ] Verify Binder payloads remain small; use transfer streams for large content.
- [ ] Test signature mismatch, revoked pairing, scope escalation, cross-client access, path traversal, URI leakage, and replay.
- [ ] Test process death, Runtime restart, reboot, cleanup, retention, and quota exhaustion.
- [ ] Test both APKs across supported Android API/ABIs and debug/release signing.
- [ ] Build from clean checkouts; record commands, device/API/ABI, APK paths/hashes, logs, and results.
- [ ] Update design docs and this TODO only when features are implemented and verified.

## 4. Rules for the next context

1. Treat `docs/IPC-PROTOCOL-V2-DESIGN.md` as the authoritative existing v2.0 contract. Treat the approved Phase B architecture and acceptance criteria in this TODO as the implementation plan.
2. Phase A, B0, B1, and B2 host implementation are complete. Resume only the outstanding B2 Runtime-consent device acceptance; do not reimplement B2 or start B3.
3. At the start of every B subphase, inspect Git status and relevant diffs in both repositories. Preserve every user modification, untracked file, local commit, and build artifact. Never use `git reset`, `git clean`, `git checkout`, forced overwrite, or bulk rollback.
4. Implement only the current B subphase. Check prerequisites, implement the minimum required changes, build, test, and verify its exit criteria before proceeding to the next B subphase.
5. Keep existing v2.0 AIDL/Parcelable field order and behavior compatible. Do not add `requestedScopes` to `ClientHello`; pairing Scope requests belong to separately versioned pairing DTOs/AIDL.
6. Never weaken the existing signature-level permission on `RuntimeV2Service`. Pairing and paired-client entry points must remain separate, narrowly scoped, and fail closed.
7. Apply any new cross-project wire contract consistently to both projects and test serialization/old-client compatibility.
8. Do not advertise a capability or Scope as a working business feature until implementation, authorization, limits, and tests exist.
9. Do not invent unresolved semantics; document concrete risks/options and ask before incompatible or security-sensitive decisions.
10. Prefer the already-connected Realme RMX3800 for device verification. Do not switch devices/emulators without a documented reason.
11. Distinguish code changes, build success, installation, unit tests, and full device acceptance in every report. Do not claim full acceptance from build/install success alone.
12. Do not commit unless the user explicitly asks. Keep Companion independent of Runtime availability and Runtime independent of LÖVE.
13. Do not enter Phase C or later phases without separate user authorization.

## 5. First task for a fresh context

**Resume Phase B2 device acceptance only; do not reimplement the host-side B2 feature.** Read this TODO, `docs/IPC-PROTOCOL-V2-DESIGN.md`, `docs/PHASE-B1-SECURITY-PRIMITIVES.md`, and `docs/PHASE-B2-PAIRING-ENTRY.md`. Inspect fresh Git status and relevant diffs in both repositories before editing. Host-side B2 implementation and forced verification are complete: Runtime 40 tests passed (0 failures/errors/skips), Companion Java/AIDL compile passed, v2.0 and B2 contract checkers passed, and `git diff --check` passed. On RMX3800/API 36, the separately signed probe bound successfully to the restricted pairing Binder, submitted and queried a PENDING request, was denied by RuntimeV2Service signature permission, cancelled the request, and confirmed CANCELLED; probe was uninstalled. Remaining blocker is Runtime consent device acceptance. Investigation found that `startActivity()` from the pairing Service could return without making the consent Activity visible; on RMX3800 the Activity task was not foreground and no pairing notification channel existed. Runtime now uses a user-tapped notification as the sole consent entry point. The Runtime APK update installed successfully, and forced Runtime verification passed (40 tests; 0 failures/errors/skips; compile and assemble passed; both cross-project contract checkers and both `git diff --check` passed). The preserved separately signed probe APK is valid (ZIP intact, minSdk 26, targetSdk 35, v2 signature verified, signer SHA-256 965e5e7daf48ae30abc1957daf95ad00c32f3ca8c511c6a00151bf88c948ece6) and was successfully installed and launched on RMX3800 via `adb install -r` (`Success`); package `com.bombless.b2probe` is installed. A fresh app launch then showed `PAIRING_BIND_REQUESTED=false`; device log identifies Oplus `OplusAppStartupManager` preventing startup of `com.bombless.luaandroid/...RestrictedPairingService` when called by the foreground probe. This is now an OEM background-start/autostart policy blocker, not an APK parse failure. Do not bypass device security settings. Ask the user to allow Runtime (`com.bombless.luaandroid`) auto-launch/background startup in the phone's app-launch/battery settings, then retry pairing bind and verify the consent notification. Next, trigger a fresh request, verify the notification is visible, tap it, approve only a selected Scope subset, and verify status plus persisted grant. Also test denial, expiry, and re-application on device. Do not claim approval from PENDING. Keep v2.0 ClientHello and signature-protected RuntimeV2Service unchanged. Do not start B3 or Phase C. Preserve all dirty/untracked files and build artifacts; do not clean, reset, overwrite, or commit.