"""Streaming, sourced alias proposals and a conservative detail-lookup priority pass."""

from __future__ import annotations

import json
from pathlib import Path

from .national import country_codes, national_identity, split_qualification
from .research import alias_candidates, api_teams, write_csv
from .store import Store, normalise, timestamp


def compatible_countries(store: Store, left: str, right: str) -> bool:
    if not left or not right or normalise(left) == normalise(right):
        return True
    first, second = country_codes(store, left), country_codes(store, right)
    return len(first) == 1 and first == second


def prioritise_details(store: Store, sports: tuple[str, ...]) -> int:
    """Change pending-job order, without creating requests or accepting identity links."""
    count = 0
    with store.db:
        for sport in sports:
            # /lookup/team/ is a fixed 13-character prefix. Joining on its ID suffix
            # permits a primary-key lookup instead of repeatedly scanning team rows.
            jobs = store.db.execute(
                "SELECT j.id,j.context,t.* FROM jobs j JOIN teams t ON t.provider='thesportsdb' "
                "AND t.sport=j.sport AND t.team_id=SUBSTR(j.endpoint,14) "
                "WHERE j.provider='thesportsdb' AND j.sport=? AND j.kind='tsdb-team-detail' "
                "AND j.endpoint LIKE '/lookup/team/%' AND j.state='pending'", (sport,))
            for job in jobs:
                context = json.loads(job["context"])
                for key in ("alias_priority", "alias_api_id", "alias_basis"):
                    context.pop(key, None)
                links = store.db.execute(
                    "SELECT l.*,t.country,t.gender FROM links l JOIN teams t ON t.provider='api-sports' "
                    "AND t.sport=l.sport AND t.team_id=l.api_id "
                    "WHERE l.sport=? AND l.tsdb_id=? AND l.status!='rejected' LIMIT 2",
                    (sport, job["team_id"])).fetchall()
                if len(links) == 1:
                    link = links[0]
                    reciprocal = store.db.execute(
                        "SELECT COUNT(*) FROM links WHERE sport=? AND api_id=? AND status!='rejected'",
                        (sport, link["api_id"])).fetchone()[0]
                    genders_agree = not link["gender"] or not job["gender"] or link["gender"].casefold() == job["gender"].casefold()
                    if reciprocal == 1 and compatible_countries(store, link["country"], job["country"]) and genders_agree and not job["detailed"]:
                        context.update(alias_priority=True, alias_api_id=link["api_id"], alias_basis=link["status"])
                        count += 1
                store.db.execute("UPDATE jobs SET context=? WHERE id=?", (json.dumps(context, sort_keys=True), job["id"]))
    return count


def proposal_risks(team, alias: dict) -> list[str]:
    risks = []
    if alias["identity_status"] == "candidate":
        risks.append("identity-unreviewed")
    if alias["kind"] == "canonical" and alias["identity_status"] == "api-sports" and alias["name"] != team["name"]:
        risks.append("historical-name")
    if len(normalise(alias["name"]).replace(" ", "")) <= 3:
        risks.append("short-name")
    if normalise(alias["name"]) == normalise(team["name"]):
        risks.append("normalisation-equivalent")
    if alias["kind"] == "national-qualified-translation":
        risks.append("constructed-country-translation")
    suffix = split_qualification(team["name"])[1]
    alias_suffix = split_qualification(alias["name"])[1]
    if suffix and suffix.casefold() != alias_suffix.casefold() and alias["kind"] != "federation-nickname":
        risks.append("category-loss")
    return risks


def prepare_proposals(store: Store) -> None:
    # SQLite owns the complete working set; Python holds at most one keyset page and
    # one identity's evidence. Languages and sources stay separate even for equal names.
    store.db.executescript("""
        DROP TABLE IF EXISTS temp.alias_proposals;
        DROP TABLE IF EXISTS temp.alias_identity_collisions;
        CREATE TEMP TABLE alias_proposals (
            sport TEXT, api_id TEXT, canonical TEXT, name TEXT, normalised TEXT,
            language TEXT, kind TEXT, source TEXT, identity_status TEXT,
            review_status TEXT, risks TEXT, is_alias INTEGER,
            PRIMARY KEY(sport,api_id,name,language,kind,source)
        );
        CREATE INDEX temp.proposals_identity ON alias_proposals(sport,api_id);
        CREATE INDEX temp.proposals_name ON alias_proposals(sport,normalised);
    """)
    with store.db:
        for team in api_teams(store):
            for alias in alias_candidates(store, team):
                is_alias = int(alias["name"] != team["name"])
                review = store.db.execute(
                    "SELECT status FROM alias_reviews WHERE sport=? AND api_id=? AND name=? AND source=?",
                    (team["sport"], team["team_id"], alias["name"], alias["source"])).fetchone()
                risks = proposal_risks(team, alias) if is_alias else []
                store.db.execute("INSERT OR IGNORE INTO alias_proposals VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                                 (team["sport"], team["team_id"], team["name"], alias["name"], normalise(alias["name"]),
                                  alias["language"], alias["kind"], alias["source"], alias["identity_status"],
                                  review[0] if review else "pending", ";".join(risks), is_alias))
        store.db.execute("CREATE TEMP TABLE alias_identity_collisions AS SELECT sport,normalised,COUNT(DISTINCT api_id) AS identities "
                         "FROM alias_proposals WHERE normalised!='' AND (is_alias=0 OR review_status!='rejected') "
                         "GROUP BY sport,normalised HAVING COUNT(DISTINCT api_id)>1")
        store.db.execute("CREATE UNIQUE INDEX temp.collision_name ON alias_identity_collisions(sport,normalised)")
        store.db.execute("UPDATE alias_proposals SET risks=CASE WHEN risks='' THEN 'shared-name' ELSE risks||';shared-name' END "
                         "WHERE is_alias=1 AND EXISTS(SELECT 1 FROM alias_identity_collisions c "
                         "WHERE c.sport=alias_proposals.sport AND c.normalised=alias_proposals.normalised)")


