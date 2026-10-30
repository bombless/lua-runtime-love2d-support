# LÖVE2D Companion

A standalone Android application embedding the real LÖVE 11.5 Android engine. The optional Lua Runtime bridge connects to the separate `ipc-android` application through Android Binder/AIDL.

## Architecture

- Companion application ID: `com.bombless.love2dcompanion`
- Upstream engine: [love2d/love-android](https://github.com/love2d/love-android), tag `11.5a`, commit `55feb38fa144f4734c26742389f279fb07d955c0`
- Upstream source and native engine: `upstream/` (includes the pinned `love` submodule)
- Bundled sample: `upstream/app/src/embed/assets/main.lua`
- Existing generic Lua runtime: `D:\mcp-agent-workspace\ipc-android` (separate application; do not merge it into this project)

The LÖVE native libraries and LuaJIT execute inside the Companion process. The separate runtime is LuaJ 3.0.1 / Lua 5.1 and is only accessed through typed IPC. Binder never transports native pointers or invokes remote NDK functions.

The bridge is optional: the bundled game starts even when the runtime is not installed. A native status badge shows connection/API information and can retry a connection. The first client milestone performs service discovery only; remote script submission and package/file operations are not yet exposed by this companion.

See:
- [Architecture decision record](docs/adr/ADR-001-architecture.md)
- [IPC v1 contract and security notes](docs/IPC-V1.md)
- [Lua interoperability matrix](docs/LUA-COMPATIBILITY.md)

## Build environment

The pinned upstream build uses:
- JDK 17
- Android SDK Platform 34
- CMake 3.22.1 (3.21 or later)
- NDK 25.2.9519653 (pinned in `upstream/love/build.gradle`)
- Gradle wrapper 8.1

The upstream tag normally enables `armeabi-v7a` and `arm64-v8a` (plus `x86_64` in debug). This project currently narrows the `love` module to `arm64-v8a` because the connected OPPO RMX3800 reports only that ABI. Add other ABI filters only when those targets are required and tested.

The upstream README currently mentions NDK 26.1.10909125, but the selected tag's Gradle module explicitly pins 25.2.9519653. Use the version declared by this pinned source revision unless its build configuration is deliberately upgraded and revalidated.

On this development machine, Android API 34, CMake 3.22.1 and NDK 25.2.9519653 are installed. A project-local Microsoft JDK 17.0.15 is installed under `.tools/jdk-17/`. If your machine has `org.gradle.java.home` in the user-level Gradle properties, ensure it also points to this JDK; that property overrides `JAVA_HOME` for the Gradle daemon.

On this development machine, the upstream Gradle 8.1 wrapper distribution could not be downloaded through its redirect. The successful local build used the already-cached Gradle 8.8 wrapper from the sibling `ipc-android` project with `-p` pointing at `upstream/`; the upstream wrapper remains unchanged for normal reproducible builds.

## Build a debug APK

Open `upstream/` as the Gradle project in Android Studio. The upstream checkout is the Gradle root; the surrounding directory holds this project's ADRs, compatibility notes and handoff APK.

Open a terminal in `upstream/`, select JDK 17 as `JAVA_HOME`, and run:

```powershell
$env:JAVA_HOME = 'D:\mcp-agent-workspace\love2d-runtime-bridge\.tools\jdk-17'
$env:ANDROID_HOME = 'D:\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat assembleEmbedNoRecordDebug
```

Expected debug APK:

```
upstream/app/build/outputs/apk/embedNoRecord/debug/
```

The generated APK is `app-embed-noRecord-debug.apk`. A copy of the verified device build is available at `dist/love2d-companion-debug.apk`.

For a release build, configure a real signing configuration first; do not distribute the default debug-signed APK as a release.

## Sample

The embedded sample demonstrates:
- animated graphics rendered by the real LÖVE engine;
- touch and mouse input;
- resize/orientation callbacks;
- FPS and viewport diagnostics;
- a native Android Binder connection indicator.

## Bridge behavior

The companion binds explicitly to:

- Package: `com.bombless.luaandroid`
- Component: `com.bombless.luaandroid.ipc.LuaRuntimeService`
- Action: `com.bombless.luaandroid.action.BIND_RUNTIME`
- Permission: `com.bombless.luaandroid.permission.USE_LUA_RUNTIME`

The AIDL client intentionally declares only the first two v1 methods, `getApiVersion()` and `getRuntimeInfo()`, in their original order. It is a discovery-only subset, not a replacement for the complete server interface.

## Important limitations

- The existing runtime's permission is declared with Android protection level `normal`; it is not strong caller authentication or a package-signature allow-list.
- API v1 does not provide capabilities negotiation.
- Do not send large scripts or archives in Binder transactions.
- Native LuaRocks modules are not compatible merely because they are installed in the runtime app. Native code must be built, packaged, verified and loaded inside the Companion process for its exact ABI and Lua C API.
- The current runtime reports Lua 5.1 via LuaJ 3.0.1. The selected LÖVE build embeds LuaJIT 2.1.1700008891 with Lua 5.1 API compatibility. This does not imply binary compatibility between arbitrary native modules built for the two engines.

## License

The upstream LÖVE Android repository and its recursive submodules retain their original license and copyright notices under `upstream/`. Review all bundled third-party notices before redistribution.
