"""Allow only the compiler's source-free runtime dictionary inside an APK."""

from __future__ import annotations

import hashlib
import json
import sqlite3
import uuid
import zipfile
from pathlib import Path

from team_dictionary.compiler import FORMAT, NORMALISATION, MAX_BYTES, MAX_TEXT
from team_dictionary.registry import KINDS, canonical_sport
from team_dictionary.store import normalise

ENTRIES = {f"assets/sport-dictionary{suffix}" for suffix in (".db", ".json", "-credits.json")}


def verify(apk: zipfile.ZipFile) -> set[str]:
    present = ENTRIES.intersection(apk.namelist())
    if not present:
        return set()
    if present != ENTRIES:
        raise ValueError("Incomplete runtime dictionary assets.")
    if apk.getinfo("assets/sport-dictionary.json").file_size > 4096:
        raise ValueError("Oversized dictionary manifest.")
    manifest = json.loads(apk.read("assets/sport-dictionary.json"))
    if manifest.get("format") != FORMAT or manifest.get("normalisation") != NORMALISATION:
        raise ValueError("Unsupported runtime dictionary.")
    size = apk.getinfo("assets/sport-dictionary.db").file_size
    if not 0 < size <= MAX_BYTES or size != manifest.get("bytes"):
        raise ValueError("Invalid runtime dictionary size.")
    content = apk.read("assets/sport-dictionary.db")
    if hashlib.sha256(content).hexdigest() != manifest.get("sha256"):
        raise ValueError("Runtime dictionary checksum mismatch.")
    expected = sqlite3.connect(":memory:")
    db = sqlite3.connect(":memory:")
    try:
        expected.executescript(Path(__file__).with_name("team_dictionary").joinpath("schema.sql").read_text(encoding="utf-8"))
        db.deserialize(content)
        schema = "SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name"
        if db.execute(schema).fetchall() != expected.execute(schema).fetchall():
            raise ValueError("Runtime dictionary schema differs; research tables are forbidden.")
        if db.execute("PRAGMA user_version").fetchone()[0] != FORMAT or db.execute("PRAGMA quick_check").fetchall() != [("ok",)] or db.execute("PRAGMA foreign_key_check").fetchall():
            raise ValueError("Invalid runtime dictionary database.")
        rows = {table: sorted(db.execute(f"SELECT * FROM {table}").fetchall()) for table in ("identity", "binding", "alias", "collision", "guard", "membership")}
        identities = {identity: (sport, kind) for identity, sport, kind, _ in rows["identity"]}
        for identity, sport, kind, category in rows["identity"]:
            uuid.UUID(identity)
            if kind not in KINDS or not sport or not category or len(category) > 64:
                raise ValueError("Invalid runtime identity.")
        for provider, product, kind, external, identity in rows["binding"]:
            if identities[identity] != (canonical_sport(product), kind) or any(not text or len(text) > 128 for text in (provider, product, kind, external)):
                raise ValueError("Invalid runtime namespace binding.")
        for _, text, start, end in rows["alias"]:
            if normalise(text) != text or len(text) > MAX_TEXT or len(text.replace(" ", "")) < 3 or not -719162 <= start <= end <= 2932896:
                raise ValueError("Invalid runtime alias or validity.")
        for participant, competition, season, start, end in rows["membership"]:
            if len(season) > 128 or identities[participant][0] != identities[competition][0] or identities[competition][1] != "competition" or not -719162 <= start <= end <= 2932896:
                raise ValueError("Invalid runtime membership.")
        counts = {table: len(values) for table, values in rows.items()}
        encoded = json.dumps(dict(format=FORMAT, normalisation=NORMALISATION, rows=rows), sort_keys=True, separators=(",", ":")).encode()
        if counts != manifest.get("counts") or hashlib.sha256(encoded).hexdigest() != manifest.get("revision"):
            raise ValueError("Runtime dictionary content revision/count mismatch.")
        credits = json.loads(apk.read("assets/sport-dictionary-credits.json"))
        if not credits or any(row.get("status") != "accepted" or not row.get("licence") or not row.get("attribution") for row in credits):
            raise ValueError("Runtime dictionary attribution is unreviewed.")
    finally:
        db.close()
        expected.close()
    return ENTRIES
