"""Reviewed-only deterministic output, disjoint dates and indexed hot queries."""

import json
import sqlite3
import unittest
from contextlib import closing
from pathlib import Path
from team_dictionary.store import normalise

from team_dictionary.compiler import compile_snapshot
from team_dictionary.test_registry import RegistryTest


class CompilerTest(RegistryTest):
    def compile(self):
        self.export()
        return compile_snapshot(self.root / "accepted.json", self.root / "asset")

    def test_deterministic_output_and_indexed_provider_alias_membership_queries(self):
        identity = self.accepted()
        first = self.compile()
        self.assertEqual(first, self.compile())
        db = sqlite3.connect(self.root / "asset/sport-dictionary.db")
        try:
            queries = (
                ("SELECT uuid FROM binding WHERE provider=? AND product=? AND kind=? AND external_id IN (?)", ("api-sports", "football", "team", "101")),
                ("SELECT normalised,valid_from,valid_to FROM alias WHERE uuid=? AND (normalised,valid_from,valid_to)>(?,?,?) ORDER BY normalised,valid_from,valid_to LIMIT 256", (identity, "", -719162, -719162)),
                ("SELECT participant FROM membership WHERE competition=? AND season=?", (identity, "2026")),
                ("SELECT competition,season,valid_from,valid_to FROM membership WHERE participant=? AND (competition,season,valid_from,valid_to)>(?,?,?,?) ORDER BY competition,season,valid_from,valid_to LIMIT 256", (identity, "", "", -719162, -719162)),
                ("SELECT qualified FROM guard WHERE sport=? AND kind=? AND base=?", ("football", "team", "northbridge")),
                ("SELECT DISTINCT a.uuid FROM alias a CROSS JOIN identity i ON i.uuid=a.uuid WHERE a.normalised=? AND i.sport=? AND i.kind=? AND a.valid_from<=? AND a.valid_to>=? LIMIT 2", ("northbridge", "football", "team", 0, 0)),
                ("SELECT base,qualified,valid_from,valid_to FROM guard WHERE sport=? AND kind=? AND base IN (?) AND (base,qualified,valid_from,valid_to)>(?,?,?,?) ORDER BY base,qualified,valid_from,valid_to LIMIT 256", ("football", "team", "northbridge", "", "", -719162, -719162)),
            )
            for query, args in queries:
                plan = " ".join(row[3] for row in db.execute("EXPLAIN QUERY PLAN " + query, args))
                self.assertIn("SEARCH", plan)
                self.assertNotIn("SCAN", plan)
                self.assertNotIn("TEMP B-TREE", plan)
        finally:
            db.close()

    def test_compiler_rejects_unreviewed_snapshot_instead_of_silently_shipping_it(self):
        self.accepted()
        data = self.export()
        data["aliases"][0]["status"] = "pending"
        (self.root / "accepted.json").write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Unreviewed"):
            compile_snapshot(self.root / "accepted.json", self.root / "asset")

    def test_guards_include_qualified_identity_without_a_current_fixture(self):
        self.accepted(name="Finland")
        youth = self.accepted(external="102", name="Finland U21")
        self.registry.review_identity(youth, "u21-men", "accepted", "Fixture youth source")
        manifest = self.compile()
        self.assertEqual(1, manifest["counts"]["guard"])
        with closing(sqlite3.connect(self.root / "asset/sport-dictionary.db")) as db:
            self.assertEqual(("finland", "finland u21"), db.execute("SELECT base,qualified FROM guard").fetchone())

    def test_oversized_group_requires_review(self):
        identity = self.accepted()
        for number in range(64):
            name = f"Fixture alias {number}"
            self.registry.propose_alias(identity, name, "en", "fixture", "Fixture")
            self.registry.review_alias(identity, name, "fixture", "accepted", "Fixture")
        with self.assertRaisesRegex(ValueError, "64 variants"):
            self.compile()

    def test_qualified_identity_cannot_borrow_the_reviewed_senior_spelling(self):
        self.accepted(name="Finland")
        youth = self.accepted(external="102", name="Finland U21")
        self.registry.review_identity(youth, "u21", "accepted", "Fixture youth source")
        self.registry.propose_alias(youth, "Finland", "en", "fixture", "Incorrect dropped qualifier")
        self.registry.review_alias(youth, "Finland", "fixture", "accepted", "Fixture deliberate invalid approval")
        with self.assertRaisesRegex(ValueError, "category"):
            self.compile()

    def test_shared_unicode_normalisation_goldens(self):
        resource = Path(__file__).resolve().parents[2] / "core/model/src/test/resources/sport-normalisation.tsv"
        for line in resource.read_text(encoding="utf-8").splitlines():
            if line and not line.startswith("#"):
                source, expected = line.split("\t")
                self.assertEqual(expected, normalise(source), source)


if __name__ == "__main__":
    unittest.main()
