"""Country-label proposals with explicit sporting territories and preserved qualifiers."""

from __future__ import annotations

import json
import re
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

from .http import CollectionError, NoRedirect
from .providers import LANGUAGES
from .store import Store, normalise

CODE_SOURCE = "https://raw.githubusercontent.com/unicode-org/cldr-json/48.2.0/cldr-json/cldr-core/supplemental/codeMappings.json"
SUBDIVISION_SOURCE = "https://raw.githubusercontent.com/unicode-org/cldr/release-48/common/subdivisions/"
SPORTING_TERRITORIES = {"gbeng": "GB-ENG", "gbsct": "GB-SCT", "gbwls": "GB-WLS", "gbnir": "GB-NIR"}
QUALIFIER = re.compile(r"^(.+?)\s+((?:U[0-9]{1,2}(?:\s+(?:W|Women))?)|W|Women|B)$", re.IGNORECASE)

# Observed names, not translated or guessed nicknames. The exact API name, country,
# national flag and product must identify one record before these are attached.
NICKNAMES = (
    ("football", "Finland", "Huuhkajat", "https://uutisarkisto.palloliitto.fi/huuhkajien-nations-leaguen-otteluohjelma-julki"),
    ("football", "Finland W", "Helmarit", "https://uutisarkisto.palloliitto.fi/helmarit-nimetty-mm-karsintojen-avausotteluun-slovakiaa-vastaan"),
    ("hockey", "Finland", "Leijonat", "https://www.leijonat.fi/maajoukkueet/a-maajoukkue/2025-26/mm2026/mm-kisojen-otteluohjelma-ja-tulokset"),
)


def split_qualification(name: str) -> tuple[str, str]:
    match = QUALIFIER.fullmatch(name.strip())
    return (match[1], match[2]) if match else (name.strip(), "")


def country_codes(store: Store, name: str) -> set[str]:
    label = normalise(name)
    codes = {row[0] for row in store.db.execute("SELECT DISTINCT code FROM country_labels WHERE normalised=?", (label,))}
    if re.fullmatch(r"[A-Za-z]{3}", name):
        codes.update(row[0] for row in store.db.execute("SELECT code FROM country_codes WHERE alpha3=?", (name.upper(),)))
    return codes


def national_identity(store: Store, team, require_flag: bool = True) -> tuple[str | None, str, str]:
    if require_flag and team["national"] != 1:
        return None, "", "provider does not mark this identity as national"
    base, suffix = split_qualification(team["name"])
    codes = country_codes(store, base)
    if len(codes) != 1:
        return None, suffix, "country name/code is missing or ambiguous; requires another source"
    code = next(iter(codes))
    country = team["country"]
    if country and normalise(country) != "world":
        evidence = country_codes(store, country)
        if evidence != {code}:
            return None, suffix, "provider country conflicts with or cannot verify the named territory"
    gender = str(team["gender"]).casefold()
    female_suffix = bool(re.search(r"(?:^|\s)(?:W|Women)$", suffix, re.IGNORECASE))
    if gender in ("female", "women", "woman", "f") and not female_suffix:
        return None, suffix, "female national identity has no distinguishing qualifier"
    if gender in ("male", "men", "man", "m") and female_suffix:
        return None, suffix, "provider gender conflicts with the national qualifier"
    return code, suffix, ""


def national_aliases(store: Store, team):
    code, suffix, _ = national_identity(store, team)
    if code is None:
        return
    for row in store.db.execute("SELECT * FROM country_labels WHERE code=? ORDER BY language,name", (code,)):
        yield {"name": row["name"] + (" " + suffix if suffix else ""), "language": row["language"],
               "kind": "national-qualified-translation" if suffix else "national-translation",
               "source": row["source"], "identity_status": "qualified-country-name" if suffix else "exact-country-name"}


