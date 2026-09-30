"""Public-source audit (plan/06 §6 gate 1): fails on anything in the tracked tree that must not be
published. Run before every push and in CI.

Checks every file git tracks (or would with `git add -A`): forbidden extensions and names, private
keys, credential-shaped tokens, credential URLs, machine paths, private network addresses and
e-mail addresses not on the allow-list. The rebuild kit in docs/rebuild/ is excluded (decision of
24 September 2026: it carries the public contact address, old machine paths and certificate
hashes by design); AGENTS.md and CLAUDE.md, which came with the kit, are excluded from the
machine-path rule only.

    python tools/audit_public_source.py
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EXCLUDED_PREFIXES = ("docs/rebuild/",)
KIT_FILES = {"AGENTS.md", "CLAUDE.md"}
# This script defines the machine-path rule, so its own patterns would match it.
RULE_FILES = {"tools/audit_public_source.py"}
RESERVED_HOST = re.compile(r"(?i)@[^/\s\"']*\.(?:example|test|invalid)\b")
# The host of an address on a reserved domain (RFC 2606), for credential query parameters.
RESERVED_URL_HOST = re.compile(r"(?i)://[^/\s\"'@:]*\.(?:example|test|invalid)(?=[:/?\s\"']|$)")

FORBIDDEN_EXTENSIONS = {
    ".aab", ".apk", ".apks", ".bak", ".db", ".jks", ".keystore", ".log", ".m3u", ".m3u8", ".smbak",
    ".sqlite", ".xmltv", ".zip", ".p12", ".pem", ".env",
}
# The one playlist allowed in the repository: Google Play's reviewer adds it from the public repository
# over HTTPS (docs/play/README.md §6). It holds only public test streams (two Blender Foundation open
# films, CC BY 3.0, and Apple's HLS test stream), no provider and no login (decision "Play review playlist").
ALLOWED_PATHS = {"docs/play/review/sohva-review.m3u"}
FORBIDDEN_NAMES = {"keystore.properties", "local.properties", "secrets.properties", "trakt-credentials.properties"}
BINARY_EXTENSIONS = {".png", ".webp", ".jpg", ".jpeg", ".gif", ".jar", ".ico", ".ttf", ".otf", ".prof", ".dm"}

# Test code and the fixture tooling may use reserved hosts.
TEST_PATH = re.compile(r"(^|/)(src/(test|androidTest)[^/]*/|tools/fixture/)")

RULES: list[tuple[str, re.Pattern[str]]] = [
    ("private key", re.compile(r"-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----")),
    ("AWS access key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("GitHub token", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36,}\b")),
    ("Slack token", re.compile(r"\bxox[abprs]-[A-Za-z0-9-]{10,}")),
    ("JWT", re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}")),
    ("assigned secret", re.compile(r"(?i)\b(?:api[_-]?key|client[_-]?secret|password|passwd|token)\s*[:=]\s*[\"'][^\"'\s]{12,}[\"']")),
    ("credential URL", re.compile(r"(?i)\b(?:https?|rtmp|rtsp)://[^/\s:@\"']+:[^/\s@\"']+@")),
    ("credential parameters", re.compile(r"(?i)[?&](?:username|password)=(?!<redacted>|%s|\$|\{)[^&\s\"']{2,}")),
]
MACHINE_PATH = re.compile(r"(?i)\b[A-Z]:\\(?:Users|SportMate|SohvaTV|Android)\b|/c/Users/|/home/[a-z]+/|\\\\wsl")
PRIVATE_ADDRESS = re.compile(r"\b(?:10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b")
EMULATOR_HOST_ALIAS = "10.0.2.2"
EMAIL = re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b")
ALLOWED_EMAILS = {"hello@luontra.fi", "noreply@anthropic.com"}
# Licence texts reproduced verbatim: MIT requires the copyright notice, its author address included.
LICENCE_TEXTS = {"app/src/main/assets/theme-licenses.txt"}
# Kotlin labels (`this@drawBehind.size`) look like addresses.
KOTLIN_LABELS = ("this", "return", "super", "continue", "break")
ALLOWED_EMAIL_DOMAINS = (".example", ".test", ".invalid", "example.com", "example.org")


def tracked_files() -> list[str]:
    out = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
        cwd=ROOT, capture_output=True, text=True, check=True, encoding="utf-8",
    ).stdout
    return sorted({line for line in out.splitlines() if line and (ROOT / line).is_file()})


def audit(path: str) -> list[str]:
    findings: list[str] = []
    name = Path(path).name
    suffix = Path(path).suffix.lower()
    if suffix in FORBIDDEN_EXTENSIONS and path.replace("\\", "/") not in ALLOWED_PATHS:
        findings.append(f"{path}: forbidden file type {suffix}")
    if name in FORBIDDEN_NAMES:
        findings.append(f"{path}: forbidden file name")
    if suffix in BINARY_EXTENSIONS:
        return findings
    try:
        text = (ROOT / path).read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return findings
    in_tests = bool(TEST_PATH.search(path))
    for number, line in enumerate(text.splitlines(), 1):
        where = f"{path}:{number}"
        for label, pattern in RULES:
            if pattern.search(line):
                # Tests may show credentials on reserved hosts, as redaction and parsing examples.
                reserved = RESERVED_HOST if label == "credential URL" else RESERVED_URL_HOST
                if label in ("credential URL", "credential parameters") and in_tests and reserved.search(line):
                    continue
                findings.append(f"{where}: {label}")
        if path not in KIT_FILES | RULE_FILES and MACHINE_PATH.search(line):
            findings.append(f"{where}: machine path")
        for address in PRIVATE_ADDRESS.findall(line):
            # The emulator's fixed alias for the host's loopback reveals no network.
            if address != EMULATOR_HOST_ALIAS:
                findings.append(f"{where}: private network address {address}")
        for email in EMAIL.findall(line):
            lower = email.lower()
            if lower.split("@")[0] in KOTLIN_LABELS:
                continue
            if lower in ALLOWED_EMAILS or lower.endswith(ALLOWED_EMAIL_DOMAINS) or path in LICENCE_TEXTS:
                continue
            findings.append(f"{where}: e-mail address not on the allow-list")
    return findings


def main() -> None:
    files = [f for f in tracked_files() if not f.startswith(EXCLUDED_PREFIXES)]
    findings = [finding for f in files for finding in audit(f)]
    for finding in findings:
        print(finding)
    print(f"public-source audit: {len(files)} files, {len(findings)} findings")
    if findings:
        sys.exit(1)


if __name__ == "__main__":
    main()
