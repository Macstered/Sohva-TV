"""Local research collector. Run --help; API keys are only accepted through masked prompts."""

from __future__ import annotations

import argparse
import getpass
import json
import sys
import time
import warnings
from pathlib import Path

from team_dictionary.http import BudgetReached, Client, CollectionError, RateLimited, RecordListGap, SeasonRestricted
from team_dictionary.providers import LANGUAGES, SUPPORTED, apply_season_access, complete, recover_tsdb_empty_lists, seed
from team_dictionary.research import alias_candidates, export_reviewed, reports
from team_dictionary.store import Store, collection_lock

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DATA = ROOT / ".local" / "team-dictionary"


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--data-dir", type=Path, default=DEFAULT_DATA)
    commands = result.add_subparsers(dest="command", required=True)
    plan = commands.add_parser("plan", help="Create a queue and report without network or keys")
    plan.add_argument("--full", action="store_true", help="Prepare the full advertised all-sports queue")
    plan.add_argument("--sports", nargs="+", choices=SUPPORTED, default=list(SUPPORTED))
    for command in ("pilot", "collect"):
        sub = commands.add_parser(command, help="Collect/resume the pilot or full advertised roster")
        sub.add_argument("--providers", nargs="+", choices=("api-sports", "thesportsdb", "cldr"),
                         default=["api-sports", "thesportsdb", "cldr"])
        sub.add_argument("--sports", nargs="+", choices=SUPPORTED, default=list(SUPPORTED))
        sub.add_argument("--api-daily-cap", type=int, default=80, choices=range(1, 81), metavar="1..80")
        sub.add_argument("--api-per-run", type=int, default=12 if command == "pilot" else 80)
        sub.add_argument("--tsdb-per-run", type=int, default=60 if command == "pilot" else 1000)
        sub.add_argument("--retry-errors", action="store_true", help="Requeue failed requests after correcting access/connection")
        sub.add_argument("--retry-blocked", action="store_true", help="Recheck season restrictions after the API-Sports plan changes")
        sub.add_argument("--recheck-record-gaps", action="store_true", help="Explicitly recheck parked league lists and unnamed team IDs")
        sub.add_argument("--key-window", action="store_true", help="Use a desktop window with masked key fields and progress")
        sub.add_argument("--session-input", action="store_true", help=argparse.SUPPRESS)
    commands.add_parser("report", help="Regenerate local CSV and coverage reports without keys")
    aliases = commands.add_parser("aliases", help="Populate sourced alias proposals and prioritise relevant pending team profiles")
    aliases.add_argument("--sports", nargs="+", choices=SUPPORTED, default=list(SUPPORTED))
    aliases.add_argument("--refresh-translations", action="store_true", help="Refresh ISO country codes and sporting territories from nine public Unicode sources")
    commands.add_parser("export", help="Export canonical identities and manually accepted aliases to JSONL")
    link = commands.add_parser("review-link", help="Accept/reject a collected identity link after reviewing its evidence")
    link.add_argument("--sport", required=True, choices=SUPPORTED)
    link.add_argument("--api-id", required=True)
    link.add_argument("--tsdb-id", required=True)
    link.add_argument("--decision", required=True, choices=("accepted", "rejected"))
    alias = commands.add_parser("review-alias", help="Accept/reject a sourced alias already present in the review report")
    alias.add_argument("--sport", required=True, choices=SUPPORTED)
    alias.add_argument("--entity-kind", choices=("team", "fighter", "driver", "circuit", "competition"), default="team")
    alias.add_argument("--api-id", required=True)
    alias.add_argument("--name", required=True)
    alias.add_argument("--source", required=True)
    alias.add_argument("--decision", required=True, choices=("accepted", "rejected"))
    return result


