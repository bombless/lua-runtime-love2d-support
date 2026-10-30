# Lua / LuaRocks compatibility matrix

| Property | Existing generic runtime | LÖVE Companion |
|---|---|---|
| VM | LuaJ 3.0.1 | LuaJIT 2.1 |
| Reported/API version | Lua 5.1 | Lua 5.1 API |
| Native process | Runtime APK process | Companion APK process |
| Native ABI | Java VM; no Lua C module support currently | Android native ABI selected by LÖVE build |
| Native library loading | Native rocks explicitly rejected | LÖVE's own native modules are packaged in this APK |

## Verified LÖVE engine details

The pinned upstream submodule is LÖVE 11.5 at commit `6eb8d546736d5915a8b5af30b2cf33456dfdcb1a`.

The checked-in LuaJIT Android header reports:

- `LuaJIT 2.1.1700008891`
- `LUA_VERSION_NUM 501` (Lua 5.1 API)
- `LUAJIT_VERSION_NUM 20199`

The pinned upstream defaults to `armeabi-v7a` and `arm64-v8a` for normal variants, and additionally `x86_64` for debug. This project currently overrides that to `arm64-v8a` only because the connected OPPO RMX3800 reports `ro.product.cpu.abilist=arm64-v8a`. Restore other ABI filters only when those targets are required and tested.

## Package classes

### Pure-Lua rock

Potentially portable only after checking:
- syntax and standard-library assumptions against Lua 5.1;
- use of LuaJIT-only extensions or FFI;
- filesystem paths and Android permissions;
- dependencies and license metadata.

The runtime may expose source/assets through a controlled manifest and content-URI transfer. Do not share arbitrary private filesystem paths.

### Native C/C++ rock

Not supported by the current runtime and not remotely loadable into the LÖVE process.

A future native module must be:
- built for the exact Android ABI;
- compatible with the exact LuaJIT/Lua C API and compile-time options;
- packaged or delivered into Companion-owned storage;
- signed or cryptographically verified and allow-listed;
- loaded only inside the Companion process under a reviewed linker and security design.

### Unsupported dependencies

Rocks requiring desktop OS tools, unavailable shared libraries, unsupported system calls, incompatible Lua APIs or unported native dependencies must be marked unsupported.

## Current policy

No arbitrary LuaRocks package is automatically imported into LÖVE. The first IPC milestone only discovers the generic runtime and displays its version. Native-rock execution is explicitly out of scope until a separate design and approval.
