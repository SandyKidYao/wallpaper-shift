#!/usr/bin/env python3
"""Renders the demo set「一日光影」: the app logo on six time-of-day gradients.

The phone itself is the logo's frame: each wallpaper is the icon's inner mark (half sun, horizon,
reflection; see ../icon/icon.svg) drawn large and white in the lower half of the screen, on a
top-to-bottom three-stop gradient like the icon's own background. Below the horizon the gradient is
a little darker (the sea), and the sun has a soft glow. Dawn is dim, night begins at sunset, and deep night is darker; the
other five follow the light through the day. The sky above the sun stays empty for the lock-screen clock.

    python3 design/demo-wallpapers/render.py            # → shared/demo-assets/demo/sky-0N-*.jpg

Needs numpy, and macOS `sips` for the JPEG step.
"""
import pathlib
import struct
import subprocess
import sys
import tempfile
import zlib

import numpy as np

WIDTH, HEIGHT = 1440, 3120  # 9 : 19.5
UNIT = WIDTH / 34 * 0.75  # pixels per icon unit: 34 units (the icon's phone frame) = 4/3 screen width
SUN_Y = 0.62  # sun centre (icon point 54, 60), as a fraction of the height
SEA_SHADE = 0.10  # how much darker the gradient is below the horizon
GLOW = 0.15  # peak strength of the sun's glow (white)
GLOW_RADIUS = 22  # icon units
JPEG_QUALITY = 92

# Name → (top, middle, bottom). Middle sits at 50%, as in the icon.
GRADIENTS = {
    "01-dawn": ("#111936", "#353C65", "#82718F"),
    "02-morning": ("#4B6CC1", "#D99BB5", "#FCE1A6"),
    "03-noon": ("#1259C3", "#3A93E6", "#A3DAF5"),
    "04-afternoon": ("#245F9E", "#5C9FC2", "#F2CB82"),
    "05-night": ("#151B40", "#393759", "#85617A"),
    "06-deepnight": ("#050918", "#111831", "#252542"),
}
PREFIX = "sky-"

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "shared" / "demo-assets" / "demo"


def rgb(hex_color):
    return np.array([int(hex_color[i:i + 2], 16) for i in (1, 3, 5)], dtype=np.float64)


def gradient(stops):
    t = np.linspace(0.0, 1.0, HEIGHT)[:, None]
    top, mid, bottom = (rgb(c) for c in stops)
    lower = t < 0.5
    k = np.where(lower, t / 0.5, (t - 0.5) / 0.5)
    column = np.where(lower, top + (mid - top) * k, mid + (bottom - mid) * k)
    return np.broadcast_to(column[:, None, :], (HEIGHT, WIDTH, 3)).copy()


def capsule(u, v, x0, x1, y, radius):
    """Distance to a horizontal stroke with round caps."""
    dx = u - np.clip(u, x0, x1)
    return np.hypot(dx, v - y) - radius


def layers():
    """Per-pixel masks: (sea, glow, opaque mark, reflection), anti-aliased from signed distances."""
    ys, xs = np.mgrid[0:HEIGHT, 0:WIDTH].astype(np.float64)
    # Pixel centres → icon units, with the sun's centre (54, 60) at the anchor.
    u = (xs + 0.5 - WIDTH / 2) / UNIT + 54
    v = (ys + 0.5 - HEIGHT * SUN_Y) / UNIT + 60

    def cover(d):
        return np.clip(0.5 - d * UNIT, 0.0, 1.0)

    # The sea starts at the horizon line's centre (y = 64) and runs across the whole screen.
    sea = cover(64 - v) * SEA_SHADE
    glow = np.exp(-(np.hypot(u - 54, v - 60) / GLOW_RADIUS) ** 2) * GLOW * (1 - cover(64 - v))
    # Sun: circle (54, 60) r 10, only the half above the horizon y = 60.
    sun = np.maximum(np.hypot(u - 54, v - 60) - 10, v - 60)
    horizon = capsule(u, v, 43, 65, 64, 1.25)
    reflection = capsule(u, v, 47, 61, 69.5, 1.25)
    return sea, glow, cover(np.minimum(sun, horizon)), cover(reflection) * 0.7


def write_png(path, pixels):
    raw = b"".join(b"\x00" + row.tobytes() for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 2, 0, 0, 0)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sea, glow, opaque, reflection = layers()
    rng = np.random.default_rng(20261020)
    with tempfile.TemporaryDirectory() as tmp:
        for name, stops in GRADIENTS.items():
            image = gradient(stops) * (1 - sea[:, :, None])
            for alpha in (glow, opaque, reflection):
                image += (255.0 - image) * alpha[:, :, None]
            # Triangular dither of ±1 level keeps the long gradients free of banding.
            image += rng.random(image.shape) - rng.random(image.shape)
            pixels = np.clip(np.rint(image), 0, 255).astype(np.uint8)
            png = pathlib.Path(tmp) / f"{name}.png"
            write_png(png, pixels)
            jpg = OUT / f"{PREFIX}{name}.jpg"
            subprocess.run(
                ["sips", "-s", "format", "jpeg", "-s", "formatOptions", str(JPEG_QUALITY), str(png), "--out", str(jpg)],
                check=True, stdout=subprocess.DEVNULL,
            )
            print(jpg.relative_to(ROOT))


if __name__ == "__main__":
    sys.exit(main())
