"""Serves the synthetic fixture over HTTP/1.1 on the host's loopback interface only.

Bound to 127.0.0.1, never to the network: the emulator reaches it through its fixed alias
10.0.2.2, and nothing else can. Every logo address maps onto one of the 40 generated images, so
each channel's logo is a separate download and decode, as with a real provider.

    python tools/fixture/serve_fixture.py            # port 8780, harness-out/fixture

Port 8765 is left to the old app's harness, which may be running on the same machine.
"""

from __future__ import annotations

import argparse
import hashlib
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class ExclusiveServer(ThreadingHTTPServer):
    # Python sets SO_REUSEADDR, which on Windows lets a second server share a port silently and
    # answer some of the requests. Fail instead when the port is taken.
    allow_reuse_address = False


class FixtureHandler(SimpleHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self) -> None:  # noqa: N802 (http.server naming)
        if self.path.startswith("/logo/"):
            digest = int(hashlib.sha1(self.path.encode()).hexdigest(), 16)
            self.path = f"/logos/logo{digest % 40:02d}.png"
        super().do_GET()

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
