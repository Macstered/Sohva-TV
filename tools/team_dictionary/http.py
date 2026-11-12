"""Header-only authentication, bounded bodies and persisted per-day request accounting."""

from __future__ import annotations

import hashlib
import json
import math
import re
import time
import urllib.error
import urllib.request

from email.utils import parsedate_to_datetime
from typing import Any, Callable

from .sports import api_base
from .store import Store, utc_day


class CollectionError(Exception):
    """Messages are fixed text or bounded, redacted provider error summaries."""


class BudgetReached(CollectionError):
    pass


class RecordListGap(CollectionError):
    """A successful league request cannot establish its roster; retain it for review."""


class RateLimited(CollectionError):
    def __init__(self, retry_after: int, attempted: bool = True):
        super().__init__("The provider minute limit was reached; collection will wait and resume.")
        self.retry_after = retry_after
        self.attempted = attempted


class SeasonRestricted(CollectionError):
    def __init__(self, message: str, first: int, last: int):
        super().__init__(message)
        self.first, self.last = first, last


def retry_seconds(value: str | None, now: float) -> int:
    # Respect Retry-After, but allow a full minute plus margin when no reset is supplied.
    try:
        seconds = int(value) if value is not None else 65
    except ValueError:
        try:
            seconds = math.ceil(parsedate_to_datetime(value).timestamp() - now)
        except (ValueError, TypeError, AttributeError, OverflowError):
            seconds = 65
    return max(65, seconds)


def is_minute_error(errors: Any) -> bool:
    if not isinstance(errors, dict):
        return False
    if any(str(key).lower().replace("_", "").replace("-", "") == "ratelimit" for key in errors):
        return True
    # Some wrappers report rate errors under `requests`. Inspect in memory; never log the text.
    return any("request" in str(value).lower() and re.search(r"\bper (?:1 )?minute\b", str(value).lower())
               for value in errors.values())


def tsdb_no_data(data: dict) -> bool:
    # Confirmed v2 response, including the capital M. Only this singleton envelope
    # means no results; authentication messages and conflicting fields never do.
    if len(data) != 1:
        return False
    message = data.get("Message", data.get("message"))
    return isinstance(message, str) and message.strip().casefold() == "no data found"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward keys to an unexpected origin, even on a provider redirect.
        return None


def scrub(value: Any, secrets: tuple[str, ...]) -> Any:
    if isinstance(value, str):
        for secret in secrets:
            if secret:
                value = value.replace(secret, "<redacted>")
        return value
    if isinstance(value, list):
        return [scrub(item, secrets) for item in value]
    if isinstance(value, dict):
        return {scrub(key, secrets): scrub(item, secrets) for key, item in value.items()}
    return value


def api_error_reason(errors: Any, secrets: tuple[str, ...]) -> str:
    # Save only recognised error fields, never the full response or reflected request headers.
    fields = {"plan", "subscription", "requests", "quota", "season", "league", "country", "id",
              "name", "search", "parameters", "required", "token", "key", "authorization", "error"}
    parts = []
    if isinstance(errors, dict):
        for field, value in errors.items():
            if not isinstance(field, str) or field.lower() not in fields or not isinstance(value, str):
                continue
            message = scrub(value, secrets)
            message = re.sub(r"https?://\S+", "<redacted URL>", message, flags=re.IGNORECASE)
            message = re.sub(r"[\w.+-]+@[\w.-]+", "<redacted email>", message)
            message = re.sub(r"[A-Za-z0-9_+/=-]{24,}", "<redacted token>", message)
            message = " ".join("".join(c if c.isprintable() else " " for c in message).split())
            parts.append(f"{field.lower()}: {message}")
            if len(parts) == 4:
                break
    return "; ".join(parts)[:512] if parts else "Unrecognised service error; check the provider dashboard."


def restricted_seasons(errors: Any) -> tuple[int, int] | None:
    if not isinstance(errors, dict):
        return None
    for field in ("plan", "subscription"):
        message = errors.get(field)
        if not isinstance(message, str) or "do not have access to this season" not in message.lower():
            continue
        match = re.search(r"\btry from ([0-9]{4}) to ([0-9]{4})\b", message, re.IGNORECASE)
        if match:
            first, last = map(int, match.groups())
            if 2000 <= first <= last <= 2100:
                return first, last
    return None


