"""Stable IDs and review exclusion without changing collection accounting."""

import json
import tempfile
import unittest
from pathlib import Path

from team_dictionary.registry import Registry
from team_dictionary.store import Store


class RegistryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        self.store = Store(self.root / "research.sqlite")
        self.store.team("api-sports", "football", "101", "Northbridge", "https://provider.example/teams")
        self.store.enqueue("api-sports", "football", "/teams", "api-teams")
        self.store.reserve("api-sports", "football", 80, day="2026-10-02")
        self.store.db.commit()
        self.registry = Registry(self.store.db)

    def tearDown(self):
        self.store.close()
        self.directory.cleanup()

    def accepted(self, product="football", external="101", name="Northbridge", kind="team"):
        identity = self.registry.identity("api-sports", product, kind, external, name, evidence="Fixture source")
        self.registry.review_identity(identity, "senior", "accepted", "Fixture identity checked")
        self.registry.review_binding("api-sports", product, kind, external, identity, "accepted", "Fixture ID checked")
        self.registry.credit("fixture", "Synthetic test data", "CC0-1.0", "accepted")
        self.registry.propose_alias(identity, name, "en", "fixture", "Fixture spelling")
        self.registry.review_alias(identity, name, "fixture", "accepted", "Fixture spelling checked")
        return identity

    def export(self):
        path = self.root / "accepted.json"
        self.registry.export(path)
        return json.loads(path.read_text(encoding="utf-8"))

    def test_seed_preserves_jobs_usage_and_uuids_across_reopen_and_rename(self):
        before = {table: list(map(tuple, self.store.db.execute("SELECT * FROM " + table))) for table in ("jobs", "usage")}
        self.registry.seed()
        identity = self.store.db.execute("SELECT uuid FROM dictionary_binding WHERE external_id='101'").fetchone()[0]
        self.store.team("api-sports", "football", "101", "Northbridge United", "https://provider.example/teams")
        self.store.db.commit()
        Registry(self.store.db).seed()
        self.assertEqual(identity, self.store.db.execute("SELECT uuid FROM dictionary_binding WHERE external_id='101'").fetchone()[0])
        self.assertEqual(before, {table: list(map(tuple, self.store.db.execute("SELECT * FROM " + table))) for table in before})
        self.assertEqual(5, self.store.db.execute("PRAGMA user_version").fetchone()[0])
        self.assertEqual([], self.export()["identities"])

    def test_two_reviewed_providers_resolve_to_one_identity_without_merging_equal_names(self):
        identity = self.accepted()
        other = self.registry.identity("fixture-provider", "football", "team", "202", "Northbridge", evidence="Fixture")
        self.assertNotEqual(identity, other)
        self.registry.review_binding("fixture-provider", "football", "team", "202", identity, "accepted", "Verified synthetic cross-provider ID")
        data = self.export()
        self.assertEqual({identity}, {row["uuid"] for row in data["bindings"]})
        self.assertEqual(2, len(data["bindings"]))
        self.assertEqual(1, len(data["aliases"]))

    def test_pending_and_rejected_aliases_and_bindings_never_export(self):
        identity = self.accepted()
        self.registry.propose_alias(identity, "The Wolves", "en", "fixture", "Unreviewed")
        self.registry.propose_alias(identity, "Wrong Wolves", "en", "fixture", "Wrong source")
        self.registry.review_alias(identity, "Wrong Wolves", "fixture", "rejected", "Wrong identity")
        self.registry.review_binding("fixture-provider", "football", "team", "303", identity, "pending", "Unverified")
        self.assertEqual(["Northbridge"], [row["spelling"] for row in self.export()["aliases"]])
        self.assertEqual(1, len(self.export()["bindings"]))

    def test_basketball_and_nba_ids_stay_separate_until_verified(self):
        basketball = self.accepted("basketball", "7")
        nba = self.accepted("nba", "7")
        self.assertNotEqual(basketball, nba)
        self.registry.review_binding("api-sports", "nba", "team", "7", basketball, "accepted", "Verified same synthetic club")
        self.assertEqual({basketball}, {row["uuid"] for row in self.export()["bindings"]})
        with self.assertRaises(ValueError):
            self.registry.review_binding("api-sports", "football", "team", "7", basketball, "accepted", "Different sport")

    def test_known_collisions_survive_a_small_accepted_slice(self):
        self.accepted()
        other = self.registry.identity("api-sports", "football", "team", "102", "Northbridge", category="senior", evidence="Fixture")
        self.registry.propose_alias(other, "Northbridge", "en", "fixture", "Different club")
        self.assertEqual(["northbridge"], [row["normalised"] for row in self.export()["collisions"]])

    def test_disjoint_validity_is_not_collapsed_and_multiple_competitions_are_allowed(self):
        team = self.accepted()
        for year in (2024, 2026):
            start, end = f"{year}-01-01", f"{year}-12-31"
            self.registry.propose_alias(team, "Old Town", "en", "fixture", "Dated source", start, end)
            self.registry.review_alias(team, "Old Town", "fixture", "accepted", "Dated source checked", start, end)
        for external in ("400", "401"):
            league = self.accepted(external=external, name="Fixture Cup " + external, kind="competition")
            self.registry.membership(team, league, "2026", "fixture", "Fixture roster", "accepted")
        data = self.export()
        self.assertEqual(2, len([row for row in data["aliases"] if row["spelling"] == "Old Town"]))
        self.assertEqual(2, len(data["memberships"]))

    def test_repeated_exports_are_byte_identical(self):
        self.accepted()
        self.export()
        first = (self.root / "accepted.json").read_bytes()
        self.export()
        self.assertEqual(first, (self.root / "accepted.json").read_bytes())


if __name__ == "__main__":
    unittest.main()
