"""TheSportsDB's actual no-data envelope must not stop valid enrichment."""

import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from sports_dictionary import collect, parser
from team_dictionary.http import Client, CollectionError, RateLimited
from team_dictionary.providers import complete
from team_dictionary.store import Store
from team_dictionary.test_collection import Opener, Response


class TsdbResponsesTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.store = Store(Path(self.directory.name) / "research.sqlite")

    def tearDown(self):
        self.store.close()
        self.directory.cleanup()

    def job(self, kind="tsdb-team-list", endpoint="/list/teams/1"):
        self.store.enqueue("thesportsdb", "football", endpoint, kind, scope="full", context={"sports": ["football"]})
        self.store.db.commit()
        return self.store.next_job("thesportsdb", "football", "full")

    def test_provider_message_no_data_is_an_empty_league_with_a_warning(self):
        job = self.job()
        complete(self.store, job, {"Message": "No data found"})
        saved = self.store.db.execute("SELECT state,rows_seen,note FROM jobs WHERE id=?", (job["id"],)).fetchone()
        self.assertEqual(("done", 0), tuple(saved[:2]))
        self.assertIn("empty league", saved["note"])
        self.assertIn("coverage", saved["note"])

    def test_three_no_data_leagues_do_not_stop_the_next_team_detail(self):
        for number, sport in enumerate(("football", "hockey", "basketball"), start=1):
            self.store.enqueue("thesportsdb", sport, "/list/teams/" + str(number), "tsdb-team-list", scope="full")
        self.store.enqueue("thesportsdb", "football", "/lookup/team/4", "tsdb-team-detail")
        self.store.db.commit()
        opener = Opener([Response({"all": []})] + [Response({"Message": "No data found"}) for _ in range(3)] +
                        [Response({"lookup": [{"idTeam": "4", "strTeam": "Fiction FC", "strSport": "Soccer"}]})])
        args = parser().parse_args(["--data-dir", self.directory.name, "collect", "--providers", "thesportsdb",
                                   "--sports", "football", "hockey", "basketball"])
        clock = [1000.0]

        def factory(store, **kwargs):
            return Client(store, **kwargs, opener=opener, clock=lambda: clock[0],
                          sleep=lambda delay: clock.__setitem__(0, clock[0] + delay))

        with patch("sports_dictionary.Client", side_effect=factory):
            self.assertEqual(0, collect(args, ("", "fixture-key"), log=lambda _: None))
        self.assertEqual(5, len(opener.requests))
        self.assertEqual(0, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE state!='done'").fetchone()[0])
        self.assertEqual(1, self.store.db.execute("SELECT COUNT(*) FROM teams").fetchone()[0])

    def test_unknown_or_conflicting_messages_do_not_claim_an_empty_roster(self):
        job = self.job()
        for payload in ({"Message": "Invalid API key"}, {"Message": "No data found", "error": 401},
                        {"Message": "No data found", "other": []}, {"Message": "No data found fixture-key"}):
            with self.assertRaises(CollectionError):
                complete(self.store, job, payload)
            self.assertEqual("pending", self.store.db.execute("SELECT state FROM jobs WHERE id=?", (job["id"],)).fetchone()[0])

    def test_no_data_is_not_a_valid_global_league_index_or_team_profile(self):
        job = self.job("tsdb-leagues", "/all/leagues")
        with self.assertRaises(CollectionError):
            complete(self.store, job, {"Message": "No data found"})
        self.store.db.execute("UPDATE jobs SET state='blocked' WHERE id=?", (job["id"],))
        self.store.db.commit()
        job = self.job("tsdb-team-detail", "/lookup/team/1")
        complete(self.store, job, {"Message": "No data found"})
        self.assertEqual("blocked", self.store.db.execute("SELECT state FROM jobs WHERE id=?", (job["id"],)).fetchone()[0])
        self.assertEqual(0, self.store.db.execute("SELECT COUNT(*) FROM teams").fetchone()[0])

    def test_missing_profiles_are_coverage_gaps_and_do_not_stop_valid_profiles(self):
        for number in range(1, 5):
            self.store.enqueue("thesportsdb", "football", "/lookup/team/" + str(number), "tsdb-team-detail")
        self.store.db.commit()
        opener = Opener([Response({"all": []})] + [Response({"Message": "No data found"}) for _ in range(3)] +
                        [Response({"lookup": [{"idTeam": "4", "strTeam": "Fiction FC", "strSport": "Soccer"}]})])
        args = parser().parse_args(["--data-dir", self.directory.name, "collect", "--providers", "thesportsdb", "--sports", "football"])
        with patch("sports_dictionary.Client", side_effect=lambda store, **kwargs: Client(store, **kwargs, opener=opener, sleep=lambda _: None)):
            self.assertEqual(0, collect(args, ("", "fixture-key"), log=lambda _: None))
        self.assertEqual(5, len(opener.requests))
        self.assertEqual(3, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE state='blocked'").fetchone()[0])
        self.assertEqual(1, self.store.db.execute("SELECT COUNT(*) FROM teams").fetchone()[0])

    def test_message_auth_failure_is_a_redacted_service_error(self):
        opener = Opener([Response({"Message": "Invalid API key fixture-key https://provider.example/private"})])
        client = Client(self.store, tsdb_key="fixture-key", opener=opener)
        with self.assertRaises(CollectionError) as error:
            client.get("thesportsdb", "football", "/list/teams/1")
        self.assertIn("Invalid API key", str(error.exception))
        self.assertNotIn("fixture-key", str(error.exception))
        self.assertNotIn("https://provider.example", str(error.exception))

    def test_capital_message_rate_error_waits_instead_of_becoming_a_coverage_gap(self):
        opener = Opener([Response({"Message": "Rate Limit exceeded. Please keep below 100 requests per 1 minute.", "error": 429})])
        client = Client(self.store, tsdb_key="fixture-key", opener=opener)
        with self.assertRaises(RateLimited):
            client.get("thesportsdb", "football", "/list/teams/1")

    def test_saved_no_data_checksums_recover_only_selected_league_requests(self):
        from team_dictionary.providers import recover_tsdb_empty_lists
        for sport, endpoint, kind, digest in (
            ("football", "/list/teams/1", "tsdb-team-list", hashlib.sha256(b'{"Message":"No data found"}').hexdigest()),
            ("football", "/list/teams/2", "tsdb-team-list", "unrecognised-digest"),
            ("hockey", "/list/teams/3", "tsdb-team-list", hashlib.sha256(json.dumps({"Message": "No data found"}, indent=2).encode()).hexdigest()),
            ("football", "/lookup/team/4", "tsdb-team-detail", hashlib.sha256(b'{"Message":"No data found"}').hexdigest()),
        ):
            self.store.enqueue("thesportsdb", sport, endpoint, kind, scope="full")
            self.store.db.execute("UPDATE jobs SET state='blocked',attempts=2,digest=? WHERE sport=? AND endpoint=?", (digest, sport, endpoint))
        self.store.db.commit()
        self.store.reserve("thesportsdb", "all", 1000, reserve=0)
        self.assertEqual(1, recover_tsdb_empty_lists(self.store, ("football",), "full"))
        self.assertEqual(0, recover_tsdb_empty_lists(self.store, ("football",), "full"))
        self.assertEqual(1, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE state='done'").fetchone()[0])
        self.assertEqual(1, self.store.db.execute("SELECT attempts FROM usage").fetchone()[0])
        self.assertTrue(all(row[0] == 2 for row in self.store.db.execute("SELECT attempts FROM jobs")))
        self.assertEqual(1, recover_tsdb_empty_lists(self.store, ("hockey",), "full"))


if __name__ == "__main__":
    unittest.main()