def prompt_keys(providers: list[str]) -> tuple[str, str]:
    if not sys.stdin.isatty():
        raise CollectionError("Use an interactive terminal or --key-window for private key entry.")
    # getpass can fall back to echoing on unsuitable terminals. Refuse that fallback.
    with warnings.catch_warnings():
        warnings.simplefilter("error", getpass.GetPassWarning)
        try:
            api = getpass.getpass("API-Sports key (masked, not saved): ") if "api-sports" in providers else ""
            tsdb = getpass.getpass("TheSportsDB Premium key (masked, not saved): ") if "thesportsdb" in providers else ""
        except getpass.GetPassWarning:
            raise CollectionError("This terminal cannot mask keys. Run again with --key-window.") from None
    for value in (api, tsdb):
        if "\n" in value or "\r" in value or len(value) > 512:
            raise CollectionError("The key is invalid. Enter a single line of at most 512 characters.")
    return api.strip(), tsdb.strip()


def collect(args, keys: tuple[str, str], log=print, cancelled=lambda: False) -> int:
    with collection_lock(args.data_dir):
        return collect_locked(args, keys, log, cancelled)


def wait_for_retry(seconds: int, cancelled, sleep=time.sleep) -> bool:
    # Short waits keep the desktop window responsive and cancellation prompt during a cooldown.
    remaining = float(seconds)
    while remaining > 0:
        if cancelled():
            return False
        step = min(1.0, remaining)
        sleep(step)
        remaining -= step
    return not cancelled()


