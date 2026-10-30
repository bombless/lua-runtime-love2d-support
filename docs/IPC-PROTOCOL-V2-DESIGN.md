# IPC Protocol v2 — Design Proposal

- Status: Draft for review; not implemented.
- Date: 2026-10-31
- Scope: coordinated changes to ipc-android (generic Runtime server) and love2d-runtime-bridge (LÖVE Companion client).
- Compatibility: no legacy external client is currently in scope. Prefer a clean v2 contract over preserving accidental v1 API shape.

## 1. Goals and boundaries

The Runtime server owns the LuaJ execution engine, job scheduler, durable job records, package catalog/installation, runtime policy, and authorization. The Companion owns the LÖVE engine and its LuaJIT VM, game lifecycle, user-facing consent, and imported modules in its own private storage.

The two Lua VMs remain separate even though both expose Lua 5.1 language semantics. The protocol exchanges data and artifacts, not VM state, native pointers, or direct function calls. LÖVE-specific behavior must not leak into the generic Runtime.

Control plane: typed AIDL/Binder calls for handshake, authorization, capabilities, jobs, events, package metadata, and transfer setup.

Data plane: bounded file-descriptor streams or narrowly granted content URIs. Binder carries small metadata only; filesystem paths are never cross-app authority.

## 2. Protocol identity and negotiation

Use protocol namespace com.bombless.luaandroid.ipc.v2.

ProtocolInfo should include:
- protocolMajor and protocolMinor;
- stable opaque runtimeId, runtimeVersion, serviceVersion, and buildId;
- engineId/engineVersion, Lua language version, and optional Lua ABI;
- supported capability IDs, each with a schema version and concrete limits;
- limits for metadata bytes, transfer bytes, input count, concurrency, queue depth, execution time, output bytes, and event batch size.

Rules:
- The bootstrap interface is intentionally tiny and frozen: getProtocolInfo and openSession.
- Incompatible major versions are rejected. Minor versions add optional fields/capabilities.
- Unknown optional fields/capabilities are ignored; an operation requiring an unknown capability fails explicitly.
- Clients must never infer capabilities from version strings.

## 3. Identity, pairing, and authorization

A normal-level Android permission is not a sufficient security boundary for a code-execution service.

Preferred deployment:
1. Use a signature-level permission when both apps are distributed with the same signing certificate.
2. Verify Binder caller UID, package ownership for that UID, and signing-certificate digest on session establishment.
3. For separately signed builds, require explicit user pairing in the Runtime app.
4. Persist pairing records keyed by package name plus signing-certificate digest, with granted scopes and revocation state.
5. Bind each session to the authenticated UID and pairing record; re-check caller UID on every method.
6. Support revocation and session expiry.

Suggested scopes: runtime.read, jobs.submit, jobs.read, jobs.cancel, packages.read, packages.install, artifacts.read. The Runtime UI must show the requesting app identity and requested scopes. Default to least privilege. Never trust a package name supplied by the client as proof of identity.

Debug and release signing need an explicit plan. Do not ship a production allow-list based on a development debug certificate.

## 4. Binder API shape (conceptual)

Bootstrap:
- getProtocolInfo() -> ProtocolInfo
- openSession(ClientHello) -> ISession or a typed authorization/protocol error

ClientHello contains client version/build, supported protocol range, requested capabilities, and a client nonce. Caller identity is derived from Binder, not from these fields.

Session operations:
- getSessionInfo(), getCapabilities()
- createUpload(TransferSpec) -> TransferHandle
- finalizeUpload(transferId, TransferCommit)
- submitJob(JobSpec) -> JobReceipt
- getJob(jobId), listJobs(JobQuery, PageToken)
- cancelJob(jobId, CancelOptions) -> CancelResult
- subscribe/unsubscribe for advisory callbacks
- getEvents(afterSequence, limit) for durable event replay
- getPackageCatalog(PackageQuery, PageToken)
- getPackageDetails(packageId, version)
- requestPackageInstall(InstallSpec), only when capability and policy permit
- openArtifact(jobId, artifactId) -> read-only descriptor or short-lived URI grant

Keep the AIDL interface typed and auditable. Do not expose arbitrary method names, shell commands, reflection, filesystem paths, or generic RPC dispatch.

## 5. Job contract

JobSpec should contain:
- client-generated requestId/idempotency key;
- source as a transfer ID or immutable script artifact reference, not a multi-megabyte inline string;
- bounded arguments array;
- typed allow-listed environment map, not arbitrary Bundle;
- workspace ID, never an absolute path;
- timeout, memory, and output limits;
- input artifact IDs;
- optional required capabilities and a user-visible execution label.

Submission must validate all limits and return quickly. Repeated requestId from the same pairing returns the original receipt rather than starting duplicate execution.

Canonical states:
ACCEPTED -> QUEUED -> STARTING -> RUNNING -> terminal.
Terminal states: SUCCEEDED, FAILED, CANCELLED, TIMED_OUT, REJECTED, INTERRUPTED.
CANCEL_REQUESTED is an intermediate state. Cancellation is a request, not a promise; report acceptance separately from the eventual terminal state. Exactly one terminal state wins races. After process death, non-resumable active jobs become INTERRUPTED.

JobSnapshot includes IDs, owner pairing, requestId, state and revision, timestamps, progress, exit status, structured error, bounded stdout/stderr counts and truncation flags, artifact descriptors, and retryability.

