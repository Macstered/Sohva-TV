"""MMA fighter and Formula 1 identities; numbers cannot collide across entity types."""

import json
import re
from datetime import datetime, timezone

from .http import CollectionError
from .store import Store


def import_entities(store: Store, job, rows: list, source: str) -> tuple[int, str]:
    from .providers import required_id, required_name

    sport, kind, scope = job["sport"], job["kind"], job["scope"]
    if kind == "mma-categories":
        if any(not isinstance(row, str) or not row.strip() for row in rows):
            raise CollectionError("The MMA category directory has an unexpected shape.")
        for category in rows if scope == "full" else rows[:2]:
            store.enqueue("api-sports", sport, "/fighters", "mma-fighters", {"category": category}, scope=scope)
        return len(rows), "" if rows else "empty category directory; coverage needs review"
    if kind == "api-seasons":
        if any(not re.fullmatch(r"[0-9]{4}", str(row)) for row in rows):
            raise CollectionError("The season directory has an unexpected shape.")
        access = json.loads(job["context"]).get("season_access")
        years = sorted({str(row) for row in rows}, reverse=True)
        if access:
            years = [year for year in years if access["first"] <= int(year) <= access["last"]]
        if scope == "pilot":
            years = [year for year in years if int(year) <= datetime.now(timezone.utc).year][:2]
        for year in years:
            store.enqueue("api-sports", sport, "/rankings/drivers", "f1-driver-rankings", {"season": year}, scope=scope)
        return len(rows), "Driver discovery uses season rankings; unranked drivers remain a coverage gap."
    if any(not isinstance(row, dict) for row in rows):
        raise CollectionError("An identity response has an unexpected record shape.")
    if kind == "f1-driver-rankings":
        for row in rows:
            driver = row.get("driver")
            if not isinstance(driver, dict):
                raise CollectionError("A driver ranking is missing its driver identity.")
            entity_id = required_id(driver, "id")
            store.entity("api-sports", sport, "driver", entity_id, required_name(driver, "name"), source)
            if driver.get("abbr"):
                store.entity_name("api-sports", sport, "driver", entity_id, str(driver["abbr"]), "code", source)
            store.enqueue("api-sports", sport, "/drivers", "f1-drivers", {"id": entity_id})
        return len(rows), "empty driver rankings; verify season access" if not rows else ""
    entity_kind = {"mma-fighters": "fighter", "f1-drivers": "driver", "f1-circuits": "circuit",
                   "f1-competitions": "competition"}.get(kind)
    if entity_kind is None:
        raise CollectionError("The collector does not recognise this identity checkpoint.")
    params = json.loads(job["params"])
    if "id" in params and (len(rows) != 1 or required_id(rows[0], "id") != str(params["id"])):
        raise CollectionError("The identity lookup did not return the requested identity.")
    for row in rows:
        country = row.get("country") or (row.get("location") or {}).get("country") or ""
        if isinstance(country, dict):
            country = country.get("name") or ""
        entity_id = required_id(row, "id")
        store.entity("api-sports", sport, entity_kind, entity_id, required_name(row, "name"), source,
                     country=str(country), gender=str(row.get("gender") or ""),
                     category=str(row.get("category") or ""), detailed=True)
        for field, name_kind in (("nickname", "nickname"), ("abbr", "code")):
            if isinstance(row.get(field), str):
                store.entity_name("api-sports", sport, entity_kind, entity_id, row[field], name_kind, source)
    return len(rows), "empty identity list; coverage needs review" if not rows else ""
