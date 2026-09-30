"""Fictional poster art for the Google Play screenshots (docs/play/README.md §9).

    python tools/play_screenshot_art.py

Writes app/src/androidTest/assets/play-art/poster-<n>.jpg for the fictional films that
PlayScreenshotsTest shows: abstract shapes in a colour of the film's own and its title. Nothing is
taken from real posters, logos or photographs (Play's listing rules, decision "Play listing").
"""
import colorsys
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/androidTest/assets/play-art"
TITLES = [
    "Harbour Lights", "Quiet Orchard", "The Long Winter", "Paper Moons", "Cold Current", "Silver Ridge",
    "Night Ferry", "Glass Garden", "Last Signal", "Blue Hour", "Salt Road", "The Lantern Keeper",
    "Kitchen Chaos", "Second Chances", "Lucky Street", "The Neighbours", "Holiday Mix-up", "Uncle Otto",
    "Wrong Number", "Best Man Down", "Summer Rules", "Grand Opening", "Plan B", "Two Left Feet",
]
W, H = 400, 600
FONT = "C:/Windows/Fonts/segoeuib.ttf"


def rgb(h: float, s: float, v: float) -> tuple[int, int, int]:
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, s, v)
    return int(r * 255), int(g * 255), int(b * 255)


def poster(n: int, title: str) -> Image.Image:
    rnd = random.Random(n * 7919)
    hue = rnd.random()
    top, bottom = rgb(hue, 0.55, 0.55), rgb(hue + 0.08, 0.75, 0.12)
    img = Image.new("RGB", (W, H))
    draw = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        draw.line([(0, y), (W, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)))
    # A few soft shapes in lighter tones: sun, hills or windows, depending on the film.
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    shapes = ImageDraw.Draw(layer)
    for _ in range(rnd.randint(3, 6)):
        x, y, r = rnd.randint(-60, W), rnd.randint(40, H - 220), rnd.randint(40, 170)
        tone = rgb(hue + rnd.uniform(-0.1, 0.15), 0.35, 0.95) + (rnd.randint(50, 110),)
        if rnd.random() < 0.6:
            shapes.ellipse([x - r, y - r, x + r, y + r], fill=tone)
        else:
            shapes.rectangle([x - r, y - r // 2, x + r, y + r], fill=tone)
    layer = layer.filter(ImageFilter.GaussianBlur(6))
    img = Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")
    draw = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, 46)
    words, lines, line = title.upper().split(), [], ""
    for w in words:
        trial = (line + " " + w).strip()
        if draw.textlength(trial, font=font) > W - 60 and line:
            lines.append(line)
            line = w
        else:
            line = trial
    lines.append(line)
    y = H - 60 - len(lines) * 54
    for text in lines:
        draw.text(((W - draw.textlength(text, font=font)) / 2, y), text, font=font, fill=(245, 245, 240))
        y += 54
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for n, title in enumerate(TITLES):
        poster(n, title).save(OUT / f"poster-{n}.jpg", quality=85, optimize=True)
    print(f"{len(TITLES)} posters in {OUT}")


if __name__ == "__main__":
    main()
