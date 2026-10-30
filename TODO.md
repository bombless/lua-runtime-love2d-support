# IPC v2 — Development Handoff

> Workspace: `D:\mcp-agent-workspace\love2d-runtime-bridge`
> Runtime project: `D:\mcp-agent-workspace\ipc-android`
> Authoritative contract: `docs/IPC-PROTOCOL-V2-DESIGN.md`
> Last updated: 2026-10-31

## 0. Read first in a fresh context

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
- [x] Runtime checks Binder UID, package ownership, signing identity, session expiry, and caller identity on Session methods.
- [x] Runtime validates basic ClientHello lengths, protocol range, requested capability count, and capability ID lengths.
- [x] Session expiry is 30 minutes.
- [x] Runtime reports installed app version/build and LuaJ engine metadata; `luaAbi` is empty because LuaJ is a JVM engine without a native Lua C ABI.
- [x] Unimplemented limits are zero and Runtime returns no capabilities rather than claiming unsupported features.
- [x] Initial protocol/auth error code constants exist.

### Last verified builds

- [x] `ipc-android`: `gradlew.bat testDebugUnitTest` — BUILD SUCCESSFUL.
- [x] Companion upstream: `:love:compileNormalDebugJavaWithJavac` — BUILD SUCCESSFUL.
- [x] `git diff --check` passed in both working trees.

### Known gaps — do not overlook

- [ ] **Companion manifest is missing** `<uses-permission android:name="com.bombless.luaandroid.permission.USE_LUA_RUNTIME" />`, while Runtime v2 service now requires that signature permission. Add it and verify both APKs use the same signing certificate for debug/device testing.
- [ ] A differently signed release Companion will be denied until user-mediated pairing is implemented. Define release signing strategy; do not weaken the signature permission as a shortcut.
- [ ] Companion currently checks namespace/major but does not fully negotiate minor versions or capability IDs/schema versions/limits.
- [ ] ProtocolInfo still needs explicit supported-capability descriptors (stable ID, schema version, limits) as described by the design.
- [ ] Session scopes and capability list are empty. No v2 Job, Transfer, Event, Package, or Artifact API exists yet.
- [ ] Client nonce is generated, but server replay/challenge semantics are not implemented. A client-generated nonce alone is not authentication.
- [ ] Review same-certificate package trust and release identity assumptions before production. Never trust a package name supplied by ClientHello as proof of identity.

## 3. Ordered development plan

Work incrementally. Keep Companion and Runtime AIDL/Parcelable wire contracts synchronized. Add tests before exposing new service methods.

### Phase A — freeze and test the protocol contract

- [ ] Finalize exact AIDL/Parcelable schemas: fields, nullability, enum values, bounds, defaults, and serialization order.
- [ ] Add capability descriptors: stable capability IDs, schema versions, and concrete limits.
- [ ] Define structured errors: domain, code, safe message, retryable, optional field path, bounded details, retry-after, correlation ID.
- [ ] Specify major/minor negotiation and unknown optional capability behavior.
- [ ] Define canonical Job states and legal transitions before implementing jobs.
- [ ] Add Parcelable round-trip tests and cross-project contract tests.
- [ ] Add the missing Companion signature-permission declaration; verify debug signing certificates match.
- [ ] Device-test successful binding/session plus missing app, permission denied, incompatible version, and service death.
- [ ] Decide release signing: same certificate preferred; otherwise mandatory user-mediated pairing.

**Exit:** frozen bootstrap/DTO contract; both builds pass; protected binding works on device; negative handshake tests pass.

### Phase B — identity, pairing, authorization

- [ ] Define identity as Binder UID + packages owned by UID + signing-certificate digest. Client-provided package names are never proof.
- [ ] Persist pairing records keyed by package identity and signing digest for separately signed clients.
- [ ] Add Runtime consent UI showing requesting app and requested scopes.
- [ ] Implement least-privilege scopes: `runtime.read`, `jobs.submit`, `jobs.read`, `jobs.cancel`, `packages.read`, `packages.install`, `artifacts.read`.
- [ ] Implement pairing revocation and invalidate associated sessions.
- [ ] Bind sessions to authenticated identity, scopes, expiry, and revocation state; re-check authorization on every method.
- [ ] Test wrong UID, untrusted/mismatched signature, revoked pairing, expired session, missing scope, malformed handshake, and replay.
- [ ] Do not leak stack traces, private paths, or secrets in remote errors.

**Exit:** only authorized callers can use sessions; scopes and revocation are enforced and tested.

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

1. Treat `docs/IPC-PROTOCOL-V2-DESIGN.md` as source of truth. Identify code/design mismatches before editing.
2. Start with Phase A only; do not implement every phase in one pass.
3. Apply every wire-contract change to both projects and test serialization compatibility.
4. Do not advertise a capability until implementation, authorization, limits, and tests exist.
5. Do not invent unresolved semantics; document alternatives and ask before incompatible decisions.
6. Inspect Git status first, preserve unrelated changes, and stage explicit paths only.
7. Do not commit unless the user explicitly asks.
8. Keep Companion independent of Runtime availability and Runtime independent of LÖVE.

## 5. First task

**Start with Phase A.** Inspect both manifests, AIDL, DTOs, and tests. Fix the missing Companion signature-permission declaration and verify signing compatibility. Then freeze capability/error/limit schemas and add cross-project Parcelable/contract tests. Do not start Job, Transfer, pairing UI, or package APIs until Phase A's exit criteria pass.