def collect_locked(args, keys: tuple[str, str], log, cancelled) -> int:
    store = Store(args.data_dir / "research.sqlite")
    failures = 0
    try:
        scope = "pilot" if args.command == "pilot" else "full"
        seed(store, scope, tuple(args.sports), tuple(args.providers))
        if "thesportsdb" in args.providers:
            recovered = recover_tsdb_empty_lists(store, tuple(args.sports), scope)
            if recovered:
                log(f"thesportsdb: recovered {recovered} confirmed empty league responses without extra requests.")
        if getattr(args, "retry_blocked", False) and "api-sports" in args.providers:
            with store.db:
                for sport in args.sports:
                    store.db.execute("UPDATE jobs SET state='pending',note='' WHERE provider='api-sports' AND sport=? "
                                     "AND state='blocked' AND scope=?", (sport, scope))
                    store.db.execute("UPDATE jobs SET state='pending',context='{}',note='' WHERE provider='api-sports' AND sport=? "
                                     "AND kind IN ('hockey-leagues','api-leagues','api-seasons') AND scope=?", (sport, scope))
        if args.retry_errors:
            with store.db:
                for provider in args.providers:
                    selected = [*args.sports, "+".join(sorted(args.sports)), "all"]
                    placeholders = ",".join("?" for _ in selected)
                    store.db.execute("UPDATE jobs SET state='pending',note='' WHERE state='error' AND provider=? "
                                     f"AND scope IN (?, 'shared') AND sport IN ({placeholders})", (provider, scope, *selected))
        if getattr(args, "recheck_record_gaps", False):
            with store.db:
                for provider in args.providers:
                    for sport in args.sports:
                        store.db.execute("UPDATE jobs SET state='pending',note='' WHERE provider=? AND sport=? "
                                         "AND scope IN (?, 'shared') AND state='blocked' "
                                         "AND kind IN ('api-team-detail','tsdb-team-list','tsdb-team-detail')", (provider, sport, scope))
        client = Client(store, api_key=keys[0], tsdb_key=keys[1], daily_limit=args.api_daily_cap)
        # Each sport has its own daily budget. One sport failing does not block another provider.
        # Per-sport API budgets; enrichment rotates sports so a large football queue cannot starve the rest.
        buckets = [("api-sports", sport) for sport in args.sports] + [("thesportsdb", None), ("cldr", None)]
        tsdb_cursor = 0
        for provider, sport in buckets:
            if provider not in args.providers:
                continue
            consecutive_throttles = 0
            consecutive_failures = 0
            while not cancelled():
                if provider == "thesportsdb":
                    job = store.next_job(provider, "+".join(sorted(args.sports)), scope)
                    if job is None:
                        for offset in range(len(args.sports)):
                            index = (tsdb_cursor + offset) % len(args.sports)
                            job = store.next_job(provider, args.sports[index], scope)
                            if job is not None:
                                tsdb_cursor = (index + 1) % len(args.sports)
                                break
                else:
                    job = store.next_job(provider, sport, scope)
                if job is None:
                    break
                bucket = sport if provider == "api-sports" else "all"
                used = client.calls.get(provider + ":" + bucket, 0)
                cap = args.api_per_run if provider == "api-sports" else args.tsdb_per_run if provider == "thesportsdb" else len(LANGUAGES)
                needed = 2 if provider == "api-sports" and sport not in client.checked else 1
                if used + needed > cap:
                    log(f"{provider} {bucket}: pilot/run limit reached; pending requests are checkpointed.")
                    break
                with store.db:
                    store.db.execute("UPDATE jobs SET attempts=attempts+1 WHERE id=?", (job["id"],))
                try:
                    data = client.get(provider, job["sport"], job["endpoint"], json.loads(job["params"]))
                    if cancelled():
                        break
                    complete(store, job, data, client.last_digest)
                    consecutive_throttles = 0
                    consecutive_failures = 0
                    saved = store.db.execute("SELECT state,rows_seen,note FROM jobs WHERE id=?", (job["id"],)).fetchone()
                    if saved["state"] == "blocked":
                        log(f"{provider} {job['sport']} {job['endpoint']}: {saved['note']}")
                    elif job["kind"] == "tsdb-team-list" and not saved["rows_seen"]:
                        log(f"{provider} {job['sport']} {job['endpoint']}: no teams listed; saved with a coverage warning.")
                    else:
                        log(f"{provider} {job['sport']}: completed {job['kind']} (job {job['id']}).")
                except RateLimited as error:
                    if error.attempted:
                        consecutive_throttles += 1
                    if consecutive_throttles >= 3 or error.retry_after > 300:
                        log(f"{provider} {bucket}: repeated or extended throttling; this request stays pending for the next run.")
                        break
                    log(f"{provider} {bucket}: minute limit; waiting {error.retry_after} seconds, then resuming the same request.")
                    if not wait_for_retry(error.retry_after, cancelled):
                        break
                except SeasonRestricted as error:
                    if job["kind"] in ("api-teams", "f1-driver-rankings"):
                        apply_season_access(store, scope, error.first, error.last, str(error), sport=job["sport"])
                        log(f"{provider} {bucket}: plan allows seasons {error.first} to {error.last}; "
                            "skipping inaccessible years and refreshing the index for historical identities.")
                    else:
                        with store.db:
                            store.db.execute("UPDATE jobs SET state='error',note=? WHERE id=?", (str(error), job["id"]))
                        failures += 1
                        log(f"{provider} {bucket}: {error}")
                        break
                except BudgetReached as error:
                    log(f"{provider} {bucket}: {error}")
                    break
                except RecordListGap as error:
                    with store.db:
                        store.db.execute("UPDATE jobs SET state='blocked',note=?,digest=? WHERE id=?",
                                         (str(error), client.last_digest, job["id"]))
                    consecutive_failures += 1
                    log(f"{provider} {job['sport']} {job['endpoint']}: {error}")
                    # Three suspect envelopes may indicate a service-wide change. Resume skips
                    # these parked requests, so they cannot repeatedly block the other sports.
                    if consecutive_failures >= 3:
                        break
                except CollectionError as error:
                    # Fixed messages or redacted error summaries; never save full bodies or exception URLs.
                    with store.db:
                        store.db.execute("UPDATE jobs SET state='error',note=? WHERE id=?", (str(error), job["id"]))
                    failures += 1
                    consecutive_failures += 1
                    log(f"{provider} {bucket}: {error}")
                    # One missing league or failed partition must not stop enrichment of every sport.
                    if provider != "thesportsdb" or consecutive_failures >= 3:
                        break
            if cancelled():
                break
        summary = reports(store, args.data_dir, tuple(args.sports))
        total = sum(row["api_teams"] for row in summary["sports"].values())
        others = sum(sum(row["api_entities"].values()) for row in summary["sports"].values())
        log(f"Research snapshot contains {total} API-Sports teams and {others} other identities. Reports saved in {args.data_dir}.")
        return 1 if failures else 0
    finally:
        store.close()


