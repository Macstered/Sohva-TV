"""The updater, end to end on the emulator (spec 72 §11, M7 exit criterion).

Builds two local release APKs of com.streammate.tv (build A and a newer build B) against a local
feed, serves B as a release with SHA256SUMS.txt and both install profiles, installs A on the
emulator, and drives Settings > About through Download, Allow installs and Install by the
controls' test tags (exposed as resource ids). Passes when the emulator runs B and ART reports
B compiled with `reason=install-dm`, the profile having arrived with the APK in one session.

Emulator only: the serial must be an emulator, never a TV. The two builds never leave this
machine; their version codes are the harness's own (decision "Updater test").

Usage: python tools/update_e2e.py [--serial emulator-5570] [--skip-build]
"""
import argparse
import functools
import http.server
import threading
import hashlib
import json
import socket
import urllib.request
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "harness-out" / "update-e2e"
sys.path.insert(0, str(Path(__file__).resolve().parent))
from sohva_device import tool  # noqa: E402

ADB = tool("platform-tools/adb")
PACKAGE = "com.streammate.tv"
# Chosen when the harness starts: a free port, so another local server can never answer instead.
PORT = 0
BUILDS = {"a": (9100, "0.2.0-e2e.1"), "b": (9101, "0.2.0-e2e.2")}

serial = "emulator-5570"


def feed() -> str:
    return f"http://10.0.2.2:{PORT}/releases"


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def adb(*args: str, check: bool = True) -> str:
    done = subprocess.run([ADB, "-s", serial, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and done.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)}: {done.stderr.strip() or done.stdout.strip()}")
    return done.stdout


def build(key: str) -> Path:
    code, name = BUILDS[key]
    log = OUT / f"build-{key}.log"
    with open(log, "w", encoding="utf-8") as f:
        done = subprocess.run(
            [str(ROOT / "gradlew.bat"), ":app:assembleRelease", f"-Psohva.updateFeed={feed()}", f"-Psohva.versionCode={code}", f"-Psohva.versionName={name}"],
            cwd=ROOT, stdout=f, stderr=subprocess.STDOUT,
        )
    if done.returncode != 0:
        sys.exit(f"build {key} failed; see {log}")
    target = OUT / key
    target.mkdir(parents=True, exist_ok=True)
    apk = ROOT / "app/build/outputs/apk/release"
    base = f"sohva-tv-{name}"
    shutil.copy(apk / "app-release.apk", target / f"{base}.apk")
    shutil.copy(apk / "baselineProfiles/0/app-release.dm", target / f"{base}.api31.dm")
    shutil.copy(apk / "baselineProfiles/1/app-release.dm", target / f"{base}.api28.dm")
    return target


def publish(b: Path) -> Path:
    """The feed as GitHub serves it (spec 72 §7.1, §7.5), for build B only."""
    site = OUT / "site"
    shutil.rmtree(site, ignore_errors=True)
    site.mkdir(parents=True)
    code, name = BUILDS["b"]
    files = sorted(p.name for p in b.iterdir())
    for n in files:
        shutil.copy(b / n, site / n)
    sums = "".join(f"{hashlib.sha256((site / n).read_bytes()).hexdigest()}  {n}\n" for n in files)
    (site / "SHA256SUMS.txt").write_text(sums, encoding="utf-8", newline="\n")
    assets = [{"name": n, "browser_download_url": f"http://10.0.2.2:{PORT}/{n}", "size": (site / n).stat().st_size} for n in ["SHA256SUMS.txt", *files]]
    body = f"# Sohva TV {name}\n\nAndroid build **{code}**. Local update test.\n\n## Changed since e2e.1\n- The updater installs this build with its profile.\n"
    feed = [{"tag_name": f"v{name}", "name": f"Sohva TV {name}", "draft": False, "prerelease": True, "body": body, "assets": assets}]
    (site / "releases").write_text(json.dumps(feed), encoding="utf-8")
    return site


def dump() -> ET.Element:
    for _ in range(5):
        adb("shell", "uiautomator", "dump", "/sdcard/sohva-ui.xml", check=False)
        raw = adb("exec-out", "cat", "/sdcard/sohva-ui.xml", check=False)
        if raw.strip().startswith("<?xml"):
            return ET.fromstring(raw)
        time.sleep(0.5)
    raise RuntimeError("no UI dump")


def find(res: str | None = None, texts: tuple[str, ...] = ()) -> tuple[int, int] | None:
    for node in dump().iter("node"):
        if res and node.get("resource-id") != res:
            continue
        if texts and node.get("text", "").strip().lower() not in {t.lower() for t in texts}:
            continue
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "[0,0][0,0]")))
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def wait_for(res: str | None = None, texts: tuple[str, ...] = (), timeout: float = 60) -> tuple[int, int] | None:
    end = time.time() + timeout
    while time.time() < end:
        at = find(res, texts)
        if at:
            return at
        time.sleep(1)
    return None


