#!/usr/bin/env python3
"""Fail the Android delivery gate if an APK lacks a JAR (v1) or APK Signing Block (v2/v3)."""
import sys
import zipfile
from pathlib import Path

MAGIC = b"APK Sig Block 42"

def has_signature(path: Path) -> bool:
    with zipfile.ZipFile(path) as apk:
        names = set(apk.namelist())
        v1 = any(n.upper().startswith("META-INF/") and n.upper().endswith((".RSA", ".DSA", ".EC")) for n in names)
        with path.open("rb") as stream:
            stream.seek(max(0, apk.start_dir - 24))
            v2 = MAGIC in stream.read(24)
        return v1 or v2

if __name__ == "__main__":
    files = [Path(v) for v in sys.argv[1:]]
    if not files:
        raise SystemExit("Usage: python scripts/check_apk_signature.py <APK> [...]")
    failures = []
    for file in files:
        try:
            ok = has_signature(file)
        except (OSError, zipfile.BadZipFile) as exc:
            print(f"INVALID {file}: {exc}")
            failures.append(file)
            continue
        print(f"{'SIGNED' if ok else 'UNSIGNED'} {file}")
        if not ok:
            failures.append(file)
    if failures:
        raise SystemExit("Delivery blocked: APK missing signature. Configure the production signing keystore and verify certificate identity with apksigner before publishing.")
