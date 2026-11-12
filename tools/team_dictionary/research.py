"""Exact identity candidates and streaming review reports; no fuzzy identity merges."""

from __future__ import annotations

import csv
import json
from pathlib import Path
from typing import Iterator

from .providers import ALL_SPORTS, LANGUAGES
from .store import Store, normalise, timestamp


def api_teams(store: Store) -> Iterator:
    last_sport, last_id = "", ""
    while True:
        rows = store.db.execute(
            "SELECT * FROM teams WHERE provider='api-sports' AND (sport,team_id)>(?,?) ORDER BY sport,team_id LIMIT 1000",
            (last_sport, last_id),
        ).fetchall()
        if not rows:
            return
        yield from rows
        last_sport, last_id = rows[-1]["sport"], rows[-1]["team_id"]


def build_links(store: Store) -> None:
    # An exact name is still only a candidate: cities, youth/gender and nicknames need review.
    store.db.execute("DELETE FROM links WHERE status IN ('candidate','provider-id')")
    store.db.execute("CREATE INDEX IF NOT EXISTS team_external ON teams(provider,sport,api_football_id)")
    for team in api_teams(store):
        direct = store.db.execute(
            "SELECT team_id,country FROM teams WHERE provider='thesportsdb' AND sport=? AND api_football_id=?",
            (team["sport"], team["team_id"] if team["sport"] == "football" else "never"),
        ).fetchall()
        for candidate in direct:
            consistent = not team["country"] or not candidate["country"] or normalise(team["country"]) == normalise(candidate["country"])
            status = "provider-id" if len(direct) == 1 and consistent else "candidate"
            evidence = "TheSportsDB idAPIfootball" + ("; conflict needs review" if status == "candidate" else "")
            store.db.execute("INSERT OR IGNORE INTO links VALUES (?,?,?,?,?)",
                             (team["sport"], team["team_id"], candidate["team_id"], status, evidence))
        candidates = store.db.execute(
            "SELECT DISTINCT t.team_id,t.country FROM names n JOIN teams t "
            "ON t.provider=n.provider AND t.sport=n.sport AND t.team_id=n.team_id "
            "WHERE n.provider='thesportsdb' AND n.sport=? AND n.normalised=?",
            (team["sport"], normalise(team["name"])),
        )
        for candidate in candidates:
            country_match = team["country"] and candidate["country"] and normalise(team["country"]) == normalise(candidate["country"])
            evidence = "exact normalised name; " + ("same country" if country_match else "country unknown or different")
            store.db.execute("INSERT OR IGNORE INTO links VALUES (?,?,?,?,?)",
                             (team["sport"], team["team_id"], candidate["team_id"], "candidate", evidence))
    store.db.commit()


def alias_candidates(store: Store, team) -> Iterator[dict]:
    rows = store.db.execute("SELECT * FROM names WHERE provider='api-sports' AND sport=? AND team_id=?",
                            (team["sport"], team["team_id"]))
    for row in rows:
        yield {"name": row["name"], "language": row["language"], "kind": row["kind"],
               "source": row["source"], "identity_status": "api-sports"}
    # Pin the link first. SQLite otherwise scans a sport's complete provider-name
    # roster for every API identity, including the many teams with no linked profile.
    rows = store.db.execute(
        "SELECT n.*,l.status FROM links l CROSS JOIN names n ON n.provider='thesportsdb' AND n.sport=l.sport AND n.team_id=l.tsdb_id "
        "WHERE l.sport=? AND l.api_id=? AND l.status!='rejected'", (team["sport"], team["team_id"]),
    )
    for row in rows:
        yield {"name": row["name"], "language": row["language"], "kind": row["kind"],
               "source": row["source"], "identity_status": row["status"]}
    if team["national"] == 1:
        from .national import national_aliases
        yield from national_aliases(store, team)


def write_csv(path: Path, headers: list[str], rows: Iterator) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8-sig", newline="") as output:
        writer = csv.writer(output)
        writer.writerow(headers)
        writer.writerows(rows)
    temporary.replace(path)