def tap(res: str | None = None, texts: tuple[str, ...] = (), timeout: float = 60) -> None:
    at = wait_for(res, texts, timeout)
    if not at:
        raise RuntimeError(f"not found: {res or texts}")
    adb("shell", "input", "tap", str(at[0]), str(at[1]))
    time.sleep(1.5)


def installed_code() -> int | None:
    m = re.search(r"versionCode=(\d+)", adb("shell", "dumpsys", "package", PACKAGE, check=False))
    return int(m.group(1)) if m else None


def main() -> None:
    global serial, PORT
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default=serial)
    parser.add_argument("--skip-build", action="store_true")
    args = parser.parse_args()
    serial = args.serial
    if not serial.startswith("emulator-"):
        sys.exit("emulator only: this installs and drives the app")
    OUT.mkdir(parents=True, exist_ok=True)
    PORT = int((OUT / "port").read_text()) if args.skip_build else free_port()
    (OUT / "port").write_text(str(PORT))
    b = OUT / "b" if args.skip_build else build("b")
    a = OUT / "a" if args.skip_build else build("a")
    site = publish(b)
    # HTTP/1.1 with keep-alive: the emulator's network sometimes cut short a response from a server
    # that closes the connection right after writing (python -m http.server, HTTP/1.0).
    class Handler(http.server.SimpleHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, *args) -> None:
            pass

    server = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), functools.partial(Handler, directory=str(site)))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        # The feed must come from this server, not from anything else on the port.
        for _ in range(20):
            try:
                with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/releases", timeout=2) as r:
                    json.loads(r.read())
                break
            except Exception:
                time.sleep(0.5)
        else:
            sys.exit(f"the local feed on port {PORT} does not answer")
        adb("uninstall", PACKAGE, check=False)
        adb("install", str(next(a.glob("*.apk"))))
        adb("logcat", "-c")
        adb("shell", "monkey", "-p", PACKAGE, "-c", "android.intent.category.LEANBACK_LAUNCHER", "1")
        # The automatic check runs after the first frame of a fresh install (ABOUT-FR-04).
        tap("home-settings")
        tap("settings-section-about")
        if not wait_for("settings-update-download", timeout=30):
            tap("settings-update-check")
        tap("settings-update-download")
        tap("settings-update-install", timeout=120)
        if find("settings-update-permission"):
            # ABOUT-09: Allow installs opens Android's page for this app; allow, come back, Install.
            tap("settings-update-permission")
            # Phones name the switch; Android TV lists the app with its state ("Sohva TV · Not allowed").
            tap(texts=("Allow from this source", "Sohva TV"), timeout=20)
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(2)
            # Android kills the app when the permission changes; the verified download is kept.
            if not wait_for("settings-update-install", timeout=5):
                adb("shell", "monkey", "-p", PACKAGE, "-c", "android.intent.category.LEANBACK_LAUNCHER", "1")
                tap("home-settings")
                tap("settings-section-about")
            tap("settings-update-install")
        # Android's own confirmation (ABOUT-FR-18).
        tap(texts=("Update", "Install"), timeout=60)
        end = time.time() + 180
        while time.time() < end and installed_code() != BUILDS["b"][0]:
            time.sleep(2)
        code = installed_code()
        log = adb("logcat", "-d", "-s", "SohvaTV:I", check=False).replace("\x00", "")
        # ART reports the install-time compile a moment after the new version code appears.
        block = ""
        end = time.time() + 90
        while time.time() < end:
            rows = adb("shell", "dumpsys", "package", "dexopt", check=False).splitlines()
            at = next((i for i, row in enumerate(rows) if row.strip() == f"[{PACKAGE}]"), None)
            block = "\n".join(rows[at:at + 3]) if at is not None else ""
            if "reason=install-dm" in block:
                break
            time.sleep(3)
        lines = [
            f"installed versionCode: {code} (expected {BUILDS['b'][0]})",
            f"session with profile logged: {'update: installing with profile' in log}",
            f"install-dm: {'reason=install-dm' in block}",
            block,
        ]
        (OUT / "result.txt").write_text("\n".join(lines), encoding="utf-8")
        print("\n".join(lines))
        if code != BUILDS["b"][0] or "reason=install-dm" not in block:
            sys.exit(1)
    finally:
        server.shutdown()
        server.server_close()


if __name__ == "__main__":
    main()
