"""Sourced alias proposals must preserve national categories and identity boundaries."""

import json
import io
import tempfile
import unittest
from pathlib import Path

from team_dictionary.store import Store, normalise
from team_dictionary.research import alias_candidates, build_links, reports
from team_dictionary.http import CollectionError
from team_dictionary.test_collection import Opener, Response


class EnrichmentTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.path = Path(self.directory.name)
        self.store = Store(self.path / "research.sqlite")
        for code, language, name in (("FI", "en", "Finland"), ("FI", "fi", "Suomi"),
                                     ("DE", "en", "Germany"), ("DE", "fi", "Saksa"), ("DE", "de", "Deutschland"),
                                     ("GB", "en", "United Kingdom"), ("GB-ENG", "en", "England"), ("GB-ENG", "fi", "Englanti"),
                                     ("US", "en", "United States"), ("US", "fi", "Yhdysvallat")):
            self.store.db.execute("INSERT INTO country_labels VALUES (?,?,?,?,?)", (code, language, name, normalise(name), "https://provider.example/" + language))
        self.store.db.commit()

    def tearDown(self):
        self.store.close()
        self.directory.cleanup()

    def team(self, identity, name, country="Finland", national=True, sport="football", gender=""):
        self.store.team("api-sports", sport, identity, name, "https://provider.example/teams", country=country, national=national, gender=gender)
        return self.store.db.execute("SELECT * FROM teams WHERE provider='api-sports' AND sport=? AND team_id=?", (sport, identity)).fetchone()

    def test_qualified_country_translations_never_become_bare_national_names(self):
        for identity, name, expected in (("1", "Finland U21", "Suomi U21"), ("2", "Finland W", "Suomi W"),
                                         ("3", "Finland U19 W", "Suomi U19 W"), ("4", "Finland Women", "Suomi Women")):
            aliases = list(alias_candidates(self.store, self.team(identity, name)))
            translations = [row for row in aliases if row["kind"].startswith("national-")]
            self.assertIn(expected, [row["name"] for row in translations])
            self.assertNotIn("Suomi", [row["name"] for row in translations])
            self.assertTrue(all(row["identity_status"] == "qualified-country-name" for row in translations))

    def test_club_flags_country_conflicts_and_unknown_qualifiers_do_not_gain_translations(self):
        for identity, name, country, national in (("1", "Finland", "Finland", False), ("2", "Hakusan", "USA", True),
                                                 ("3", "Finland", "Germany", True), ("4", "Finland Academy", "Finland", True)):
            aliases = list(alias_candidates(self.store, self.team(identity, name, country, national)))
            self.assertFalse(any(row["kind"].startswith("national-") for row in aliases))
        aliases = list(alias_candidates(self.store, self.team("5", "Finland", gender="Female")))
        self.assertFalse(any(row["kind"].startswith("national-") for row in aliases))

    def test_sporting_countries_use_their_own_subdivision_labels(self):
        england = list(alias_candidates(self.store, self.team("1", "England U21", "England")))
        self.assertIn("Englanti U21", [row["name"] for row in england])
        self.assertNotIn("United Kingdom U21", [row["name"] for row in england])
        britain = list(alias_candidates(self.store, self.team("2", "Great Britain", "United Kingdom")))
        self.assertFalse(any(row["kind"].startswith("national-") for row in britain))

    def test_iso_three_letter_country_names_resolve_without_inferring_from_club_country(self):
        self.store.db.execute("INSERT INTO country_codes VALUES ('US','USA','https://provider.example/codes')")
        aliases = list(alias_candidates(self.store, self.team("1", "USA U23", "USA")))
        self.assertIn("Yhdysvallat U23", [row["name"] for row in aliases])
        aliases = list(alias_candidates(self.store, self.team("2", "Fiction Club", "USA")))
        self.assertFalse(any(row["kind"].startswith("national-") for row in aliases))

    def test_curated_nicknames_require_one_matching_sport_and_category(self):
        from team_dictionary.national import seed_nicknames
        self.team("1", "Finland")
        self.team("2", "Finland W")
        self.team("3", "Finland U21")
        self.team("4", "Finland", sport="hockey")
        seed_nicknames(self.store, ("football", "hockey"))
        names = {(row[0], row[1]) for row in self.store.db.execute("SELECT team_id,name FROM names WHERE kind='federation-nickname'")}
        self.assertIn(("1", "Huuhkajat"), names)
        self.assertIn(("2", "Helmarit"), names)
        self.assertIn(("4", "Leijonat"), names)
        self.assertNotIn(("3", "Huuhkajat"), names)

    def test_detail_priority_helps_relevant_aliases_without_approving_identity_links(self):
        from team_dictionary.enrichment import prioritise_details
        self.team("1", "Fiction FC", national=False)
        self.store.team("thesportsdb", "football", "11", "Fiction FC", "https://provider.example", country="Finland")
        self.store.enqueue("thesportsdb", "football", "/list/teams/1", "tsdb-team-list", scope="full")
        self.store.enqueue("thesportsdb", "football", "/lookup/team/11", "tsdb-team-detail")
        self.store.db.commit()
        build_links(self.store)
        self.assertEqual(1, prioritise_details(self.store, ("football",)))
        self.assertEqual("tsdb-team-detail", self.store.next_job("thesportsdb", "football", "full")["kind"])
        self.assertEqual("candidate", self.store.db.execute("SELECT status FROM links").fetchone()[0])
        self.assertEqual(0, self.store.db.execute("SELECT COUNT(*) FROM alias_reviews").fetchone()[0])

    def test_shared_identity_candidates_or_known_country_conflicts_are_not_prioritised(self):
        from team_dictionary.enrichment import prioritise_details
        self.team("1", "Fiction United", national=False)
        self.team("2", "Fiction United", national=False)
        self.store.team("thesportsdb", "football", "11", "Fiction United", "https://provider.example", country="Finland")
        self.store.team("thesportsdb", "football", "12", "Fiction United", "https://provider.example", country="Germany")
        for identity in ("11", "12"):
            self.store.enqueue("thesportsdb", "football", "/lookup/team/" + identity, "tsdb-team-detail")
        self.store.db.commit()
        build_links(self.store)
        self.assertEqual(0, prioritise_details(self.store, ("football",)))

    def test_review_report_counts_api_id_collisions_and_preserves_language_evidence(self):
        from team_dictionary.enrichment import alias_reports
        self.team("1", "Finland")
        self.team("2", "Fiction Club", national=False)
        for identity in ("1", "2"):
            self.store.name("api-sports", "football", identity, "Shared Nickname", "nickname", "https://provider.example")
        summary = alias_reports(self.store, self.path)
        self.assertEqual(1, summary["collision_groups"])
        self.assertEqual(0, summary["approved_aliases"])
        proposals = [json.loads(line) for line in (self.path / "aliases.proposed.jsonl").read_text(encoding="utf-8").splitlines()]
        finland = next(row for row in proposals if row["team_id"] == "1")
        suomi = next(row for row in finland["aliases"] if row["name"] == "Suomi")
        self.assertEqual("fi", suomi["language"])
        self.assertEqual("pending", suomi["review_status"])
        shared = next(row for row in finland["aliases"] if row["name"] == "Shared Nickname")
        self.assertIn("shared-name", shared["risks"])

    def test_schema_four_migration_preserves_existing_sources_and_quota_history(self):
        self.team("1", "Finland")
        self.store.reserve("api-sports", "football", 80)
        self.store.db.executescript("DROP TABLE country_codes; PRAGMA user_version=4;")
        self.store.close()
        self.store = Store(self.path / "research.sqlite")
        self.assertEqual(5, self.store.db.execute("PRAGMA user_version").fetchone()[0])
        self.assertEqual(1, self.store.db.execute("SELECT COUNT(*) FROM teams").fetchone()[0])
        self.assertEqual(1, self.store.db.execute("SELECT attempts FROM usage").fetchone()[0])
        self.assertEqual(10, self.store.db.execute("SELECT COUNT(*) FROM country_labels").fetchone()[0])

    def test_gender_conflicts_and_missing_womens_qualifiers_remain_gaps(self):
        for identity, name, gender in (("1", "Finland U21", "Female"), ("2", "Finland W", "Male")):
            aliases = alias_candidates(self.store, self.team(identity, name, gender=gender))
            self.assertFalse(any(row["kind"].startswith("national-") for row in aliases))

    def test_country_conflict_alone_prevents_detail_priority(self):
        from team_dictionary.enrichment import prioritise_details
        self.team("1", "Fiction FC", national=False)
        self.store.team("thesportsdb", "football", "11", "Fiction FC", "https://provider.example", country="Germany")
        self.store.enqueue("thesportsdb", "football", "/lookup/team/11", "tsdb-team-detail", context={"alias_priority": True})
        self.store.db.commit()
        build_links(self.store)
        self.assertEqual(0, prioritise_details(self.store, ("football",)))
        self.assertNotIn("alias_priority", json.loads(self.store.next_job("thesportsdb", "football")["context"]))

    def test_alias_colliding_with_another_canonical_name_is_reported(self):
        from team_dictionary.enrichment import alias_reports
        self.team("1", "Fiction FC", national=False)
        self.team("2", "Fiction United", national=False)
        self.store.name("api-sports", "football", "1", "Fiction United", "nickname", "https://provider.example")
        self.assertEqual(1, alias_reports(self.store, self.path)["collision_groups"])

    def test_rejected_provider_link_removes_its_alias_evidence(self):
        self.team("1", "Fiction FC", national=False)
        self.store.team("thesportsdb", "football", "11", "Fiction FC", "https://provider.example")
        self.store.name("thesportsdb", "football", "11", "Fiction Alternate", "alternate", "https://provider.example")
        self.store.db.commit()
        first = reports(self.store, self.path)["aliases"]
        self.assertEqual(1, first["additional_spellings"])
        self.assertEqual(0, first["collision_groups"])
        with self.store.db:
            self.store.db.execute("UPDATE links SET status='rejected'")
        self.assertEqual(0, reports(self.store, self.path)["aliases"]["additional_spellings"])

    def test_public_source_refresh_is_bounded_and_preserves_paid_provider_quotas(self):
        from team_dictionary.national import refresh_translations
        from team_dictionary.providers import LANGUAGES
        codes = {"supplemental": {"codeMappings": {"FI": {"_alpha3": "FIN"}, "DE": {"_alpha3": "DEU"}, "XX": {"_alpha3": "XXX"}}}}
        responses = [Response(codes)]
        for language in LANGUAGES:
            xml = '<ldml><identity><language type="' + language + '"/></identity><localeDisplayNames><subdivisions>'
            xml += ''.join('<subdivision type="' + code + '">Fiction ' + code + '</subdivision>' for code in ("gbeng", "gbsct", "gbwls", "gbnir"))
            responses.append(io.BytesIO((xml + '</subdivisions></localeDisplayNames></ldml>').encode()))
        opener = Opener(responses)
        self.store.reserve("api-sports", "football", 80)
        self.store.reserve("thesportsdb", "all", 1000, reserve=0)
        before = [tuple(row) for row in self.store.db.execute("SELECT * FROM usage ORDER BY provider")]
        sleeps = []
        refresh_translations(self.store, log=lambda _: None, opener=opener, sleep=sleeps.append)
        self.assertEqual(9, len(opener.requests))
        self.assertEqual([1.0] * 8, sleeps)
        self.assertTrue(all(request.full_url.startswith("https://raw.githubusercontent.com/unicode-org/") for request in opener.requests))
        self.assertTrue(all("key" not in str(request.headers).casefold() for request in opener.requests))
        self.assertEqual(before, [tuple(row) for row in self.store.db.execute("SELECT * FROM usage WHERE provider!='cldr-enrichment' ORDER BY provider")])
        self.assertEqual(2, self.store.db.execute("SELECT COUNT(*) FROM country_codes").fetchone()[0])
        self.assertEqual(9, self.store.db.execute("SELECT attempts FROM usage WHERE provider='cldr-enrichment'").fetchone()[0])

    def test_bad_subdivision_source_does_not_import_a_partial_roster(self):
        from team_dictionary.national import import_subdivisions
        before = self.store.db.execute("SELECT COUNT(*) FROM country_labels").fetchone()[0]
        for raw in (b'not xml', b'<ldml><identity><language type="fi"/></identity><subdivision type="gbeng">Fiction England</subdivision></ldml>',
                    b'<ldml><identity><language type="de"/></identity></ldml>'):
            with self.assertRaises(CollectionError):
                import_subdivisions(self.store, "fi", raw)
            self.assertEqual(before, self.store.db.execute("SELECT COUNT(*) FROM country_labels").fetchone()[0])

    def test_detail_priority_uses_identity_index_instead_of_rescanning_a_roster(self):
        plan = self.store.db.execute(
            "EXPLAIN QUERY PLAN SELECT j.id,t.team_id FROM jobs j JOIN teams t "
            "ON t.provider='thesportsdb' AND t.sport=j.sport AND t.team_id=SUBSTR(j.endpoint,14) "
            "WHERE j.provider='thesportsdb' AND j.sport='football' AND j.state='pending' AND j.kind='tsdb-team-detail'").fetchall()
        detail = next(row[3] for row in plan if 'SEARCH t ' in row[3])
        self.assertIn('team_id=?', detail)

    def test_linked_alias_lookup_starts_with_one_identity_not_the_whole_provider_roster(self):
        team = self.team("1", "Finland")
        observed = []
        self.store.db.set_trace_callback(observed.append)
        list(alias_candidates(self.store, team))
        self.store.db.set_trace_callback(None)
        query = next(sql for sql in observed if 'SELECT n.*,l.status' in sql)
        plan = self.store.db.execute('EXPLAIN QUERY PLAN ' + query).fetchall()
        detail = next(row[3] for row in plan if 'SEARCH n ' in row[3])
        self.assertIn('team_id=?', detail)


if __name__ == "__main__":
    unittest.main()
