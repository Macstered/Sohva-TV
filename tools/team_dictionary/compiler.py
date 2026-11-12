"""Deterministic reviewed snapshot -> indexed, read-only Android asset. No research DB ships."""

from __future__ import annotations

import hashlib
import json
import sqlite3
import uuid
from datetime import date
from pathlib import Path

from .registry import KINDS, canonical_sport, interval
from .store import normalise

FORMAT = 1
NORMALISATION = 1
MAX_VARIANTS = 64
MAX_TEXT = 300
MAX_BYTES = 16 * 1024 * 1024
FIRST_DAY = -719162
LAST_DAY = 2932896


def day(value: str, default: int) -> int:
    return (date.fromisoformat(value) - date(1970, 1, 1)).days if value else default


def compile_snapshot(source: Path, output: Path) -> dict:
    data = json.loads(source.read_text(encoding="utf-8"))
    if data.get("format") != FORMAT or data.get("normalisation") != NORMALISATION:
        raise ValueError("Unsupported dictionary snapshot or normalisation version.")
    identities = {}
    for row in data["identities"]:
        uuid.UUID(row["uuid"])
        if row["uuid"] in identities or row["kind"] not in KINDS or row["status"] != "accepted" or not row["category"] or len(row["category"]) > 64 or not row["sport"] or len(row["sport"]) > 32:
            raise ValueError("Invalid, duplicate or unreviewed identity.")
        identities[row["uuid"]] = row
    credits = {row["source"]: row for row in data["credits"]}
    if any(row["status"] != "accepted" or not row["licence"] or not row["attribution"] for row in credits.values()):
        raise ValueError("Unreviewed source attribution.")
    bindings = {}
    for row in data["bindings"]:
        identity = identities.get(row["uuid"])
        key = tuple(row[field] for field in ("provider", "product", "kind", "external_id"))
        if not identity or row["status"] != "accepted" or any(not value or len(value) > 128 for value in key):
            raise ValueError("Invalid or unreviewed provider binding.")
        if identity["sport"] != canonical_sport(row["product"]) or identity["kind"] != row["kind"] or key in bindings:
            raise ValueError("Binding crosses a sport/kind or duplicates an external key.")
        bindings[key] = row["uuid"]
    aliases = set()
    for row in data["aliases"]:
        if row["uuid"] not in identities or row["status"] != "accepted" or row["source"] not in credits:
            raise ValueError("Unreviewed or orphan alias/source.")
        norm = normalise(row["spelling"])
        if norm != row["normalised"] or len(norm.replace(" ", "")) < 3 or len(norm) > MAX_TEXT:
            raise ValueError("Alias normalisation or length is invalid.")
        interval(row["valid_from"], row["valid_to"])
        aliases.add((row["uuid"], norm, day(row["valid_from"], FIRST_DAY), day(row["valid_to"], LAST_DAY)))
    variants = {}
    for identity, norm, _, _ in aliases:
        variants.setdefault(identity, set()).add(norm)
    if any(len(names) > MAX_VARIANTS for names in variants.values()):
        raise ValueError("More than 64 variants for an identity; review the group instead of truncating it.")
    memberships = set()
    for row in data["memberships"]:
        participant, competition = identities.get(row["participant"]), identities.get(row["competition"])
        if not participant or not competition or row["status"] != "accepted" or row["source"] not in credits:
            raise ValueError("Unreviewed membership.")
        if participant["sport"] != competition["sport"] or competition["kind"] != "competition" or participant["kind"] == "competition":
            raise ValueError("Membership crosses a sport or kind.")
        if len(row["season"]) > 128:
            raise ValueError("Membership season exceeds the narrow lookup-row limit.")
        interval(row["valid_from"], row["valid_to"])
        memberships.add((row["participant"], row["competition"], row["season"],
                         day(row["valid_from"], FIRST_DAY), day(row["valid_to"], LAST_DAY)))
    collisions = set()
    for row in data["collisions"]:
        norm = row["normalised"]
        if row["kind"] not in KINDS or normalise(norm) != norm or len(norm) > MAX_TEXT:
            raise ValueError("Invalid ambiguity marker.")
        collisions.add((row["sport"], row["kind"], norm))
    # Guards come from reviewed qualified identities. No city/acronym/category guessing.
    seniors = {}
    for identity, norm, start, end in aliases:
        row = identities[identity]
        if row["category"] in ("senior", "senior-men", "open"):
            seniors.setdefault((row["sport"], row["kind"]), []).append((norm, start, end))
    guards = set()
    for identity, norm, start, end in aliases:
        row = identities[identity]
        if row["category"] in ("senior", "senior-men", "open", "unknown"):
            continue
        for base, base_start, base_end in seniors.get((row["sport"], row["kind"]), ()):
            if norm == base and max(start, base_start) <= min(end, base_end):
                raise ValueError("Qualified alias loses its category to a reviewed senior identity.")
            if norm.startswith(base + " "):
                first, last = max(start, base_start), min(end, base_end)
                if first <= last:
                    guards.add((row["sport"], row["kind"], base, norm, first, last))
    rows = {
        "identity": sorted((key, row["sport"], row["kind"], row["category"]) for key, row in identities.items()),
        "binding": sorted((*key, value) for key, value in bindings.items()),
        "alias": sorted(aliases), "collision": sorted(collisions),
        "guard": sorted(guards), "membership": sorted(memberships),
    }
    # This is the runtime content identity, independent of evidence spelling or review-log order.
    encoded = json.dumps(dict(format=FORMAT, normalisation=NORMALISATION, rows=rows), sort_keys=True, separators=(",", ":")).encode()
    revision = hashlib.sha256(encoded).hexdigest()
    output.mkdir(parents=True, exist_ok=True)
    temporary = output / "dictionary.tmp"
    temporary.unlink(missing_ok=True)
    db = sqlite3.connect(temporary)
    try:
        db.executescript(Path(__file__).with_name("schema.sql").read_text(encoding="utf-8"))
        for table, values in rows.items():
            if values:
                db.executemany(f"INSERT INTO {table} VALUES ({','.join('?' for _ in values[0])})", values)
        db.commit()
        if db.execute("PRAGMA foreign_key_check").fetchall():
            raise ValueError("Dictionary contains broken references.")
        db.execute("VACUUM")
    finally:
        db.close()
    if temporary.stat().st_size > MAX_BYTES:
        temporary.unlink()
        raise ValueError("Dictionary exceeds the runtime 16 MiB file limit.")
    destination = output / "sport-dictionary.db"
    temporary.replace(destination)
    manifest = dict(format=FORMAT, normalisation=NORMALISATION, revision=revision,
                    sha256=hashlib.sha256(destination.read_bytes()).hexdigest(), bytes=destination.stat().st_size,
                    counts={key: len(value) for key, value in rows.items()})
    (output / "sport-dictionary.json").write_text(json.dumps(manifest, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    (output / "sport-dictionary-credits.json").write_text(json.dumps(sorted(credits.values(), key=lambda row: row["source"]), ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    return manifest
