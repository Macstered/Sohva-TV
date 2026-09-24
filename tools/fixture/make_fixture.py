"""Generates the owner-scale synthetic IPTV fixture of plan/07 §6.1 (test tooling, never shipped).

Live part (M0): 56,164 channels in 800 groups as an M3U playlist, 65 % with logos, and an XMLTV
guide of 1,800 channels x 92 programmes of 45 minutes anchored to the time it runs (the importer
keeps only recent history, so regenerate a fixture older than about two days). Films and series
join in M1. Every name is fictional; addresses point at the loopback fixture server, which the
emulator reaches as 10.0.2.2.

    python tools/fixture/make_fixture.py                      # into harness-out/fixture
    python tools/fixture/make_fixture.py --stress-descriptions   # 2,150-character descriptions
"""

from __future__ import annotations

import argparse
import gzip
import json
import random
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from xml.sax.saxutils import escape, quoteattr

ROOT = Path(__file__).resolve().parents[2]
CHANNELS = 56_164
GROUPS = 800
GUIDE_CHANNELS = 1_800
PROGRAMMES_PER_CHANNEL = 92
PROGRAMME_MINUTES = 45
HISTORY_HOURS = 12
LOGO_SHARE = 0.65
LOGO_IMAGES = 40
STRESS_DESCRIPTION_CHARS = 2_150

COUNTRIES = [
    "Aurelia", "Borealis", "Caldera", "Dunmore", "Estavia", "Fjordland", "Galvania", "Halcyon", "Isola", "Juniper",
    "Kestrel", "Lumeria", "Marrow", "Norhaven", "Ostrava Nova", "Pallis", "Quillon", "Rivermark", "Solenne", "Tamsin",
    "Umbria Vale", "Veloria", "Westmarch", "Xandria", "Yarrow", "Zephyr Bay", "Alderney Nord", "Brightwater", "Coralind",
    "Driftwood", "Emberlyn", "Frostholm", "Greyhaven", "Highmoor", "Ironbridge", "Jadeport", "Kingsreach", "Larkspur",
    "Moonfall", "Northwind",
]
KINDS = [
    "News", "Sports", "Movies", "Series", "Kids", "Documentary", "Music", "Entertainment", "Lifestyle", "Nature",
    "Science", "History", "Comedy", "Drama", "Local", "Weather", "Business", "Travel", "Cooking", "Culture",
]
NAME_A = ["Northstar", "Meridian", "Pulse", "Summit", "Harbor", "Lumen", "Cobalt", "Ember", "Solstice", "Aurora",
          "Granite", "Willow", "Vertex", "Tidal", "Beacon", "Falcon", "Orchid", "Quartz", "Riverside", "Zenith"]
NAME_B = ["One", "Two", "Plus", "Max", "Live", "HD", "Channel", "TV", "World", "Prime", "Select", "Classic"]
TITLE_A = ["Signal", "Harbor", "North", "Glass", "Silent", "Hidden", "Morning", "Last", "Borrowed", "Studio",
           "Distant", "Winter", "Open", "Paper", "Midnight", "Copper", "Evening", "Second", "Quiet", "Bright"]
TITLE_B = ["at Dawn", "Routes", "Horizon", "Kitchen", "Weather", "Lighthouse", "Report", "Eleven", "Door", "Current",
           "Garden", "Market", "Voyage", "Letters", "Frontier", "Workshop", "Journal", "Harvest", "Circuit", "Coast"]
SENTENCES = [
    "A calm look at the week's events.", "Two friends take a long way home.", "Experts explain what changed and why.",
    "The team faces its hardest match yet.", "A cook travels the coast in search of old recipes.",
    "An archive film restored for the first time.", "Live coverage with commentary.",
]


def groups() -> list[str]:
    return [f"{country} · {kind}" for country in COUNTRIES for kind in KINDS][:GROUPS]


def channel_name(i: int) -> str:
    return f"{NAME_A[i % len(NAME_A)]} {NAME_B[(i // len(NAME_A)) % len(NAME_B)]} {i + 1}"