def seed_nicknames(store: Store, sports: tuple[str, ...]) -> int:
    added = 0
    with store.db:
        for sport, canonical, alias, source in NICKNAMES:
            if sport not in sports:
                continue
            teams = store.db.execute("SELECT * FROM teams WHERE provider='api-sports' AND sport=? AND name=? AND national=1",
                                     (sport, canonical)).fetchall()
            if len(teams) != 1 or national_identity(store, teams[0])[0] != "FI":
                continue
            before = store.db.total_changes
            store.name("api-sports", sport, teams[0]["team_id"], alias, "federation-nickname", source, "fi")
            added += store.db.total_changes - before
    return added


def import_codes(store: Store, data: dict) -> int:
    try:
        mappings = data["supplemental"]["codeMappings"]
    except (KeyError, TypeError):
        raise CollectionError("Unicode country-code data has an unexpected shape.") from None
    if not isinstance(mappings, dict):
        raise CollectionError("Unicode country-code data has no mapping table.")
    count = 0
    with store.db:
        for code, values in mappings.items():
            alpha3 = values.get("_alpha3") if isinstance(values, dict) else None
            if not re.fullmatch(r"[A-Z]{2}", code) or not isinstance(alpha3, str) or not re.fullmatch(r"[A-Z]{3}", alpha3):
                continue
            # Keep mappings only for explicitly collected territories, not reserved codes.
            if store.db.execute("SELECT 1 FROM country_labels WHERE code=? LIMIT 1", (code,)).fetchone():
                store.db.execute("INSERT OR REPLACE INTO country_codes VALUES (?,?,?)", (code, alpha3, CODE_SOURCE))
                count += 1
    if not count:
        raise CollectionError("Unicode country-code data contained no matching territories.")
    return count


def import_subdivisions(store: Store, language: str, raw: bytes) -> int:
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        raise CollectionError("Unicode subdivision labels could not be parsed.") from None
    identity = root.find("identity/language")
    if identity is None or identity.attrib.get("type") != language:
        raise CollectionError("Unicode subdivision labels have a different language identity.")
    labels = {}
    for node in root.iter("subdivision"):
        code = SPORTING_TERRITORIES.get(node.attrib.get("type"))
        if code and node.text and node.text.strip() and not node.attrib.get("alt"):
            labels[code] = node.text.strip()
    if len(labels) != len(SPORTING_TERRITORIES):
        raise CollectionError("Unicode subdivision data is missing a required sporting territory.")
    source = SUBDIVISION_SOURCE + language + ".xml"
    with store.db:
        for code, name in labels.items():
            store.db.execute("INSERT OR IGNORE INTO country_labels VALUES (?,?,?,?,?)", (code, language, name, normalise(name), source))
    return len(labels)


def refresh_translations(store: Store, log=print, opener=None, sleep=time.sleep) -> None:
    opener = opener or urllib.request.build_opener(NoRedirect())

    def read(url: str) -> bytes:
        store.reserve("cldr-enrichment", "all", 1_000_000_000, reserve=0)
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "Sohva-Team-Research/1", "Accept": "application/json, application/xml"})
            with opener.open(request, timeout=20) as response:
                raw = response.read(1024 * 1024 + 1)
                if len(raw) > 1024 * 1024:
                    raise CollectionError("Unicode source exceeded the bounded research response size.")
            return raw
        except (urllib.error.URLError, TimeoutError, OSError):
            raise CollectionError("Unicode reference data could not be reached. Existing translations are preserved; retry the refresh later.") from None

    try:
        count = import_codes(store, json.loads(read(CODE_SOURCE)))
    except (ValueError, UnicodeError):
        raise CollectionError("Unicode country-code data could not be read as JSON.") from None
    log(f"Unicode: {count} territory code mappings collected.")
    for language in LANGUAGES:
        sleep(1.0)  # Nine public-source calls per refresh, with no API-Sports/TSDB keys or quota.
        count = import_subdivisions(store, language, read(SUBDIVISION_SOURCE + language + ".xml"))
        log(f"Unicode {language}: {count} sporting-territory labels collected.")