def offline(args) -> int:
    store = Store(args.data_dir / "research.sqlite")
    try:
        if args.command == "plan":
            seed(store, "full" if args.full else "pilot", tuple(args.sports), ("api-sports", "thesportsdb", "cldr"))
        if args.command == "aliases" and args.refresh_translations:
            from team_dictionary.national import refresh_translations
            refresh_translations(store)
        if args.command == "review-link":
            with store.db:
                cursor = store.db.execute("UPDATE links SET status=? WHERE sport=? AND api_id=? AND tsdb_id=?",
                                          (args.decision, args.sport, args.api_id, args.tsdb_id))
                if not cursor.rowcount:
                    raise CollectionError("That identity link is not in the collected review dataset.")
        if args.command == "review-alias":
            if args.entity_kind != "team":
                with store.db:
                    candidate = store.db.execute("SELECT language FROM entity_names WHERE provider='api-sports' AND sport=? "
                                                 "AND entity_kind=? AND entity_id=? AND name=? AND source=?",
                                                 (args.sport, args.entity_kind, args.api_id, args.name, args.source)).fetchone()
                    if candidate is None:
                        raise CollectionError("That sourced entity alias is not in the collected review dataset.")
                    store.db.execute("INSERT OR REPLACE INTO entity_alias_reviews VALUES (?,?,?,?,?,?,?)",
                                     (args.sport, args.entity_kind, args.api_id, args.name, args.source, candidate[0], args.decision))
                reports(store, args.data_dir)
                return 0
            team = store.db.execute("SELECT * FROM teams WHERE provider='api-sports' AND sport=? AND team_id=?",
                                    (args.sport, args.api_id)).fetchone()
            candidates = list(alias_candidates(store, team)) if team else []
            match = next((row for row in candidates if row["name"] == args.name and row["source"] == args.source), None)
            if match is None:
                raise CollectionError("That sourced alias is not in the collected review dataset.")
            if args.decision == "accepted" and match["identity_status"] == "candidate":
                raise CollectionError("Review and accept the identity link before accepting its aliases.")
            with store.db:
                store.db.execute("INSERT OR REPLACE INTO alias_reviews VALUES (?,?,?,?,?,?)",
                                 (args.sport, args.api_id, args.name, args.source, match["language"], args.decision))
        summary = reports(store, args.data_dir, tuple(args.sports) if args.command == "aliases" else ())
        if args.command == "aliases":
            aliases = summary["aliases"]
            print(f"Alias proposals: {aliases['additional_spellings']} additional spellings for {aliases['teams_with_aliases']} teams; "
                  f"{aliases['national_teams_with_translations']} national identities translated; {aliases['collision_groups']} shared-name groups.")
            print(f"Pending TheSportsDB profiles prioritised: {aliases['prioritised_detail_jobs']}. No identity or alias was automatically approved.")
            print(f"Alias report: {args.data_dir / 'alias_summary.md'}")
        if args.command == "export":
            count = export_reviewed(store, args.data_dir / "dictionary.reviewed.jsonl")
            print(f"Exported {count} canonical identities with accepted aliases only.")
        print(f"Report: {args.data_dir / 'coverage.md'}")
        if args.command == "plan":
            if args.full:
                print("No API requests made. Full discovery is queued for all selected API-Sports products.")
                print("Collection will respect existing checkpoints, 80 attempted calls per API sport/day and 20 reported remaining.")
            else:
                print("No API requests made. A bounded pilot is queued for each selected product.")
                print("Limits: at most 12 calls per API sport and 60 TheSportsDB calls in this pilot; country labels in eight languages.")
        return 0
    finally:
        store.close()


def main() -> int:
    args = parser().parse_args()
    try:
        if args.command in ("pilot", "collect"):
            if args.api_per_run < 1 or args.tsdb_per_run < 1:
                raise CollectionError("Per-run limits must be positive.")
            if args.key_window:
                from team_dictionary.window import run_window
                return run_window(args)
            if args.session_input:
                from team_dictionary.session import collect_from_pipe
                return collect_from_pipe(args, collect)
            return collect(args, prompt_keys(args.providers))
        # Schema migration, report/review writes and collection never run concurrently.
        with collection_lock(args.data_dir):
            return offline(args)
    except (CollectionError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 1
    except (KeyboardInterrupt, EOFError):
        print("Stopped. Completed requests and quota accounting are checkpointed; keys were not saved.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