Callbacks are advisory and may be duplicated or lost. Every event has a monotonically increasing sequence number. Clients recover using snapshots and getEvents(afterSequence); polling remains supported.

## 6. Transfer contract

- Binder metadata budget target: no more than 64 KiB per call.
- Upload is two-step: create transfer with purpose, declared size, media type, checksum and expiry; stream through a write-only descriptor; finalize and verify actual size/checksum.
- Download is read-only and scoped to one artifact.
- Enforce per-transfer/session quotas, expiry, cleanup, and atomic publication after validation.
- Use opaque transfer IDs; never accept caller absolute paths.
- Prefer service-returned PFD streams. If ContentProvider URIs are used, issue short-lived per-URI grants and verify session ownership. An exported provider must never be globally readable.
- Incomplete uploads are not visible as committed artifacts.

## 7. Package metadata and compatibility

Package descriptors should include canonical ID, name, semantic version, publisher, description, registry/source, SPDX license, checksum/signature provenance, dependency constraints, package kind, Lua language/API target, entry points, file manifest and per-file hashes.

Native package descriptors additionally require ABI, Android min API, NDK/toolchain, Lua C ABI, shared-library dependencies, and signing identity.

Compatibility is evaluated against the consumer engine, not the Runtime engine. LuaJIT's Lua 5.1 language compatibility does not imply compatibility with LuaJ native modules or LuaJ C API binaries. Initially expose package catalog/details and pure-Lua transfer only. Native module install/loading remains unavailable until signed, ABI-specific, in-process delivery is separately designed.

The current LuaRocksManager is rudimentary; protocol capabilities must describe implemented behavior, not imply a complete LuaRocks resolver.

## 8. Structured errors

Every operation returns or throws a stable structured error with domain, code, safe message, retryable, optional fieldPath, bounded details, retryAfterMillis, and correlationId.

Initial families:
- protocol: PROTOCOL_MAJOR_MISMATCH, UNSUPPORTED_CAPABILITY, INVALID_MESSAGE;
- auth: PAIRING_REQUIRED, PAIRING_REVOKED, SCOPE_DENIED, CALLER_IDENTITY_INVALID;
- transfer: LIMIT_EXCEEDED, TRANSFER_EXPIRED, CHECKSUM_MISMATCH, CONTENT_REJECTED;
- jobs: JOB_NOT_FOUND, JOB_CONFLICT, QUEUE_FULL, EXECUTION_TIMEOUT, EXECUTION_CANCELLED, EXECUTION_INTERRUPTED;
- packages: PACKAGE_NOT_FOUND, DEPENDENCY_UNSATISFIED, PACKAGE_INCOMPATIBLE, NATIVE_MODULE_UNSUPPORTED;
- internal: INTERNAL_ERROR.

Do not expose stack traces, private paths, or secrets in remote errors.

## 9. Persistence, ownership, and cleanup

Persist jobs, pairing records, idempotency keys, event sequence, and artifact metadata transactionally. Every job/artifact belongs to its creating pairing by default; cross-client enumeration is denied. Define retention by age and storage quota. Clean incomplete uploads, expired grants, and orphaned directories. Process death must not erase accepted job records.

## 10. Initial capability set

Recommended first release:
- runtime.info.v1, runtime.capabilities.v1
- jobs.submit.v1, jobs.query.v1, jobs.cancel.v1, jobs.events.v1
- transfers.upload.v1, artifacts.download.v1
- packages.catalog.read.v1, packages.details.read.v1

Defer package installation until resolver, provenance, dependency, and rollback semantics exist. Defer native modules, arbitrary filesystem browsing, shell/process execution, and generic RPC.

## 11. Existing v1 gaps to fix

- normal permission and no signing-certificate allow-list are insufficient for a code-execution service.
- The exported results provider does not itself enforce per-client authorization.
- JobManager keeps jobs and callbacks in memory; there is no durable recovery.
- submit() synchronously stages file descriptors and lacks complete transfer quotas/checksums.
- Bundle environment and loose string states/errors are weakly typed.
- Request apiVersion is not visibly enforced.
- Callback registration is process/session-local and cannot guarantee event delivery.
- Filename validation is not a complete workspace authorization model.
- Current package manager behavior must not be advertised as full LuaRocks support.

## 12. Decisions to settle before implementation

1. Will both APKs use the same release signing certificate? Recommended: yes, plus UID/certificate verification; otherwise mandatory user-mediated pairing.
2. Pairing granularity: approve app identity with explicit scopes and revocation.
3. Should jobs continue after Companion disconnects? Recommended: yes by default.
4. On reboot, persist accepted jobs and mark non-resumable active jobs INTERRUPTED.
5. Initial package API should be read-only catalog/details; defer install.
6. Use isolated per-job workspaces by default; persistent workspaces require a separate capability.
7. Freeze the v2 AIDL interface for its first release; evolve minor versions with optional fields/capabilities and use a new major namespace for breaking changes.

## 13. Implementation sequence

1. Review and approve security and product decisions above.
2. Finalize exact AIDL/Parcelable schemas, field bounds, nullability, enums, and state transitions.
3. Implement Runtime authentication/pairing and session ownership.
4. Implement transfer sessions and quotas before exposing job submission.
5. Add durable job persistence, idempotency, and state recovery.
6. Add event sequence/replay and query pagination.
7. Add honest package catalog metadata and compatibility classification.
8. Implement Companion client against the frozen v2 contract.
9. Add contract, hostile-input, Binder-size, process-death, and two-app integration tests.
