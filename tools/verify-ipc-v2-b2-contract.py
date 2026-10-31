#!/usr/bin/env python3
"""Checks parity of the separately versioned B2 pairing wire contract."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT.parent / "ipc-android" / "app" / "src" / "main"
COMPANION = ROOT / "upstream" / "love" / "src" / "main"
PACKAGE = Path("com/bombless/luaandroid/ipc/v2/pairing")


def read(base: Path, relative: Path) -> str:
    path = base / relative
    if not path.is_file():
        raise AssertionError(f"missing B2 contract file: {path}")
    return path.read_text(encoding="utf-8").replace("\r\n", "\n")


def writes(source: str):
    return re.findall(r"(?:dest|p)\.(writeInt|writeLong|writeString|writeStringArray)\(([^()]*)\)", source)


def reads(source: str):
    return re.findall(r"(?:source|p)\.(readInt|readLong|readString|createStringArray)\(", source)


def main():
    aidl = ("IRestrictedPairingService.aidl", "PairingRequest.aidl", "PairingRequestResult.aidl")
    for name in aidl:
        rel = Path("aidl") / PACKAGE / name
        if read(RUNTIME, rel) != read(COMPANION, rel):
            raise AssertionError(f"B2 AIDL mismatch: {name}")

    pairing_interface = read(RUNTIME, Path("aidl") / PACKAGE / "IRestrictedPairingService.aidl")
    methods = re.findall(r"^\s*(?:[\w.]+)\s+(\w+)\s*\(", pairing_interface, re.MULTILINE)
    if methods != ["submitPairingRequest", "getPairingRequestStatus", "cancelPairingRequest"]:
        raise AssertionError(f"Restricted pairing interface exposes unexpected methods: {methods}")

    for stem in ("PairingRequest", "PairingRequestResult"):
        java = read(COMPANION, Path("java") / PACKAGE / f"{stem}.java")
        kotlin = read(RUNTIME, Path("java") / PACKAGE / f"{stem}.kt")
        if writes(java) != writes(kotlin):
            raise AssertionError(f"B2 Parcelable write order mismatch: {stem}: {writes(java)} != {writes(kotlin)}")
        if reads(java) != reads(kotlin):
            raise AssertionError(f"B2 Parcelable read order mismatch: {stem}: {reads(java)} != {reads(kotlin)}")

    manifest = read(RUNTIME, Path("AndroidManifest.xml"))
    if 'android:name=".ipc.v2.RuntimeV2Service"' not in manifest or \
       'android:permission="com.bombless.luaandroid.permission.USE_LUA_RUNTIME"' not in manifest:
        raise AssertionError("Existing signature-protected RuntimeV2Service declaration changed")
    if 'android:name=".ipc.v2.pairing.RestrictedPairingService"' not in manifest or \
       'android:exported="true"' not in manifest:
        raise AssertionError("Restricted pairing entry is not exported")
    if 'android:name=".ipc.v2.pairing.PairingConsentActivity"' not in manifest or \
       'android:exported="false"' not in manifest:
        raise AssertionError("Runtime consent activity must remain private")
    print("B2 pairing contract checks passed: 3 AIDL files, 2 Parcelable field orders, restricted API, manifest boundaries.")


if __name__ == "__main__":
    main()
