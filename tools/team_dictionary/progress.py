"""Report-backed collection state, independent of the desktop UI and credentials."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class BatchState:
    message: str
    resume: bool
    expand: bool = False


def batch_state(args, report: dict, result: int) -> BatchState:
    scope = "pilot" if args.command == "pilot" else "full"
    selected = set(args.sports)
    totals = {"pending": 0, "error": 0, "blocked": 0}
    for key, count in report.get("jobs", {}).items():
        provider, sport, job_scope, state = key.split(":", 3)
        if provider not in args.providers or job_scope not in (scope, "shared"):
            continue
        if sport != "all" and not set(sport.split("+")).issubset(selected):
            continue
        if state in totals:
            totals[state] += count
    pending, errors, blocked = (totals[key] for key in ("pending", "error", "blocked"))
    if result or errors:
        return BatchState(f"Batch saved. {errors} failed requests; {blocked} coverage gaps. Review the messages and resume after correcting the reported cause.", True)
    if pending:
        return BatchState(f"Batch saved. {pending} requests remain; {blocked} coverage gaps. Review the messages before resuming.", True)
    if scope == "pilot":
        teams = sum(report.get("sports", {}).get(sport, {}).get("api_teams", 0) for sport in selected)
        others = sum(sum(report.get("sports", {}).get(sport, {}).get("api_entities", {}).values()) for sport in selected)
        return BatchState(f"Pilot complete: {teams} API-Sports teams and {others} other identities; {blocked} blocked requests. "
                          "Choose Full collection to collect more teams.", False, True)
    return BatchState(f"Full queue finished: {blocked} blocked requests. Alias and coverage review remain.", False)
