#!/usr/bin/env python3
"""Cross-repository IPC v2 wire-contract parity checks."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
COMPANION = ROOT / "upstream" / "love" / "src" / "main"
RUNTIME = ROOT.parent / "ipc-android" / "app" / "src" / "main"
PACKAGE = Path("com/bombless/luaandroid/ipc/v2")


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def read(base, relative):
    path = base / relative
    require(path.is_file(), f"missing contract file: {path}")
    return path.read_text(encoding="utf-8").replace("\r\n", "\n")


def write_signature(source):
    # Normalize equivalent Java/Kotlin null guards and boolean encoding, then compare
    # both operation types and the field being written (not just operation counts).
    source = source.replace("value(clientVersion)", "clientVersion")
    source = source.replace("value(buildId)", "buildId")
    source = source.replace("value(nonce)", "nonce")
    source = source.replace('sessionId == null ? "" : sessionId', "sessionId")
    source = source.replace("grantedScopes == null ? new String[0] : grantedScopes", "grantedScopes")
    source = source.replace("requestedCapabilities == null ? new String[0] : requestedCapabilities", "requestedCapabilities")
    source = source.replace("retryable ? 1 : 0", "retryable")
    source = source.replace("if (retryable) 1 else 0", "retryable")
    return re.findall(r"p\.(writeString|writeInt|writeLong|writeStringArray|writeTypedList)\(([^()]*)\)", source)


def main():
    aidl_files = [
        "CapabilityInfo.aidl", "ClientHello.aidl", "ErrorInfo.aidl",
        "IProtocolBootstrap.aidl", "ISession.aidl", "ProtocolInfo.aidl", "SessionInfo.aidl",
    ]
    for name in aidl_files:
        rel = PACKAGE / name
        require(read(COMPANION, Path("aidl") / rel) == read(RUNTIME, Path("aidl") / rel),
                f"AIDL mismatch: {name}")

    pairs = {
        "ProtocolInfo": ("ProtocolInfo.java", "ProtocolInfo.kt"),
        "ClientHello": ("ClientHello.java", "ClientHello.kt"),
        "SessionInfo": ("SessionInfo.java", "SessionInfo.kt"),
        "CapabilityInfo": ("CapabilityInfo.java", "CapabilityInfo.kt"),
        "ErrorInfo": ("ErrorInfo.java", "ErrorInfo.kt"),
    }
    for name, (java_file, kotlin_file) in pairs.items():
        java_source = read(COMPANION, Path("java/com/bombless/luaandroid/ipc/v2") / java_file)
        kotlin_source = read(RUNTIME, Path("java/com/bombless/luaandroid/ipc/v2") / kotlin_file)
        require(write_signature(java_source) == write_signature(kotlin_source),
                f"Parcelable write-operation/field order mismatch: {name}")
        java_reads = re.findall(r"p\.(readString|readInt|readLong|createStringArray|createTypedArrayList)\(", java_source)
        kotlin_reads = re.findall(r"p\.(readString|readInt|readLong|createStringArray|createTypedArrayList)\(", kotlin_source)
        require(java_reads == kotlin_reads, f"Parcelable read-operation order mismatch: {name}")
        require("writeToParcel" in java_source and "writeToParcel" in kotlin_source,
                f"Parcelable writer missing: {name}")

    manifest = read(COMPANION, Path("AndroidManifest.xml"))
    require('com.bombless.luaandroid.permission.USE_LUA_RUNTIME' in manifest,
            "Companion manifest missing Runtime signature permission")
    runtime_manifest = read(RUNTIME, Path("AndroidManifest.xml"))
    require('android:protectionLevel="signature"' in runtime_manifest,
            "Runtime permission is not signature-protected")

    print("IPC v2 cross-project contract checks passed.")
    print(f"  AIDL files compared: {len(aidl_files)}")
    print(f"  Parcelable operation/field-order pairs compared: {len(pairs)}")
    print("  Companion permission declaration and Runtime protection level verified.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"IPC v2 contract check FAILED: {exc}", file=sys.stderr)
        sys.exit(1)