class Client:
    BODY_LIMIT = 16 * 1024 * 1024  # One desktop response, discarded after its import.

    def __init__(self, store: Store, api_key: str = "", tsdb_key: str = "", daily_limit: int = 80,
                 reserve: int = 20, opener=None, sleep: Callable[[float], None] = time.sleep,
                 clock: Callable[[], float] = time.time):
        self.store = store
        self.api_key = api_key
        self.tsdb_key = tsdb_key
        self.daily_limit = daily_limit
        self.reserve_count = reserve
        self.opener = opener or urllib.request.build_opener(NoRedirect())
        self.sleep = sleep
        self.clock = clock
        self.calls: dict[str, int] = {}
        self.checked: set[str] = set()
        self.last_digest = ""

    def get(self, provider: str, sport: str, endpoint: str, params: dict | None = None) -> dict:
        if provider == "api-sports" and sport not in self.checked:
            self._request(provider, sport, "/status", {})
            self.checked.add(sport)
        return self._request(provider, sport, endpoint, params or {})

    def _request(self, provider: str, sport: str, endpoint: str, params: dict) -> dict:
        from urllib.parse import urlencode
        if provider == "api-sports":
            if not self.api_key:
                raise CollectionError("Enter the API-Sports key locally before collecting.")
            base = api_base(sport)
            headers = {"x-apisports-key": self.api_key}
            gap = 7.5  # Eight starts/minute, below the documented free-tier limit of ten.
            bucket = sport
        elif provider == "thesportsdb":
            if not self.tsdb_key:
                raise CollectionError("Enter the TheSportsDB key locally before collecting.")
            base = "https://www.thesportsdb.com/api/v2/json"
            headers = {"X-API-KEY": self.tsdb_key}
            gap = 0.8  # At most 75 starts/minute; no burst, including retries.
            bucket = "all"
        elif provider == "cldr":
            base = "https://raw.githubusercontent.com/unicode-org/cldr-json/48.2.0/cldr-json/cldr-localenames-full/main"
            headers = {}
            gap, bucket = 0.0, "all"
        else:
            raise CollectionError("Unsupported data provider.")
        if not endpoint.startswith("/") or ".." in endpoint or "?" in endpoint or "#" in endpoint:
            raise CollectionError("The collector rejected an invalid endpoint.")
        url = base + endpoint + ("?" + urlencode(params) if params else "")
        headers.update({"Accept": "application/json", "User-Agent": "Sohva-Team-Research/1"})
        cooldown = self.store.cooldown_until(provider) - self.clock()
        if cooldown > 0:
            raise RateLimited(math.ceil(cooldown), attempted=False)
        # Persist spacing across restarts; the dataset's OS lock prevents concurrent sends.
        delay = self.store.last_sent(provider) + gap - self.clock()
        if delay > 60:
            raise BudgetReached("The system clock moved backward. Check its time before resuming.")
        if delay > 0:
            self.sleep(delay)
        request_day = utc_day()
        if provider == "api-sports" and not self.store.reserve(provider, bucket, self.daily_limit,
                                                              self.reserve_count, request_day):
            raise BudgetReached("Daily collection budget reached. Resume after the next UTC reset.")
        if provider != "api-sports":
            self.store.reserve(provider, bucket, 1_000_000_000, 0, request_day)
        self.calls[provider + ":" + bucket] = self.calls.get(provider + ":" + bucket, 0) + 1
        self.store.sent(provider, self.clock())
        try:
            with self.opener.open(urllib.request.Request(url, headers=headers), timeout=30) as response:
                remaining = response.headers.get("x-ratelimit-requests-remaining")
                if str(response.headers.get("X-RateLimit-Remaining")) == "0":
                    self.store.cooldown(provider, self.clock() + retry_seconds(response.headers.get("Retry-After"), self.clock()))
                if provider == "api-sports" and remaining is not None and str(remaining).isdigit():
                    self.store.remaining(provider, bucket, int(remaining), request_day)
                chunks, size = [], 0
                while chunk := response.read(64 * 1024):
                    size += len(chunk)
                    if size > self.BODY_LIMIT:
                        raise CollectionError("The response exceeded the desktop size limit. Split this request before retrying.")
                    chunks.append(chunk)
                raw = b"".join(chunks)
            self.last_digest = hashlib.sha256(raw).hexdigest()
            data = json.loads(raw.decode("utf-8-sig"))
            if not isinstance(data, dict):
                raise CollectionError("The provider returned an unexpected response shape.")
            if provider == "api-sports" and data.get("errors"):
                remaining_today = self.store.remaining_today(provider, bucket, request_day)
                if remaining_today is not None and remaining_today <= self.reserve_count:
                    raise BudgetReached("Daily collection budget reached. Resume after the next UTC reset.")
                if is_minute_error(data["errors"]):
                    self._throttle(provider, response.headers.get("Retry-After"))
                reason = api_error_reason(data["errors"], (self.api_key, self.tsdb_key))
                message = f"API-Sports rejected {sport} {endpoint}: {reason}"
                seasons = restricted_seasons(data["errors"])
                if seasons is not None and "season" in params:
                    raise SeasonRestricted(message, *seasons)
                raise CollectionError(message)
            if provider == "api-sports" and endpoint == "/status":
                quota = data.get("response", {}).get("requests", {})
                limit = quota.get("limit_day", quota.get("limit"))
                if isinstance(limit, int) and isinstance(quota.get("current"), int):
                    available = limit - quota["current"]
                    if remaining is not None and str(remaining).isdigit():
                        available = min(available, int(remaining))
                    self.store.remaining(provider, bucket, available, request_day)
            if provider == "thesportsdb":
                status = data.get("status")
                message = data.get("Message", data.get("message"))
                errors = data.get("errors") or {"error": data.get("error"), "requests": message}
                if data.get("error") == 429 or status == 429 or is_minute_error(errors):
                    self._throttle(provider, response.headers.get("Retry-After"))
                failure = status is False or isinstance(status, str) and status.lower() in (
                    "error", "failed", "failure", "unauthorized", "forbidden")
                message_only = message and not any(key in data for key in ("all", "list", "lookup", "search")) and not tsdb_no_data(data)
                if failure or data.get("error") or data.get("errors") or message_only:
                    reason = api_error_reason(data.get("errors") or {"error": message or data.get("error")},
                                              (self.api_key, self.tsdb_key))
                    raise CollectionError(f"TheSportsDB rejected {sport} {endpoint}: {reason}")
            return scrub(data, (self.api_key, self.tsdb_key))
        except urllib.error.HTTPError as error:
            try:
                remaining = error.headers.get("x-ratelimit-requests-remaining")
                if provider == "api-sports" and remaining is not None and str(remaining).isdigit():
                    self.store.remaining(provider, bucket, int(remaining), request_day)
                if error.code == 429:
                    available = self.store.remaining_today(provider, bucket, request_day)
                    if provider == "api-sports" and available is not None and available <= self.reserve_count:
                        raise BudgetReached("Daily collection budget reached. Resume after the next UTC reset.") from None
                    self._throttle(provider, error.headers.get("Retry-After"))
                if error.code in (401, 403):
                    raise CollectionError("The provider refused access. Check the key and subscription in its dashboard.") from None
                raise CollectionError(f"The provider returned HTTP {error.code}. Resume to retry the pending request.") from None
            finally:
                error.close()
        except (urllib.error.URLError, TimeoutError, OSError):
            raise CollectionError("The provider could not be reached. Check the connection and resume.") from None
        except (ValueError, UnicodeError, AttributeError):
            raise CollectionError("The provider returned invalid or unexpected JSON. The request remains pending.") from None

    def _throttle(self, provider: str, retry_after: str | None) -> None:
        seconds = retry_seconds(retry_after, self.clock())
        self.store.cooldown(provider, self.clock() + seconds)
        raise RateLimited(seconds) from None
