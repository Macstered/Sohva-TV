"""Copies the brand bitmaps from the kit into :app as lossless WebP at xhdpi and xxxhdpi only.

Android TV UIs run at xhdpi on nearly every device (docs/rebuild/assets/README.md, lightweight
notes); lossless WebP keeps the pixels and is about a third smaller than PNG. Re-run after the
kit's brand PNGs change.

    python tools/brand_assets.py
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "docs" / "rebuild" / "assets" / "res"
TARGET = ROOT / "app" / "src" / "main" / "res"

for density in ("xhdpi", "xxxhdpi"):
    for name in ("sohva_mark", "sohva_wordmark"):
        src = SOURCE / f"drawable-{density}" / f"{name}.png"
        out = TARGET / f"drawable-{density}" / f"{name}.webp"
        out.parent.mkdir(parents=True, exist_ok=True)
        with Image.open(src) as image:
            image.save(out, "WEBP", lossless=True, exact=True, quality=100, method=6)
        with Image.open(src) as a, Image.open(out) as b:
            same = a.convert("RGBA").tobytes() == b.convert("RGBA").tobytes()
        print(f"{out.relative_to(ROOT)}: {src.stat().st_size} -> {out.stat().st_size} bytes, identical={same}")
        if not same:
            raise SystemExit("lossless conversion changed pixels")
