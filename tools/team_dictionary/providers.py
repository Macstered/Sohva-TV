"""Resumable discovery for all API-Sports products and TheSportsDB v2 enrichment."""

from __future__ import annotations

import hashlib
import json
import re
from datetime import datetime, timezone
from urllib.parse import urlencode

from .http import CollectionError, RecordListGap, tsdb_no_data
from .store import Store, normalise, timestamp
from .sports import ALL_SPORTS, api_base, routes, tsdb_products, tsdb_team_sport_matches

SUPPORTED = ALL_SPORTS
LANGUAGES = ("en", "fi", "sv", "de", "es", "pt", "it", "fr")
PILOT_COUNTRIES = {"Finland", "Germany", "England", "World"}
PILOT_HOCKEY_LEAGUES = {"liiga", "nhl", "world championship"}
PILOT_TSDB_LEAGUES = {"english premier league", "finnish sm liiga", "finnish liiga", "nhl",
                      "ice hockey world championship", "iihf world championship", "australian afl", "nba",
                      "mlb", "nfl", "formula 1", "aviva premiership rugby", "german handball bundesliga"}


def seed(store: Store, scope: str, sports: tuple[str, ...], providers: tuple[str, ...]) -> None:
    with store.db:
        if "api-sports" in providers:
            for sport in sports:
                for endpoint, kind in routes(sport, scope):
                    context = {}
                    if scope == "full":
                        prior = store.db.execute("SELECT context FROM jobs WHERE provider='api-sports' AND sport=? "
                                                 "AND state='done' AND context LIKE '%season_access%' "
                                                 "ORDER BY id DESC LIMIT 1", (sport,)).fetchone()
                        if prior:
                            context["season_access"] = json.loads(prior["context"])["season_access"]
                    store.enqueue("api-sports", sport, endpoint, kind, context=context, scope=scope)
        if "thesportsdb" in providers:
            store.enqueue("thesportsdb", "+".join(sorted(sports)), "/all/leagues", "tsdb-leagues",
                          context={"sports": sorted(sports)}, scope=scope)
        if "cldr" in providers:
            for language in LANGUAGES:
                store.enqueue("cldr", "all", f"/{language}/territories.json", "cldr-labels",
                              context={"language": language})


def apply_season_access(store: Store, scope: str, first: int, last: int, reason: str, sport: str = "hockey") -> None:
    """Keep unavailable requests as coverage gaps; rediscover only advertised allowed seasons."""
    with store.db:
        cursor = store.db.execute("SELECT id,params FROM jobs WHERE provider='api-sports' AND sport=? "
                                  "AND scope IN (?, 'shared') AND state IN ('pending','error') ORDER BY id", (sport, scope))
        for row in cursor:
            season = str(json.loads(row["params"]).get("season", ""))
            if season[:4].isdigit() and not first <= int(season[:4]) <= last:
                store.db.execute("UPDATE jobs SET state='blocked',note=? WHERE id=?", (reason, row["id"]))
        for row in store.db.execute("SELECT id,context FROM jobs WHERE provider='api-sports' AND sport=? "
                                    "AND kind IN ('hockey-leagues','api-leagues','api-seasons') AND scope=?", (sport, scope)):
            context = json.loads(row["context"])
            context["season_access"] = {"first": first, "last": last}
            store.db.execute("UPDATE jobs SET state='pending',context=?,note=? WHERE id=?",
                             (json.dumps(context, sort_keys=True), reason, row["id"]))


def records(data: dict, key: str) -> list[dict]:
    value = data.get(key)
    if not isinstance(value, list) or any(not isinstance(row, dict) for row in value):
        raise CollectionError(record_shape(data, key) + ". No checkpoint was completed.")
    return value


def record_shape(data: dict, key: str) -> str:
    # Only recognised field names and types, never values or arbitrary response keys.
    recognised = ("all", "list", "lookup", "search", "response", "teams", "status", "message", "Message", "error", "errors")
    fields = [f"{field} ({'null' if data[field] is None else type(data[field]).__name__})"
              for field in recognised if field in data]
    unknown = sum(field not in recognised for field in data)
    if unknown:
        fields.append(f"{unknown} unrecognised fields")
    return f"The provider response expected '{key}' as a record list; received " + (", ".join(fields) or "an empty object")


