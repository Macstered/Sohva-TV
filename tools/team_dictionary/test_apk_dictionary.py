"""A database exception cannot smuggle research data into the release APK."""
import io
import json
import sqlite3
import zipfile
from verify_sport_dictionary import verify
from team_dictionary.test_compiler import CompilerTest


class ApkDictionaryTest(CompilerTest):
    def archive(self, overrides=None):
        content = io.BytesIO()
        with zipfile.ZipFile(content, "w") as archive:
            for path in (self.root / "asset").iterdir():
                archive.writestr("assets/" + path.name, (overrides or {}).get(path.name, path.read_bytes()))
        content.seek(0)
        return zipfile.ZipFile(content)

    def test_exact_compiler_asset_is_accepted(self):
        self.accepted()
        self.compile()
        with self.archive() as archive:
            self.assertEqual(3, len(verify(archive)))

    def test_research_table_is_rejected_even_with_a_matching_file_checksum(self):
        self.accepted()
        self.compile()
        path = self.root / "asset/sport-dictionary.db"
        db = sqlite3.connect(path)
        try:
            db.execute("CREATE TABLE usage (secret TEXT)")
            db.commit()
        finally:
            db.close()
        import hashlib
        manifest = json.loads((self.root / "asset/sport-dictionary.json").read_text())
        manifest.update(bytes=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest())
        with self.archive({"sport-dictionary.json": json.dumps(manifest).encode()}) as archive:
            with self.assertRaisesRegex(ValueError, "schema"):
                verify(archive)

    def test_changed_database_and_unsupported_manifest_are_rejected(self):
        self.accepted()
        self.compile()
        with self.archive({"sport-dictionary.db": b"fake"}) as archive:
            with self.assertRaises(ValueError):
                verify(archive)
        manifest = json.loads((self.root / "asset/sport-dictionary.json").read_text())
        manifest["normalisation"] = 99
        with self.archive({"sport-dictionary.json": json.dumps(manifest).encode()}) as archive:
            with self.assertRaisesRegex(ValueError, "Unsupported"):
                verify(archive)
