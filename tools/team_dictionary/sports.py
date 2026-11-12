"""API products and discovery routes, checked against the provider's references."""

ALL_SPORTS = ("football", "hockey", "afl", "basketball", "baseball", "handball", "rugby",
              "volleyball", "american-football", "nba", "mma", "formula-1")
COUNTRY_SPORTS = ("hockey", "basketball", "baseball", "handball", "rugby", "volleyball")
INDEX_KINDS = ("football-countries", "api-countries", "hockey-leagues", "api-leagues",
               "mma-categories", "mma-teams", "api-seasons", "tsdb-leagues")


def api_base(sport: str) -> str:
    if sport not in ALL_SPORTS:
        raise ValueError("Unknown API-Sports product.")
    version = {"football": "v3", "nba": "v2"}.get(sport, "v1")
    return f"https://{version}.{sport}.api-sports.io"


def routes(sport: str, scope: str) -> tuple[tuple[str, str], ...]:
    if sport == "football":
        return (("/teams/countries", "football-countries"),)
    if sport == "hockey" and scope == "pilot":
        return (("/leagues", "hockey-leagues"),)
    if sport in COUNTRY_SPORTS:
        return (("/countries", "api-countries"),)
    if sport == "american-football":
        return (("/leagues", "api-leagues"),)
    if sport == "mma":
        return (("/categories", "mma-categories"), ("/teams", "mma-teams"))
    if sport == "formula-1":
        return (("/teams", "api-teams"), ("/competitions", "f1-competitions"),
                ("/circuits", "f1-circuits"), ("/seasons", "api-seasons"))
    if sport in ("afl", "nba"):
        return (("/teams", "api-teams"),)
    raise ValueError("Unknown API-Sports product.")


def tsdb_products(label: str, league: str = "") -> tuple[str, ...]:
    # API-NBA IDs belong to a separate product. Only its competition feeds its namespace.
    if label == "Basketball":
        return ("basketball", "nba") if league.strip().casefold() == "nba" else ("basketball",)
    if label in ("Motorsport", "MotorSport"):
        return ("formula-1",) if league.strip().casefold() in ("formula 1", "formula one") else ()
    return {"Soccer": ("football",), "Ice Hockey": ("hockey",), "Australian Football": ("afl",),
            "Baseball": ("baseball",), "Handball": ("handball",), "Rugby": ("rugby",),
            "Rugby Union": ("rugby",), "Rugby League": ("rugby",), "Volleyball": ("volleyball",),
            "American Football": ("american-football",)}.get(label, ())


def tsdb_team_sport_matches(label: str, sport: str) -> bool:
    # A detail response can omit league names. The list job has already scoped NBA/F1.
    if sport == "nba":
        return label == "Basketball"
    if sport == "formula-1":
        return label in ("Motorsport", "MotorSport")
    return sport in tsdb_products(label)