def proposed_aliases(store: Store, team) -> list[dict]:
    rows = store.db.execute(
        "SELECT name,language,kind,source,identity_status,review_status,risks FROM alias_proposals "
        "WHERE sport=? AND api_id=? AND is_alias=1 ORDER BY name,language,source,kind",
        (team["sport"], team["team_id"]))
    aliases = []
    for row in rows:
        alias = dict(row)
        alias["risks"] = row["risks"].split(";") if row["risks"] else []
        aliases.append(alias)
    return aliases


def national_gaps(store: Store):
    for team in api_teams(store):
        code, suffix, reason = national_identity(store, team)
        if team["national"] == 1 and code is None:
            yield (team["sport"], team["team_id"], team["name"], team["country"], team["national"], team["gender"], reason, team["source"])
        elif team["national"] != 1 and country_codes(store, split_qualification(team["name"])[0]):
            yield (team["sport"], team["team_id"], team["name"], team["country"], team["national"], team["gender"],
                   "country-shaped name without a verified national flag; no translations proposed", team["source"])


def coverage_rows(store: Store):
    for team in api_teams(store):
        row = store.db.execute(
            "SELECT COUNT(DISTINCT name),COUNT(*),GROUP_CONCAT(DISTINCT CASE WHEN language!='und' THEN language END),"
            "COUNT(DISTINCT CASE WHEN kind LIKE 'national-%' THEN name END) "
            "FROM alias_proposals WHERE sport=? AND api_id=? AND is_alias=1 AND review_status!='rejected'",
            (team["sport"], team["team_id"])).fetchone()
        yield (team["sport"], team["team_id"], team["name"], team["country"], team["national"], row[0], row[1], row[2] or "", row[3])


def atomic_text(path: Path, value: str) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(value, encoding="utf-8")
    temporary.replace(path)


