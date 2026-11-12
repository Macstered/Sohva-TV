"""Reviewed, persistent Sohva identities beside (never in place of) collection ledgers.

The registry has its own schema version. The collector's PRAGMA user_version and all jobs,
request pacing and quota tables remain untouched. Seeding is local and never approves data.
"""

from __future__ import annotations

import json
import sqlite3
import uuid
from contextlib import contextmanager
from datetime import date
from pathlib import Path

from .store import normalise

PAGE = 256
KINDS = {"team", "fighter", "gym", "driver", "competition", "circuit"}
STATES = {"pending", "accepted", "rejected"}


def canonical_sport(product: str) -> str:
    return "basketball" if product == "nba" else product


def interval(start: str, end: str) -> None:
    if start:
        date.fromisoformat(start)
    if end:
        date.fromisoformat(end)
    if start and end and start > end:
        raise ValueError("Validity ends before it starts.")


class Registry:
    def __init__(self, db: sqlite3.Connection):
        self.db = db
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.executescript("""
            CREATE TABLE IF NOT EXISTS dictionary_meta (
                key TEXT PRIMARY KEY, value TEXT NOT NULL
            );
            INSERT OR IGNORE INTO dictionary_meta VALUES ('schema','1');
            CREATE TABLE IF NOT EXISTS dictionary_identity (
                uuid TEXT PRIMARY KEY, sport TEXT NOT NULL, kind TEXT NOT NULL,
                name TEXT NOT NULL, country TEXT NOT NULL, category TEXT NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('pending','accepted','rejected')),
                evidence TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS dictionary_binding (
                provider TEXT NOT NULL, product TEXT NOT NULL, kind TEXT NOT NULL,
                external_id TEXT NOT NULL, uuid TEXT NOT NULL REFERENCES dictionary_identity(uuid),
                status TEXT NOT NULL CHECK(status IN ('pending','accepted','rejected')),
                evidence TEXT NOT NULL, PRIMARY KEY(provider,product,kind,external_id)
            );
            CREATE INDEX IF NOT EXISTS dictionary_binding_uuid ON dictionary_binding(uuid);
            CREATE TABLE IF NOT EXISTS dictionary_alias (
                uuid TEXT NOT NULL REFERENCES dictionary_identity(uuid), spelling TEXT NOT NULL,
                normalised TEXT NOT NULL, language TEXT NOT NULL, source TEXT NOT NULL,
                valid_from TEXT NOT NULL DEFAULT '', valid_to TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL CHECK(status IN ('pending','accepted','rejected')),
                evidence TEXT NOT NULL,
                PRIMARY KEY(uuid,spelling,source,valid_from,valid_to)
            );
            CREATE INDEX IF NOT EXISTS dictionary_alias_name ON dictionary_alias(normalised,uuid);
            CREATE TABLE IF NOT EXISTS dictionary_membership (
                participant TEXT NOT NULL REFERENCES dictionary_identity(uuid),
                competition TEXT NOT NULL REFERENCES dictionary_identity(uuid), season TEXT NOT NULL,
                valid_from TEXT NOT NULL, valid_to TEXT NOT NULL, source TEXT NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('pending','accepted','rejected')),
                evidence TEXT NOT NULL,
                PRIMARY KEY(participant,competition,season,valid_from,valid_to,source)
            );
            CREATE TABLE IF NOT EXISTS dictionary_credit (
                source TEXT PRIMARY KEY, attribution TEXT NOT NULL, licence TEXT NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('pending','accepted','rejected'))
            );
            CREATE TABLE IF NOT EXISTS dictionary_review (
                id INTEGER PRIMARY KEY, record TEXT NOT NULL, decision TEXT NOT NULL,
                evidence TEXT NOT NULL
            );
        """)
        if self.db.execute("SELECT value FROM dictionary_meta WHERE key='schema'").fetchone()[0] != "1":
            raise ValueError("The dictionary registry needs a newer tool.")

    def identity(self, provider: str, product: str, kind: str, external_id: str,
                 name: str, country: str = "", category: str = "unknown", evidence: str = "") -> str:
        if kind not in KINDS or not external_id or not name.strip():
            raise ValueError("A typed source identity needs an ID and name.")
        with self._write():
            prior = self.db.execute(
                "SELECT uuid FROM dictionary_binding WHERE provider=? AND product=? AND kind=? AND external_id=?",
                (provider, product, kind, external_id),
            ).fetchone()
            if prior:
                # A provider rename does not rewrite reviewed metadata or allocate another UUID.
                return prior[0]
            identity = str(uuid.uuid4())
            self.db.execute("INSERT INTO dictionary_identity VALUES (?,?,?,?,?,?,?,?)",
                            (identity, canonical_sport(product), kind, name.strip(), country, category, "pending", evidence))
            self.db.execute("INSERT INTO dictionary_binding VALUES (?,?,?,?,?,?,?)",
                            (provider, product, kind, external_id, identity, "pending", evidence))
            return identity

    def propose_alias(self, identity: str, spelling: str, language: str, source: str,
                      evidence: str, valid_from: str = "", valid_to: str = "") -> None:
        interval(valid_from, valid_to)
        norm = normalise(spelling)
        if len(norm.replace(" ", "")) < 3 or not source or not evidence:
            raise ValueError("An alias needs a complete spelling, source and evidence.")
        with self._write():
            self.db.execute("INSERT OR IGNORE INTO dictionary_alias VALUES (?,?,?,?,?,?,?,?,?)",
                            (identity, spelling.strip(), norm, language, source, valid_from, valid_to, "pending", evidence))

    def review_identity(self, identity: str, category: str, decision: str, evidence: str) -> None:
        self._decision(decision, evidence)
        if not category:
            raise ValueError("Review must retain an explicit category, including unknown coverage.")
        with self._write():
            changed = self.db.execute("UPDATE dictionary_identity SET category=?,status=?,evidence=? WHERE uuid=?",
                                      (category, decision, evidence, identity))
            if changed.rowcount != 1:
                raise ValueError("Unknown Sohva identity.")
            self._record("identity:" + identity, decision, evidence)

    def review_binding(self, provider: str, product: str, kind: str, external_id: str,
                       identity: str, decision: str, evidence: str) -> None:
        self._decision(decision, evidence)
        row = self.db.execute("SELECT sport,kind FROM dictionary_identity WHERE uuid=?", (identity,)).fetchone()
        if not row or row["sport"] != canonical_sport(product) or row["kind"] != kind:
            raise ValueError("Binding has a different sport or participant kind.")
        with self._write():
            self.db.execute("INSERT INTO dictionary_binding VALUES (?,?,?,?,?,?,?) "
                            "ON CONFLICT(provider,product,kind,external_id) DO UPDATE SET "
                            "uuid=excluded.uuid,status=excluded.status,evidence=excluded.evidence",
                            (provider, product, kind, external_id, identity, decision, evidence))
            self._record("binding:" + json.dumps([provider, product, kind, external_id]), decision, evidence)

    def review_alias(self, identity: str, spelling: str, source: str, decision: str,
                     evidence: str, valid_from: str = "", valid_to: str = "") -> None:
        self._decision(decision, evidence)
        interval(valid_from, valid_to)
        with self._write():
            changed = self.db.execute("UPDATE dictionary_alias SET status=?,evidence=? WHERE uuid=? AND spelling=? "
                                      "AND source=? AND valid_from=? AND valid_to=?",
                                      (decision, evidence, identity, spelling, source, valid_from, valid_to))
            if changed.rowcount != 1:
                raise ValueError("Review only an existing sourced alias proposal.")
            self._record("alias:" + json.dumps([identity, spelling, source, valid_from, valid_to]), decision, evidence)

    def credit(self, source: str, attribution: str, licence: str, decision: str) -> None:
        self._decision(decision, attribution)
        if not licence or not source:
            raise ValueError("Source attribution needs a licence and source.")
        with self._write():
            self.db.execute("INSERT INTO dictionary_credit VALUES (?,?,?,?) ON CONFLICT(source) "
                            "DO UPDATE SET attribution=excluded.attribution,licence=excluded.licence,status=excluded.status",
                            (source, attribution, licence, decision))

    def membership(self, participant: str, competition: str, season: str, source: str,
                   evidence: str, decision: str = "pending", valid_from: str = "", valid_to: str = "") -> None:
        self._decision(decision, evidence)
        interval(valid_from, valid_to)
        first = self.db.execute("SELECT sport,kind FROM dictionary_identity WHERE uuid=?", (participant,)).fetchone()
        second = self.db.execute("SELECT sport,kind FROM dictionary_identity WHERE uuid=?", (competition,)).fetchone()
        if not first or not second or first["sport"] != second["sport"] or second["kind"] != "competition" or first["kind"] == "competition":
            raise ValueError("Membership must connect a participant to a competition in its sport.")
        with self._write():
            self.db.execute("INSERT INTO dictionary_membership VALUES (?,?,?,?,?,?,?,?) "
                            "ON CONFLICT(participant,competition,season,valid_from,valid_to,source) "
                            "DO UPDATE SET status=excluded.status,evidence=excluded.evidence",
                            (participant, competition, season, valid_from, valid_to, source, decision, evidence))
            self._record("membership:" + json.dumps([participant, competition, season, valid_from, valid_to, source]), decision, evidence)

    def seed(self) -> dict[str, int]:
        """Keyset pages avoid retaining the collected catalogue; no name-based identity merges."""
        with self._write():
            return self._seed()

    def _seed(self) -> dict[str, int]:
        counts = {"records": 0, "aliases": 0, "short_proposals": 0}
        for table, kind_column, id_column in (("teams", "'team'", "team_id"), ("entities", "entity_kind", "entity_id")):
            after = 0
            while True:
                page = self.db.execute(f"SELECT rowid AS cursor,*,{kind_column} AS kind,{id_column} AS external_id "
                                       f"FROM {table} WHERE rowid>? ORDER BY rowid LIMIT ?", (after, PAGE)).fetchall()
                for row in page:
                    kind = row["kind"]
                    if kind not in KINDS:
                        continue
                    identity = self.identity(row["provider"], row["sport"], kind, row["external_id"],
                                             row["name"], row["country"], evidence=row["source"])
                    if len(normalise(row["name"]).replace(" ", "")) >= 3:
                        self.propose_alias(identity, row["name"], "und", row["source"], "Collected canonical name")
                        counts["aliases"] += 1
                    names_table = "names" if table == "teams" else "entity_names"
                    condition = "" if table == "teams" else " AND entity_kind=?"
                    parameters = (row["provider"], row["sport"], row["external_id"])
                    if table != "teams":
                        parameters += (kind,)
                    names = self.db.execute(f"SELECT name,language,source,kind FROM {names_table} "
                                            f"WHERE provider=? AND sport=? AND {id_column}=?{condition}", parameters)
                    for proposal in names:
                        if len(normalise(proposal["name"]).replace(" ", "")) >= 3:
                            self.propose_alias(identity, proposal["name"], proposal["language"], proposal["source"], proposal["kind"])
                            counts["aliases"] += 1
                    if table == "teams" and row["provider"] == "api-sports" and row["national"] == 1:
                        from types import SimpleNamespace
                        from .national import national_aliases
                        for proposal in national_aliases(SimpleNamespace(db=self.db), row):
                            if len(normalise(proposal["name"]).replace(" ", "")) < 3:
                                counts["short_proposals"] += 1
                                continue
                            self.propose_alias(identity, proposal["name"], proposal["language"], proposal["source"], proposal["identity_status"])
                            counts["aliases"] += 1
                    counts["records"] += 1
                if len(page) < PAGE:
                    break
                after = page[-1]["cursor"]
        return counts

    def export(self, path: Path) -> dict[str, int]:
        """A review snapshot; runtime compilation is separate and validates this again."""
        identities = [dict(row) for row in self.db.execute("SELECT * FROM dictionary_identity WHERE status='accepted' ORDER BY uuid")]
        bindings = [dict(row) for row in self.db.execute("SELECT b.* FROM dictionary_binding b CROSS JOIN dictionary_identity i "
                    "ON i.uuid=b.uuid WHERE b.status='accepted' AND i.status='accepted' ORDER BY provider,product,kind,external_id")]
        aliases = [dict(row) for row in self.db.execute("SELECT a.* FROM dictionary_alias a CROSS JOIN dictionary_identity i "
                    "ON i.uuid=a.uuid CROSS JOIN dictionary_credit c ON c.source=a.source "
                    "WHERE a.status='accepted' AND i.status='accepted' AND c.status='accepted' ORDER BY a.uuid,normalised,valid_from,valid_to,spelling,a.source")]
        memberships = [dict(row) for row in self.db.execute("SELECT m.* FROM dictionary_membership m "
                    "CROSS JOIN dictionary_identity p ON p.uuid=m.participant CROSS JOIN dictionary_identity c ON c.uuid=m.competition "
                    "CROSS JOIN dictionary_credit s ON s.source=m.source "
                    "WHERE m.status='accepted' AND p.status='accepted' AND c.status='accepted' AND s.status='accepted' "
                    "ORDER BY participant,competition,season,valid_from,valid_to,source")]
        # Pending identities still contribute ambiguity evidence. A small release slice cannot
        # turn a known collision into an unambiguous exact-name fallback.
        collisions = [dict(row) for row in self.db.execute("SELECT i.sport,i.kind,'*' AS category,a.normalised "
                    "FROM dictionary_alias a CROSS JOIN dictionary_identity i ON i.uuid=a.uuid "
                    "WHERE a.status!='rejected' AND i.status!='rejected' GROUP BY i.sport,i.kind,a.normalised "
                    "HAVING COUNT(DISTINCT i.uuid)>1 ORDER BY i.sport,i.kind,a.normalised")]
        by_identity = {row["uuid"]: row for row in identities}
        shipped_names = {(by_identity[row["uuid"]]["sport"], by_identity[row["uuid"]]["kind"], row["normalised"]) for row in aliases}
        collisions = [row for row in collisions if (row["sport"], row["kind"], row["normalised"]) in shipped_names]
        credits = [dict(row) for row in self.db.execute("SELECT * FROM dictionary_credit WHERE status='accepted' ORDER BY source")]
        data = dict(format=1, normalisation=1, identities=identities, bindings=bindings,
                    aliases=aliases, memberships=memberships, collisions=collisions, credits=credits)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        return {key: len(value) for key, value in data.items() if isinstance(value, list)}

    @staticmethod
    def _decision(decision: str, evidence: str) -> None:
        if decision not in STATES or not evidence.strip():
            raise ValueError("A review needs a decision and evidence.")

    def _record(self, record: str, decision: str, evidence: str) -> None:
        self.db.execute("INSERT INTO dictionary_review(record,decision,evidence) VALUES (?,?,?)", (record, decision, evidence))

    @contextmanager
    def _write(self):
        # SAVEPOINT composes with a page transaction; nested sqlite connection contexts do not.
        self.db.execute("SAVEPOINT dictionary_edit")
        try:
            yield
            self.db.execute("RELEASE dictionary_edit")
        except BaseException:
            self.db.execute("ROLLBACK TO dictionary_edit")
            self.db.execute("RELEASE dictionary_edit")
            raise