def write_playlist(out: Path, base: str) -> None:
    names = groups()
    rng = random.Random(1)
    with (out / "playlist.m3u").open("w", encoding="utf-8", newline="\n") as m3u:
        m3u.write(f'#EXTM3U url-tvg="{base}/guide.xml.gz"\n')
        for i in range(CHANNELS):
            tvg_id = f"ch{i:05d}"
            logo = f' tvg-logo="{base}/logo/{tvg_id}.png"' if rng.random() < LOGO_SHARE else ""
            group = names[i % GROUPS]
            m3u.write(f'#EXTINF:-1 tvg-id="{tvg_id}" tvg-name="{channel_name(i)}"{logo} group-title="{group}",{channel_name(i)}\n')
            m3u.write(f"{base}/stream/{tvg_id}.ts\n")


def write_guide(out: Path, stress: bool) -> None:
    now = datetime.now(timezone.utc).replace(second=0, microsecond=0)
    start = now - timedelta(hours=HISTORY_HOURS)
    start -= timedelta(minutes=start.minute % 15)
    rng = random.Random(2)
    fmt = "%Y%m%d%H%M%S +0000"
    with gzip.open(out / "guide.xml.gz", "wt", encoding="utf-8", newline="\n") as xml:
        xml.write('<?xml version="1.0" encoding="UTF-8"?>\n<tv generator-info-name="sohva-fixture">\n')
        for i in range(GUIDE_CHANNELS):
            xml.write(f'  <channel id="ch{i:05d}"><display-name>{escape(channel_name(i))}</display-name></channel>\n')
        for i in range(GUIDE_CHANNELS):
            begin = start
            for _ in range(PROGRAMMES_PER_CHANNEL):
                end = begin + timedelta(minutes=PROGRAMME_MINUTES)
                title = f"{rng.choice(TITLE_A)} {rng.choice(TITLE_B)}"
                text = " ".join(rng.choice(SENTENCES) for _ in range(3))
                if stress:
                    text = (text + " ") * (STRESS_DESCRIPTION_CHARS // len(text) + 1)
                    text = text[:STRESS_DESCRIPTION_CHARS]
                xml.write(
                    f'  <programme start={quoteattr(begin.strftime(fmt))} stop={quoteattr(end.strftime(fmt))} '
                    f'channel="ch{i:05d}"><title>{escape(title)}</title><desc>{escape(text)}</desc>'
                    f"<category>{KINDS[i % len(KINDS)]}</category></programme>\n",
                )
                begin = end
        xml.write("</tv>\n")


def write_logos(out: Path) -> None:
    """40 real PNGs; the server maps every logo address onto one of them, so each is its own download."""
    from PIL import Image, ImageDraw

    folder = out / "logos"
    folder.mkdir(exist_ok=True)
    rng = random.Random(3)
    for n in range(LOGO_IMAGES):
        image = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        colour = tuple(rng.randrange(60, 256) for _ in range(3)) + (255,)
        draw.rounded_rectangle((24, 24, 232, 232), radius=48, fill=colour)
        draw.ellipse((80, 80, 176, 176), fill=(255, 255, 255, 220))
        image.save(folder / f"logo{n:02d}.png", optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=ROOT / "harness-out" / "fixture")
    parser.add_argument("--host", default="10.0.2.2", help="how the device reaches the host's loopback")
    parser.add_argument("--port", type=int, default=8780)
    parser.add_argument("--stress-descriptions", action="store_true")
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    base = f"http://{args.host}:{args.port}"
    t0 = time.monotonic()
    write_playlist(args.out, base)
    write_guide(args.out, args.stress_descriptions)
    write_logos(args.out)
    info = {
        "generated_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "channels": CHANNELS, "groups": GROUPS, "guide_channels": GUIDE_CHANNELS,
        "programmes": GUIDE_CHANNELS * PROGRAMMES_PER_CHANNEL, "stress_descriptions": args.stress_descriptions,
        "base": base,
    }
    (args.out / "fixture.json").write_text(json.dumps(info, indent=2), encoding="utf-8")
    sizes = {p.name: p.stat().st_size for p in args.out.iterdir() if p.is_file()}
    print(f"fixture in {args.out} in {time.monotonic() - t0:.1f} s: {sizes}")


if __name__ == "__main__":
    main()
