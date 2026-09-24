"""Shared helpers for the rebuild's device tooling: finding the SDK, and refusing real devices.

The owner's Shield is often attached to adb next to the emulator, and another emulator on the
machine holds the old app's installs. Every tool here acts only on an emulator whose AVD name
starts with ``sohva_rebuild`` (AGENTS.md section 7: never touch the owner's devices).
"""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

REBUILD_AVD_PREFIX = "sohva_rebuild"
REPO_ROOT = Path(__file__).resolve().parent.parent


def sdk_root() -> Path:
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        value = os.environ.get(key)
        if value and Path(value).is_dir():
            return Path(value)
    local = REPO_ROOT / "local.properties"
    if local.is_file():
        for line in local.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                return Path(line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\"))
    sys.exit("Android SDK not found: set ANDROID_HOME.")


def tool(relative: str) -> str:
    """Path of an SDK tool, with the Windows extension when needed."""
    base = sdk_root() / relative
    for suffix in ("", ".exe", ".bat"):
        candidate = Path(str(base) + suffix)
        if candidate.is_file():
            return str(candidate)
    sys.exit(f"SDK tool not found: {base}")


def latest_build_tool(name: str) -> str:
    versions = sorted((sdk_root() / "build-tools").iterdir(), key=lambda p: [int(x) for x in p.name.split(".") if x.isdigit()])
    return tool(f"build-tools/{versions[-1].name}/{name}")


def adb(serial: str, *args: str, check: bool = True, capture: bool = True) -> str:
    result = subprocess.run(
        [tool("platform-tools/adb"), "-s", serial, *args],
        capture_output=capture,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if check and result.returncode != 0:
        sys.exit(f"adb {' '.join(args)} failed ({result.returncode}): {result.stderr or result.stdout}")
    return result.stdout if capture else ""


def require_rebuild_emulator(serial: str) -> str:
    """Refuses anything but a running rebuild emulator. Returns its AVD name."""
    if not serial.startswith("emulator-"):
        sys.exit(f"Refusing {serial}: only emulators (emulator-NNNN) are allowed.")
    if adb(serial, "shell", "getprop", "ro.kernel.qemu").strip() != "1":
        sys.exit(f"Refusing {serial}: ro.kernel.qemu is not 1, so it is not an emulator.")
    avd = adb(serial, "emu", "avd", "name").splitlines()[0].strip()
    if not avd.startswith(REBUILD_AVD_PREFIX):
        sys.exit(f"Refusing {serial}: AVD '{avd}' is not a rebuild emulator ({REBUILD_AVD_PREFIX}*).")
    return avd
