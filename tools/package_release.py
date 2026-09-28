"""Packages a release for testers (plan/06 §6 gates 7 and 9, §8): the five assets and nothing else.

    python tools/package_release.py --version 0.2.0-beta.3 --code 106

It checks, in order, and stops at the first failure:
1. the version name and code match the release APK's badging; the folder does not exist yet
   (a frozen build is never overwritten, plan/06 §6 gate 11);
2. the release dex was verified on the stand-in for this exact APK (`verify_release_dex.py`'s receipt);
3. the APK safety audit: package, label, not debuggable, permissions on the allow-list, signed by
   the Sohva TV certificate, no sensitive entries, and no secret-shaped text in any text entry;
4. both install profiles belong to this APK;
5. the nine tester documents exist, four of them name the version, and the document audit passes;
then copies everything into `release/<version>/`, writes SHA256SUMS.txt, the tester ZIP and
assets.json, and checks the ZIP again. It uploads, commits and publishes nothing.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_public_source as audit  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
APK = ROOT / "app/build/outputs/apk/release/app-release.apk"
PROFILES = {  # asset suffix -> AGP output (plan/06 §8)
    "api31": ROOT / "app/build/outputs/apk/release/baselineProfiles/0/app-release.dm",
    "api28": ROOT / "app/build/outputs/apk/release/baselineProfiles/1/app-release.dm",
}
DOCS = ROOT / "docs/release"
OUT = ROOT / "release"
DOCUMENTS = [
    "README.md", "INSTALL.md", "ADDONS.md", "TESTING.md", "RELEASE_NOTES.md", "PRIVACY.md",
    "THIRD_PARTY_NOTICES.md", "LICENSE", "LICENSE-APACHE-2.0.txt",
]
# Documents that must name the version being packaged (plan/06 §6 gate 9).
VERSIONED = ["README.md", "INSTALL.md", "TESTING.md", "RELEASE_NOTES.md"]
VERSION = re.compile(r"^\d+\.\d+\.\d+-beta\.\d+$")

PACKAGE = "com.streammate.tv"
LABEL = "Sohva TV"
CERTIFICATE = "985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6"
# Spec 73 SEC-FR-29, plus SCHEDULE_EXACT_ALARM (decision "Spec 22 open questions" Q1).
PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.WAKE_LOCK",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "com.streammate.tv.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}
# Spec 73 SEC-FR-30: the only exported components of the merged release manifest.
EXPORTED = {
    ("activity-alias", "com.streammate.tv.app.MainActivity"),
    ("service", "com.sohva.tv.core.player.PlaybackService"),
    ("service", "androidx.work.impl.background.systemjob.SystemJobService"),
    ("receiver", "androidx.work.impl.diagnostics.DiagnosticsReceiver"),
    ("receiver", "androidx.profileinstaller.ProfileInstallReceiver"),
    ("activity", "androidx.media3.session.BluetoothValidationActivity"),
}
# Release documents may only be these files (plan/06 §6 gate 2).
DOCUMENT_EXTENSIONS = {"", ".md", ".txt"}
SENSITIVE_ENTRY = re.compile(r"(?i)(\.jks|\.keystore|\.properties|\.pem|\.p12|\.smbak|\.db|\.sqlite|\.m3u8?|\.xmltv|secret|credential)$")
# Entries the build tools add to every APK; their text is still scanned below.
KNOWN_ENTRIES = {"META-INF/com/android/build/gradle/app-metadata.properties"}
VCS_ENTRY = "META-INF/version-control-info.textproto"
TEXT_ENTRY = re.compile(r"(?i)\.(txt|json|xml|properties|md|html|js|css|version|pro|kotlin_builtins)$|^META-INF/.*\.(MF|version)$")
# Release documents: every URL host must be one of these (plan/06 §6 gate 2).
DOC_HOSTS = {
    "github.com", "www.gnu.org", "www.apache.org", "trakt.tv", "www.themoviedb.org", "www.tvmaze.com",
    "api-sports.io", "www.stremio.com", "stremio.com", "developer.android.com", "fsf.org", "gnu.org",
}
URL = re.compile(r"https?://([A-Za-z0-9.-]+)")
AUTH = re.compile(r"(?i)\b(authorization:|bearer\s+[A-Za-z0-9._-]{12,})")


def fail(message: str) -> None:
    sys.exit(f"package_release: {message}")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sdk_tool(name: str) -> str:
    tools = sorted((Path("C:/Android/sdk/build-tools")).glob("*"), key=lambda p: [int(x) if x.isdigit() else 0 for x in p.name.split(".")])
    if not tools:
        fail("no Android build-tools found")
    for candidate in (tools[-1] / name, tools[-1] / f"{name}.exe", tools[-1] / f"{name}.bat"):
        if candidate.exists():
            return str(candidate)
    fail(f"{name} not found in {tools[-1]}")
    return ""


def run(*command: str) -> str:
    return subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace", check=True).stdout


def check_badging(version: str, code: int) -> None:
    badging = run(sdk_tool("aapt2"), "dump", "badging", str(APK))
    head = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not head or head.group(1) != PACKAGE or int(head.group(2)) != code or head.group(3) != version:
        fail(f"badging says {head.groups() if head else 'nothing'}, expected {PACKAGE} {code} {version}")
    label = re.search(r"application-label:'([^']*)'", badging)
    if not label or label.group(1) != LABEL:
        fail(f"label is {label.group(1) if label else 'missing'}, expected {LABEL}")
    if "application-debuggable" in badging:
        fail("the release APK is debuggable")
    used = set(re.findall(r"uses-permission: name='([^']+)'", badging))
    extra = used - PERMISSIONS
    if extra:
        fail(f"permissions not on the allow-list: {sorted(extra)}")


def check_manifest() -> None:
    """SEC-18, SEC-19: exported components on the allow-list; profileable by the shell only."""
    tree = run(sdk_tool("aapt2"), "dump", "xmltree", "--file", "AndroidManifest.xml", str(APK))
    exported: set[tuple[str, str]] = set()
    component: list | None = None
    for line in tree.splitlines():
        element = re.match(r"\s*E: (activity-alias|activity|service|receiver|provider) ", line)
        if element:
            component = [element.group(1), None, None]
            continue
        if component is None:
            continue
        name = re.search(r'android:name\(0x01010003\)="([^"]+)"', line)
        if name and component[1] is None:
            component[1] = name.group(1)
        flag = re.search(r"android:exported\(0x01010010\)=(\w+)", line)
        if flag and component[2] is None:
            component[2] = flag.group(1)
            if flag.group(1) == "true" and component[1]:
                exported.add((component[0], component[1]))
    if exported != EXPORTED:
        fail(f"exported components differ from the allow-list: extra {sorted(exported - EXPORTED)}, missing {sorted(EXPORTED - exported)}")
    if not re.search(r"E: profileable[\s\S]*?android:shell\(0x01010594\)=true", tree):
        fail("the release is not profileable by the shell (spec 73 SEC-FR-31)")
    if "android:debuggable(0x0101000f)=true" in tree:
        fail("the release manifest is debuggable")


def check_signature() -> None:
    out = run(sdk_tool("apksigner"), "verify", "--print-certs", str(APK))
    digests = re.findall(r"certificate SHA-256 digest: ([0-9a-f]{64})", out)
    if digests != [CERTIFICATE]:
        fail(f"signed by {digests}, expected the Sohva TV certificate only")


def check_entries() -> None:
    findings: list[str] = []
    with zipfile.ZipFile(APK) as apk:
        # SEC-27: the source-revision record names only the project root and a commit.
        vcs = apk.read(VCS_ENTRY).decode("utf-8") if VCS_ENTRY in apk.namelist() else ""
        roots = re.findall(r'local_root_path: "([^"]*)"', vcs)
        revisions = re.findall(r'revision: "([^"]*)"', vcs)
        if vcs and (roots != ["$PROJECT_DIR"] or len(revisions) != 1 or not re.fullmatch(r"[0-9a-f]{40}", revisions[0])):
            findings.append(f"{VCS_ENTRY} holds more than the project root and a commit")
        for info in apk.infolist():
            name = info.filename
            if SENSITIVE_ENTRY.search(name) and name not in KNOWN_ENTRIES:
                findings.append(f"sensitive-looking entry {name}")
            if not TEXT_ENTRY.search(name) or info.file_size > 4 * 1024 * 1024:
                continue
            text = apk.read(name).decode("utf-8", errors="replace")
            for label, pattern in audit.RULES:
                if pattern.search(text):
                    findings.append(f"{name}: {label}")
            if audit.MACHINE_PATH.search(text):
                findings.append(f"{name}: machine path")
            for address in audit.PRIVATE_ADDRESS.findall(text):
                if address != audit.EMULATOR_HOST_ALIAS:
                    findings.append(f"{name}: private network address")
    if findings:
        fail("APK safety audit:\n  " + "\n  ".join(findings[:20]))


def check_receipt() -> None:
    receipt = APK.with_name(APK.name + ".dex-verified")
    if not receipt.is_file() or receipt.read_text(encoding="utf-8").strip() != sha256(APK):
        fail("no dex receipt for this exact APK; run tools/verify_release_dex.py --serial emulator-5570 first")


def check_profiles() -> None:
    with zipfile.ZipFile(APK) as apk:
        names = set(apk.namelist())
        prof = apk.read("assets/dexopt/baseline.prof") if "assets/dexopt/baseline.prof" in names else None
        profm = apk.read("assets/dexopt/baseline.profm") if "assets/dexopt/baseline.profm" in names else None
    if prof is None or profm is None:
        fail("the APK carries no baseline profile")
    for suffix, dm in PROFILES.items():
        if not dm.is_file():
            fail(f"missing install profile {dm}")
        with zipfile.ZipFile(dm) as z:
            inside = {n: z.read(n) for n in z.namelist()}
        # plan/06 §8: the API 31 profile carries the APK's .profm; the API 28 one its .prof.
        wanted = profm if suffix == "api31" else prof
        if wanted not in inside.values():
            fail(f"the {suffix} install profile does not belong to this APK")


def check_documents(version: str) -> None:
    missing = [d for d in DOCUMENTS if not (DOCS / d).is_file()]
    if missing:
        fail(f"tester documents missing in docs/release: {missing}")
    stray = [p.name for p in DOCS.iterdir() if p.name not in DOCUMENTS or p.suffix not in DOCUMENT_EXTENSIONS]
    if stray:
        fail(f"docs/release holds files that are not tester documents: {stray}")
    for name in VERSIONED:
        if version not in (DOCS / name).read_text(encoding="utf-8"):
            fail(f"{name} does not name {version}")
    findings: list[str] = []
    for name in DOCUMENTS:
        text = (DOCS / name).read_text(encoding="utf-8")
        for host in URL.findall(text):
            if host.lower() not in DOC_HOSTS:
                findings.append(f"{name}: host {host} not on the allow-list")
        if AUTH.search(text):
            findings.append(f"{name}: authorisation header or bearer value")
        for label, pattern in audit.RULES:
            if pattern.search(text):
                findings.append(f"{name}: {label}")
        for address in audit.PRIVATE_ADDRESS.findall(text):
            findings.append(f"{name}: private network address {address}")
        for email in audit.EMAIL.findall(text):
            if email.lower() not in audit.ALLOWED_EMAILS and name not in {"LICENSE", "LICENSE-APACHE-2.0.txt"}:
                findings.append(f"{name}: e-mail address")
    if findings:
        fail("release-document audit:\n  " + "\n  ".join(findings))


def package(version: str) -> Path:
    folder = OUT / version
    if folder.exists():
        fail(f"{folder} exists: a packaged build is never overwritten; a changed build gets a new version code")
    folder.mkdir(parents=True)
    base = f"sohva-tv-{version}"
    shutil.copyfile(APK, folder / f"{base}.apk")
    for suffix, dm in PROFILES.items():
        shutil.copyfile(dm, folder / f"{base}.{suffix}.dm")
    for name in DOCUMENTS:
        shutil.copyfile(DOCS / name, folder / name)
    files = sorted(p.name for p in folder.iterdir())
    sums = "".join(f"{sha256(folder / n)}  {n}\n" for n in files)
    (folder / "SHA256SUMS.txt").write_bytes(sums.encode("utf-8"))
    pack = folder / f"{base}-tester-pack.zip"
    with zipfile.ZipFile(pack, "w", zipfile.ZIP_DEFLATED) as z:
        for name in files + ["SHA256SUMS.txt"]:
            z.write(folder / name, name)
    # Every ZIP entry is exactly its file (plan/06 §6 gate 9).
    with zipfile.ZipFile(pack) as z:
        if sorted(z.namelist()) != sorted(files + ["SHA256SUMS.txt"]):
            fail("the tester ZIP does not hold exactly the packaged files")
        for name in z.namelist():
            if hashlib.sha256(z.read(name)).hexdigest() != sha256(folder / name):
                fail(f"ZIP entry {name} differs from its file")
    assets = [f"{base}.apk", f"{base}.api31.dm", f"{base}.api28.dm", "SHA256SUMS.txt", pack.name]
    (folder / "assets.json").write_text(
        json.dumps({n: {"size": (folder / n).stat().st_size, "sha256": sha256(folder / n)} for n in assets}, indent=2) + "\n",
        encoding="utf-8",
    )
    return folder


def audit_only() -> None:
    """`check_all.py`'s step: the APK and document audits on the current release build; nothing is written."""
    if not APK.is_file():
        fail("no release APK; run ./gradlew :app:assembleRelease first")
    badging = run(sdk_tool("aapt2"), "dump", "badging", str(APK))
    head = re.search(r"versionCode='(\d+)' versionName='([^']+)'", badging)
    if not head:
        fail("no version in the badging")
    version, code = head.group(2), int(head.group(1))
    check_badging(version, code)
    check_signature()
    check_manifest()
    check_entries()
    check_profiles()
    check_documents(version)
    print(f"Release audits pass for {version} (build {code}).")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version")
    parser.add_argument("--code", type=int)
    parser.add_argument("--audit-only", action="store_true", help="the APK and document audits only; no receipt needed")
    args = parser.parse_args()
    if args.audit_only:
        audit_only()
        return
    if not args.version or args.code is None:
        fail("--version and --code are required to package")
    if not VERSION.match(args.version):
        fail("the version name must look like 0.2.0-beta.1")
    if args.code <= 57:
        fail("the version code must be above 57 (plan/06 §7)")
    if not APK.is_file():
        fail("no release APK; run ./gradlew :app:assembleRelease first")
    if (OUT / args.version).exists():
        fail(f"release/{args.version} exists: a packaged build is never overwritten")
    check_badging(args.version, args.code)
    check_receipt()
    check_signature()
    check_manifest()
    check_entries()
    check_profiles()
    check_documents(args.version)
    folder = package(args.version)
    print(f"Packaged {args.version} (build {args.code}) into {folder.relative_to(ROOT)}; nothing was uploaded or published.")


if __name__ == "__main__":
    main()
