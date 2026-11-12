"""Regression cases for incomplete records that previously stopped whole products."""

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from team_dictionary.http import Client, CollectionError
from team_dictionary.providers import complete
from team_dictionary.research import reports
from team_dictionary.store import Store
from team_dictionary.test_collection import Opener, Response


class RecordGapsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.path = Path(self.directory.name) / "research.sqlite"
        self.store = Store(self.path)

    def tearDown(self):
        self.store.close()
        self.directory.cleanup()

    def roster(self, sport="nba"):
        self.store.enqueue("api-sports", sport, "/teams", "api-teams", scope="full")
        self.store.db.commit()
        return self.store.next_job("api-sports", sport, "full")

    def test_unnamed_records_keep_valid_teams_and_queue_bounded_id_recovery(self):
        job = self.roster()
        payload = {"results": 4, "response": [{"id": 1, "name": "Fiction One"},
                   {"id": 2, "name": None}, {"id": 3, "name": "  "},
                   {"id": 4, "name": "Fiction Four"}]}
        complete(self.store, job, payload)
        complete(self.store, job, payload)
        self.assertEqual(2, self.store.db.execute("SELECT COUNT(*) FROM teams").fetchone()[0])
        gaps = self.store.db.execute("SELECT entity_id FROM record_gaps WHERE resolved_at IS NULL ORDER BY entity_id").fetchall()
        self.assertEqual(["2", "3"], [row[0] for row in gaps])
        self.assertEqual(2, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE kind='api-team-detail'").fetchone()[0])
        saved = self.store.db.execute("SELECT state,rows_seen,note FROM jobs WHERE id=?", (job["id"],)).fetchone()
        self.assertEqual(("done", 2), tuple(saved[:2]))
        self.assertIn("quarantined", saved["note"])
        report = reports(self.store, Path(self.directory.name))
        self.assertEqual(2, report["sports"]["nba"]["unresolved_records"])
        self.assertFalse(report["complete_roster_claim"])
        self.assertTrue((Path(self.directory.name) / "record_gaps.csv").exists())

    def test_a_second_unnamed_id_response_is_parked_without_an_endless_retry(self):
        complete(self.store, self.roster(), {"response": [{"id": 2, "name": None}]})
        job = self.store.next_job("api-sports", "nba", "full")
        complete(self.store, job, {"response": [{"id": 2, "name": None}]})
        self.assertIsNone(self.store.next_job("api-sports", "nba", "full"))
        self.assertEqual("blocked", self.store.db.execute("SELECT state FROM jobs WHERE id=?", (job["id"],)).fetchone()[0])
        self.assertEqual(1, self.store.db.execute("SELECT COUNT(*) FROM record_gaps WHERE resolved_at IS NULL").fetchone()[0])

    def test_verified_id_name_resolves_gap_and_a_wrong_id_cannot(self):
        complete(self.store, self.roster(), {"response": [{"id": 2, "name": None}]})
        job = self.store.next_job("api-sports", "nba", "full")
        with self.assertRaises(CollectionError):
            complete(self.store, job, {"response": [{"id": 3, "name": "Wrong club"}]})
        self.assertEqual(0, self.store.db.execute("SELECT COUNT(*) FROM teams").fetchone()[0])
        complete(self.store, job, {"response": [{"id": 2, "name": "Fiction Two"}]})
        self.assertEqual(0, self.store.db.execute("SELECT COUNT(*) FROM record_gaps WHERE resolved_at IS NULL").fetchone()[0])

    def test_redacted_or_wrong_type_names_still_roll_back_the_roster(self):
        job = self.roster("handball")
        for invalid in (42, ["Fiction"], "<redacted>"):
            with self.assertRaises(CollectionError):
                complete(self.store, job, {"response": [{"id": 1, "name": None}, {"id": 2, "name": invalid}]})
            self.assertEqual(0, self.store.db.execute("SELECT COUNT(*) FROM record_gaps").fetchone()[0])
            self.assertEqual(0, self.store.db.execute("SELECT COUNT(*) FROM teams").fetchone()[0])

    def test_missing_tsdb_lists_are_deferred_and_next_resume_does_not_repeat_them(self):
        from sports_dictionary import collect, parser
        for index, sport in enumerate(("football", "hockey", "afl"), start=1):
            self.store.enqueue("thesportsdb", sport, "/list/teams/" + str(index), "tsdb-team-list", scope="full")
        self.store.enqueue("thesportsdb", "basketball", "/lookup/team/4", "tsdb-team-detail")
        self.store.db.commit()
        args = parser().parse_args(["--data-dir", self.directory.name, "collect", "--providers", "thesportsdb",
                                   "--sports", "football", "hockey", "afl", "basketball", "--retry-errors"])
        # Complete the index so only the partition requests are exercised.
        self.store.enqueue("thesportsdb", "+".join(sorted(args.sports)), "/all/leagues", "tsdb-leagues", scope="full")
        self.store.db.execute("UPDATE jobs SET state='done' WHERE kind='tsdb-leagues'")
        self.store.db.commit()
        opener = Opener([Response({}), Response({"status": "success"}), Response({"teams": None})])
        clock = [1000.0]

        def client_factory(store, **kwargs):
            return Client(store, **kwargs, opener=opener, clock=lambda: clock[0],
                          sleep=lambda delay: clock.__setitem__(0, clock[0] + delay))

        logs = []
        with patch("sports_dictionary.Client", side_effect=client_factory):
            collect(args, ("", "fixture-key"), log=logs.append)
        self.assertEqual(3, self.store.db.execute("SELECT COUNT(*) FROM jobs WHERE state='blocked'").fetchone()[0])
        self.assertTrue(any("expected 'list'" in message for message in logs))
        # Reusing retry-errors must skip these gaps and advance to healthy detail requests.
        opener.responses = [Response({"lookup": [{"idTeam": "4", "strTeam": "Fiction Four", "strSport": "Basketball"}]})]
        with patch("sports_dictionary.Client", side_effect=client_factory):
            self.assertEqual(0, collect(args, ("", "fixture-key"), log=logs.append))
        self.assertEqual(4, len(opener.requests))
        self.assertEqual(1, self.store.db.execute("SELECT COUNT(*) FROM teams").fetchone()[0])

    def test_unknown_response_keys_or_values_are_not_logged(self):
        self.store.enqueue("thesportsdb", "nba", "/list/teams/1", "tsdb-team-list", scope="full")
        self.store.db.commit()
        job = self.store.next_job("thesportsdb", "nba", "full")
        for payload in ({"fixture-key": "fixture-key"}, {"list": ["fixture-key"]}):
            with self.assertRaises(CollectionError) as error:
                complete(self.store, job, payload)
            self.assertNotIn("fixture-key", str(error.exception))
            self.assertIn("expected 'list'", str(error.exception))

    def test_empty_id_lookup_retains_gap_and_known_names_are_never_erased(self):
        self.store.team("api-sports", "nba", "2", "Previously verified", "https://provider.example")
        complete(self.store, self.roster(), {"response": [{"id": 2, "name": None}]})
        detail = self.store.next_job("api-sports", "nba", "full")
        complete(self.store, detail, {"response": []})
        self.assertEqual("Previously verified", self.store.db.execute("SELECT name FROM teams").fetchone()[0])
        self.assertEqual("blocked", self.store.db.execute("SELECT state FROM jobs WHERE id=?", (detail["id"],)).fetchone()[0])

    def test_unnamed_mma_gym_still_discovers_its_fighters(self):
        job = self.roster("mma")
        complete(self.store, job, {"response": [{"id": 2, "name": None}]})
        fighters = self.store.db.execute("SELECT params FROM jobs WHERE kind='mma-fighters'").fetchone()
        self.assertEqual({"team": "2"}, json.loads(fighters["params"]))

    def test_gap_recheck_does_not_requeue_inaccessible_seasons_or_other_sports(self):
        from sports_dictionary import collect, parser
        for provider, sport, endpoint, kind, params in (
            ("api-sports", "nba", "/teams", "api-team-detail", {"id": "2"}),
            ("api-sports", "hockey", "/teams", "api-teams", {"season": "2026"}),
            ("thesportsdb", "nba", "/list/teams/2", "tsdb-team-list", {}),
            ("thesportsdb", "football", "/list/teams/3", "tsdb-team-list", {}),
        ):
            self.store.enqueue(provider, sport, endpoint, kind, params, scope="full")
        self.store.db.execute("UPDATE jobs SET state='blocked'")
        self.store.db.commit()
        args = parser().parse_args(["--data-dir", self.directory.name, "collect", "--sports", "nba",
                                   "--providers", "api-sports", "thesportsdb", "--recheck-record-gaps"])
        collect(args, ("fixture-key", "fixture-key"), log=lambda _: None, cancelled=lambda: True)
        states = {tuple(row[:2]): row[2] for row in self.store.db.execute("SELECT provider,sport,state FROM jobs WHERE kind!='tsdb-leagues'")}
        self.assertEqual("pending", states[("api-sports", "nba")])
        self.assertEqual("pending", states[("thesportsdb", "nba")])
        self.assertEqual("blocked", states[("api-sports", "hockey")])
        self.assertEqual("blocked", states[("thesportsdb", "football")])

    def test_schema_three_migration_keeps_data_and_adds_only_gap_tracking(self):
        self.store.team("api-sports", "nba", "2", "Fiction Two", "https://provider.example")
        self.store.entity("api-sports", "mma", "fighter", "2", "Fiction Fighter", "https://provider.example")
        self.store.reserve("api-sports", "mma", 80)
        self.roster()
        self.store.db.executescript("DROP TABLE record_gaps; PRAGMA user_version=3;")
        self.store.close()
        self.store = Store(self.path)
        self.assertEqual(5, self.store.db.execute("PRAGMA user_version").fetchone()[0])
        self.assertEqual("Fiction Two", self.store.db.execute("SELECT name FROM teams").fetchone()[0])
        self.assertEqual("Fiction Fighter", self.store.db.execute("SELECT name FROM entities").fetchone()[0])
        self.assertEqual(1, self.store.db.execute("SELECT attempts FROM usage").fetchone()[0])
        self.assertEqual(0, self.store.db.execute("SELECT COUNT(*) FROM record_gaps").fetchone()[0])
        self.assertEqual("pending", self.store.next_job("api-sports", "nba", "full")["state"])

    def test_gui_batches_use_fresh_processes_and_never_persist_pipe_credentials(self):
        from sports_dictionary import parser
        from team_dictionary.session import batch_command, run_batch
        import subprocess
        for _ in range(80):
            self.store.reserve("api-sports", "nba", 80)
        args = parser().parse_args(["--data-dir", self.directory.name, "collect", "--sports", "nba", "--providers", "api-sports"])
        keys = ("fictional-session-secret", "")
        self.assertNotIn(keys[0], " ".join(batch_command(args)))
        logs = []
        with patch("team_dictionary.session.subprocess.Popen", wraps=subprocess.Popen) as processes:
            for _ in range(2):
                self.assertEqual(0, run_batch(args, keys, logs.append, lambda: False))
        self.assertEqual(2, processes.call_count)
        self.assertTrue(any("Daily collection budget" in line for line in logs))
        self.assertFalse(any(keys[0] in line for line in logs))
        for file in Path(self.directory.name).iterdir():
            if file.is_file():
                self.assertNotIn(keys[0].encode(), file.read_bytes())
        self.assertEqual(80, self.store.db.execute("SELECT attempts FROM usage").fetchone()[0])

    def test_pipe_cancellation_is_observed_without_echoing_session_input(self):
        from team_dictionary.session import collect_from_pipe
        from unittest.mock import Mock
        import threading
        data = json.dumps(["fictional-session-secret", ""]).encode() + b"\n"
        reads = iter([bytes([byte]) for byte in data] + [b"cancel\n"])
        stdin = Mock()
        stdin.isatty.return_value = False
        stdin.fileno.return_value = 123

        def collect(args, keys, cancelled):
            self.assertEqual(("fictional-session-secret", ""), keys)
            event = threading.Event()
            for _ in range(50):
                if cancelled():
                    return 130
                event.wait(0.01)
            self.fail("pipe cancellation was not delivered")

        with patch("team_dictionary.session.sys.stdin", stdin), patch("team_dictionary.session.os.read", side_effect=lambda *_: next(reads)):
            self.assertEqual(130, collect_from_pipe(None, collect))


if __name__ == "__main__":
    unittest.main()