def alias_reports(store: Store, directory: Path) -> dict:
    directory.mkdir(parents=True, exist_ok=True)
    prepare_proposals(store)

    def scalar(query: str):
        return store.db.execute(query).fetchone()[0]

    summary = {
        "generated_at": timestamp(), "scope": "sourced proposals; identity and alias review still required",
        "exhaustive_alias_claim": False,
        "api_teams": scalar("SELECT COUNT(*) FROM teams WHERE provider='api-sports'"),
        "teams_with_aliases": scalar("SELECT COUNT(*) FROM (SELECT DISTINCT sport,api_id FROM alias_proposals WHERE is_alias=1 AND review_status!='rejected')"),
        "additional_spellings": scalar("SELECT COUNT(*) FROM (SELECT DISTINCT sport,api_id,name FROM alias_proposals WHERE is_alias=1 AND review_status!='rejected')"),
        "alias_evidence_rows": scalar("SELECT COUNT(*) FROM alias_proposals WHERE is_alias=1"),
        "approved_aliases": scalar("SELECT COUNT(*) FROM (SELECT DISTINCT sport,api_id,name FROM alias_proposals WHERE is_alias=1 AND review_status='accepted' AND identity_status!='candidate')"),
        "collision_groups": scalar("SELECT COUNT(*) FROM alias_identity_collisions"),
        "national_teams_with_translations": scalar("SELECT COUNT(*) FROM (SELECT DISTINCT sport,api_id FROM alias_proposals WHERE kind LIKE 'national-%')"),
        "qualified_national_teams": scalar("SELECT COUNT(*) FROM (SELECT DISTINCT sport,api_id FROM alias_proposals WHERE kind='national-qualified-translation')"),
        "federation_nicknames": scalar("SELECT COUNT(*) FROM alias_proposals WHERE kind='federation-nickname'"),
        "prioritised_detail_jobs": scalar("SELECT COUNT(*) FROM jobs WHERE state='pending' AND kind='tsdb-team-detail' AND context LIKE '%\"alias_priority\": true%'"),
        "languages": {row[0]: row[1] for row in store.db.execute(
            "SELECT language,COUNT(*) FROM alias_proposals WHERE is_alias=1 AND language!='und' GROUP BY language")},
        "entity_aliases": {row[0] + ":" + row[1]: row[2] for row in store.db.execute(
            "SELECT n.sport,n.entity_kind,COUNT(*) FROM entity_names n JOIN entities e USING(provider,sport,entity_kind,entity_id) "
            "WHERE n.provider='api-sports' AND n.name!=e.name GROUP BY n.sport,n.entity_kind")},
    }
    path = directory / "aliases.proposed.jsonl"
    with path.with_suffix(".jsonl.tmp").open("w", encoding="utf-8") as output:
        for team in api_teams(store):
            output.write(json.dumps({"format_version": 1, "status": "proposed", "provider": "api-sports",
                                     "sport": team["sport"], "team_id": team["team_id"], "canonical": team["name"],
                                     "country": team["country"], "national": team["national"],
                                     "aliases": proposed_aliases(store, team)}, ensure_ascii=False) + "\n")
    path.with_suffix(".jsonl.tmp").replace(path)
    write_csv(directory / "alias_review.csv",
              ["sport", "api_id", "canonical", "alias", "normalised", "language", "kind", "identity_status", "review_status", "risks", "source"],
              (tuple(row) for row in store.db.execute(
                  "SELECT sport,api_id,canonical,name,normalised,language,kind,identity_status,review_status,risks,source "
                  "FROM alias_proposals WHERE is_alias=1 ORDER BY CASE WHEN risks LIKE '%shared-name%' OR risks LIKE '%category-loss%' THEN 0 "
                  "WHEN identity_status='candidate' THEN 1 ELSE 2 END,sport,api_id,name,language,source")))
    write_csv(directory / "alias_identity_collisions.csv", ["sport", "normalised", "api_identities"],
              (tuple(row) for row in store.db.execute("SELECT * FROM alias_identity_collisions ORDER BY sport,normalised")))
    write_csv(directory / "alias_coverage.csv",
              ["sport", "api_id", "canonical", "country", "national", "additional_spellings", "evidence_rows", "languages", "national_spellings"],
              coverage_rows(store))
    write_csv(directory / "national_alias_gaps.csv",
              ["sport", "api_id", "canonical", "country", "national", "gender", "reason", "source"], national_gaps(store))
    summary["national_identity_gaps"] = sum(1 for _ in national_gaps(store))
    atomic_text(directory / "alias_summary.json", json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
    lines = ["# Alias enrichment snapshot", "", f"Generated: {summary['generated_at']}", "",
             f"{summary['api_teams']:,} API-Sports team identities; {summary['teams_with_aliases']:,} have additional spellings.",
             f"{summary['additional_spellings']:,} distinct team/name proposals, with {summary['alias_evidence_rows']:,} language/source evidence rows.",
             f"{summary['approved_aliases']:,} approved additional spellings; {summary['collision_groups']:,} shared normalised names need identity review.", "",
             f"{summary['national_teams_with_translations']:,} national identities have country-label translations, including {summary['qualified_national_teams']:,} qualified identities.",
             f"{summary['national_identity_gaps']:,} national flag/name/country gaps remain; see national_alias_gaps.csv.",
             f"{summary['federation_nicknames']:,} Finnish federation nicknames are sourced explicitly.", "",
             "Languages: English, Finnish, Swedish, German, Spanish, Portuguese, Italian and French.",
             "Examples: Finland → Suomi; Germany → Saksa / Deutschland; England → Englanti.",
             "Qualified translations retain the original suffix: Finland U21 → Suomi U21; Finland W → Suomi W.",
             "Suffix words are preserved, not claimed as fully localised category labels. Qualified teams never receive bare country aliases.", "",
             f"{summary['prioritised_detail_jobs']:,} pending TheSportsDB profiles are prioritised for unique, compatible identity candidates.",
             "Prioritisation fetches evidence; it does not approve an identity or an alias.", "",
             "Review alias_review.csv, including shared-name, category-loss, short-name and unreviewed-identity flags.",
             "aliases.proposed.jsonl groups original spellings, languages, sources and review status by API-Sports ID.",
             "entity_alias_candidates.csv keeps fighter nicknames and driver codes separate from team aliases.", "",
             "Coverage gaps remain for provider profiles, historical names, unverified national flags and additional languages/nicknames.",
             "This is a first sourced pass, not an exhaustive alias claim or a production matching dictionary.",
             "Sources: API-Sports, TheSportsDB, Unicode CLDR 48.2.0 / release-48 (Unicode-3.0), and linked Finnish federations.", ""]
    atomic_text(directory / "alias_summary.md", "\n".join(lines))
    return summary
