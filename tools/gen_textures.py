#!/usr/bin/env python3
"""Procedural texture generator for the Arcane Enchanting mod.

All textures are original and generated deterministically, so they can be
re-generated (or tuned) at any time:

    python3 tools/gen_textures.py

Output goes to src/main/resources/assets/arcaneenchanting/textures/.

Block icons (16x16, block atlas) are laid out so that the pixel rows match the
pedestal geometry drawn by RenderArcaneTable (pixel-consistent UV mapping, the
same convention RenderBlocks uses):

    rows  2..3   corner posts           (y 0.750 .. 0.875)
    rows  4..6   cap side band          (y 0.5625 .. 0.75)
    rows  7..12  column, cols 3..12     (y 0.1875 .. 0.5625)
    rows 13..15  plinth side            (y 0.0 .. 0.1875)
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "assets", "arcaneenchanting", "textures")
BLOCKS = os.path.join(ROOT, "blocks")
FX = os.path.join(ROOT, "fx")

# ---------------------------------------------------------------- palette
STONE = [(17, 11, 27), (22, 14, 34), (27, 18, 41), (33, 22, 50)]
STONE_SPECK = (58, 40, 86)
ENGRAVE = (74, 42, 118)
GOLD_D = (122, 86, 34)
GOLD = (190, 146, 60)
GOLD_L = (236, 204, 116)
RUNE_GLOW = (236, 220, 255)


def save(img, folder, name, meta=None):
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, name)
    img.save(path)
    if meta is not None:
        with open(path + ".mcmeta", "w") as f:
            json.dump(meta, f, indent=2)
            f.write("\n")
    print("wrote", os.path.relpath(path, ROOT))


def stone_pixel(rng):
    v = rng.random()
    if v < 0.04:
        return STONE_SPECK
    return STONE[min(3, int(rng.random() ** 1.6 * 4))]


# ---------------------------------------------------------------- block icons
# 3 runes on the column face, local 10x6 grid (cols 3..12, rows 7..12)
COLUMN_RUNES = [
    "..........",
    ".##..#..#.",
    ".#..###.##",
    ".##..#..#.",
    "..#.#.#.##",
    "..........",
]


def column_rune_pixels():
    px = set()
    for r, line in enumerate(COLUMN_RUNES):
        for c, ch in enumerate(line):
            if ch == "#":
                px.add((3 + c, 7 + r))
    return px


def top_sigil_pixels():
    px = set()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            manhattan = abs(x - cx) + abs(y - cy)
            if 4.6 <= d <= 5.4:
                px.add((x, y))
            elif 3.0 <= manhattan <= 3.0:
                px.add((x, y))
            elif d < 1.0:
                px.add((x, y))
    return px


def make_side():
    rng = random.Random(11)
    img = Image.new("RGBA", (16, 16))
    runes = column_rune_pixels()
    for y in range(16):
        for x in range(16):
            c = stone_pixel(rng)
            if y in (2, 3):                      # corner posts: gold
                c = GOLD_L if y == 2 else GOLD
            elif y == 4 or y == 6:               # cap rims
                c = GOLD if y == 4 else GOLD_D
            elif y == 5:                         # cap band: studs
                c = GOLD_L if x % 4 == 1 else stone_pixel(rng)
            elif 7 <= y <= 12:                   # column
                if x in (3, 12):
                    c = (40, 27, 60)
                if (x, y) in runes:
                    c = ENGRAVE
            elif y == 13:                        # plinth top rim
                c = GOLD
            img.putpixel((x, y), c + (255,))
    save(img, BLOCKS, "arcane_side.png")


def make_side_runes():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y) in column_rune_pixels():
        img.putpixel((x, y), RUNE_GLOW + (255,))
    for x in range(16):                          # faint glow on cap studs
        if x % 4 == 1:
            img.putpixel((x, 5), (255, 236, 180, 150))
    save(img, BLOCKS, "arcane_side_runes.png")


def make_top():
    rng = random.Random(12)
    img = Image.new("RGBA", (16, 16))
    sigil = top_sigil_pixels()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                c = GOLD_D
            elif edge == 1:
                c = GOLD
            else:
                c = stone_pixel(rng)
                if (x, y) in sigil:
                    c = ENGRAVE
            img.putpixel((x, y), c + (255,))
    save(img, BLOCKS, "arcane_top.png")


def make_top_runes():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y) in top_sigil_pixels():
        if 2 <= x <= 13 and 2 <= y <= 13:
            img.putpixel((x, y), RUNE_GLOW + (255,))
    save(img, BLOCKS, "arcane_top_runes.png")


def make_stone():
    rng = random.Random(13)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), stone_pixel(rng) + (255,))
    save(img, BLOCKS, "arcane_stone.png")


def make_crystal():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            t = ((x + y) % 8) / 7.0
            band = (x - y) % 6 < 2
            r = int(150 + 80 * t)
            g = int(110 + 70 * t)
            b = 255
            if band:
                r, g = min(255, r + 40), min(255, g + 50)
            img.putpixel((x, y), (r, g, b, 255))
    save(img, BLOCKS, "arcane_crystal.png")


def make_mote():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 7.5
            a = max(0.0, 1.0 - d) ** 1.4
            if a > 0.02:
                img.putpixel((x, y), (255, 255, 255, int(255 * a)))
    save(img, BLOCKS, "arcane_mote.png")


def make_spark():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            a = max(0.0, 1.0 - min(dx, dy) / 1.3) * max(0.0, 1.0 - max(dx, dy) / 8.0)
            core = max(0.0, 1.0 - math.hypot(dx, dy) / 3.0)
            a = min(1.0, a + core)
            if a > 0.03:
                img.putpixel((x, y), (255, 255, 255, int(255 * a)))
    save(img, BLOCKS, "arcane_spark.png")


# ---------------------------------------------------------------- glyphs
LATTICE = [(x, y) for x in (0.0, 0.5, 1.0) for y in (0.0, 0.33, 0.66, 1.0)]


def glyph_strokes(seed):
    rng = random.Random(seed)
    strokes = [((0.5, 0.0), (0.5, 1.0))] if rng.random() < 0.6 else []
    for _ in range(rng.randint(2, 4)):
        a, b = rng.sample(LATTICE, 2)
        strokes.append((a, b))
    dot = rng.choice(LATTICE) if rng.random() < 0.5 else None
    return strokes, dot


def draw_glyph(draw, seed, x0, y0, w, h, width, fill=(255, 255, 255, 255)):
    strokes, dot = glyph_strokes(seed)
    for (a, b) in strokes:
        draw.line([(x0 + a[0] * w, y0 + a[1] * h), (x0 + b[0] * w, y0 + b[1] * h)],
                  fill=fill, width=width)
    if dot is not None:
        r = width * 1.2
        cx, cy = x0 + dot[0] * w, y0 + dot[1] * h
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill)


# ---------------------------------------------------------------- fx textures
def make_rune_band():
    S = 4
    W, H = 256 * S, 32 * S
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.line([(0, 3 * S), (W, 3 * S)], fill=(255, 255, 255, 160), width=S)
    d.line([(0, H - 3 * S), (W, H - 3 * S)], fill=(255, 255, 255, 160), width=S)
    cell = W // 8
    for i in range(8):
        x0 = i * cell + cell * 0.25
        draw_glyph(d, 100 + i, x0, 8 * S, cell * 0.5, H - 16 * S, int(1.6 * S))
        # small separator diamond
        cx, cy = i * cell, H / 2
        r = 2 * S
        d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=(255, 255, 255, 200))
    img = img.resize((256, 32), Image.LANCZOS)
    save(img, FX, "rune_band.png", {"texture": {"blur": True}})


def polygon_points(cx, cy, r, n, rot=-math.pi / 2):
    return [(cx + r * math.cos(rot + 2 * math.pi * k / n), cy + r * math.sin(rot + 2 * math.pi * k / n))
            for k in range(n)]


def ring(d, cx, cy, r, w, fill=(255, 255, 255, 255)):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=fill, width=w)


def make_magic_circle():
    N = 1024
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = N / 2
    ring(d, c, c, 500, 7)
    ring(d, c, c, 474, 3)
    ring(d, c, c, 372, 5)
    ring(d, c, c, 205, 4)
    ring(d, c, c, 64, 4)
    # heptagram {7/3}
    pts = polygon_points(c, c, 362, 7)
    for k in range(7):
        d.line([pts[k], pts[(k + 3) % 7]], fill=(255, 255, 255, 230), width=5)
    for p in pts:
        ring(d, p[0], p[1], 22, 4)
    # hexagram
    for rot in (-math.pi / 2, math.pi / 2):
        tri = polygon_points(c, c, 200, 3, rot)
        d.polygon(tri, outline=(255, 255, 255, 220))
        for k in range(3):
            d.line([tri[k], tri[(k + 1) % 3]], fill=(255, 255, 255, 220), width=4)
    # glyph band between radii 380 and 466, glyphs drawn upright then rotated
    count = 28
    for k in range(count):
        g = Image.new("RGBA", (64, 72), (0, 0, 0, 0))
        gd = ImageDraw.Draw(g)
        draw_glyph(gd, 300 + k, 14, 6, 36, 60, 5)
        ang = 360.0 * k / count
        gr = g.rotate(-ang, resample=Image.BICUBIC, expand=True)
        rad = math.radians(ang - 90)
        px = c + 423 * math.cos(rad) - gr.width / 2
        py = c + 423 * math.sin(rad) - gr.height / 2
        img.alpha_composite(gr, (int(px), int(py)))
    # tick marks on outermost ring
    for k in range(72):
        a = 2 * math.pi * k / 72
        r0, r1 = 476, 496 if k % 3 == 0 else 488
        d.line([(c + r0 * math.cos(a), c + r0 * math.sin(a)), (c + r1 * math.cos(a), c + r1 * math.sin(a))],
               fill=(255, 255, 255, 200), width=3)
    img = img.resize((256, 256), Image.LANCZOS)
    save(img, FX, "magic_circle.png", {"texture": {"blur": True, "clamp": True}})


def make_glow():
    N = 64
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for y in range(N):
        for x in range(N):
            r = math.hypot(x - (N - 1) / 2, y - (N - 1) / 2) / (N / 2)
            a = max(0.0, 1.0 - r) ** 2.2
            img.putpixel((x, y), (255, 255, 255, int(255 * a)))
    save(img, FX, "glow.png", {"texture": {"blur": True, "clamp": True}})


def make_beam():
    W, H = 32, 128
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for y in range(H):
        t = y / H
        streak = 0.86 + 0.07 * math.sin(2 * math.pi * 3 * t) + 0.07 * math.sin(2 * math.pi * 7 * t + 1.3)
        for x in range(W):
            u = (x + 0.5) / W - 0.5
            core = math.exp(-(u / 0.08) ** 2)
            halo = math.exp(-(u / 0.28) ** 2) * 0.55
            a = min(1.0, (core + halo) * streak)
            img.putpixel((x, y), (255, 255, 255, int(255 * a)))
    save(img, FX, "beam.png", {"texture": {"blur": True}})


def make_thread():
    W, H = 16, 64
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for y in range(H):
        t = y / H
        pulse = 0.25 + 0.75 * max(0.0, math.sin(2 * math.pi * 2 * t)) ** 4
        for x in range(W):
            u = (x + 0.5) / W - 0.5
            a = math.exp(-(u / 0.18) ** 2) * pulse
            img.putpixel((x, y), (255, 255, 255, int(255 * min(1.0, a))))
    save(img, FX, "thread.png", {"texture": {"blur": True}})


def make_shockwave():
    N = 128
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for y in range(N):
        for x in range(N):
            r = math.hypot(x - (N - 1) / 2, y - (N - 1) / 2) / (N / 2)
            a = math.exp(-((r - 0.86) / 0.06) ** 2) + 0.25 * math.exp(-((r - 0.70) / 0.14) ** 2)
            if r > 1.0:
                a = 0.0
            img.putpixel((x, y), (255, 255, 255, int(255 * min(1.0, a))))
    save(img, FX, "shockwave.png", {"texture": {"blur": True, "clamp": True}})


if __name__ == "__main__":
    make_side()
    make_side_runes()
    make_top()
    make_top_runes()
    make_stone()
    make_crystal()
    make_mote()
    make_spark()
    make_rune_band()
    make_magic_circle()
    make_glow()
    make_beam()
    make_thread()
    make_shockwave()