def reports(store: Store, directory: Path, alias_sports: tuple[str, ...] = ()) -> dict:
    directory.mkdir(parents=True, exist_ok=True)
    from .enrichment import alias_reports, atomic_text, prioritise_details
    from .national import seed_nicknames
    seed_nicknames(store, ALL_SPORTS)
    build_links(store)
    if alias_sports:
        prioritise_details(store, alias_sports)
    counts = {"generated_at": timestamp(), "scope": "research; not integrated into the app", "sports": {},
              "languages": {}, "jobs": {}, "requests": [], "warnings": []}
    for sport in ALL_SPORTS:
        counts["sports"][sport] = {
            "adapter": "enabled",
            "unresolved_records": store.db.execute(
                "SELECT COUNT(DISTINCT entity_kind||':'||entity_id) FROM record_gaps "
                "WHERE provider='api-sports' AND sport=? AND resolved_at IS NULL", (sport,)).fetchone()[0],
            "api_entities": {row[0]: row[1] for row in store.db.execute(
                "SELECT entity_kind,COUNT(*) FROM entities WHERE provider='api-sports' AND sport=? GROUP BY entity_kind", (sport,))},
            "verified_requests": store.db.execute(
                "SELECT COUNT(*) FROM jobs WHERE provider='api-sports' AND sport=? AND state='done'", (sport,)).fetchone()[0],
            "api_teams": store.db.execute("SELECT COUNT(*) FROM teams WHERE provider='api-sports' AND sport=?", (sport,)).fetchone()[0],
            "tsdb_teams": store.db.execute("SELECT COUNT(*) FROM teams WHERE provider='thesportsdb' AND sport=?", (sport,)).fetchone()[0],
            "tsdb_details": store.db.execute("SELECT COUNT(*) FROM teams WHERE provider='thesportsdb' AND sport=? AND detailed=1", (sport,)).fetchone()[0],
            "linked_teams": store.db.execute("SELECT COUNT(DISTINCT api_id) FROM links WHERE sport=? AND status IN ('provider-id','accepted')", (sport,)).fetchone()[0],
        }
    for language in LANGUAGES:
        counts["languages"][language] = store.db.execute("SELECT COUNT(*) FROM country_labels WHERE language=?", (language,)).fetchone()[0]
    for row in store.db.execute("SELECT provider,sport,scope,state,COUNT(*) AS count FROM jobs GROUP BY provider,sport,scope,state"):
        counts["jobs"][":".join(row[:4])] = row["count"]
    counts["requests"] = [dict(row) for row in store.db.execute("SELECT * FROM usage ORDER BY day,provider,sport")]
    counts["warnings"] = [dict(row) for row in store.db.execute("SELECT provider,sport,endpoint,params,note FROM jobs WHERE note!=''")]
    counts["complete_roster_claim"] = False
    write_csv(directory / "record_gaps.csv",
              ["provider", "sport", "entity_kind", "entity_id", "reason", "source", "job_id", "observed_at", "resolved_at"],
              (tuple(row) for row in store.db.execute("SELECT * FROM record_gaps ORDER BY provider,sport,entity_kind,entity_id,source")))
    write_csv(directory / "teams.csv", ["provider", "sport", "team_id", "name", "country", "national", "gender", "source"],
              (tuple(row) for row in store.db.execute("SELECT provider,sport,team_id,name,country,national,gender,source FROM teams ORDER BY provider,sport,team_id")))
    write_csv(directory / "link_candidates.csv", ["sport", "api_id", "tsdb_id", "status", "evidence"],
              (tuple(row) for row in store.db.execute("SELECT * FROM links ORDER BY sport,api_id,tsdb_id")))
    write_csv(directory / "country_labels.csv", ["code", "language", "name", "normalised", "source"],
              (tuple(row) for row in store.db.execute("SELECT * FROM country_labels ORDER BY code,language,name")))
    write_csv(directory / "entities.csv", ["provider", "sport", "entity_kind", "entity_id", "name", "country", "gender", "category", "detailed", "source"],
              (tuple(row) for row in store.db.execute(
                  "SELECT provider,sport,entity_kind,entity_id,name,country,gender,category,detailed,source "
                  "FROM entities ORDER BY provider,sport,entity_kind,entity_id")))
    write_csv(directory / "entity_alias_candidates.csv",
              ["sport", "entity_kind", "api_id", "canonical", "alias", "normalised", "language", "kind", "alias_status", "source"],
              (tuple(row) for row in store.db.execute(
                  "SELECT n.sport,n.entity_kind,n.entity_id,e.name,n.name,n.normalised,n.language,n.kind,"
                  "COALESCE(r.status,'pending'),n.source FROM entity_names n JOIN entities e "
                  "USING(provider,sport,entity_kind,entity_id) LEFT JOIN entity_alias_reviews r "
                  "ON r.sport=n.sport AND r.entity_kind=n.entity_kind AND r.api_id=n.entity_id AND r.name=n.name AND r.source=n.source "
                  "WHERE n.provider='api-sports' ORDER BY n.sport,n.entity_kind,n.entity_id,n.name")))
    candidate_path = directory / "alias_candidates.csv"
    with candidate_path.with_suffix(".csv.tmp").open("w", encoding="utf-8-sig", newline="") as output:
        writer = csv.writer(output)
        writer.writerow(["sport", "api_id", "canonical", "alias", "normalised", "language", "kind", "identity_status", "alias_status", "source"])
        for team in api_teams(store):
            for alias in alias_candidates(store, team):
                review = store.db.execute("SELECT status FROM alias_reviews WHERE sport=? AND api_id=? AND name=? AND source=?",
                                          (team["sport"], team["team_id"], alias["name"], alias["source"])).fetchone()
                writer.writerow([team["sport"], team["team_id"], team["name"], alias["name"], normalise(alias["name"]),
                                 alias["language"], alias["kind"], alias["identity_status"], review[0] if review else "pending", alias["source"]])
    candidate_path.with_suffix(".csv.tmp").replace(candidate_path)
    write_csv(directory / "alias_collisions.csv", ["sport", "normalised", "teams", "providers"],
              (tuple(row) for row in store.db.execute(
                  "SELECT sport,normalised,COUNT(DISTINCT provider||':'||team_id),GROUP_CONCAT(DISTINCT provider) "
                  "FROM names WHERE normalised!='' GROUP BY sport,normalised HAVING COUNT(DISTINCT provider||':'||team_id)>1")))
    write_csv(directory / "entity_alias_collisions.csv", ["sport", "entity_kind", "normalised", "identities"],
              (tuple(row) for row in store.db.execute(
                  "SELECT sport,entity_kind,normalised,COUNT(DISTINCT provider||':'||entity_id) FROM entity_names "
                  "WHERE normalised!='' GROUP BY sport,entity_kind,normalised "
                  "HAVING COUNT(DISTINCT provider||':'||entity_id)>1")))
    counts["aliases"] = alias_reports(store, directory)
    temporary = directory / "coverage.json.tmp"
    temporary.write_text(json.dumps(counts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(directory / "coverage.json")
    lines = ["# Team dictionary coverage", "", f"Generated: {counts['generated_at']}", "",
             "This is a research snapshot. Complete API coverage and reviewed aliases are still pending.", "",
             "| Sport | Adapter | API teams | TheSportsDB teams | Details | Strong links |",
             "|---|---|---:|---:|---:|---:|"]
    for sport, row in counts["sports"].items():
        lines.append(f"| {sport} | {row['adapter']} | {row['api_teams']} | {row['tsdb_teams']} | {row['tsdb_details']} | {row['linked_teams']} |")
    lines += ["", "Other API identities (kept separately from teams):", ""]
    for sport, row in counts["sports"].items():
        if row["api_entities"]:
            lines.append(f"- {sport}: " + ", ".join(f"{kind}: {count}" for kind, count in row["api_entities"].items()))
    lines += ["", "Successful API requests by sport: " + ", ".join(
        f"{sport}: {row['verified_requests']}" for sport, row in counts["sports"].items()), ""]
    unresolved = sum(row["unresolved_records"] for row in counts["sports"].values())
    if unresolved:
        lines += [f"{unresolved} unnamed provider identities remain quarantined. See record_gaps.csv; no placeholder names are imported or exported.", ""]
    if counts["warnings"]:
        lines += ["", "Collection warnings:", ""]
        for warning in counts["warnings"]:
            lines.append(f"- {warning['provider']} {warning['sport']} {warning['endpoint']} "
                         f"{warning['params']}: {warning['note']}")
    lines += ["", "Country-label languages: " + ", ".join(f"{language}: {count}" for language, count in counts["languages"].items()), "",
              "CSV files retain original spellings, source URLs and pending review status. Strong provider-ID links do not approve aliases.", "",
              "Potential collisions include the same team represented by both providers; review identities before judging a collision.", "",
              "Alias proposals and API-ID collision checks are in alias_summary.md and alias_review.csv. Country qualifiers are preserved.", "",
              "Sporting-country translations use separate CLDR subdivision labels; GB never implies England/Scotland/Wales/Northern Ireland.", "",
              "MMA gyms are teams; fighters are separate identities. Category and gym filters may miss unaffiliated, uncategorised fighters.", "",
              "Formula 1 drivers are discovered through season rankings. Unranked drivers remain a coverage gap. Competitions and circuits are separate identities.", "",
              "TheSportsDB enriches the ten team products; its fighter/player enrichment remains pending. NBA uses only its NBA league, Formula 1 only its Formula 1 league.", "",
              "An enabled adapter is not verified account access or complete coverage. No app data or runtime rules changed.", "",
              "Sources: API-Sports; TheSportsDB (https://www.thesportsdb.com); Unicode CLDR 48.2.0 (Unicode-3.0 licence).", ""]
    atomic_text(directory / "coverage.md", "\n".join(lines))
    return counts


def export_reviewed(store: Store, destination: Path) -> int:
    # The export is a proposed ID-based interchange format, not the app's name-based registry.
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    count = 0
    with temporary.open("w", encoding="utf-8") as output:
        for team in api_teams(store):
            valid = {(alias["name"], alias["source"]) for alias in alias_candidates(store, team)
                     if alias["identity_status"] != "candidate"}
            aliases = [dict(row) for row in store.db.execute(
                "SELECT name,language,source FROM alias_reviews WHERE sport=? AND api_id=? AND status='accepted' ORDER BY name,source",
                (team["sport"], team["team_id"]),
            ) if (row["name"], row["source"]) in valid and len(normalise(row["name"])) >= 3]
            output.write(json.dumps({"format_version": 1, "provider": "api-sports", "sport": team["sport"],
                                     "team_id": team["team_id"], "canonical": team["name"], "aliases": aliases}, ensure_ascii=False) + "\n")
            count += 1
    temporary.replace(destination)
    export_entities(store, destination.with_name("entities.reviewed.jsonl"))
    return count


def export_entities(store: Store, destination: Path) -> int:
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    count, last = 0, ("", "", "")
    with temporary.open("w", encoding="utf-8") as output:
        while True:
            rows = store.db.execute("SELECT * FROM entities WHERE provider='api-sports' AND "
                                    "(sport,entity_kind,entity_id)>(?,?,?) ORDER BY sport,entity_kind,entity_id LIMIT 1000", last).fetchall()
            if not rows:
                break
            for entity in rows:
                aliases = [dict(row) for row in store.db.execute(
                    "SELECT r.name,r.language,r.source FROM entity_alias_reviews r JOIN entity_names n "
                    "ON n.provider='api-sports' AND n.sport=r.sport AND n.entity_kind=r.entity_kind "
                    "AND n.entity_id=r.api_id AND n.name=r.name AND n.source=r.source "
                    "WHERE r.sport=? AND r.entity_kind=? AND r.api_id=? AND r.status='accepted' "
                    "GROUP BY r.name,r.language,r.source ORDER BY r.name,r.source",
                    (entity["sport"], entity["entity_kind"], entity["entity_id"]),
                ) if len(normalise(row["name"])) >= 3]
                output.write(json.dumps({"format_version": 1, "provider": "api-sports", "sport": entity["sport"],
                                         "entity_kind": entity["entity_kind"], "entity_id": entity["entity_id"],
                                         "canonical": entity["name"], "aliases": aliases}, ensure_ascii=False) + "\n")
                count += 1
            last = (rows[-1]["sport"], rows[-1]["entity_kind"], rows[-1]["entity_id"])
    temporary.replace(destination)
    return count
