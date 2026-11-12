"""Private local key entry and collection progress, with no saved credentials."""

from __future__ import annotations

import json
import queue
import threading
import tkinter as tk
from tkinter import ttk

from .progress import batch_state
from .sports import ALL_SPORTS
from .session import run_batch


def run_window(args) -> int:
    root = tk.Tk()
    root.title("Sohva TV — updated team dictionary — " + f"{len(args.sports)} sports")
    root.geometry("740x570")
    root.minsize(700, 500)
    frame = ttk.Frame(root, padding=20)
    frame.pack(fill="both", expand=True)
    ttk.Label(frame, text="Keys stay in memory until this window closes. Each Resume loads the latest collector code.", wraplength=650).pack(anchor="w")
    limits = tk.StringVar()

    def update_limits() -> None:
        limits.set(f"{args.command.title()}: {', '.join(args.sports)}. Up to {args.api_per_run} API-Sports calls per sport, "
                   f"{args.tsdb_per_run} TheSportsDB calls.\nReports are saved under .local/team-dictionary.")

    update_limits()
    ttk.Label(frame, textvariable=limits, wraplength=650).pack(anchor="w", pady=(8, 14))
    entries = {}
    for provider, label in (("api-sports", "API-Sports key"), ("thesportsdb", "TheSportsDB Premium key")):
        if provider in args.providers:
            ttk.Label(frame, text=label).pack(anchor="w")
            entry = ttk.Entry(frame, show="*", width=65)
            entry.pack(fill="x", pady=(3, 10))
            entries[provider] = entry
    status = tk.StringVar(value="Waiting for keys. No authenticated requests have been made.")
    ttk.Label(frame, textvariable=status, wraplength=650).pack(anchor="w", pady=8)
    messages = queue.Queue(maxsize=200)
    cancelled = threading.Event()
    session_keys: tuple[str, str] | None = None
    progress = tk.Text(frame, height=8, state="disabled", wrap="word")
    progress.pack(fill="both", expand=True, pady=8)

    def append(message: str) -> None:
        progress.configure(state="normal")
        progress.insert("end", message + "\n")
        # Bounded progress history: at most 100 lines, oldest evicted.
        if int(progress.index("end-1c").split(".")[0]) > 100:
            progress.delete("1.0", "2.0")
        progress.see("end")
        progress.configure(state="disabled")

    def worker(keys: tuple[str, str]) -> None:
        try:
            result = run_batch(args, keys, log=messages.put, cancelled=cancelled.is_set)
            report = json.loads((args.data_dir / "coverage.json").read_text(encoding="utf-8"))
            state = batch_state(args, report, result)
            messages.put(("done", state.message, state.resume, state.expand))
        except ValueError as error:
            messages.put(("done", str(error), True, False))
        except Exception:
            # A traceback can include request objects; display a fixed message only.
            messages.put(("done", "Collection stopped unexpectedly. Its checkpoints are saved; keys were not saved.", True, False))

    def start() -> None:
        nonlocal session_keys
        if session_keys is None:
            values = {provider: entry.get().strip() for provider, entry in entries.items()}
            if any(not value or len(value) > 512 or "\n" in value or "\r" in value for value in values.values()):
                status.set("Enter a valid key for each selected provider before starting.")
                return
            session_keys = (values.get("api-sports", ""), values.get("thesportsdb", ""))
        for entry in entries.values():
            entry.delete(0, "end")
            entry.configure(state="disabled")
        start_button.configure(state="disabled")
        change_button.configure(state="disabled")
        full_button.configure(state="disabled")
        all_button.configure(state="disabled")
        recheck_button.configure(state="disabled")
        args.recheck_record_gaps = recheck.get()
        status.set("Collecting. Closing this window stops after the current request; resume from the saved checkpoints.")
        threading.Thread(target=worker, args=(session_keys,), daemon=True).start()

    def change_keys() -> None:
        nonlocal session_keys
        session_keys = None
        for entry in entries.values():
            entry.configure(state="normal")
        status.set("Enter replacement keys locally. They will not be saved.")
        if entries:
            next(iter(entries.values())).focus_set()

    def full_collection() -> None:
        args.command = "collect"
        args.api_per_run = 80
        args.tsdb_per_run = 1000
        args.retry_errors = False
        args.retry_blocked = False
        root.title("Sohva TV — team dictionary full collection")
        update_limits()
        start()

    def all_sports() -> None:
        args.sports = list(ALL_SPORTS)
        full_collection()

    buttons = ttk.Frame(frame)
    buttons.pack(anchor="w")
    start_button = ttk.Button(buttons, text="Start " + args.command, command=start)
    start_button.pack(side="left")
    change_button = ttk.Button(buttons, text="Change keys", command=change_keys, state="disabled")
    change_button.pack(side="left", padx=8)
    full_button = ttk.Button(buttons, text="Full collection", command=full_collection, state="disabled")
    full_button.pack(side="left")
    all_button = ttk.Button(buttons, text="All sports", command=all_sports,
                            state="normal" if set(args.sports) != set(ALL_SPORTS) else "disabled")
    all_button.pack(side="left", padx=8)
    recheck = tk.BooleanVar(value=getattr(args, "recheck_record_gaps", False))
    recheck_button = ttk.Checkbutton(frame, text="Recheck deferred leagues and unnamed team IDs once", variable=recheck)
    recheck_button.pack(anchor="w", pady=(8, 0))

    def poll() -> None:
        while True:
            try:
                message = messages.get_nowait()
            except queue.Empty:
                break
            if isinstance(message, tuple):
                status.set(message[1])
                start_button.configure(state="normal" if message[2] else "disabled",
                                       text="Resume " + args.command if message[2] else "Queue finished")
                change_button.configure(state="normal")
                full_button.configure(state="normal" if message[3] else "disabled")
                all_button.configure(state="normal" if set(args.sports) != set(ALL_SPORTS) else "disabled")
                args.retry_errors = True
                args.recheck_record_gaps = False
                recheck.set(False)
                recheck_button.configure(state="normal")
            else:
                append(message)
                status.set(message)
        root.after(150, poll)

    def close() -> None:
        nonlocal session_keys
        cancelled.set()
        session_keys = None
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", close)
    root.after(150, poll)
    if entries:
        next(iter(entries.values())).focus_set()
    root.mainloop()
    return 0
