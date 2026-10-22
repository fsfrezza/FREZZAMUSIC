"""Reject debuggable, unoptimized, or unsigned production artifacts."""
from pathlib import Path
import os
import subprocess

root = Path(os.environ["ANDROID_HOME"])
build_tools = sorted((root / "build-tools").glob("*/"), key=lambda p: [int(x) for x in p.name.split(".") if x.isdigit()])
tool_dir = build_tools[-1]
aapt = tool_dir / "aapt"
apksigner = tool_dir / "apksigner"

for flavor in ("frezzamusic", "solasias", "thefrezza"):
    apk = Path(f"app/build/outputs/apk/{flavor}/release/app-{flavor}-release.apk")
    if not apk.is_file():
        raise SystemExit(f"Missing signed release APK: {apk}")
    subprocess.run([str(apksigner), "verify", "--verbose", str(apk)], check=True)
    badging = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
    assert "application-debuggable" not in badging, "Release APK is debuggable"
    manifest = subprocess.check_output([str(aapt), "dump", "xmltree", str(apk), "AndroidManifest.xml"], text=True)
    assert ("fsfrezza.github.io" in manifest) == (flavor == "frezzamusic"), "Only FREZZAMUSIC should claim shared web links"
    mapping = Path(f"app/build/outputs/mapping/{flavor}Release/mapping.txt")
    assert mapping.is_file() and mapping.stat().st_size > 100, "Missing R8 mapping"
    assert any(
        "com.frezzamusic.app.data.LiveDriveCatalog -> " in line and not line.endswith("LiveDriveCatalog:")
        for line in mapping.read_text().splitlines()
    ), "Catalog class was not obfuscated"
    print(f"{flavor}: signed release APK, debug disabled and R8 mapping verified")
