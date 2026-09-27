"""Proves ART accepts the release dex (plan/05 §4.6, lessons 3.4).

R8 once produced dex that ART refused to verify while every test on the debug build was green.
This installs the release APK on a rebuild emulator, forces a verify compile, and fails when the
log shows "failed to verify" or when no verification ran at all. An unsigned APK is signed with
the local debug key first (the emulator cannot install unsigned APKs); the dex is unchanged.

    python tools/verify_release_dex.py --serial emulator-5570
"""

from __future__ import annotations

import argparse
import hashlib
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from sohva_device import REPO_ROOT, adb, latest_build_tool, require_rebuild_emulator

PACKAGE = "com.streammate.tv"
APK_DIR = REPO_ROOT / "app" / "build" / "outputs" / "apk" / "release"


def signed_copy(apk: Path, work: Path) -> Path:
    if not apk.name.endswith("-unsigned.apk"):
        return apk
    keystore = Path.home() / ".android" / "debug.keystore"
    if not keystore.is_file():
        sys.exit(f"No debug keystore at {keystore}; build any debug APK once to create it.")
    aligned = work / "aligned.apk"
    signed = work / "release-debugsigned.apk"
    subprocess.run([latest_build_tool("zipalign"), "-f", "-p", "4", str(apk), str(aligned)], check=True)
    subprocess.run(
        [
            latest_build_tool("apksigner"), "sign", "--ks", str(keystore), "--ks-pass", "pass:android",
            "--key-pass", "pass:android", "--ks-key-alias", "androiddebugkey", "--out", str(signed), str(aligned),
        ],
        check=True,
    )
    return signed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, help="default: the one APK in app/build/outputs/apk/release")
    args = parser.parse_args()

    require_rebuild_emulator(args.serial)
    apk = args.apk or next(iter(sorted(APK_DIR.glob("*.apk"))), None)
    if apk is None or not apk.is_file():
        sys.exit("No release APK; run ./gradlew :app:assembleRelease first.")

    with tempfile.TemporaryDirectory() as tmp:
        installable = signed_copy(apk, Path(tmp))
        adb(args.serial, "install", "-r", str(installable))
    adb(args.serial, "logcat", "-c")
    verification = adb(args.serial, "shell", "cmd", "package", "compile", "-m", "verify", "-f", PACKAGE)
    if not any(line.strip() == "Success" for line in verification.splitlines()):
        sys.exit(f"The emulator did not report a successful verify compile:\n{verification}")
    log = adb(args.serial, "logcat", "-d")

    ran = sum(1 for line in log.splitlines() if "dex2oat" in line)
    if ran == 0:
        sys.exit("No dex2oat lines in the log: no verification ran, so this proved nothing.")
    rejected = [line for line in log.splitlines() if "failed to verify" in line]
    if rejected:
        print("ART rejected the release dex:", *rejected[:4], sep="\n  ")
        print("Usually a composable with too many parameters; pass a state object instead.")
        sys.exit(1)

    # Launch smoke (plan/06 §6 gate 8): the verified APK cold-starts and is still alive 8 s later.
    adb(args.serial, "shell", "am", "force-stop", PACKAGE)
    # Through the exported launcher alias (beta 23's name, kept so launcher shortcuts survive).
    launch = adb(args.serial, "shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.streammate.tv.app.MainActivity")
    if "Status: ok" not in launch:
        sys.exit("The release APK did not start:\n" + launch)
    total = next((line.split(":")[1].strip() for line in launch.splitlines() if line.startswith("TotalTime")), "?")
    time.sleep(8)
    if not adb(args.serial, "shell", "pidof", PACKAGE).strip():
        sys.exit("The release app died within 8 s of starting.")
    adb(args.serial, "shell", "am", "force-stop", PACKAGE)
    print(f"Launch smoke: started in {total} ms and alive 8 s later.")

    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    # The receipt packaging requires (plan/06 §6 gate 5): this exact APK was verified.
    apk.with_name(apk.name + ".dex-verified").write_text(digest + "\n", encoding="utf-8")
    print(f"Release dex verifies ({ran} dex2oat lines, no rejections). {apk.name} sha256 {digest}")


if __name__ == "__main__":
    main()