def source_url(job) -> str:
    if job["provider"] == "api-sports":
        base = api_base(job["sport"])
    elif job["provider"] == "thesportsdb":
        base = "https://www.thesportsdb.com/api/v2/json"
    else:
        base = "https://raw.githubusercontent.com/unicode-org/cldr-json/48.2.0/cldr-json/cldr-localenames-full/main"
    params = json.loads(job["params"])
    return base + job["endpoint"] + ("?" + urlencode(params) if params else "")


def required_id(row: dict, key: str) -> str:
    value = str(row.get(key) or "")
    if not value.isdigit() or int(value) <= 0:
        raise CollectionError("A provider record has no valid identity. The request remains incomplete.")
    return value


def required_name(row: dict, key: str) -> str:
    value = row.get(key)
    if not isinstance(value, str) or not value.strip() or "<redacted>" in value:
        raise CollectionError("A provider record has no usable name. The request remains incomplete.")
    return value.strip()


def import_payload(store: Store, job, data: dict) -> tuple[int, str]:
    """Caller commits rows, new jobs and completion together; malformed pages roll back."""
    scope, kind = job["scope"], job["kind"]
    context = json.loads(job["context"])
    sport, source = job["sport"], source_url(job)
    if job["provider"] == "api-sports":
        rows = data.get("response")
        if not isinstance(rows, list):
            raise CollectionError("The API response is missing its record list. No checkpoint was completed.")
        paging = data.get("paging", {})
        if int(paging.get("total", 1)) > 1:
            raise CollectionError("This endpoint reports extra pages. Verify its paging parameters before proceeding.")
        if data.get("results", len(rows)) != len(rows):
            raise CollectionError("The API result count differs from the returned rows. This request is incomplete.")
        if kind == "mma-categories" or kind == "api-seasons" or kind.startswith("f1-") or kind == "mma-fighters":
            from .entities import import_entities
            return import_entities(store, job, rows, source)
        rows = records(data, "response")
        if kind in ("football-countries", "api-countries"):
            for row in rows:
                country = required_name(row, "name")
                if scope == "full" or country in PILOT_COUNTRIES:
                    store.enqueue("api-sports", sport, "/teams", "api-teams", {"country": country}, scope=scope)
            return len(rows), ""
        if kind in ("hockey-leagues", "api-leagues"):
            missing = 0
            selected = 0
            access = context.get("season_access")
            for row in rows:
                league = row.get("league", row)
                name = required_name(league, "name")
                if scope == "pilot":
                    if sport == "hockey" and normalise(name) not in PILOT_HOCKEY_LEAGUES:
                        continue
                    if sport != "hockey" and selected >= 3:
                        continue
                selected += 1
                league_id = required_id(league, "id")
                seasons = row.get("seasons")
                if not isinstance(seasons, list) or not seasons:
                    missing += 1
                    continue
                years = []
                for season in seasons:
                    year = season.get("season", season.get("year")) if isinstance(season, dict) else season
                    if year is None or not re.fullmatch(r"[0-9]{4}(?:-[0-9]{4})?", str(year)):
                        raise CollectionError("An advertised season has an unexpected format.")
                    years.append(str(year))
                years = sorted(set(years), reverse=True)
                if access:
                    years = [year for year in years if access["first"] <= int(year[:4]) <= access["last"]]
                    if not years:
                        missing += 1
                if scope == "pilot":
                    # Avoid advertised future seasons with no roster; full collection keeps them.
                    years = [year for year in years if int(year[:4]) <= datetime.now(timezone.utc).year][:2]
                for year in years:
                    store.enqueue("api-sports", sport, "/teams", "api-teams", {"league": league_id, "season": year},
                                  context={"league_name": name}, scope=scope)
            notes = [f"{missing} selected leagues had no advertised seasons within the selected access"] if missing else []
            if access:
                notes.append(f"Plan access: seasons {access['first']} to {access['last']}; other seasons remain coverage gaps.")
            return len(rows), "; ".join(notes)
        if kind in ("api-teams", "mma-teams", "api-team-detail"):
            detail = kind == "api-team-detail"
            if detail and len(rows) > 1:
                raise CollectionError("The team ID lookup returned multiple records. No checkpoint was completed.")
            imported, unnamed = 0, 0
            for row in rows:
                team = row.get("team", row)
                if not isinstance(team, dict):
                    raise CollectionError("A team record has an unexpected shape.")
                team_id = required_id(team, "id")
                if detail and team_id != str(json.loads(job["params"]).get("id")):
                    raise CollectionError("The team ID lookup returned a different identity. Review the provider record.")
                if sport == "mma" and scope == "full":
                    store.enqueue("api-sports", sport, "/fighters", "mma-fighters", {"team": team_id}, scope=scope)
                name = team.get("name")
                if name is None or isinstance(name, str) and not name.strip():
                    # Never invent a canonical name. Keep the ID as a gap, import the other rows,
                    # and schedule one shared ID lookup instead of retrying the whole roster.
                    if not detail:
                        store.db.execute("INSERT INTO record_gaps VALUES (?,?,?,?,?,?,?,?,NULL) "
                                         "ON CONFLICT(provider,sport,entity_kind,entity_id,source) DO UPDATE SET "
                                         "job_id=excluded.job_id,observed_at=excluded.observed_at,resolved_at=NULL",
                                         ("api-sports", sport, "team", team_id, "missing canonical name", source, job["id"], timestamp()))
                        store.enqueue("api-sports", sport, "/teams", "api-team-detail", {"id": team_id})
                    unnamed += 1
                    continue
                country = team.get("country") or ""
                if isinstance(country, dict):
                    country = str(country.get("name") or "")
                national = team.get("national")
                venue = row.get("venue") or team.get("arena") or team.get("stadium") or {}
                if not isinstance(venue, dict):
                    venue = {}
                store.team("api-sports", sport, team_id, required_name(team, "name"), source,
                           country=str(country), national=national if isinstance(national, bool) else None,
                           gender=str(team.get("gender") or ""), founded=str(team.get("founded") or team.get("established") or ""),
                           city=str(team.get("city") or venue.get("city") or venue.get("location") or ""),
                           detailed=True)
                store.db.execute("UPDATE record_gaps SET resolved_at=? WHERE provider='api-sports' AND sport=? "
                                 "AND entity_kind='team' AND entity_id=? AND resolved_at IS NULL", (timestamp(), sport, team_id))
                imported += 1
                if team.get("code"):
                    store.name("api-sports", sport, str(team["id"]), str(team["code"]), "code", source)
                if isinstance(team.get("nickname"), str):
                    store.name("api-sports", sport, str(team["id"]), team["nickname"], "nickname", source)
            if detail and not imported:
                return 0, "Team ID lookup still has no usable name; identity remains quarantined for coverage review."
            if unnamed:
                return imported, f"Imported {imported}/{len(rows)} teams; {unnamed} unnamed IDs quarantined for individual lookup and coverage review."
            return imported, "empty roster; verify plan access and season" if not rows else ""
    if kind == "tsdb-leagues":
        rows = records(data, "all")
        pilot_seen = set()
        for row in rows:
            for league_sport in tsdb_products(row.get("strSport"), row.get("strLeague", "")):
                if league_sport not in context["sports"]:
                    continue
                name = required_name(row, "strLeague")
                if scope == "pilot":
                    if league_sport in ("football", "hockey") and normalise(name) not in PILOT_TSDB_LEAGUES:
                        continue
                    if league_sport not in ("football", "hockey") and league_sport in pilot_seen:
                        continue
                pilot_seen.add(league_sport)
                store.enqueue("thesportsdb", league_sport, "/list/teams/" + required_id(row, "idLeague"),
                              "tsdb-team-list", context={"league": name}, scope=scope)
        return len(rows), "response reached documented 3000-record limit" if len(rows) >= 3000 else ""
    if kind in ("tsdb-team-list", "tsdb-team-detail", "tsdb-search"):
        key = {"tsdb-team-list": "list", "tsdb-team-detail": "lookup", "tsdb-search": "search"}[kind]
        if tsdb_no_data(data):
            if kind == "tsdb-team-list":
                return 0, "empty league team list; TheSportsDB reported 'No data found'; coverage needs review"
            if kind == "tsdb-search":
                return 0, "TheSportsDB reported 'No data found' for this search; coverage needs review"
            return 0, "TheSportsDB reported 'No data found' for this team ID. Its profile remains unavailable for coverage review."
        if kind == "tsdb-team-list" and key in data and data[key] is None:
            return 0, "empty league team list; coverage needs review"
        if kind == "tsdb-team-list" and not isinstance(data.get(key), list):
            raise RecordListGap(record_shape(data, key) + ". League deferred for coverage review; its roster is unverified.")
        rows = records(data, key)
        for row in rows:
            if row.get("strSport") and not tsdb_team_sport_matches(row["strSport"], sport):
                raise CollectionError("TheSportsDB returned a team from another sport. Review the lookup.")
            team_id = required_id(row, "idTeam")
            detail = kind == "tsdb-team-detail"
            if detail and team_id != job["endpoint"].rsplit("/", 1)[-1]:
                raise CollectionError("The detail lookup returned a different team ID. Review the provider record.")
            external = str(row.get("idAPIfootball") or "")
            store.team("thesportsdb", sport, team_id, required_name(row, "strTeam"), source,
                       country=str(row.get("strCountry") or ""), gender=str(row.get("strGender") or ""),
                       founded=str(row.get("intFormedYear") or ""), city=str(row.get("strLocation") or ""),
                       api_football_id=external if external.isdigit() and int(external) > 0 else "", detailed=detail)
            for field, name_kind in (("strTeamAlternate", "alternate"), ("strTeamShort", "short")):
                for alias in re.split(r"[,;]", str(row.get(field) or "")):
                    store.name("thesportsdb", sport, team_id, alias, name_kind, source)
            if not detail:
                store.enqueue("thesportsdb", sport, "/lookup/team/" + team_id, "tsdb-team-detail")
        if kind == "tsdb-team-detail" and len(rows) != 1:
            raise CollectionError("The team detail lookup did not return exactly one team.")
        limit = 100 if kind == "tsdb-team-list" else 10 if kind == "tsdb-search" else 2
        note = "response reached documented record limit; coverage needs review" if len(rows) >= limit else ""
        if kind == "tsdb-team-list" and not rows:
            note = "empty league team list; coverage needs review"
        return len(rows), note
    if kind == "cldr-labels":
        language = context["language"]
        try:
            labels = data["main"][language]["localeDisplayNames"]["territories"]
        except (KeyError, TypeError):
            raise CollectionError("The CLDR country-name response has an unexpected shape.") from None
        if not isinstance(labels, dict):
            raise CollectionError("The CLDR country-name response is missing its label table.")
        count = 0
        for key, label in labels.items():
            code = key.split("-alt-")[0]
            if re.fullmatch(r"[A-Z]{2}", code) and isinstance(label, str):
                store.db.execute("INSERT OR IGNORE INTO country_labels VALUES (?,?,?,?,?)",
                                 (code, language, label, normalise(label), source))
                count += 1
        return count, ""
    raise CollectionError("The collector does not recognise this checkpoint type.")


