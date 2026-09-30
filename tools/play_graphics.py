"""Google Play store graphics from the app's own logo artwork (docs/rebuild/assets).

    python tools/play_graphics.py

Writes docs/play/graphics/: the 512x512 icon (32-bit PNG), the 1280x720 TV banner and the
1024x500 feature graphic (24-bit PNGs, as Play asks). Only the Sohva mark and wordmark are
used: no third-party logos, posters or crests (Play's listing rules, decision "Play listing").
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "docs/rebuild/assets/res/drawable-xxxhdpi"
OUT = ROOT / "docs/play/graphics"
# The launcher's background colour (mipmap-anydpi-v26/ic_launcher.xml) and a lighter centre.
EDGE = (5, 7, 13)
CENTRE = (13, 24, 48)


def ground(size: tuple[int, int]) -> Image.Image:
    """A radial glow from the centre to the launcher colour, drawn once."""
    w, h = size
    img = Image.new("RGB", size, EDGE)
    glow = Image.new("L", size, 0)
    draw = ImageDraw.Draw(glow)
    steps = 60
    for i in range(steps):
        t = i / steps
        rx, ry = w * 0.6 * (1 - t), h * 0.6 * (1 - t)
        draw.ellipse((w / 2 - rx, h / 2 - ry, w / 2 + rx, h / 2 + ry), fill=int(255 * t))
    return Image.composite(Image.new("RGB", size, CENTRE), img, glow)


def scaled(img: Image.Image, width: int) -> Image.Image:
    return img.resize((width, round(img.height * width / img.width)), Image.LANCZOS)


def lockup(canvas: Image.Image, mark_h: int, gap: int) -> Image.Image:
    """Mark then wordmark, centred on [canvas] as one group (the launcher banner's layout)."""
    mark = Image.open(ART / "sohva_mark.png").convert("RGBA")
    word = Image.open(ART / "sohva_wordmark.png").convert("RGBA")
    mark = mark.resize((mark_h, mark_h), Image.LANCZOS)
    word = scaled(word, round(mark_h * 1.35))
    total = mark.width + gap + word.width
    x = (canvas.width - total) // 2
    out = canvas.convert("RGBA")
    out.alpha_composite(mark, (x, (canvas.height - mark.height) // 2))
    out.alpha_composite(word, (x + mark.width + gap, (canvas.height - word.height) // 2))
    return out.convert("RGB")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    icon = ground((512, 512)).convert("RGBA")
    mark = Image.open(ART / "sohva_mark.png").convert("RGBA").resize((400, 400), Image.LANCZOS)
    icon.alpha_composite(mark, (56, 50))
    icon.save(OUT / "icon-512.png")
    lockup(ground((1280, 720)), 400, 36).save(OUT / "tv-banner-1280x720.png")
    lockup(ground((1024, 500)), 290, 26).save(OUT / "feature-graphic-1024x500.png")
    for p in sorted(OUT.glob("*.png")):
        with Image.open(p) as im:
            print(p.name, im.size, im.mode)


if __name__ == "__main__":
    main()
