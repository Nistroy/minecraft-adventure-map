#!/usr/bin/env python3
"""Génère les textures de l'écran de la carte et le sprite provisoire de l'objet (stdlib seule).

Relancer après chaque retouche : python3 tools/generate_textures.py
Le sprite de l'objet est un bouche-trou : à remplacer par un sprite fait main si nistroy en dessine un.
"""
import math
import random
import struct
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/adventuremap/textures"


def write_png(path, width, height, pixels):
    """pixels : liste de (r, g, b, a) ligne par ligne."""
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(pixels[y * width + x])

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n"
                     + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
                     + chunk(b"IEND", b""))


def value_noise(size, cell, seed):
    """Bruit lissé qui se répète (tuile sans couture)."""
    rng = random.Random(seed)
    n = size // cell
    grid = [[rng.random() for _ in range(n)] for _ in range(n)]

    def smooth(t):
        return t * t * (3 - 2 * t)

    def at(x, y):
        gx, gy = x / cell, y / cell
        x0, y0 = int(gx) % n, int(gy) % n
        x1, y1 = (x0 + 1) % n, (y0 + 1) % n
        tx, ty = smooth(gx - int(gx)), smooth(gy - int(gy))
        top = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
        bottom = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
        return top * (1 - ty) + bottom * ty

    return at


def parchment():
    size = 256
    octaves = [(value_noise(size, 64, 1), 0.5), (value_noise(size, 16, 2), 0.3), (value_noise(size, 4, 3), 0.2)]
    grain = random.Random(4)
    base = (232, 214, 174)
    pixels = []
    for y in range(size):
        for x in range(size):
            v = sum(f(x, y) * w for f, w in octaves) - 0.5
            v += (grain.random() - 0.5) * 0.08
            shade = 1 + v * 0.16
            pixels.append(tuple(max(0, min(255, int(c * shade))) for c in base) + (255,))
    write_png(OUT / "gui/parchment.png", size, size, pixels)


def fog():
    size = 64
    noise = value_noise(size, 8, 5)
    detail = value_noise(size, 4, 6)
    pixels = []
    for y in range(size):
        for x in range(size):
            dx, dy = (x + 0.5) / size * 2 - 1, (y + 0.5) / size * 2 - 1
            d = math.hypot(dx, dy)
            edge = 1 - d + (noise(x, y) - 0.5) * 0.7 + (detail(x, y) - 0.5) * 0.25
            alpha = max(0.0, min(1.0, edge * 2.2))
            shade = int(232 + (noise(x, y) - 0.5) * 30)
            pixels.append((shade, shade - 8, shade - 26, int(alpha * 245)))
    write_png(OUT / "gui/fog.png", size, size, pixels)


def seal():
    size = 32
    pixels = []
    for y in range(size):
        for x in range(size):
            dx, dy = x + 0.5 - 16, y + 0.5 - 16
            d = math.hypot(dx, dy)
            wobble = 13.5 + math.sin(math.atan2(dy, dx) * 9) * 1.2
            if d > wobble:
                pixels.append((0, 0, 0, 0))
                continue
            light = max(0.0, 1 - math.hypot(dx + 5, dy + 5) / 22)
            r, g, b = 122 + int(120 * light), 83 + int(122 * light), 16 + int(90 * light)
            if abs(d - 9.5) < 0.8 or d > wobble - 1.3:
                r, g, b = 90, 61, 10
            # ✓ gravé au centre
            if (-5 <= dx <= -1 and abs(dy - (dx + 5) - 1) < 1.2) or (-1 <= dx <= 6 and abs(dy - (4 - (dx + 1) * 1.2)) < 1.2):
                r, g, b = 74, 50, 8
            pixels.append((r, g, b, 255))
    write_png(OUT / "gui/seal.png", size, size, pixels)


def item():
    # Parchemin roulé aux deux bouts, ✕ rouge, pointillés : lisible en 16 × 16.
    P, D, E, R, K, T = (232, 214, 174, 255), (196, 170, 120, 255), (120, 88, 50, 255), (163, 38, 43, 255), (58, 42, 25, 255), (0, 0, 0, 0)
    rows = [
        "................",
        ".EEEEEEEEEEEEEE.",
        ".EDDDDDDDDDDDDE.",
        "..PPPPPPPPPPPP..",
        "..PPPPPPPPPPPP..",
        "..PKPPPPPPPPPP..",
        "..PPPKPPPPRPRP..",
        "..PPPPPKPPPRPP..",
        "..PPPPPPPKRPRP..",
        "..PPPPPPPPPPPP..",
        "..PPPPPPPPPPPP..",
        "..PPPPPPPPPPPP..",
        ".EDDDDDDDDDDDDE.",
        ".EEEEEEEEEEEEEE.",
        "................",
        "................",
    ]
    palette = {".": T, "P": P, "D": D, "E": E, "R": R, "K": K}
    pixels = [palette[c] for row in rows for c in row]
    write_png(OUT / "item/adventurer_map.png", 16, 16, pixels)


if __name__ == "__main__":
    parchment()
    fog()
    seal()
    item()
    print("textures écrites dans", OUT)
