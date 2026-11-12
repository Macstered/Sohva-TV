"""Synthetic contracts for all-product discovery and typed identities."""

import json
import tempfile
import unittest
from pathlib import Path
from urllib.parse import urlparse
from unittest.mock import patch

from team_dictionary.http import Client, CollectionError
from team_dictionary.providers import ALL_SPORTS, SUPPORTED, apply_season_access, complete, seed, source_url
from team_dictionary.research import export_entities, reports
from team_dictionary.store import Store, utc_day
from team_dictionary.test_collection import Opener, Response


class AllSportsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.store = Store(Path(self.directory.name) / "research.sqlite")

    def tearDown(self):
        self.store.close()
        self.directory.cleanup()

    def job(self, sport, endpoint, kind, params=None, context=None):
        self.store.enqueue("api-sports", sport, endpoint, kind, params, context, "full")
        self.store.db.commit()
        return self.store.next_job("api-sports", sport, "full")

    def test_all_twelve_products_have_discovery_jobs(self):
        self.assertEqual(ALL_SPORTS, SUPPORTED)
        seed(self.store, "full", ALL_SPORTS, ("api-sports",))
        self.assertEqual(set(ALL_SPORTS), {row[0] for row in self.store.db.execute("SELECT DISTINCT sport FROM jobs")})

    def test_nba_uses_v2_in_transport_and_evidence(self):
        job = self.job("nba", "/teams", "api-teams")
        self.assertEqual("https://v2.nba.api-sports.io/teams", source_url(job))
        opener = Opener([Response({"response": {"requests": {"limit_day": 100, "current": 70}}}),
                         Response({"response": []})])
        client = Client(self.store, api_key="fixture-key", opener=opener, sleep=lambda _: None)
        client.get("api-sports", "nba", "/teams")
        self.assertTrue(all(r.full_url.startswith("https://v2.nba.api-sports.io/") for r in opener.requests))
        self.assertEqual(29, self.store.remaining_today("api-sports", "nba", utc_day()))

    def test_country_directories_queue_exact_names_without_seasons(self):
        for sport in ("basketball", "baseball", "handball", "rugby", "volleyball", "hockey"):
            with self.subTest(sport=sport):
                job = self.job(sport, "/countries", "api-countries")
                complete(self.store, job, {"response": [{"id": 1, "name": "Fiction-land"}, {"id": 2, "name": "World"}]})
                queries = [json.loads(row[0]) for row in self.store.db.execute(
                    "SELECT params FROM jobs WHERE sport=? AND kind='api-teams'", (sport,))]
                self.assertCountEqual([{"country": "Fiction-land"}, {"country": "World"}], queries)

    def test_nfl_nested_leagues_preserve_advertised_start_year(self):
        job = self.job("american-football", "/leagues", "api-leagues")
        complete(self.store, job, {"response": [{"league": {"id": 1, "name": "Fiction League"},
                                               "seasons": [{"year": 2024}, {"year": 2025}]}]})
        self.assertEqual(2, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE kind='api-teams'").fetchone()[0])

    def test_mma_categories_do_not_use_an_unfiltered_fighter_request(self):
        job = self.job("mma", "/categories", "mma-categories")
        complete(self.store, job, {"response": ["Lightweight", "Women's Flyweight"]})
        queries = [json.loads(r[0]) for r in self.store.db.execute("SELECT params FROM jobs WHERE kind='mma-fighters'")]
        self.assertCountEqual([{"category": "Lightweight"}, {"category": "Women's Flyweight"}], queries)

    def test_mma_gyms_and_fighters_with_same_id_are_separate(self):
        job = self.job("mma", "/teams", "api-teams")
        complete(self.store, job, {"response": [{"id": 1, "name": "Fiction Gym"}]})
        job = self.job("mma", "/fighters", "mma-fighters", {"category": "Lightweight"})
        complete(self.store, job, {"response": [{"id": 1, "name": "Fiction Fighter", "nickname": "The Fiction",
                                               "category": "Lightweight", "gender": "M"}]})
        self.assertEqual("Fiction Gym", self.store.db.execute("SELECT name FROM teams").fetchone()[0])
        self.assertEqual("Fiction Fighter", self.store.db.execute("SELECT name FROM entities").fetchone()[0])
        self.assertEqual(2, self.store.db.execute("SELECT COUNT(*) FROM entity_names").fetchone()[0])

    def test_f1_seasons_discover_drivers_by_rankings_then_id(self):
        job = self.job("formula-1", "/seasons", "api-seasons")
        complete(self.store, job, {"response": [2023, 2024]})
        self.assertEqual(2, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE kind='f1-driver-rankings'").fetchone()[0])
        job = self.store.next_job("api-sports", "formula-1", "full")
        complete(self.store, job, {"response": [{"driver": {"id": 1, "name": "Fiction Driver", "abbr": "FIC"}}]})
        detail = self.store.db.execute("SELECT params FROM jobs WHERE kind='f1-drivers'").fetchone()
        self.assertEqual({"id": "1"}, json.loads(detail[0]))

    def test_f1_circuit_and_competition_same_id_do_not_collide(self):
        for kind in ("circuit", "competition"):
            job = self.job("formula-1", "/" + kind + "s", "f1-" + kind + "s")
            complete(self.store, job, {"response": [{"id": 1, "name": "Fiction " + kind}]})
        self.assertEqual(2, self.store.db.execute("SELECT COUNT(*) FROM entities").fetchone()[0])
        report = reports(self.store, Path(self.directory.name) / "reports")
        self.assertEqual({"circuit": 1, "competition": 1}, report["sports"]["formula-1"]["api_entities"])
        self.assertEqual(0, report["sports"]["formula-1"]["api_teams"])

    def test_malformed_fighter_rolls_back_page_and_child_jobs(self):
        job = self.job("mma", "/fighters", "mma-fighters", {"category": "Lightweight"})
        with self.assertRaises(CollectionError):
            complete(self.store, job, {"response": [{"id": 1, "name": "Fiction Fighter"}, {"name": "Broken"}]})
        self.assertEqual(0, self.store.db.execute("SELECT COUNT(*) FROM entities").fetchone()[0])

    def test_all_team_shapes_keep_original_names_and_qualifiers(self):
        for sport in ALL_SPORTS:
            with self.subTest(sport=sport):
                job = self.job(sport, "/teams", "api-teams")
                team = {"id": 1, "name": "Fiction Women U21", "code": "FWU", "national": True,
                        "country": {"name": "Finland"}, "city": "Fiction City"}
                row = {"team": team} if sport in ("football", "american-football") else team
                complete(self.store, job, {"response": [row]})
                stored = self.store.db.execute("SELECT name,national,country,city FROM teams WHERE sport=?", (sport,)).fetchone()
                self.assertEqual(("Fiction Women U21", 1, "Finland", "Fiction City"), tuple(stored))

    def test_full_mma_expands_gyms_even_after_a_completed_pilot(self):
        seed(self.store, "pilot", ("mma",), ("api-sports",))
        with self.store.db:
            self.store.db.execute("UPDATE jobs SET state='done' WHERE kind='mma-categories'")
        job = self.store.next_job("api-sports", "mma")
        complete(self.store, job, {"response": [{"id": 1, "name": "Fiction Gym"}]})
        seed(self.store, "full", ("mma",), ("api-sports",))
        with self.store.db:
            self.store.db.execute("UPDATE jobs SET state='done' WHERE kind='mma-categories'")
        job = self.store.next_job("api-sports", "mma", "full")
        self.assertEqual("mma-teams", job["kind"])
        complete(self.store, job, {"response": [{"id": 1, "name": "Fiction Gym"}]})
        self.assertEqual({"team": "1"}, json.loads(self.store.next_job("api-sports", "mma", "full")["params"]))

    def test_tsdb_scopes_nba_and_formula1_without_importing_other_competitions(self):
        self.store.enqueue("thesportsdb", "+".join(sorted(ALL_SPORTS)), "/all/leagues", "tsdb-leagues",
                           context={"sports": list(ALL_SPORTS)}, scope="full")
        self.store.db.commit()
        job = self.store.next_job("thesportsdb", scope="full")
        complete(self.store, job, {"all": [
            {"idLeague": "1", "strLeague": "NBA", "strSport": "Basketball"},
            {"idLeague": "2", "strLeague": "Fiction Women's League", "strSport": "Basketball"},
            {"idLeague": "3", "strLeague": "Formula 1", "strSport": "Motorsport"},
            {"idLeague": "4", "strLeague": "Fiction Rally", "strSport": "Motorsport"},
            {"idLeague": "5", "strLeague": "Fiction AFL", "strSport": "Australian Football"},
        ]})
        self.assertEqual(["/list/teams/1"], [r[0] for r in self.store.db.execute("SELECT endpoint FROM jobs WHERE sport='nba'")])
        self.assertEqual(2, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE sport='basketball'").fetchone()[0])
        self.assertEqual(["/list/teams/3"], [r[0] for r in self.store.db.execute("SELECT endpoint FROM jobs WHERE sport='formula-1'")])

    def test_null_tsdb_list_is_a_coverage_gap_not_a_schema_failure(self):
        self.store.enqueue("thesportsdb", "football", "/list/teams/1", "tsdb-team-list", scope="full")
        self.store.db.commit()
        job = self.store.next_job("thesportsdb", "football", "full")
        complete(self.store, job, {"list": None})
        self.assertEqual("done", self.store.db.execute("SELECT state FROM jobs").fetchone()[0])
        self.assertIn("empty league", self.store.db.execute("SELECT note FROM jobs").fetchone()[0])

    def test_f1_access_recovery_queues_only_allowed_advertised_seasons(self):
        job = self.job("formula-1", "/seasons", "api-seasons")
        complete(self.store, job, {"response": [2023, 2024, 2025, 2026]})
        apply_season_access(self.store, "full", 2023, 2024, "Plan allows 2023 to 2024", sport="formula-1")
        index = self.store.next_job("api-sports", "formula-1", "full")
        self.assertEqual("api-seasons", index["kind"])
        complete(self.store, index, {"response": [2023, 2024, 2025, 2026]})
        years = [json.loads(r[0])["season"] for r in self.store.db.execute(
            "SELECT params FROM jobs WHERE kind='f1-driver-rankings' AND state='pending'")]
        self.assertCountEqual(["2023", "2024"], years)
        self.assertEqual(2, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE state='blocked'").fetchone()[0])

    def test_typed_export_excludes_unreviewed_nicknames_and_keeps_same_numbers_separate(self):
        for kind in ("driver", "circuit"):
            self.store.entity("api-sports", "formula-1", kind, "1", "Fiction " + kind, "https://provider.example")
            self.store.entity_name("api-sports", "formula-1", kind, "1", "Fiction Short", "alternate", "https://provider.example")
        destination = Path(self.directory.name) / "entities.reviewed.jsonl"
        self.assertEqual(2, export_entities(self.store, destination))
        exported = [json.loads(line) for line in destination.read_text().splitlines()]
        self.assertEqual({"driver", "circuit"}, {row["entity_kind"] for row in exported})
        self.assertTrue(all(not row["aliases"] for row in exported))

    def test_schema_two_migration_preserves_names_quotas_and_pending_requests(self):
        self.store.team("api-sports", "hockey", "1", "Fiction HC", "https://provider.example")
        self.store.reserve("api-sports", "hockey", 80)
        self.store.enqueue("api-sports", "hockey", "/leagues", "hockey-leagues", scope="full")
        self.store.db.executescript("DROP TABLE entity_alias_reviews; DROP TABLE entity_names; DROP TABLE entities; PRAGMA user_version=2;")
        self.store.close()
        self.store = Store(Path(self.directory.name) / "research.sqlite")
        self.assertEqual("Fiction HC", self.store.db.execute("SELECT name FROM teams").fetchone()[0])
        self.assertEqual(1, self.store.db.execute("SELECT attempts FROM usage").fetchone()[0])
        self.assertEqual("pending", self.store.next_job("api-sports", "hockey", "full")["state"])
        self.assertEqual(0, self.store.db.execute("SELECT COUNT(*) FROM entities").fetchone()[0])

    def test_collector_runs_all_twelve_with_independent_caps_and_one_shared_pace(self):
        from sports_dictionary import collect, parser

        class FixtureOpener:
            def __init__(self):
                self.requests = []

            def open(self, request, timeout):
                self.requests.append(request)
                url = urlparse(request.full_url)
                sport = url.netloc.split(".")[1]
                data = {
                    "/status": {"response": {"requests": {"current": 0, "limit_day": 100}}},
                    "/teams/countries": {"response": [{"name": "Fictionland"}]},
                    "/countries": {"response": [{"id": 1, "name": "Fictionland"}]},
                    "/teams": {"response": [{"id": 1, "name": "Fiction Team"}]},
                    "/leagues": {"response": [{"league": {"id": 1, "name": "Fiction League"}, "seasons": [{"year": 2024}]}]},
                    "/categories": {"response": ["Fiction Class"]},
                    "/fighters": {"response": [{"id": 1, "name": "Fiction Fighter", "nickname": "The Fiction"}]},
                    "/seasons": {"response": [2024]},
                    "/competitions": {"response": [{"id": 1, "name": "Fiction Grand Prix"}]},
                    "/circuits": {"response": [{"id": 1, "name": "Fiction Circuit"}]},
                    "/rankings/drivers": {"response": [{"driver": {"id": 1, "name": "Fiction Driver"}}]},
                    "/drivers": {"response": [{"id": 1, "name": "Fiction Driver"}]},
                }[url.path]
                if sport == "football" and url.path == "/teams":
                    data = {"response": [{"team": {"id": 1, "name": "Fiction FC"}}]}
                return Response(data)

        # Bind no real transport: all products exercise scheduling and import together.
        opener, clock, sent = FixtureOpener(), [1000.0], []
        def factory(store, **kwargs):
            class RecordedOpener:
                def open(self, request, timeout):
                    sent.append(clock[0])
                    return opener.open(request, timeout)
            return Client(store, **kwargs, opener=RecordedOpener(), clock=lambda: clock[0],
                          sleep=lambda delay: clock.__setitem__(0, clock[0] + delay))

        args = parser().parse_args(["--data-dir", self.directory.name, "collect", "--providers", "api-sports", "--api-per-run", "6"])
        with patch("sports_dictionary.Client", side_effect=factory):
            self.assertEqual(0, collect(args, ("fixture-key", ""), log=lambda _: None))
        usage = dict(self.store.db.execute("SELECT sport,attempts FROM usage WHERE provider='api-sports'"))
        self.assertEqual(set(ALL_SPORTS), set(usage))
        self.assertTrue(all(value <= 6 for value in usage.values()))
        self.assertTrue(all(b - a >= 7.5 for a, b in zip(sent, sent[1:])))
        self.assertEqual(set(ALL_SPORTS), {row[0] for row in self.store.db.execute("SELECT DISTINCT sport FROM teams")})

    def test_enrichment_rotates_selected_sports_and_does_not_run_unselected_jobs(self):
        from sports_dictionary import collect, parser
        for sport in ("football", "hockey", "afl"):
            for number in range(3):
                self.store.enqueue("thesportsdb", sport, f"/list/teams/{number+1}", "tsdb-team-list", scope="full")
        self.store.db.commit()
        opener = Opener([Response({"all": []})] + [Response({"list": []}) for _ in range(4)])
        args = parser().parse_args(["--data-dir", self.directory.name, "collect", "--providers", "thesportsdb",
                                    "--sports", "football", "hockey", "--tsdb-per-run", "5"])
        def factory(store, **kwargs):
            return Client(store, **kwargs, opener=opener, sleep=lambda _: None)
        with patch("sports_dictionary.Client", side_effect=factory):
            self.assertEqual(0, collect(args, ("", "fixture-key"), log=lambda _: None))
        completed = dict(self.store.db.execute("SELECT sport,COUNT(*) FROM jobs WHERE kind='tsdb-team-list' AND state='done' GROUP BY sport"))
        self.assertEqual({"football": 2, "hockey": 2}, completed)

    def test_repeated_tsdb_failures_stop_after_three_attempts(self):
        from sports_dictionary import collect, parser
        for number in range(4):
            self.store.enqueue("thesportsdb", "football", f"/list/teams/{number+1}", "tsdb-team-list", scope="full")
        self.store.db.commit()
        opener = Opener([Response({"all": []})] + [Response({"status": "error", "error": "fixture failure"}) for _ in range(3)])
        args = parser().parse_args(["--data-dir", self.directory.name, "collect", "--providers", "thesportsdb", "--sports", "football"])
        def factory(store, **kwargs):
            return Client(store, **kwargs, opener=opener, sleep=lambda _: None)
        with patch("sports_dictionary.Client", side_effect=factory):
            self.assertEqual(1, collect(args, ("", "fixture-key"), log=lambda _: None))
        self.assertEqual(4, len(opener.requests))
        self.assertEqual(1, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE state='pending'").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