def complete(store: Store, job, data: dict, digest: str = "") -> None:
    try:
        with store.db:
            count, note = import_payload(store, job, data)
            state = "blocked" if job["kind"] in ("api-team-detail", "tsdb-team-detail") and not count else "done"
            store.db.execute("UPDATE jobs SET state=?, rows_seen=?,note=?,completed_at=?,digest=? WHERE id=?",
                             (state, count, note, timestamp(), digest, job["id"]))
    except (ValueError, TypeError, AttributeError, KeyError):
        raise CollectionError("The provider record shape changed. No rows or checkpoints from this request were saved.") from None


def recover_tsdb_empty_lists(store: Store, sports: tuple[str, ...], scope: str) -> int:
    """Reclassify only retained checksums that prove an exact known no-data body."""
    if not sports:
        return 0
    # A fixed, bounded set of encodings of the public response, not arbitrary body
    # recovery. Original keys/bodies are unavailable and unknown checksums stay blocked.
    payload = {"Message": "No data found"}
    fingerprints = {hashlib.sha256((json.dumps(payload, indent=indent, separators=separators) + tail).encode()).hexdigest()
                    for indent in (None, 0, 1, 2, 4) for separators in (None, (",", ":"), (", ", ": "))
                    for tail in ("", "\n", "\r\n")}
    selected = ",".join("?" for _ in sports)
    count, last = 0, 0
    while True:
        jobs = store.db.execute("SELECT * FROM jobs WHERE provider='thesportsdb' AND kind='tsdb-team-list' "
                                "AND state='blocked' AND scope IN (?, 'shared') "
                                f"AND sport IN ({selected}) AND id>? ORDER BY id LIMIT 1000", (scope, *sports, last)).fetchall()
        if not jobs:
            return count
        for job in jobs:
            last = job["id"]
            if job["digest"] in fingerprints:
                complete(store, job, payload, job["digest"])
                with store.db:
                    store.db.execute("UPDATE jobs SET note=note||' Recovered from the saved response checksum; no extra API request.' WHERE id=?",
                                     (job["id"],))
                count += 1
