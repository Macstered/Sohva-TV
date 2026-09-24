"""Serves the synthetic fixture over HTTP/1.1 on the host's loopback interface only.

Bound to 127.0.0.1, never to the network: the emulator reaches it through its fixed alias
10.0.2.2, and nothing else can. Every logo address maps onto one of the 40 generated images, so
each channel's logo is a separate download and decode, as with a real provider. The Xtream
variant answers `player_api.php` and `xmltv.php` for the account `fixture` / `fixture` from `xtream/`.

    python tools/fixture/serve_fixture.py            # port 8780, harness-out/fixture

Port 8765 is left to the old app's harness, which may be running on the same machine.
"""

from __future__ import annotations

import argparse
import hashlib
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

ROOT = Path(__file__).resolve().parents[2]
# The fictional Xtream account of the fixture (make_fixture.py XTREAM_ACCOUNT).
XTREAM_ACCOUNT = "fixture"


class ExclusiveServer(ThreadingHTTPServer):
    # Python sets SO_REUSEADDR, which on Windows lets a second server share a port silently and
    # answer some of the requests. Fail instead when the port is taken.
    allow_reuse_address = False


class FixtureHandler(SimpleHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self) -> None:  # noqa: N802 (http.server naming)
        url = urlsplit(self.path)
        if url.path.startswith("/logo/"):
            digest = int(hashlib.sha1(url.path.encode()).hexdigest(), 16)
            self.path = f"/logos/logo{digest % 40:02d}.png"
        elif url.path in ("/player_api.php", "/xmltv.php"):
            query = parse_qs(url.query)
            if query.get("username") != [XTREAM_ACCOUNT] or query.get("password") != [XTREAM_ACCOUNT]:
                self.path = "/xtream/denied.json"
            elif url.path == "/xmltv.php":
                self.path = "/guide.xml.gz"
            else:
                action = query.get("action", ["account"])[0]
                # Only the actions the generator wrote; anything else is a 404 as from a real panel.
                self.path = f"/xtream/{action}.json" if action.replace("_", "").isalnum() else "/missing"
        super().do_GET()

    def guess_type(self, path: str) -> str:  # noqa: D102
        # The guide is served gzip-compressed as a plain body: the importer sniffs the magic bytes.
        return "application/octet-stream" if str(path).endswith(".gz") else super().guess_type(path)

    def log_message(self, format: str, *args: object) -> None:  # noqa: A002
        pass  # quiet: a guide import requests thousands of logos


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", type=Path, default=ROOT / "harness-out" / "fixture")
    parser.add_argument("--port", type=int, default=8780)
    args = parser.parse_args()
    if not (args.dir / "playlist.m3u").is_file():
        raise SystemExit(f"No fixture in {args.dir}; run tools/fixture/make_fixture.py first")
    server = ExclusiveServer(("127.0.0.1", args.port), partial(FixtureHandler, directory=str(args.dir)))
    print(f"serving {args.dir} on http://127.0.0.1:{args.port} (emulator: http://10.0.2.2:{args.port})")
    server.serve_forever()


if __name__ == "__main__":
    main()
