# Existing Lua Runtime IPC v1

## Endpoint

| Field | Value |
|---|---|
| Package | `com.bombless.luaandroid` |
| Service | `com.bombless.luaandroid.ipc.LuaRuntimeService` |
| Action | `com.bombless.luaandroid.action.BIND_RUNTIME` |
| Permission | `com.bombless.luaandroid.permission.USE_LUA_RUNTIME` |
| API version | `1` |
| AIDL descriptor | `com.bombless.luaandroid.ipc.ILuaRuntimeService` |

The companion uses an explicit `ComponentName` and declares package visibility and the service permission in its manifest.

## Discovery subset implemented in the companion

The client AIDL file declares the first two server methods in their original order:

1. `int getApiVersion()`
2. `String getRuntimeInfo()`

The AIDL descriptor and transaction ordering are required to match. This deliberately small interface only supports service discovery and status display. It does not claim to implement the remaining server methods.

The Activity binds in `onStart()`, unbinds in `onStop()`, and performs remote metadata calls on a dedicated executor. The SDL/LÖVE render loop does not wait for Binder calls. The client accepts API version 1 and displays an explicit incompatible-version state for any other value. A ten-second bind timeout unbinds a service that never connects and leaves a tap-to-retry path. Failed metadata calls also release the broken binding so retry is functional. Service absence, bind failure, permission denial, remote errors, service disconnect and binding death are displayed as status.

## Server interface inspected

The existing server AIDL additionally defines:

- `submit(JobRequest)` returning an asynchronous job ID;
- `getJob(jobId)`;
- `listJobs(limit, beforeCreatedAtMillis)`;
- `cancel(jobId)`;
- `registerCallback(jobId, IJobCallback)` and `unregisterCallback(...)`;
- `openResult(jobId, artifactId)` returning a `ParcelFileDescriptor`.

The runtime uses API v1 and reports `Lua 5.1 (Luaj engine)`.

## Security and compatibility findings

- The service is exported and guarded by a custom permission with protection level `normal`. Any installed app able to request that permission may potentially receive it. This is not a signature-level trust boundary.
- The service checks the calling UID's permission for its methods, but inspected code does not enforce an explicit caller package/signing-certificate allow-list.
- The existing MainActivity binds using `setPackage(packageName)`, which targets its own package and is not a cross-app example.
- `JobRequest.apiVersion` is present, but inspected request validation does not visibly reject unsupported versions.
- Inline script input is limited to 5 MiB by service validation, which is still too large for safe Binder payloads.
- The server stages script/input file descriptors synchronously in `JobManager.submit()` before queuing execution. Large copies can occupy Binder threads.
- Simple filename validation is present, but it is not a complete authorization or content-validation policy.

## Rules for future client features

1. Keep all Binder calls off the render thread.
2. Use small, bounded request payloads only.
3. Prefer opaque IDs and structured results over filesystem paths.
4. For large content, design a scoped content-URI/descriptor transfer with explicit ownership, grants, size limits and cleanup.
5. Never execute scripts or install packages without explicit user action and a trust/permission model.
6. Do not modify the generic runtime service, permission or AIDL contract without explicit approval.
7. Before exposing job submission, add client-side protocol/version checks, bounded input handling, cancellation, timeouts and clear user consent.
