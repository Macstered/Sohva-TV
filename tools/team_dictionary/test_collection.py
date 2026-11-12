"""Synthetic provider tests: imports, identity separation, quota and secret handling."""

import io
import json
import tempfile
import unittest
import urllib.error
from pathlib import Path

from team_dictionary.http import BudgetReached, Client, CollectionError, NoRedirect, RateLimited
from team_dictionary.store import Store, collection_lock, normalise, utc_day
from team_dictionary.providers import complete, seed
from team_dictionary.research import alias_candidates, build_links, export_reviewed, reports


class Response(io.BytesIO):
    def __init__(self, body, headers=None):
        super().__init__(json.dumps(body).encode())
        self.headers = headers or {}


class Opener:
    def __init__(self, responses):
        self.responses = list(responses)
        self.requests = []

    def open(self, request, timeout):
        self.requests.append(request)
        item = self.responses.pop(0)
        if isinstance(item, Exception):
            raise item
        return item


class CollectionTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.store = Store(Path(self.directory.name) / "research.sqlite")

    def tearDown(self):
        self.store.close()
        self.directory.cleanup()

    def test_budget_persists_and_resets_only_on_a_new_utc_day(self):
        self.assertTrue(self.store.reserve("api-sports", "football", 2, day="2026-10-01"))
        self.assertTrue(self.store.reserve("api-sports", "football", 2, day="2026-10-01"))
        self.assertFalse(self.store.reserve("api-sports", "football", 2, day="2026-10-01"))
        self.assertTrue(self.store.reserve("api-sports", "hockey", 2, day="2026-10-01"))
        self.assertTrue(self.store.reserve("api-sports", "football", 2, day="2026-10-02"))

    def test_remote_usage_leaves_twenty_requests_for_other_use(self):
        self.store.remaining("api-sports", "football", 21)
        self.assertTrue(self.store.reserve("api-sports", "football", 80))
        self.assertFalse(self.store.reserve("api-sports", "football", 80))

    def test_http_200_service_error_is_not_success_or_free(self):
        opener = Opener([Response({"errors": {"quota": "private-key"}, "response": []})])
        client = Client(self.store, api_key="private-key", opener=opener)
        with self.assertRaises(CollectionError) as caught:
            client.get("api-sports", "football", "/teams")
        self.assertNotIn("private-key", str(caught.exception))
        self.assertEqual(1, self.store.db.execute("SELECT attempts FROM usage").fetchone()[0])

    def test_plan_error_keeps_the_provider_reason_and_requested_endpoint(self):
        opener = Opener([Response({"errors": [], "response": {}}),
                         Response({"errors": {"plan": "Free plans do not have access to this season, try from 2022 to 2024."}})])
        client = Client(self.store, api_key="private-key", opener=opener, sleep=lambda delay: None)
        with self.assertRaises(CollectionError) as caught:
            client.get("api-sports", "hockey", "/teams", {"league": "16", "season": "2026"})
        self.assertIn("plan:", str(caught.exception))
        self.assertIn("try from 2022 to 2024", str(caught.exception))
        self.assertIn("hockey /teams", str(caught.exception))
        self.assertEqual("SeasonRestricted", type(caught.exception).__name__)
        self.assertEqual((2022, 2024), (caught.exception.first, caught.exception.last))

    def test_parameter_error_redacts_credentials_links_and_line_breaks(self):
        opener = Opener([Response({"errors": {"season": "Invalid private-key\nhttps://provider.example/?key=private-key\nother-secret"}})])
        client = Client(self.store, api_key="private-key", tsdb_key="other-secret", opener=opener)
        with self.assertRaises(CollectionError) as caught:
            client.get("api-sports", "hockey", "/teams")
        message = str(caught.exception)
        self.assertIn("season: Invalid", message)
        self.assertNotIn("private-key", message)
        self.assertNotIn("other-secret", message)
        self.assertNotIn("https://", message)
        self.assertNotIn("\n", message)

    def test_provider_error_summary_is_bounded_and_does_not_echo_unknown_fields(self):
        opener = Opener([Response({"errors": {"plan": "x " * 3000, "private-key": "hidden"}})])
        client = Client(self.store, api_key="private-key", opener=opener)
        with self.assertRaises(CollectionError) as caught:
            client.get("api-sports", "hockey", "/teams")
        message = str(caught.exception)
        self.assertIn("plan:", message)
        self.assertLessEqual(len(message), 650)
        self.assertNotIn("private-key", message)
        self.assertNotIn("hidden", message)

    def test_collector_saves_redacted_error_in_reports_without_saving_keys(self):
        from unittest.mock import patch
        from sports_dictionary import collect, parser
        opener = Opener([Response({"errors": [], "response": {}}),
                         Response({"errors": {"plan": "Unavailable season private-key; try from 2022 to 2024."}})])
        args = parser().parse_args(["--data-dir", self.directory.name, "pilot", "--providers", "api-sports", "--sports", "hockey"])

        def client_factory(store, **kwargs):
            return Client(store, **kwargs, opener=opener, sleep=lambda delay: None)

        logs = []
        with patch("sports_dictionary.Client", side_effect=client_factory):
            self.assertEqual(1, collect(args, ("private-key", ""), log=logs.append))
        note = self.store.db.execute("SELECT note FROM jobs WHERE state='error'").fetchone()[0]
        report = (Path(self.directory.name) / "coverage.md").read_text(encoding="utf-8")
        self.assertIn("plan: Unavailable season", note)
        self.assertIn(note, report)
        self.assertNotIn("private-key", report + " ".join(logs))
        self.assertNotIn(b"private-key", (Path(self.directory.name) / "research.sqlite").read_bytes())

    def test_status_checks_shared_quota_and_key_stays_out_of_url(self):
        opener = Opener([Response({"errors": [], "response": {"requests": {"limit": 100, "current": 80}}})])
        client = Client(self.store, api_key="private-key", opener=opener, sleep=lambda delay: None)
        with self.assertRaises(BudgetReached):
            client.get("api-sports", "football", "/teams")
        self.assertEqual(1, len(opener.requests))
        self.assertNotIn("private-key", opener.requests[0].full_url)

    def test_api_free_tier_spacing_is_below_ten_requests_per_minute(self):
        opener = Opener([Response({"errors": [], "response": {}}), Response({"errors": [], "response": []})])
        delays = []
        client = Client(self.store, api_key="private-key", opener=opener, sleep=delays.append)
        client.get("api-sports", "football", "/teams")
        self.assertGreaterEqual(delays[0], 7.0)

    def test_pacing_survives_a_new_client(self):
        self.store.sent("thesportsdb", 1000.0)
        delays = []
        client = Client(self.store, tsdb_key="private-key", opener=Opener([Response({"lookup": []})]),
                        sleep=delays.append, clock=lambda: 1000.0)
        client.get("thesportsdb", "football", "/lookup/team/1")
        self.assertAlmostEqual(0.8, delays[0])

    def test_dataset_lock_rejects_concurrent_collectors_and_releases(self):
        directory = Path(self.directory.name)
        with collection_lock(directory):
            with self.assertRaises(ValueError):
                with collection_lock(directory):
                    self.fail("A second collector acquired the same dataset.")
        with collection_lock(directory):
            pass

    def test_body_cap_prevents_unbounded_provider_reads(self):
        client = Client(self.store, tsdb_key="private-key", opener=Opener([Response({"lookup": ["x" * 200]})]))
        client.BODY_LIMIT = 100
        with self.assertRaises(CollectionError):
            client.get("thesportsdb", "football", "/lookup/team/1")

    def test_provider_minute_header_stops_further_requests(self):
        opener = Opener([Response({"lookup": []}, {"X-RateLimit-Remaining": "0"})])
        client = Client(self.store, tsdb_key="private-key", opener=opener)
        client.get("thesportsdb", "football", "/lookup/team/1")
        with self.assertRaises(RateLimited):
            client.get("thesportsdb", "football", "/lookup/team/2")
        self.assertEqual(1, len(opener.requests))

    def test_http_200_minute_limit_is_retryable_and_keeps_quota_accounting(self):
        opener = Opener([Response({"errors": [], "response": {}}),
                         Response({"errors": {"rateLimit": "Ten requests per minute private-key"}, "response": []})])
        client = Client(self.store, api_key="private-key", opener=opener, sleep=lambda delay: None)
        with self.assertRaises(CollectionError) as caught:
            client.get("api-sports", "hockey", "/teams")
        self.assertEqual("RateLimited", type(caught.exception).__name__)
        self.assertNotIn("private-key", str(caught.exception))
        self.assertEqual(2, self.store.db.execute("SELECT attempts FROM usage").fetchone()[0])

    def test_http_429_is_retryable_without_repeating_a_completed_status_check(self):
        clock = [1000.0]
        opener = Opener([Response({"errors": [], "response": {}}),
                         urllib.error.HTTPError("https://provider.example", 429, "limited", {"Retry-After": "90"}, None),
                         Response({"errors": [], "response": []})])
        client = Client(self.store, api_key="private-key", opener=opener,
                        sleep=lambda delay: clock.__setitem__(0, clock[0] + delay), clock=lambda: clock[0])
        with self.assertRaises(CollectionError) as caught:
            client.get("api-sports", "hockey", "/teams")
        self.assertEqual("RateLimited", type(caught.exception).__name__)
        self.assertGreaterEqual(caught.exception.retry_after, 90)
        clock[0] += caught.exception.retry_after
        client.get("api-sports", "hockey", "/teams")
        self.assertEqual(3, len(opener.requests))
        self.assertEqual(1, sum(request.full_url.endswith("/status") for request in opener.requests))

    def test_zero_minute_header_cooldown_expires_and_survives_a_new_client(self):
        clock = [1000.0]
        first = Client(self.store, tsdb_key="private-key", clock=lambda: clock[0],
                       opener=Opener([Response({"lookup": []}, {"X-RateLimit-Remaining": "0"})]))
        first.get("thesportsdb", "football", "/lookup/team/1")
        opener = Opener([Response({"lookup": []})])
        second = Client(self.store, tsdb_key="private-key", clock=lambda: clock[0], opener=opener)
        with self.assertRaises(CollectionError) as caught:
            second.get("thesportsdb", "football", "/lookup/team/2")
        self.assertEqual("RateLimited", type(caught.exception).__name__)
        self.assertEqual(0, len(opener.requests))
        clock[0] += caught.exception.retry_after
        second.get("thesportsdb", "football", "/lookup/team/2")
        self.assertEqual(1, len(opener.requests))

    def test_daily_quota_still_stops_instead_of_retrying(self):
        opener = Opener([Response({"errors": [], "response": {}}),
                         urllib.error.HTTPError("https://provider.example", 429, "limited",
                                                {"x-ratelimit-requests-remaining": "0"}, None)])
        client = Client(self.store, api_key="private-key", opener=opener, sleep=lambda delay: None)
        with self.assertRaises(BudgetReached):
            client.get("api-sports", "hockey", "/teams")
        self.assertEqual(0, self.store.remaining_today("api-sports", "hockey", utc_day()))

    def test_cooldown_wait_is_cancellable_in_short_steps(self):
        from sports_dictionary import wait_for_retry
        steps = []
        self.assertTrue(wait_for_retry(65, lambda: False, sleep=steps.append))
        self.assertEqual(65, sum(steps))
        self.assertTrue(all(step <= 1 for step in steps))
        steps.clear()
        self.assertFalse(wait_for_retry(65, lambda: len(steps) >= 2, sleep=steps.append))
        self.assertEqual(2, len(steps))

    def test_version_one_migration_keeps_roster_and_usage(self):
        self.store.team("api-sports", "football", "1", "Fiction FC", "https://provider.example")
        self.store.reserve("api-sports", "football", 80)
        with self.store.db:
            self.store.db.execute("DROP TABLE request_cooldown")
            self.store.db.execute("PRAGMA user_version = 1")
        self.store.close()
        self.store = Store(Path(self.directory.name) / "research.sqlite")
        self.assertEqual(5, self.store.db.execute("PRAGMA user_version").fetchone()[0])
        self.assertEqual(1, self.store.db.execute("SELECT COUNT(*) FROM teams").fetchone()[0])
        self.assertEqual(1, self.store.db.execute("SELECT attempts FROM usage").fetchone()[0])

    def test_collector_retries_throttled_job_without_marking_it_failed(self):
        from unittest.mock import patch
        from sports_dictionary import collect, parser
        clock = [1000.0]
        opener = Opener([Response({"errors": [], "response": {}}),
                         Response({"errors": {"rateLimit": "Ten requests per minute"}, "response": []}),
                         Response({"errors": [], "response": []})])
        args = parser().parse_args(["--data-dir", self.directory.name, "pilot", "--providers", "api-sports", "--sports", "hockey"])

        def client_factory(store, **kwargs):
            return Client(store, **kwargs, opener=opener, clock=lambda: clock[0],
                          sleep=lambda delay: clock.__setitem__(0, clock[0] + delay))

        def fast_wait(seconds, cancelled):
            clock[0] += seconds
            return True

        with patch("sports_dictionary.Client", side_effect=client_factory), patch("sports_dictionary.wait_for_retry", side_effect=fast_wait):
            self.assertEqual(0, collect(args, ("private-key", ""), log=lambda message: None))
        self.assertEqual(3, len(opener.requests))
        job = self.store.db.execute("SELECT state,attempts FROM jobs WHERE kind='hockey-leagues'").fetchone()
        self.assertEqual(("done", 2), tuple(job))

    def test_repeated_throttling_is_bounded_and_leaves_job_pending(self):
        from unittest.mock import patch
        from sports_dictionary import collect, parser
        clock = [1000.0]
        opener = Opener([Response({"errors": [], "response": {}})] +
                        [Response({"errors": {"rateLimit": "Ten requests per minute"}, "response": []}) for _ in range(3)])
        args = parser().parse_args(["--data-dir", self.directory.name, "pilot", "--providers", "api-sports", "--sports", "hockey"])

        def client_factory(store, **kwargs):
            return Client(store, **kwargs, opener=opener, clock=lambda: clock[0],
                          sleep=lambda delay: clock.__setitem__(0, clock[0] + delay))

        def fast_wait(seconds, cancelled):
            clock[0] += seconds
            return True

        with patch("sports_dictionary.Client", side_effect=client_factory), patch("sports_dictionary.wait_for_retry", side_effect=fast_wait):
            self.assertEqual(0, collect(args, ("private-key", ""), log=lambda message: None))
        self.assertEqual(4, len(opener.requests))
        self.assertEqual("pending", self.store.db.execute("SELECT state FROM jobs WHERE kind='hockey-leagues'").fetchone()[0])

    def test_provider_key_echo_is_removed_from_importable_data(self):
        opener = Opener([Response({"lookup": [{"strTeam": "private-key"}]})])
        client = Client(self.store, tsdb_key="private-key", opener=opener)
        self.assertEqual("<redacted>", client.get("thesportsdb", "football", "/lookup/team/1")["lookup"][0]["strTeam"])

    def test_redirects_never_forward_headers(self):
        self.assertIsNone(NoRedirect().redirect_request(None, None, 302, "", {}, "https://provider.example"))

    def test_client_rate_limit_returns_retry_information_without_a_hidden_request(self):
        opener = Opener([urllib.error.HTTPError("https://provider.example", 429, "limited", {}, None)])
        client = Client(self.store, tsdb_key="private-key", opener=opener)
        with self.assertRaises(RateLimited):
            client.get("thesportsdb", "football", "/all/leagues")
        self.assertEqual(1, len(opener.requests))

    def test_provider_ids_and_sports_are_separate(self):
        for provider, sport, name in [("api-sports", "football", "Finland"), ("api-sports", "hockey", "Finland"),
                                      ("thesportsdb", "football", "Finland Women")]:
            self.store.team(provider, sport, "1", name, "https://provider.example")
        self.assertEqual(3, self.store.db.execute("SELECT COUNT(*) FROM teams").fetchone()[0])

    def test_normalisation_keeps_team_qualifiers(self):
        self.assertEqual("bayern munchen u21 women", normalise("Bayern München U21 Women"))
        self.assertNotEqual(normalise("Finland"), normalise("Finland Women"))

    def test_pilot_jobs_do_not_claim_full_collection(self):
        self.store.enqueue("api-sports", "football", "/teams/countries", "countries", scope="pilot")
        self.store.db.execute("UPDATE jobs SET state='done'")
        self.store.enqueue("api-sports", "football", "/teams/countries", "countries", scope="full")
        self.assertIsNone(self.store.next_job("api-sports", "football", "pilot"))
        self.assertIsNotNone(self.store.next_job("api-sports", "football", "full"))

    def test_full_queue_inherits_confirmed_hockey_season_access(self):
        seed(self.store, "pilot", ("hockey",), ("api-sports",))
        with self.store.db:
            self.store.db.execute("UPDATE jobs SET context=?,state='done' WHERE kind='hockey-leagues'",
                                 (json.dumps({"season_access": {"first": 2022, "last": 2024}}),))
        seed(self.store, "full", ("hockey",), ("api-sports",))
        job = self.store.next_job("api-sports", "hockey", "full")
        self.assertEqual({"first": 2022, "last": 2024}, json.loads(job["context"])["season_access"])

    def test_full_roster_queue_reuses_a_completed_pilot_request(self):
        job = self.job("api-teams")
        complete(self.store, job, {"response": [{"team": {"id": 1, "name": "Fiction FC"}}]}, "fixture-digest")
        with self.store.db:
            self.store.enqueue("api-sports", "football", "/teams", "api-teams", scope="full")
        full = self.store.db.execute("SELECT state,rows_seen,digest,attempts FROM jobs WHERE scope='full'").fetchone()
        self.assertEqual(("done", 1, "fixture-digest", 0), tuple(full))

    def test_finished_pilot_status_does_not_offer_an_empty_resume(self):
        from types import SimpleNamespace
        from team_dictionary.progress import batch_state
        args = SimpleNamespace(command="pilot", providers=["api-sports"], sports=["hockey"])
        state = batch_state(args, {"sports": {"hockey": {"api_teams": 72}}, "jobs": {
            "api-sports:hockey:pilot:done": 7, "api-sports:hockey:pilot:blocked": 6}}, 0)
        self.assertFalse(state.resume)
        self.assertTrue(state.expand)
        self.assertIn("Pilot complete", state.message)
        self.assertIn("72", state.message)

    def test_pending_full_status_keeps_resume_and_sports_filters(self):
        from types import SimpleNamespace
        from team_dictionary.progress import batch_state
        args = SimpleNamespace(command="collect", providers=["api-sports"], sports=["hockey"])
        state = batch_state(args, {"jobs": {"api-sports:hockey:full:pending": 3,
                                           "api-sports:football:full:error": 2}}, 0)
        self.assertTrue(state.resume)
        self.assertFalse(state.expand)
        self.assertIn("3 requests remain", state.message)

    def test_finished_full_status_keeps_coverage_limits_visible(self):
        from types import SimpleNamespace
        from team_dictionary.progress import batch_state
        args = SimpleNamespace(command="collect", providers=["api-sports"], sports=["hockey"])
        state = batch_state(args, {"jobs": {"api-sports:hockey:full:blocked": 6}}, 0)
        self.assertFalse(state.resume)
        self.assertIn("6 blocked", state.message)
        self.assertIn("coverage review", state.message)

    def job(self, kind, sport="football", endpoint="/teams", provider="api-sports", scope="pilot", context=None):
        self.store.enqueue(provider, sport, endpoint, kind, context=context, scope=scope)
        self.store.db.commit()
        return self.store.next_job(provider, sport, scope)

    def test_football_country_expansion_is_resumable_and_full_keeps_other_countries(self):
        data = {"response": [{"name": "Finland"}, {"name": "Fictionland"}], "results": 2}
        pilot = self.job("football-countries", endpoint="/teams/countries")
        complete(self.store, pilot, data)
        self.assertEqual(1, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE kind='api-teams'").fetchone()[0])
        self.assertEqual("done", self.store.db.execute("SELECT state FROM jobs WHERE id=?", (pilot["id"],)).fetchone()[0])
        full = self.job("football-countries", endpoint="/teams/countries", scope="full")
        complete(self.store, full, data)
        self.assertEqual(3, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE kind='api-teams'").fetchone()[0])

    def test_malformed_second_team_rolls_back_first_team_and_checkpoint(self):
        job = self.job("api-teams")
        with self.assertRaises(CollectionError):
            complete(self.store, job, {"response": [{"team": {"id": 1, "name": "Fiction FC"}}, {"team": {"name": "Broken"}}]})
        self.assertEqual(0, self.store.db.execute("SELECT COUNT(*) FROM teams").fetchone()[0])
        self.assertEqual("pending", self.store.next_job("api-sports")["state"])

    def test_extra_pages_and_count_mismatches_do_not_claim_completion(self):
        job = self.job("api-teams")
        for data in [{"response": [], "paging": {"total": 2}}, {"response": [], "results": 10}]:
            with self.assertRaises(CollectionError):
                complete(self.store, job, data)
        self.assertIsNotNone(self.store.next_job("api-sports"))

    def test_empty_team_response_is_a_coverage_warning(self):
        job = self.job("api-teams")
        complete(self.store, job, {"response": []})
        self.assertIn("empty roster", self.store.db.execute("SELECT note FROM jobs").fetchone()[0])

    def test_hockey_all_advertised_seasons_are_queued_in_full_scope(self):
        job = self.job("hockey-leagues", "hockey", "/leagues", scope="full")
        complete(self.store, job, {"response": [{"id": 1, "name": "Fiction Hockey League", "seasons": [2024, {"season": 2025}]}]})
        self.assertEqual(2, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE kind='api-teams'").fetchone()[0])

    def test_hockey_pilot_uses_newest_advertised_seasons_inside_reported_access_range(self):
        job = self.job("hockey-leagues", "hockey", "/leagues", context={"season_access": {"first": 2022, "last": 2024}})
        complete(self.store, job, {"response": [{"id": 1, "name": "Liiga", "seasons": [2022, 2023, 2024, 2025, 2026]}]})
        years = [json.loads(row[0])["season"] for row in self.store.db.execute("SELECT params FROM jobs WHERE kind='api-teams' ORDER BY id")]
        self.assertEqual(["2024", "2023"], years)

    def test_season_access_blocks_unavailable_jobs_and_refreshes_league_index(self):
        from team_dictionary.providers import apply_season_access
        index = self.job("hockey-leagues", "hockey", "/leagues")
        complete(self.store, index, {"response": [{"id": 1, "name": "Liiga", "seasons": [2022, 2023, 2024, 2025, 2026]}]})
        apply_season_access(self.store, "pilot", 2022, 2024, "Restricted season; allowed 2022 to 2024.")
        self.assertEqual(2, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE state='blocked'").fetchone()[0])
        index = self.store.next_job("api-sports", "hockey")
        self.assertEqual("hockey-leagues", index["kind"])
        self.assertEqual({"first": 2022, "last": 2024}, json.loads(index["context"])["season_access"])

    def test_collector_recovers_season_restriction_using_advertised_historical_rosters(self):
        from unittest.mock import patch
        from sports_dictionary import collect, parser
        leagues = {"response": [{"id": 1, "name": "Liiga", "seasons": [2022, 2023, 2024, 2025, 2026]}]}
        team = {"response": [{"id": 11, "name": "Fiction HC", "country": {"name": "Finland"}, "national": False}]}
        opener = Opener([Response({"errors": [], "response": {}}), Response(leagues),
                         Response({"errors": {"plan": "Free plans do not have access to this season, try from 2022 to 2024."}}),
                         Response(leagues), Response(team), Response(team)])
        args = parser().parse_args(["--data-dir", self.directory.name, "pilot", "--providers", "api-sports", "--sports", "hockey"])

        def client_factory(store, **kwargs):
            return Client(store, **kwargs, opener=opener, sleep=lambda delay: None)

        with patch("sports_dictionary.Client", side_effect=client_factory):
            self.assertEqual(0, collect(args, ("private-key", ""), log=lambda message: None))
        self.assertEqual(6, len(opener.requests))
        self.assertEqual(2, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE state='blocked'").fetchone()[0])
        self.assertEqual(1, self.store.db.execute("SELECT COUNT(*) FROM teams").fetchone()[0])
        self.assertIn("season=2024", opener.requests[-2].full_url)
        self.assertIn("season=2023", opener.requests[-1].full_url)
        self.assertNotIn("season=2025", " ".join(request.full_url for request in opener.requests))

    def test_missing_hockey_seasons_are_reported(self):
        job = self.job("hockey-leagues", "hockey", "/leagues", scope="full")
        complete(self.store, job, {"response": [{"id": 1, "name": "Fiction Hockey League"}]})
        self.assertIn("no advertised seasons", self.store.db.execute("SELECT note FROM jobs").fetchone()[0])

    def test_tsdb_league_list_queues_details_without_losing_alternate_names(self):
        job = self.job("tsdb-team-list", endpoint="/list/teams/1", provider="thesportsdb")
        complete(self.store, job, {"list": [{"idTeam": "11", "strTeam": "Fiction FC", "strTeamShort": "FFC", "strCountry": "Finland"}]})
        detail = self.store.next_job("thesportsdb", "football")
        self.assertEqual("tsdb-team-detail", detail["kind"])
        complete(self.store, detail, {"lookup": [{"idTeam": "11", "strTeam": "Fiction FC", "strTeamAlternate": "Fiction Club; Fiction United",
                                                 "idAPIfootball": "1", "strSport": "Soccer", "strGender": "Male", "strCountry": "Finland"}]})
        self.assertEqual(4, self.store.db.execute("SELECT COUNT(*) FROM names").fetchone()[0])
        self.assertEqual("1", self.store.db.execute("SELECT api_football_id FROM teams").fetchone()[0])
        self.assertEqual("Finland", self.store.db.execute("SELECT country FROM teams").fetchone()[0])

    def test_wrong_detail_id_or_sport_is_rejected(self):
        job = self.job("tsdb-team-detail", endpoint="/lookup/team/11", provider="thesportsdb")
        for row in [{"idTeam": "12", "strTeam": "Fiction FC"}, {"idTeam": "11", "strTeam": "Fiction FC", "strSport": "Ice Hockey"}]:
            with self.assertRaises(CollectionError):
                complete(self.store, job, {"lookup": [row]})
        self.assertEqual(0, self.store.db.execute("SELECT COUNT(*) FROM teams").fetchone()[0])

    def test_country_translations_do_not_spread_to_clubs_or_youth_teams(self):
        for language, label in [("en", "Finland"), ("fi", "Suomi"), ("de", "Finnland")]:
            job = self.job("cldr-labels", "all", f"/{language}/territories.json", "cldr", context={"language": language})
            complete(self.store, job, {"main": {language: {"localeDisplayNames": {"territories": {"FI": label, "001": "World"}}}}})
        for team_id, name, national in [("1", "Finland", True), ("2", "Finland U21", True), ("3", "Finland", False), ("4", "Finland Women", True)]:
            self.store.team("api-sports", "football", team_id, name, "https://provider.example", national=national)
            team = self.store.db.execute("SELECT * FROM teams WHERE team_id=?", (team_id,)).fetchone()
            aliases = [row["name"] for row in alias_candidates(self.store, team) if row["kind"] == "national-translation"]
            self.assertCountEqual(["Finland", "Suomi", "Finnland"] if team_id == "1" else [], aliases)

    def test_shared_names_do_not_merge_clubs_or_auto_approve_links(self):
        for team_id in ("1", "2"):
            self.store.team("api-sports", "football", team_id, "Fiction United", "https://provider.example", country="Finland")
        self.store.team("thesportsdb", "football", "11", "Fiction United", "https://provider.example", country="Finland")
        build_links(self.store)
        self.assertEqual(2, self.store.db.execute("SELECT COUNT(*) FROM links").fetchone()[0])
        self.assertEqual({"candidate"}, {row[0] for row in self.store.db.execute("SELECT status FROM links")})

    def test_direct_ids_link_only_football_and_conflicts_remain_candidates(self):
        self.store.team("api-sports", "football", "1", "Fiction FC", "https://provider.example", country="Finland")
        self.store.team("thesportsdb", "football", "11", "Fiction Club", "https://provider.example", country="Finland", api_football_id="1")
        build_links(self.store)
        self.assertEqual("provider-id", self.store.db.execute("SELECT status FROM links").fetchone()[0])
        self.store.team("thesportsdb", "football", "12", "Fiction Reserves", "https://provider.example", country="Finland", api_football_id="1")
        build_links(self.store)
        self.assertEqual({"candidate"}, {row[0] for row in self.store.db.execute("SELECT status FROM links")})

    def test_export_excludes_unreviewed_aliases_and_keeps_ids_separate(self):
        for sport in ("football", "hockey"):
            self.store.team("api-sports", sport, "1", "Finland", "https://provider.example", national=True)
            self.store.name("api-sports", sport, "1", "Suomi", "alternate", "https://provider.example")
        destination = Path(self.directory.name) / "reviewed.jsonl"
        self.assertEqual(2, export_reviewed(self.store, destination))
        exported = [json.loads(line) for line in destination.read_text().splitlines()]
        self.assertTrue(all(row["aliases"] == [] for row in exported))

    def test_rejected_link_revokes_previously_accepted_alias_exports(self):
        self.store.team("api-sports", "football", "1", "Fiction FC", "https://provider.example")
        self.store.team("thesportsdb", "football", "11", "Fiction Club", "https://provider.example")
        self.store.db.execute("INSERT INTO links VALUES ('football','1','11','accepted','manual')")
        self.store.db.execute("INSERT INTO alias_reviews VALUES ('football','1','Fiction Club','https://provider.example','und','accepted')")
        destination = Path(self.directory.name) / "reviewed.jsonl"
        export_reviewed(self.store, destination)
        self.assertEqual(1, len(json.loads(destination.read_text())["aliases"]))
        self.store.db.execute("UPDATE links SET status='rejected'")
        export_reviewed(self.store, destination)
        self.assertEqual([], json.loads(destination.read_text())["aliases"])

    def test_keyset_walk_does_not_drop_teams_after_first_thousand(self):
        from team_dictionary.research import api_teams
        for number in range(2001):
            self.store.team("api-sports", "football", str(number + 1), f"Fiction FC {number}", "https://provider.example")
        self.assertEqual(2001, len({row["team_id"] for row in api_teams(self.store)}))

    def test_enabled_adapter_does_not_claim_verification_or_completion(self):
        summary = reports(self.store, Path(self.directory.name) / "reports")
        self.assertFalse(summary["complete_roster_claim"])
        self.assertEqual("enabled", summary["sports"]["basketball"]["adapter"])
        self.assertEqual(0, summary["sports"]["basketball"]["verified_requests"])
        self.assertEqual(8, len(summary["languages"]))


if __name__ == "__main__":
    unittest.main()
