"""Cold-boots a rebuild emulator on a fixed port and waits until it has booted.

Cold boot (-no-snapshot-load) gives the guide a correct clock (lessons 5.4, 7.7). The fixed port
makes the serial predictable: port 5570 -> emulator-5570.

    python tools/start_emulator.py --avd sohva_rebuild_tv30 --port 5570
"""

from __future__ import annotations

import argparse
import subprocess
import sys
import time

from sohva_device import REBUILD_AVD_PREFIX, adb, require_rebuild_emulator, tool


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--avd", default="sohva_rebuild_tv30")
    parser.add_argument("--port", type=int, default=5570)
    parser.add_argument("--window", action="store_true", help="show the emulator window")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()
    if not args.avd.startswith(REBUILD_AVD_PREFIX):
        sys.exit(f"Only {REBUILD_AVD_PREFIX}* AVDs are started by this tool.")

    serial = f"emulator-{args.port}"
    command = [
        tool("emulator/emulator"), "-avd", args.avd, "-port", str(args.port),
        "-no-snapshot-load", "-no-snapshot-save", "-no-boot-anim", "-gpu", "swiftshader_indirect",
        "-no-audio",
    ]
    if not args.window:
        command.append("-no-window")
    subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)

    deadline = time.monotonic() + args.timeout
    while time.monotonic() < deadline:
        time.sleep(3)
        booted = adb(serial, "shell", "getprop", "sys.boot_completed", check=False).strip()
        if booted == "1":
            avd = require_rebuild_emulator(serial)
            print(f"{serial} booted ({avd})")
            return
    sys.exit(f"{serial} did not boot within {args.timeout} s")


if __name__ == "__main__":
    main()
