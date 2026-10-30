# LÖVE2D Companion App + Lua Runtime IPC Bridge

> Handoff brief for a fresh implementation context.
> Workspace: D:\mcp-agent-workspace\love2d-runtime-bridge
> Existing runtime: D:\mcp-agent-workspace\ipc-android
> Status: Phase 0 complete; Phase 1 companion implemented, built, installed, and device-verified. Phase 2+ remain in progress. Do not modify the existing runtime project unless a later, explicit compatibility change is agreed.

## 1. Goal

Create a **new, separately installable Android application** embedding the real LÖVE2D Android engine, including its native/NDK components. The new app cooperates with the existing generic Lua runtime through Android IPC.

The existing ipc-android project is intended to become a general-purpose Lua runtime with LuaRocks package management. Keep it generic and independently useful; do not turn it into a LÖVE-specific app.

The companion app provides the actual LÖVE2D experience: graphics, animation, touch/input, audio, filesystem/save data, and supported love.* APIs. It may request Lua-side services, package metadata, or controlled package/file access from the existing runtime.

## 2. Critical architecture boundary

NDK calls cannot be transferred to another APK as remote function calls. Native code executes in the process that loads its shared libraries.

- The new LÖVE2D app must contain and load the LÖVE2D native libraries in its own process.
- The existing Lua runtime remains a separate app/process and exposes a documented IPC contract (prefer its existing AIDL/Binder service if suitable).
- IPC carries typed commands, metadata, file/package references, and results—not native pointers or raw function calls.
- A LuaRocks package containing native Android .so code cannot simply be loaded into LÖVE from the other app's private storage. Native plugin delivery/loading requires a deliberate design, ABI compatibility, signing/security checks, and Android linker review.
- Never assume the existing runtime's Lua VM is ABI-compatible with the Lua/LuaJIT embedded in LÖVE. Determine exact versions and C API/ABI first.

Treat this as a service protocol, not remote NDK invocation.

## 3. Intended topology

    +---------------------------------------+
    | New app: LÖVE2D Companion             |
    | Android Activity / LÖVE Android host  |
    | LÖVE2D native engine (.so / NDK)      |
    | LÖVE Lua VM + love.* APIs             |
    | Game code, assets, save data          |
    +-------------------+-------------------+
                        |
                        | Android IPC (AIDL/Binder)
                        |
    +-------------------v-------------------+
    | Existing app: ipc-android             |
    | Generic Lua runtime                   |
    | LuaRocks package manager (goal)       |
    | Package metadata / dependency info   |
    | Script execution / optional services |
    +---------------------------------------+

The bridge must be optional at startup. The companion should launch and run a bundled sample even if ipc-android is absent or its service is unavailable. Show disconnected status and allow reconnect.

## 4. Non-goals and constraints

- Do not convert ipc-android into a LÖVE2D-only application.
- Do not replace/remove its generic Lua runtime or LuaRocks direction.
- Do not copy the whole LuaRocks installation without defining ownership and synchronization.
- Do not load arbitrary native libraries from another app's private data directory.
- Do not promise all LuaRocks packages work in LÖVE. Pure-Lua and native rocks have different compatibility requirements.
- Do not silently execute remote scripts or install packages without a clear trust/permission model.
- Keep the new app independently installable, updateable, and uninstallable.

## 5. Environment and known facts to re-check

- Windows workspace root: D:\mcp-agent-workspace
- Existing project: D:\mcp-agent-workspace\ipc-android
- Test phone previously identified as OPPO RMX3800.
- Android SDK previously found at D:\android-sdk.
- Previously observed CMake: 3.22.1.
- Previously observed NDK: 25.2.9519653.
- Earlier upstream LÖVE Android instructions indicated JDK 17, Android API 34, CMake >= 3.21, and NDK 26.1.10909125. Re-check current upstream requirements before building.
- Official upstream: https://github.com/love2d/love-android
- Official distribution guidance: https://love2d.org/wiki/Game_Distribution
- Upstream Android repo uses recursive submodules; inspect README/build scripts and exact Gradle tasks for the chosen revision.
- Existing project previously used AGP 8.2.2, Kotlin Gradle Plugin 1.9.22, Gradle wrapper 8.8, Java 17, application ID com.bombless.luaandroid. Re-inspect; do not assume current files are unchanged.

