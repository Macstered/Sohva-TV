"""Fresh code for each GUI batch; credentials cross only an anonymous local pipe."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import threading
from pathlib import Path

from .http import CollectionError


def batch_command(args) -> list[str]:
    executable = Path(sys.executable)
    if os.name == "nt" and executable.name.lower() == "pythonw.exe":
        executable = executable.with_name("python.exe")
    script = Path(__file__).resolve().parents[1] / "sports_dictionary.py"
    command = [str(executable), "-X", "utf8", "-u", str(script), "--data-dir", str(args.data_dir), args.command,
               "--providers", *args.providers, "--sports", *args.sports,
               "--api-daily-cap", str(args.api_daily_cap), "--api-per-run", str(args.api_per_run),
               "--tsdb-per-run", str(args.tsdb_per_run), "--session-input"]
    for flag in ("retry_errors", "retry_blocked", "recheck_record_gaps"):
        if getattr(args, flag, False):
            command.append("--" + flag.replace("_", "-"))
    return command


def run_batch(args, keys: tuple[str, str], log, cancelled) -> int:
    # A new interpreter loads the current sources. Keys never appear in argv, the
    # environment or a file. The GUI retains them only until Change keys or Close.
    options = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}
    with subprocess.Popen(batch_command(args), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                          stderr=subprocess.DEVNULL, text=True, encoding="utf-8", **options) as process:
        process.stdin.write(json.dumps(keys) + "\n")
        process.stdin.flush()
        finished = threading.Event()

        def cancellation() -> None:
            while not finished.wait(0.2):
                if cancelled():
                    try:
                        process.stdin.write("cancel\n")
                        process.stdin.flush()
                    except (OSError, ValueError):
                        pass
                    return

        threading.Thread(target=cancellation, daemon=True).start()
        try:
            for line in process.stdout:
                # Defense in depth for GUI output, including unexpected future messages.
                for key in keys:
                    if key:
                        line = line.replace(key, "<redacted>")
                log(line.rstrip())
            return process.wait()
        finally:
            finished.set()
            process.stdin.close()


def collect_from_pipe(args, collect) -> int:
    if sys.stdin is None or sys.stdin.isatty():
        raise CollectionError("The key window's private session pipe is unavailable.")
    try:
        descriptor = sys.stdin.fileno()
        first_line = bytearray()
        for _ in range(4096):
            byte = os.read(descriptor, 1)
            if not byte or byte == b"\n":
                break
            first_line.extend(byte)
        else:
            raise ValueError("session size")
        keys = json.loads(first_line)
    except (OSError, ValueError, UnicodeError):
        raise CollectionError("The key window's private session could not be read.") from None
    if not isinstance(keys, list) or len(keys) != 2 or any(
        not isinstance(key, str) or len(key) > 512 or "\n" in key or "\r" in key for key in keys
    ):
        raise CollectionError("The key window's private session is invalid.")
    cancelled = threading.Event()

    def cancellation() -> None:
        # Closing the parent also closes the pipe; stop after the current request.
        # A raw descriptor avoids buffered-stdin locks during interpreter shutdown.
        try:
            os.read(descriptor, 16)
        except OSError:
            pass
        cancelled.set()

    threading.Thread(target=cancellation, daemon=True).start()
    return collect(args, tuple(keys), cancelled=cancelled.is_set)
