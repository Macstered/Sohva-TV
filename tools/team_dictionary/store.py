"""Checkpointed research store. Keys and raw HTTP responses are never persisted."""

from __future__ import annotations

import json
import os
import re
import sqlite3
import unicodedata
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def utc_day() -> str:
    return datetime.now(timezone.utc).date().isoformat()


def timestamp() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def normalise(value: str) -> str:
    # Matches SPORT-FR-105; retain the original Unicode spelling separately.
    text = "".join(c for c in unicodedata.normalize("NFKD", value) if not unicodedata.category(c).startswith("M"))
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


class Store:
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(path)
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA foreign_keys = ON")
        self.db.execute("PRAGMA busy_timeout = 5000")
        version = self.db.execute("PRAGMA user_version").fetchone()[0]
        if version not in (0, 1, 2, 3, 4, 5):
            raise ValueError("The research database needs a newer collector.")
        self.db.executescript("""
            CREATE TABLE IF NOT EXISTS jobs (
                id INTEGER PRIMARY KEY, provider TEXT NOT NULL, sport TEXT NOT NULL,
                endpoint TEXT NOT NULL, params TEXT NOT NULL, kind TEXT NOT NULL,
                scope TEXT NOT NULL DEFAULT 'shared',
                context TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'pending',
                attempts INTEGER NOT NULL DEFAULT 0, rows_seen INTEGER NOT NULL DEFAULT 0,
                note TEXT NOT NULL DEFAULT '', completed_at TEXT, digest TEXT,
                UNIQUE(provider, sport, endpoint, params, scope)
            );
            CREATE INDEX IF NOT EXISTS jobs_state ON jobs(provider, sport, state, id);
            CREATE TABLE IF NOT EXISTS usage (
                provider TEXT NOT NULL, sport TEXT NOT NULL, day TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0, remaining INTEGER,
                PRIMARY KEY(provider, sport, day)
            );
            CREATE TABLE IF NOT EXISTS request_pace (
                provider TEXT PRIMARY KEY, sent_at REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS request_cooldown (
                provider TEXT PRIMARY KEY, until_at REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS teams (
                provider TEXT NOT NULL, sport TEXT NOT NULL, team_id TEXT NOT NULL,
                name TEXT NOT NULL, country TEXT NOT NULL DEFAULT '',
                national INTEGER, gender TEXT NOT NULL DEFAULT '',
                founded TEXT NOT NULL DEFAULT '', city TEXT NOT NULL DEFAULT '',
                api_football_id TEXT NOT NULL DEFAULT '', detailed INTEGER NOT NULL DEFAULT 0,
                source TEXT NOT NULL, collected_at TEXT NOT NULL,
                PRIMARY KEY(provider, sport, team_id)
            );
            CREATE TABLE IF NOT EXISTS names (
                provider TEXT NOT NULL, sport TEXT NOT NULL, team_id TEXT NOT NULL,
                name TEXT NOT NULL, normalised TEXT NOT NULL, language TEXT NOT NULL,
                kind TEXT NOT NULL, source TEXT NOT NULL, collected_at TEXT NOT NULL,
                PRIMARY KEY(provider, sport, team_id, name, kind),
                FOREIGN KEY(provider, sport, team_id) REFERENCES teams(provider, sport, team_id)
            );
            CREATE INDEX IF NOT EXISTS names_lookup ON names(sport, normalised, provider);
            CREATE TABLE IF NOT EXISTS country_labels (
                code TEXT NOT NULL, language TEXT NOT NULL, name TEXT NOT NULL,
                normalised TEXT NOT NULL, source TEXT NOT NULL,
                PRIMARY KEY(code, language, name)
            );
            CREATE INDEX IF NOT EXISTS labels_lookup ON country_labels(language, normalised);
            CREATE INDEX IF NOT EXISTS labels_name ON country_labels(normalised);
            CREATE TABLE IF NOT EXISTS links (
                sport TEXT NOT NULL, api_id TEXT NOT NULL, tsdb_id TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'candidate', evidence TEXT NOT NULL,
                PRIMARY KEY(sport, api_id, tsdb_id)
            );
            CREATE INDEX IF NOT EXISTS links_tsdb ON links(sport,tsdb_id,status);
            CREATE TABLE IF NOT EXISTS alias_reviews (
                sport TEXT NOT NULL, api_id TEXT NOT NULL, name TEXT NOT NULL,
                source TEXT NOT NULL, language TEXT NOT NULL DEFAULT 'und',
                status TEXT NOT NULL CHECK(status IN ('accepted', 'rejected')),
                PRIMARY KEY(sport, api_id, name, source)
            );
            CREATE TABLE IF NOT EXISTS entities (
                provider TEXT NOT NULL, sport TEXT NOT NULL, entity_kind TEXT NOT NULL,
                entity_id TEXT NOT NULL, name TEXT NOT NULL, country TEXT NOT NULL DEFAULT '',
                gender TEXT NOT NULL DEFAULT '', category TEXT NOT NULL DEFAULT '',
                detailed INTEGER NOT NULL DEFAULT 0, source TEXT NOT NULL, collected_at TEXT NOT NULL,
                PRIMARY KEY(provider,sport,entity_kind,entity_id)
            );
            CREATE TABLE IF NOT EXISTS entity_names (
                provider TEXT NOT NULL, sport TEXT NOT NULL, entity_kind TEXT NOT NULL,
                entity_id TEXT NOT NULL, name TEXT NOT NULL, normalised TEXT NOT NULL,
                language TEXT NOT NULL, kind TEXT NOT NULL, source TEXT NOT NULL, collected_at TEXT NOT NULL,
                PRIMARY KEY(provider,sport,entity_kind,entity_id,name,kind),
                FOREIGN KEY(provider,sport,entity_kind,entity_id) REFERENCES entities(provider,sport,entity_kind,entity_id)
            );
            CREATE INDEX IF NOT EXISTS entity_names_lookup ON entity_names(sport,entity_kind,normalised,provider);
            CREATE TABLE IF NOT EXISTS entity_alias_reviews (
                sport TEXT NOT NULL, entity_kind TEXT NOT NULL, api_id TEXT NOT NULL,
                name TEXT NOT NULL, source TEXT NOT NULL, language TEXT NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('accepted','rejected')),
                PRIMARY KEY(sport,entity_kind,api_id,name,source)
            );
            CREATE TABLE IF NOT EXISTS record_gaps (
                provider TEXT NOT NULL, sport TEXT NOT NULL, entity_kind TEXT NOT NULL,
                entity_id TEXT NOT NULL, reason TEXT NOT NULL, source TEXT NOT NULL,
                job_id INTEGER NOT NULL, observed_at TEXT NOT NULL, resolved_at TEXT,
                PRIMARY KEY(provider,sport,entity_kind,entity_id,source),
                FOREIGN KEY(job_id) REFERENCES jobs(id)
            );
            CREATE TABLE IF NOT EXISTS country_codes (
                code TEXT PRIMARY KEY, alpha3 TEXT NOT NULL, source TEXT NOT NULL
            );
            PRAGMA user_version = 5;
        """)

    def close(self) -> None:
        self.db.close()

    def last_sent(self, provider: str) -> float:
        row = self.db.execute("SELECT sent_at FROM request_pace WHERE provider=?", (provider,)).fetchone()
        return row[0] if row else 0.0

    def sent(self, provider: str, at: float) -> None:
        with self.db:
            self.db.execute("INSERT OR REPLACE INTO request_pace VALUES (?,?)", (provider, at))

    def cooldown_until(self, provider: str) -> float:
        row = self.db.execute("SELECT until_at FROM request_cooldown WHERE provider=?", (provider,)).fetchone()
        return row[0] if row else 0.0

    def cooldown(self, provider: str, until: float) -> None:
        with self.db:
            self.db.execute("INSERT INTO request_cooldown VALUES (?,?) ON CONFLICT(provider) "
                            "DO UPDATE SET until_at=MAX(until_at,excluded.until_at)", (provider, until))

    def remaining_today(self, provider: str, sport: str, day: str) -> int | None:
        row = self.db.execute("SELECT remaining FROM usage WHERE provider=? AND sport=? AND day=?",
                              (provider, sport, day)).fetchone()
        return row[0] if row else None

    def enqueue(self, provider: str, sport: str, endpoint: str, kind: str,
                params: dict[str, Any] | None = None, context: dict[str, Any] | None = None,
                scope: str = "shared") -> None:
        encoded_params = json.dumps(params or {}, sort_keys=True)
        inserted = self.db.execute(
            "INSERT OR IGNORE INTO jobs(provider,sport,endpoint,params,kind,context,scope) VALUES (?,?,?,?,?,?,?)",
            (provider, sport, endpoint, encoded_params, kind,
             json.dumps(context or {}, sort_keys=True), scope),
        )
        if inserted.rowcount and scope == "full" and kind in ("api-teams", "tsdb-team-list"):
            # Leaf requests have identical results across scopes. Indexes must still expand
            # the full queue; copying a pilot index would omit the non-pilot partitions.
            prior = self.db.execute("SELECT id,scope,rows_seen,note,completed_at,digest FROM jobs "
                                    "WHERE provider=? AND sport=? AND endpoint=? AND params=? AND kind=? "
                                    "AND state='done' AND scope!='full' ORDER BY id DESC LIMIT 1",
                                    (provider, sport, endpoint, encoded_params, kind)).fetchone()
            if prior:
                note = f"Reused completed {prior['scope']} request (job {prior['id']})."
                if prior["note"]:
                    note = prior["note"] + " " + note
                self.db.execute("UPDATE jobs SET state='done',rows_seen=?,note=?,completed_at=?,digest=? WHERE id=?",
                                (prior["rows_seen"], note, prior["completed_at"], prior["digest"], inserted.lastrowid))

    def next_job(self, provider: str, sport: str | None = None, scope: str = "pilot") -> sqlite3.Row | None:
        from .sports import INDEX_KINDS
        kinds = ",".join("?" for _ in INDEX_KINDS)
        return self.db.execute(
            "SELECT * FROM jobs WHERE provider=? AND state='pending' AND (? IS NULL OR sport=?) "
            f"AND scope IN (?, 'shared') ORDER BY CASE WHEN kind IN ({kinds}) THEN 0 "
            "WHEN kind='api-teams' AND params LIKE '%\"country\"%' THEN 1 "
            "WHEN kind='tsdb-team-detail' AND context LIKE '%\"alias_priority\": true%' THEN 1 ELSE 2 END,id LIMIT 1",
            (provider, sport, sport, scope, *INDEX_KINDS),
        ).fetchone()

    def reserve(self, provider: str, sport: str, limit: int, reserve: int = 20,
                day: str | None = None) -> bool:
        # Commit before sending: crashes, failed calls and repeated runs still consume budget.
        day = day or utc_day()
        with self.db:
            self.db.execute("INSERT OR IGNORE INTO usage(provider,sport,day) VALUES (?,?,?)", (provider, sport, day))
            cursor = self.db.execute(
                "UPDATE usage SET attempts=attempts+1, remaining=CASE WHEN remaining IS NULL THEN NULL ELSE remaining-1 END "
                "WHERE provider=? AND sport=? AND day=? AND attempts<? AND (remaining IS NULL OR remaining>?)",
                (provider, sport, day, limit, reserve),
            )
        return cursor.rowcount == 1

    def remaining(self, provider: str, sport: str, value: int, day: str | None = None) -> None:
        self.db.execute(
            "INSERT INTO usage(provider,sport,day,remaining) VALUES (?,?,?,?) "
            "ON CONFLICT(provider,sport,day) DO UPDATE SET remaining=excluded.remaining",
            (provider, sport, day or utc_day(), max(0, value)),
        )
        self.db.commit()

    def team(self, provider: str, sport: str, team_id: str, name: str, source: str,
             country: str = "", national: bool | None = None, gender: str = "",
             founded: str = "", city: str = "", api_football_id: str = "", detailed: bool = False) -> None:
        self.db.execute(
            "INSERT INTO teams VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(provider,sport,team_id) DO UPDATE SET "
            "name=excluded.name, country=CASE WHEN excluded.country='' THEN country ELSE excluded.country END, "
            "national=COALESCE(excluded.national,national), gender=CASE WHEN excluded.gender='' THEN gender ELSE excluded.gender END, "
            "founded=CASE WHEN excluded.founded='' THEN founded ELSE excluded.founded END, "
            "city=CASE WHEN excluded.city='' THEN city ELSE excluded.city END, "
            "api_football_id=CASE WHEN excluded.api_football_id='' THEN api_football_id ELSE excluded.api_football_id END, "
            "detailed=MAX(detailed,excluded.detailed), source=excluded.source, collected_at=excluded.collected_at",
            (provider, sport, team_id, name, country, national, gender, founded, city,
             api_football_id, int(detailed), source, timestamp()),
        )
        self.name(provider, sport, team_id, name, "canonical", source)

    def name(self, provider: str, sport: str, team_id: str, name: str, kind: str,
             source: str, language: str = "und") -> None:
        if not name.strip():
            return
        self.db.execute(
            "INSERT OR IGNORE INTO names VALUES (?,?,?,?,?,?,?,?,?)",
            (provider, sport, team_id, name.strip(), normalise(name), language, kind, source, timestamp()),
        )

    def entity(self, provider: str, sport: str, entity_kind: str, entity_id: str, name: str, source: str,
               country: str = "", gender: str = "", category: str = "", detailed: bool = False) -> None:
        self.db.execute(
            "INSERT INTO entities VALUES (?,?,?,?,?,?,?,?,?,?,?) "
            "ON CONFLICT(provider,sport,entity_kind,entity_id) DO UPDATE SET name=excluded.name, "
            "country=CASE WHEN excluded.country='' THEN country ELSE excluded.country END, "
            "gender=CASE WHEN excluded.gender='' THEN gender ELSE excluded.gender END, "
            "category=CASE WHEN excluded.category='' THEN category ELSE excluded.category END, "
            "detailed=MAX(detailed,excluded.detailed),source=excluded.source,collected_at=excluded.collected_at",
            (provider, sport, entity_kind, entity_id, name, country, gender, category, int(detailed), source, timestamp()),
        )
        self.entity_name(provider, sport, entity_kind, entity_id, name, "canonical", source)

    def entity_name(self, provider: str, sport: str, entity_kind: str, entity_id: str, name: str,
                    kind: str, source: str, language: str = "und") -> None:
        if not name.strip():
            return
        self.db.execute("INSERT OR IGNORE INTO entity_names VALUES (?,?,?,?,?,?,?,?,?,?)",
                        (provider, sport, entity_kind, entity_id, name.strip(), normalise(name),
                         language, kind, source, timestamp()))


@contextmanager
def collection_lock(directory: Path):
    """One collector per dataset; an OS lock releases automatically on crash or window close."""
    directory.mkdir(parents=True, exist_ok=True)
    handle = (directory / "collection.lock").open("a+b")
    try:
        if handle.tell() == 0:
            handle.write(b"0")
            handle.flush()
        handle.seek(0)
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            raise ValueError("Another collector is using this dataset. Close it before starting a second run.") from None
        yield
    finally:
        handle.close()
