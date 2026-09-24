"""Runs the Build workflow's checks locally, in the same order (lessons 8.1: the public build was
red for weeks while nobody ran lint locally). Run before every push.

    python tools/check_all.py
"""

from __future__ import annotations

import os
import subprocess
import sys
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
    ("public-source audit", [sys.executable, "tools/audit_public_source.py"]),
    ("gitleaks tree", [gitleaks(), "dir", ".", "--config", ".gitleaks.toml", "--no-banner", "--redact"]),
    ("gitleaks history", [gitleaks(), "git", ".", "--config", ".gitleaks.toml", "--no-banner", "--redact"]),
    ("gradle", [
        GRADLE, "checkModuleDependencies", "testDebugUnitTest", ":core:model:test", ":lint-checks:test",
        "verifyRoborazziDebug", "lintDebug", ":app:lintRelease", ":app:lintLab", "assembleDebugAndroidTest",
        ":app:assembleRelease", ":app:assembleLab", ":benchmark:assemble", ":app:checkReleaseGates",
    ]),
]


def main() -> None:
    log = ROOT / "harness-out" / "check_all.log"
    log.parent.mkdir(exist_ok=True)
    with log.open("w", encoding="utf-8") as out:
        for name, command in STEPS:
            print(f"== {name}", flush=True)
            # Gradle output goes to a file, never a pipe: the daemon holds a pipe open (CLAUDE.md).
            result = subprocess.run(command, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT)
            if result.returncode != 0:
                sys.exit(f"{name} failed; see {log}")
    print(f"all checks passed ({log})")


if __name__ == "__main__":
    main()
