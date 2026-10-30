# Architecture Decision Record

## ADR-001: Standalone LÖVE2D companion and existing Lua Runtime IPC

- Status: Accepted for Phase 1 implementation; IPC reuse is provisional pending security review.
- Date: 2026-10-31
- Scope: `love2d-runtime-bridge`; the existing `ipc-android` project is read-only.

## Context and verified facts

The existing runtime project at `D:\mcp-agent-workspace\ipc-android` contains an Android application module and these IPC declarations:

- Service action: `com.bombless.luaandroid.action.BIND_RUNTIME`
- Service class: `com.bombless.luaandroid.ipc.LuaRuntimeService`
- AIDL: `com.bombless.luaandroid.ipc.ILuaRuntimeService`
- Callback: `IJobCallback`
- API version: `1`
- Runtime info reports `Lua 5.1 (Luaj engine); API version 1`.
- Execution dependency: `org.luaj:luaj-jse:3.0.1` using `JsePlatform.standardGlobals()`.
- Requests/jobs are asynchronous by job ID; callbacks and polling are available.
- Artifacts can be opened by opaque job/artifact IDs as `ParcelFileDescriptor`.
- `JobRequest` supports inline source, script FD, arguments, environment, working directory, timeout, memory limit, and input FDs.
- `LuaRocksManager` currently provides Lua 5.1 paths, rudimentary installed-rock enumeration and a pure-Lua file writer. This source alone does not establish a complete LuaRocks package manager.
- The existing manifest exports the service under a custom `normal` permission. Service methods check that permission, but no package allow-list or signature-level caller authentication is present in the inspected code.
- The existing activity binds with `setPackage(packageName)`, so it binds to its own package; this is not a cross-app client example.
- Runtime request validation checks simple file names and caps inline source at 5 MiB. It does not visibly validate `apiVersion`; script/input FD staging occurs synchronously in `JobManager.submit()`.
- The existing directory has no `.git` directory, so Git status/history cannot be used there. No files in that project have been changed.

Build machine facts:

- Windows 11 x64; command execution enabled.
- Android SDK: `D:\android-sdk`; installed platforms 34, 35, 36.
- CMake 3.22.1.
- Installed NDK: 25.2.9519653 only.
- Java default: Microsoft OpenJDK 25.0.3. A project-local Microsoft JDK 17.0.15 is installed under `.tools/jdk-17/`.
- User-level Gradle properties contain `org.gradle.java.home` pointing to JDK 20, which overrides `JAVA_HOME`; it was temporarily redirected to the project JDK for each build and restored exactly afterward.
- Existing runtime uses Gradle wrapper 8.8. The local build used that cached wrapper against the upstream project because the upstream Gradle 8.1 distribution redirect timed out; the upstream wrapper was not changed.
- Connected test device: OPPO RMX3800, Android API 36, `ro.product.cpu.abilist=arm64-v8a`.

Upstream facts:

- Official repository: https://github.com/love2d/love-android
- Selected upstream release: 11.5 Hotfix 1, tag `11.5a`, commit `55feb38`.
- Upstream README requires recursive submodules, JDK 17, CMake >= 3.21, Android API 34 and NDK 26.1.10909125; its normal build task is `assembleNormalRecord`.
- The pinned tag's `love/build.gradle` explicitly sets `ndkVersion '25.2.9519653'`; upstream defaults to `armeabi-v7a` and `arm64-v8a`, and adds `x86_64` for debug. This project overrides the development build to `arm64-v8a` only for the connected target device. The README/build-file NDK discrepancy is recorded; the pinned Gradle configuration is used for this revision.
- The pinned LuaJIT header reports `LuaJIT 2.1.1700008891`, `LUA_VERSION_NUM 501`, and `LUAJIT_VERSION_NUM 20199`.
- Embedded game source belongs in `app/src/embed/assets`, with `main.lua` at that root.
- Upstream 11.4+ mentions experimental third-party Lua module support; this does not imply compatibility with arbitrary LuaRocks native modules.

## Phase 1 build and device verification