## 6. Phase 0 — inspect current contracts (read-only)

- [x] Inspect ipc-android project tree and Git status; do not modify it. (The directory has no `.git` metadata; read-only inspection completed.)
- [x] Read manifest, Gradle files, AIDL definitions, service implementation, request/result parcelables, and runtime execution path.
- [x] Verify service action/component/package, exported status, permissions, bind behavior, process behavior, and Android API compatibility.
- [x] Determine whether the runtime currently executes LuaJ, LuaJIT, another VM, or multiple engines. Record Lua language version and native ABI.
- [x] Verify current MainActivity and runtime service behavior; distinguish verified facts from earlier assumptions.
- [x] Inspect LuaRocks install/storage/resolve/execute behavior and identify pure-Lua vs native-rock support. (Pure-Lua writer/path helpers are present; complete dependency resolution and native-rock support are not established.)
- [x] Inspect official love-android at a pinned tag/commit, including submodules, supported ABIs, Lua/LuaJIT version, lifecycle, Gradle modules, and license notices.
- [x] Write a concise architecture decision record before implementation.

## 7. Phase 1 — standalone LÖVE2D companion app

- [x] Clone/fetch official love2d/love-android recursively into this new project (or vendor a pinned upstream revision); preserve license notices. (Tag `11.5a`, commit `55feb38`; source lives under `upstream/`.)
- [x] Keep ipc-android unchanged.
- [x] Configure a distinct application ID, label, version, icon, and signing configuration. (Debug signing uses the Android Gradle Plugin's debug keystore; release signing is intentionally not configured.)
- [x] Embed a minimal main.lua sample demonstrating:
  - [x] animated shapes/sprites at a stable frame rate (60 FPS observed on device);
  - [x] touch input (ADB tap moved the orb);
  - [x] resize/orientation handling (`love.resize`, resizable window and full-sensor orientation; device rotation test remains pending);
  - [x] visible bridge connection indicator (API v1 and runtime info displayed);
  - [x] graceful behavior when the runtime app is absent (bind failure/permission paths are handled; absent-app device test remains pending).
- [x] Build a debug APK for the phone's supported ABI(s), install it, and verify the real LÖVE engine starts (not a LuaJ-only substitute). (OPPO RMX3800, API 36, arm64-v8a; `liblove.so` packaged.)
- [ ] Verify Android back/resume/pause lifecycle and audio focus. (Back and HOME/resume were verified; audio focus has not yet been tested.)

## 8. Phase 2 — IPC bridge

First inspect the existing AIDL contract. Reuse it only if generic, secure, versionable, and suitable. Otherwise propose a minimal, versioned AIDL contract and add the smallest compatible server-side adapter to ipc-android only after explicit approval.

**LÖVE2D companion (client)**
- [x] Discover/bind to runtime service using explicit package/component identity. (Explicit package/component, API v1 discovery, background executor, bind timeout, disconnect and retry states implemented.)
- [ ] Negotiate protocol version and runtime capabilities.

> Current safe boundary: the Companion uses only `getApiVersion()` and `getRuntimeInfo()`. Job submission/cancellation/artifact APIs are deferred until the existing `normal`-permission service's caller-authentication weakness and the absence of capability negotiation are addressed or explicitly accepted. Do not change `ipc-android` without approval. (API version 1 is checked/rejected if unknown; the existing v1 server exposes no capabilities endpoint.)
- [ ] Request package metadata, dependency information, and package file manifests.
- [ ] Request script/task execution only through constrained, documented APIs.
- [ ] Receive structured results, errors, progress, and cancellation acknowledgements.
- [x] Handle service death, disconnect, timeout, reconnect, and app-not-installed. (Code paths implemented; absent-app and forced service-death scenarios still need dedicated device tests.)
- [x] Never block the render thread on Binder or file I/O. (Binder discovery calls use a dedicated executor.)

**Generic Lua runtime (server)**
- [ ] Keep generic Lua execution/package management independent of LÖVE.
- [ ] Expose only documented, typed IPC operations.
- [ ] Validate caller identity/UID and enforce permissions/allow-listing.
- [ ] Validate paths and package IDs; prevent path traversal.
- [ ] Apply request size limits, timeouts, cancellation, and concurrency limits.
- [ ] Return structured error codes and protocol version/capabilities.
- [ ] Avoid arbitrary requests from untrusted callers.

**Protocol**
- [ ] Add version negotiation and capability discovery.
- [ ] Use small Parcelable/AIDL data structures; avoid large Binder transactions.
- [ ] For larger assets/archives, use controlled transfer (e.g. app-private cache plus narrowly scoped ContentProvider/content URI with temporary grants), not raw filesystem paths.
- [ ] Define package identity, version, dependency, checksum, ABI, Lua version, license, and native dependency metadata.
- [ ] Define lifecycle and error semantics.
- [ ] Decide which app owns package installation and updates.

## 9. Phase 3 — Lua/LuaRocks interoperability

This is the highest-risk area. Do not promise universal compatibility.

- [ ] Separate three cases:
  1. Pure Lua rock: source/assets may be shared through a controlled content URI or copied to companion cache, subject to Lua version/API compatibility.
  2. Native C/C++ rock: requires Android build for exact ABI, compatible Lua C API/ABI, correct packaging, and loading inside the LÖVE process. It cannot run in the runtime process and magically affect LÖVE's VM.
  3. Rock depending on OS tools, unavailable libraries, or unsupported APIs: mark unsupported unless specifically ported.
- [ ] Decide whether LÖVE uses its bundled Lua/LuaJIT exclusively or selected pure-Lua modules can be imported from the runtime app.
- [ ] If native modules are desired, choose secure distribution (e.g. signed/verified modules packaged with companion or delivered as validated artifacts into its own private storage). Never dlopen arbitrary untrusted paths.
- [ ] Define compatibility metadata: Lua version, Lua C API compatibility, Android ABI, min API, dependencies, checksum/signature.
- [ ] Test one pure-Lua package. Test a native package only if its ABI/build pipeline is actually supported.
- [ ] Clearly document unsupported package types.

## 10. Phase 4 — UX

- [ ] Home screen: launch sample/game, connection status, runtime capabilities, reconnect, diagnostics.
- [ ] User can choose whether to connect; standalone game execution remains available.
- [ ] Show actionable states for missing/outdated runtime, permission denied, and protocol mismatch.
- [ ] Do not imply LuaRocks packages are automatically LÖVE-compatible.
- [ ] Diagnostics: engine version, ABI, Lua version, bridge protocol version, runtime connection.

## 11. Phase 5 — verification

- [ ] Build from a clean checkout using documented commands.
- [ ] Install both apps on OPPO RMX3800 (or API-compatible device).
- [ ] Verify companion works without runtime installed.
- [ ] Verify bridge connection when both apps are installed.
- [ ] Verify runtime unavailable/restarted/killed during request.
- [ ] Verify version mismatch and permission-denied paths.
- [ ] Verify large file transfer avoids Binder transaction limits.
- [ ] Verify package/path validation and caller authentication.
- [ ] Verify no main-thread blocking during rendering.
- [ ] Verify update/uninstall behavior and data ownership.
- [ ] Record APK paths, hashes, build logs, device/API/ABI, and test results.

## 12. Deliverables

- [ ] Standalone Android Studio/Gradle project in this directory.
- [ ] Buildable debug APK and reproducible build instructions.
- [ ] Working animated LÖVE2D sample.
- [ ] Versioned IPC protocol documentation.
- [ ] Minimal compatible runtime-side adapter only if needed and explicitly approved.
- [ ] Lua/LuaRocks compatibility matrix (pure Lua vs native rocks).
- [ ] README: architecture, setup, build, install, troubleshooting, security.
- [ ] No accidental LÖVE-specific coupling in generic runtime.

## 13. First actions in the new context

1. Read this TODO and confirm the task is the separate companion app + IPC bridge, not LÖVE integration inside the existing app.
2. Inspect ipc-android read-only and map its AIDL/service contract.
3. Inspect/pin official love-android upstream and current toolchain requirements.
4. Write the architecture decision record, especially Lua VM / Lua C ABI / native-rock boundary.
5. Only then scaffold and build the standalone LÖVE2D companion app.
