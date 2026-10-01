"""Runs the Build workflow's checks locally, in the same order (lessons 8.1: the public build was
red for weeks while nobody ran lint locally). Run before every push.

    python tools/check_all.py
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GRADLE = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))


def gitleaks() -> str:
    for candidate in [os.environ.get("GITLEAKS", "")] + [str(p) for p in Path(os.environ.get("LOCALAPPDATA", "/nonexistent")).glob("Microsoft/WinGet/Packages/Gitleaks*/gitleaks.exe")]:
        if candidate and Path(candidate).is_file():
            return candidate
    return "gitleaks"


STEPS: list[tuple[str, list[str]]] = [
    ("strings", [sys.executable, "tools/import_strings.py", "--check"]),
    ("French coverage and formatting", [sys.executable, "tools/check_french_strings.py"]),
    ("public-source audit", [sys.executable, "tools/audit_public_source.py"]),
    ("gitleaks tree", ["publishable-tree"]),
    ("gitleaks history", [gitleaks(), "git", ".", "--config", ".gitleaks.toml", "--no-banner", "--redact"]),
    ("gradle", [
        GRADLE, "checkModuleDependencies", "testDebugUnitTest", ":core:model:test", ":lint-checks:test",
        "verifyRoborazziDebug", "lintDebug", ":app:lintRelease", ":app:lintLab", ":app:lintPlay", "assembleDebugAndroidTest",
        ":app:assembleRelease", ":app:assembleLab", ":app:assemblePlay", ":app:bundlePlay",
        ":app:assembleBenchmarkRelease", ":benchmark:assemble",
        ":app:checkReleaseGates", ":app:checkPlayGates",
    ]),
    # The APK safety and release-document audits on the release just built (plan/06 §6 gates 2 and 7).
    ("release audits", [sys.executable, "tools/package_release.py", "--audit-only"]),
]


def scan_publishable_tree(out) -> int:
    """
    Scans what a commit could contain: tracked files and untracked files git does not ignore.
    `gitleaks dir .` also reads ignored local material (the emulator signing setup in .local/,
    R8 mapping files in build/), which never leaves the machine and made the step fail on every
    run after a release build.
    """
    listed = subprocess.run(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
        cwd=ROOT, capture_output=True, check=True,
    ).stdout.decode("utf-8").split("\0")
    with tempfile.TemporaryDirectory(prefix="sohva-leaks-") as copy:
        for name in filter(None, listed):
            source = ROOT / name
            if source.is_file():
                target = Path(copy) / name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
        command = [gitleaks(), "dir", copy, "--config", str(ROOT / ".gitleaks.toml"), "--no-banner", "--redact"]
        return subprocess.run(command, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT).returncode


def main() -> None:
    log = ROOT / "harness-out" / "check_all.log"
    log.parent.mkdir(exist_ok=True)
    with log.open("w", encoding="utf-8") as out:
        for name, command in STEPS:
            print(f"== {name}", flush=True)
            # Gradle output goes to a file, never a pipe: the daemon holds a pipe open (CLAUDE.md).
            out.flush()
            if command == ["publishable-tree"]:
                code = scan_publishable_tree(out)
            else:
                code = subprocess.run(command, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT).returncode
            if code != 0:
                sys.exit(f"{name} failed; see {log}")
    print(f"all checks passed ({log})")


if __name__ == "__main__":
    main()