- Gradle task: `assembleEmbedNoRecordDebug`; build succeeded with JDK 17 and cached Gradle 8.8.
- APK: `upstream/app/build/outputs/apk/embedNoRecord/debug/app-embed-noRecord-debug.apk` (11,408,385 bytes).
- SHA-256: `0087F7AC5F7C54513006D8A658E8DAF73069DA297EF5BE65CEB9EE3BD5F5DEFF`.
- Installed and launched on OPPO RMX3800 / Android API 36 / arm64-v8a. APK contains `liblove.so`, `libopenal.so`, `libmpg123.so`, and `libc++_shared.so`.
- On-device screenshot shows the embedded LÖVE sample rendering at 60 FPS; an ADB touchscreen tap moved the animated orb.
- UI hierarchy confirms the Binder overlay reports `Lua Runtime: connected · API 1` and `Lua 5.1 (Luaj engine); API version 1`.
- Android Back exits the game Activity; HOME then relaunch/resume succeeds and reconnects the runtime. Audio focus and actual orientation rotation remain unverified.

## Decisions

1. Keep the companion as a separate Android application with its own application ID, signing identity, process, LÖVE native libraries and embedded LÖVE Lua VM.
2. Pin upstream LÖVE Android 11.5 Hotfix 1 (`11.5a`, `55feb38`) and preserve all upstream/submodule license notices.
3. Do not modify `ipc-android` in Phase 0 or Phase 1.
4. The bridge is optional. A bundled game must run without the runtime installed, and UI must expose disconnected/reconnect states.
5. Treat LuaJ 3.0.1/Lua 5.1 in the runtime and the LÖVE LuaJIT VM as separate runtimes. No shared VM, pointers, native calls, or ABI assumptions.
6. For the first client implementation, use the existing explicit service identity and API v1 only after validating the permission declaration and handling service-not-found, bind failure, disconnect and timeout. Do not silently claim strong caller authentication: the current `normal` permission is not an allow-list.
7. Phase 1 exposes only API/runtime discovery. Job submission/query/cancel/artifact operations remain gated on an approved caller-authentication model and capability/version semantics because the current server permission is `normal` and API v1 has no capabilities endpoint. When approved, all Binder work must remain on a background executor and payloads must be bounded.
8. Do not send large inline scripts or assets through Binder. Prefer small requests; any future large transfer needs a reviewed ContentProvider/URI grant or descriptor design.
9. Pure-Lua rock source may be imported only after Lua version/API compatibility checks and controlled file transfer. Native rocks are unsupported until a separate signed, ABI-specific, in-process delivery/loading design is approved.
10. Do not add a runtime-side adapter or alter its permission/security contract without explicit approval.

## Consequences and open questions

- API v1 has no capabilities negotiation. Client must treat `getApiVersion()` and `getRuntimeInfo()` as the available discovery surface and reject unknown API versions.
- The current service permission is weak for a security-sensitive public API. A future server-side change should use signature-level permission or explicit caller-signature/UID allow-listing, plus request limits and path validation.
- Runtime FD copying currently happens before the asynchronous job is scheduled; large descriptors can occupy a Binder thread. The client must keep initial requests small.
- Need inspect upstream build files/submodules and actual LÖVE LuaJIT version before documenting compatibility claims.
- Need install/use JDK 17 and NDK 26.1.10909125 before building this pinned upstream revision.
- Need confirm target device API/ABI before APK install testing.

## Phase 0 checklist

- [x] Inspect existing runtime tree and manifest.
- [x] Inspect AIDL service and callback.
- [x] Inspect request/result/job types and execution path.
- [x] Identify Lua VM and current LuaRocks implementation limits.
- [x] Record build-machine SDK/NDK/CMake/Java/Gradle facts.
- [x] Select and pin official LÖVE Android upstream release.
- [x] Record architecture/security boundary.
- [x] Inspect pinned upstream source/submodules in workspace.
- [x] Verify device API/ABI and complete build-tool installation (project-local JDK 17 installed; API 34 and pinned NDK 25.2 available).
