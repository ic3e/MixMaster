#!/usr/bin/env python3
"""
Draws the demo's blueprints and site photos into app/src/main/assets/demo.

The demo projects are made up, so their drawings and photos are too: floor plans drawn to scale
from the same room sizes the demo writes into the app (DemoData.kt), and "photos" rendered from
the finishes each project uses — microtopping, Lixio, Lixio+, Architop, epoxy primer with quartz
broadcast into it. Run it again after changing a room here and there.

    python3 tools/demo_images.py
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "demo")
FONTS = os.path.join(ROOT, "app", "src", "main", "res", "font")


# Manrope joins "fl" into one glyph, which read as a rendering bug in "floor" in the app too.
NO_LIGA = ["-liga"]


def font(weight, size):
    return ImageFont.truetype(os.path.join(FONTS, f"manrope_{weight}.ttf"), size)


# ---------------------------------------------------------------- the plans
# Rooms in metres: (name, x, y, w, h, finish). A finish of None is drawn hatched: not our work.
PLANS = {
    "punavuori": ("Punavuori loft", "Tehtaankatu 12 B 7, 00140 Helsinki", [
        ("Apartment", [
            ("Living room & kitchen", 0, 0, 8.5, 5.0, "Microtopping · Ash White"),
            ("Hall", 0, 5.0, 4.1, 2.0, "Microtopping · Ash White"),
            ("Bathroom", 4.1, 5.0, 2.8, 2.0, "Microtopping · Smoke\nfloor 5.6 m² · walls 18.4 m²"),
            ("Wardrobe", 6.9, 5.0, 1.6, 2.0, None),
        ]),
    ]),
    "sointu": ("Kahvila Sointu", "Hämeenkatu 21, 33200 Tampere", [
        ("Ground floor", [
            ("Café", 0, 0, 10.0, 6.4, "Lixio · Carrara · Ash White"),
            ("Counter", 0, 6.4, 5.0, 2.5, "Lixio · Carrara · Ash White"),
            ("Toilets", 5.0, 6.4, 3.4, 2.0, "Microtopping · Silver Grey"),
            ("Kitchen", 8.4, 6.4, 1.6, 2.5, None),
        ]),
    ]),
    "tapiola": ("Nordic Kitchen showroom", "Tapiontori 3, 02100 Espoo", [
        ("Showroom", [
            ("Showroom", 0, 0, 13.0, 9.0, "Architop · Beige Grey"),
            ("Office", 13.0, 0, 5.5, 4.0, "Architop · Beige Grey"),
            ("Storage", 13.0, 4.0, 5.5, 5.0, None),
        ]),
    ]),
    "oulu": ("Oulu library lobby", "Kaarlenväylä 3, 90100 Oulu", [
        ("Entrance level", [
            ("Lobby", 0, 0, 12.0, 8.0, "Lixio+ · Neutro / Botticino"),
            ("Corridor", 0, 8.0, 17.0, 2.0, "Lixio · Carrara · Beige Grey"),
            ("Stair landing", 12.0, 0, 3.0, 3.0, "Lixio · Carrara · Beige Grey"),
            ("Stairs", 12.0, 3.0, 5.0, 5.0, None),
        ]),
    ]),
    "saimaa": ("Villa Saimaa", "Rantatie 41, 57230 Savonlinna", [
        ("Ground floor", [
            ("Living room", 0, 0, 7.6, 5.0, "Architop · Tortora"),
            ("Kitchen", 7.6, 0, 4.0, 4.0, "Architop · Tortora"),
            ("Entrance", 7.6, 4.0, 2.5, 3.0, "Lixio+ · Antracite / Nero Ebano"),
            ("Terrace", 0, 5.0, 7.6, 2.9, "Lixio+ · Antracite / Nero Ebano\n30 mm, outdoor"),
        ]),
        ("Upstairs", [
            ("Bathroom", 0, 0, 3.0, 2.4, "Microtopping · Tortora\nfloor 7.2 m² · walls 21 m²"),
            ("Bedroom", 3.0, 0, 4.2, 3.6, None),
            ("Landing", 0, 2.4, 3.0, 1.2, None),
        ]),
    ]),
}

BLUE = (29, 78, 137)
LINE = (236, 243, 250)


def blueprint(key, project, address, floor, rooms, path):
    W, H = 1600, 1130
    img = Image.new("RGB", (W, H), BLUE)
    d = ImageDraw.Draw(img, "RGBA")
    # the paper's grid
    for x in range(0, W, 40):
        d.line([(x, 0), (x, H)], fill=(255, 255, 255, 18 if x % 200 else 34), width=1)
    for y in range(0, H, 40):
        d.line([(0, y), (W, y)], fill=(255, 255, 255, 18 if y % 200 else 34), width=1)
    x1 = max(r[1] + r[3] for r in rooms)
    y1 = max(r[2] + r[4] for r in rooms)
    s = min((W - 360) / x1, (H - 330) / y1)
    ox, oy = 150, 150

    def P(x, y):
        return ox + x * s, oy + y * s

    for name, x, y, w, h, finish in rooms:
        a, b = P(x, y), P(x + w, y + h)
        if finish is None:
            # not ours: hatched, and said so
            rw, rh = int(b[0] - a[0]), int(b[1] - a[1])
            hatch = Image.new("RGBA", (rw, rh), (0, 0, 0, 0))
            hd = ImageDraw.Draw(hatch)
            for k in range(-rh, rw, 18):
                hd.line([(k, rh), (k + rh, 0)], fill=(255, 255, 255, 46), width=1)
            img.paste(hatch, (int(a[0]), int(a[1])), hatch)
            d = ImageDraw.Draw(img, "RGBA")
        d.rectangle([a, b], outline=LINE, width=6)
        cx, cy = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        area = round(w * h, 1)
        lines = [(name, font("extrabold", 26))]
        if finish is None:
            lines.append(("not in scope", font("medium", 19)))
        else:
            lines.append((f"{area:g} m²", font("bold", 22)))
            for part in finish.split("\n"):
                lines.append((part, font("medium", 18)))
        total = sum(f.size + 6 for _, f in lines)
        ty = cy - total / 2
        for text, f in lines:
            tw = d.textlength(text, font=f, features=NO_LIGA)
            if tw > (b[0] - a[0]) - 16 and f.size > 16:
                f = font("medium", 15)
                tw = d.textlength(text, font=f, features=NO_LIGA)
            d.text((cx - tw / 2, ty), text, font=f, fill=LINE, features=NO_LIGA)
            ty += f.size + 6
    # the overall dimensions, along the top and the left
    fdim = font("bold", 20)
    a, b = P(0, 0), P(x1, 0)
    d.line([(a[0], a[1] - 48), (b[0], b[1] - 48)], fill=LINE, width=2)
    for px in (a[0], b[0]):
        d.line([(px, a[1] - 60), (px, a[1] - 36)], fill=LINE, width=2)
    t = f"{x1 * 1000:,.0f}".replace(",", " ")
    d.text(((a[0] + b[0]) / 2 - d.textlength(t, font=fdim, features=NO_LIGA) / 2, a[1] - 78), t, font=fdim, fill=LINE, features=NO_LIGA)
    a, b = P(0, 0), P(0, y1)
    d.line([(a[0] - 48, a[1]), (b[0] - 48, b[1])], fill=LINE, width=2)
    for py in (a[1], b[1]):
        d.line([(a[0] - 60, py), (a[0] - 36, py)], fill=LINE, width=2)
    t = f"{y1 * 1000:,.0f}".replace(",", " ")
    txt = Image.new("RGBA", (int(d.textlength(t, font=fdim, features=NO_LIGA)) + 4, 30), (0, 0, 0, 0))
    ImageDraw.Draw(txt).text((0, 0), t, font=fdim, fill=LINE, features=NO_LIGA)
    txt = txt.rotate(90, expand=True)
    img.paste(txt, (int(a[0] - 84), int((a[1] + b[1]) / 2 - txt.size[1] / 2)), txt)
    # north
    nx, ny = W - 110, 110
    d.polygon([(nx, ny - 44), (nx + 16, ny + 18), (nx, ny + 8), (nx - 16, ny + 18)], fill=LINE)
    d.text((nx - 8, ny + 24), "N", font=font("extrabold", 24), fill=LINE, features=NO_LIGA)
    # the title block
    bx, by = W - 560, H - 190
    d.rectangle([bx, by, W - 40, H - 40], outline=LINE, width=3)
    d.line([(bx, by + 56), (W - 40, by + 56)], fill=LINE, width=2)
    d.text((bx + 18, by + 12), "ConWiC Oy · floor finishes", font=font("extrabold", 26), fill=LINE, features=NO_LIGA)
    d.text((bx + 18, by + 68), project, font=font("bold", 24), fill=LINE, features=NO_LIGA)
    d.text((bx + 18, by + 100), address, font=font("medium", 18), fill=LINE, features=NO_LIGA)
    d.text((bx + 18, by + 124), f"{floor} · floor plan · 1:100 · rev A", font=font("medium", 18), fill=LINE, features=NO_LIGA)
    img.save(path, optimize=True)


# ---------------------------------------------------------------- the finishes
def noise(n, cells, rng):
    g = rng.random((cells, cells)).astype(np.float32)
    return np.asarray(Image.fromarray((g * 255).astype(np.uint8)).resize((n, n), Image.BICUBIC), dtype=np.float32) / 255.0


def fbm(n, rng, base=4, octaves=5):
    out = np.zeros((n, n), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        out += noise(n, base * 2 ** o, rng) * amp
        tot += amp
        amp *= 0.5
    return out / tot


def hexrgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float32)


def to_img(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))


def microtopping(n, colour, rng, mesh=False, contrast=0.09):
    base = hexrgb(colour)
    cloud = fbm(n, rng, 3, 6) - 0.5
    grain = rng.normal(0, 3.5, (n, n)).astype(np.float32)
    arr = base[None, None, :] * (1 + contrast * cloud[..., None]) + grain[..., None]
    img = to_img(arr)
    # trowel arcs, lighter and darker, laid over and softened
    over = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(over)
    for _ in range(140):
        r = rng.uniform(n * 0.06, n * 0.2)
        cx, cy = rng.uniform(0, n), rng.uniform(0, n)
        a0 = rng.uniform(0, 360)
        tone = 255 if rng.random() < 0.55 else 0
        d.arc([cx - r, cy - r, cx + r, cy + r], a0, a0 + rng.uniform(40, 110), fill=(tone, tone, tone, int(rng.uniform(5, 12))), width=int(rng.uniform(10, 24)))
    over = over.filter(ImageFilter.GaussianBlur(7))
    img = Image.alpha_composite(img.convert("RGBA"), over).convert("RGB")
    if mesh:
        d = ImageDraw.Draw(img, "RGBA")
        step = n // 90
        for k in range(0, n, step):
            d.line([(k, 0), (k, n)], fill=(255, 255, 255, 14), width=2)
            d.line([(0, k), (n, k)], fill=(255, 255, 255, 14), width=2)
    return img


MARBLE = {
    "Carrara White": ["#f4f3ef", "#e7e6e1", "#cfd0cf", "#b9bbbd", "#f9f8f4"],
    "Botticino": ["#e9dcc4", "#d9c7a8", "#f2e8d6", "#c8b08a", "#a58d6c"],
    "Nero Ebano": ["#1d1d1f", "#303134", "#4a4b4e", "#e9e7e2", "#6e6f72"],
}


def lixio(n, matrix, marble, rng, chip=(3, 7), density=0.9, polished=True):
    base = hexrgb(matrix)
    cloud = fbm(n, rng, 4, 5) - 0.5
    arr = base[None, None, :] * (1 + 0.05 * cloud[..., None]) + rng.normal(0, 4, (n, n, 1)).astype(np.float32)
    img = to_img(arr)
    d = ImageDraw.Draw(img)
    cols = MARBLE[marble]
    count = int(density * n * n / ((chip[0] + chip[1]) ** 2 / 2.2))
    for _ in range(count):
        cx, cy = rng.uniform(0, n), rng.uniform(0, n)
        r = rng.uniform(*chip)
        k = int(rng.integers(5, 9))
        pts = []
        for j in range(k):
            ang = 2 * math.pi * j / k + rng.uniform(-0.35, 0.35)
            rr = r * rng.uniform(0.55, 1.1)
            pts.append((cx + math.cos(ang) * rr, cy + math.sin(ang) * rr))
        c = hexrgb(cols[int(rng.integers(0, len(cols)))]) * rng.uniform(0.92, 1.05)
        d.polygon(pts, fill=tuple(int(v) for v in np.clip(c, 0, 255)))
    img = img.filter(ImageFilter.GaussianBlur(0.5 if polished else 0.9))
    return img


def architop(n, colour, rng):
    base = hexrgb(colour)
    cloud = fbm(n, rng, 2, 6) - 0.5
    vein = np.abs(fbm(n, rng, 5, 4) - 0.5)
    veins = np.asarray(Image.fromarray(((vein < 0.035) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(4)), np.float32) / 255.0
    arr = base[None, None, :] * (1 + 0.26 * cloud[..., None] - 0.07 * veins[..., None]) + rng.normal(0, 3, (n, n, 1)).astype(np.float32)
    return to_img(arr).filter(ImageFilter.GaussianBlur(1.5))


def epoxy_quartz(n, rng):
    base = hexrgb("#8d8a80")
    cloud = fbm(n, rng, 4, 4) - 0.5
    arr = base[None, None, :] * (1 + 0.08 * cloud[..., None])
    img = to_img(arr)
    d = ImageDraw.Draw(img)
    for _ in range(int(n * n / 55)):
        x, y = rng.uniform(0, n), rng.uniform(0, n)
        r = rng.uniform(0.7, 2.2)
        t = int(rng.uniform(170, 240))
        d.ellipse([x - r, y - r, x + r, y + r], fill=(t, t - 4, t - 12))
    return img


def raw_concrete(n, rng):
    base = hexrgb("#9a9892")
    cloud = fbm(n, rng, 3, 6) - 0.5
    arr = base[None, None, :] * (1 + 0.16 * cloud[..., None]) + rng.normal(0, 7, (n, n, 1)).astype(np.float32)
    img = to_img(arr)
    d = ImageDraw.Draw(img)
    for _ in range(int(n * n / 900)):
        x, y = rng.uniform(0, n), rng.uniform(0, n)
        r = rng.uniform(0.8, 3.0)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(78, 76, 72))
    return img


# ---------------------------------------------------------------- the room around it
def perspective_coeffs(src, dst):
    """Coefficients for PIL's PERSPECTIVE transform, mapping the output quad dst onto src."""
    m = []
    for (x, y), (u, v) in zip(dst, src):
        m.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        m.append([0, 0, 0, x, y, 1, -v * x, -v * y])
    a = np.array(m, np.float64)
    b = np.array([c for p in src for c in p], np.float64)
    return np.linalg.solve(a, b).tolist()


def room_photo(floor_tex, path, wall="#e8e4dc", gloss=0.35, rng=None, wall_tex=None):
    W, H = 1280, 960
    img = Image.new("RGB", (W, H), wall)
    horizon = 430
    bl, br = (170, horizon), (1110, horizon)
    fl, fr = (-700, H + 40), (W + 700, H + 40)
    n = floor_tex.size[0]
    floor = floor_tex.transform((W, H), Image.PERSPECTIVE,
                                perspective_coeffs([(0, 0), (n, 0), (n, n), (0, n)], [bl, br, fr, fl]),
                                Image.BICUBIC)
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).polygon([bl, br, fr, fl], fill=255)
    # the walls: back, left, right, a touch darker to the sides; the ceiling
    d = ImageDraw.Draw(img)
    wc = hexrgb(wall)
    d.polygon([(0, 0), (170, 70), (170, horizon), (0, horizon + 125)], fill=tuple(int(v) for v in wc * 0.86))
    d.polygon([(W, 0), (1110, 70), (1110, horizon), (W, horizon + 125)], fill=tuple(int(v) for v in wc * 0.9))
    d.polygon([(0, 0), (W, 0), (1110, 70), (170, 70)], fill=tuple(int(v) for v in wc * 0.97))
    if wall_tex is not None:
        wt = wall_tex.resize((940, 360))
        img.paste(wt, (170, 70))
    # a window in the left wall, and the daylight it throws across the floor
    d.polygon([(30, 120), (130, 150), (130, 360), (30, 385)], fill=(214, 229, 240))
    d.line([(80, 135), (80, 372)], fill=tuple(int(v) for v in wc * 0.7), width=5)
    img.paste(floor, (0, 0), mask)
    light = Image.new("L", (W, H), 0)
    ImageDraw.Draw(light).polygon([(150, 520), (520, 470), (900, 700), (260, 900)], fill=int(90 * gloss + 25))
    light = light.filter(ImageFilter.GaussianBlur(70))
    img = Image.composite(Image.new("RGB", (W, H), (255, 252, 244)), img, light)
    # skirting along the walls
    d = ImageDraw.Draw(img)
    d.line([bl, br], fill=(250, 250, 248), width=7)
    d.line([bl, (0, horizon + 125)], fill=(250, 250, 248), width=7)
    d.line([br, (W, horizon + 125)], fill=(250, 250, 248), width=7)
    # a door in the back wall
    d.rectangle([760, 170, 880, horizon - 3], fill=tuple(int(v) for v in wc * 0.78))
    d.rectangle([760, 170, 880, horizon - 3], outline=(250, 250, 248), width=6)
    # the camera: grain and a little vignette
    arr = np.asarray(img, np.float32)
    yy, xx = np.mgrid[0:H, 0:W]
    vig = 1 - 0.28 * (((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2) / 2
    arr = arr * vig[..., None] + (rng or np.random.default_rng(1)).normal(0, 3.2, arr.shape)
    to_img(arr).save(path, quality=80, optimize=True)


def close_up(tex, path, rng):
    """A detail shot looking down, the light raking across it."""
    W, H = 1280, 960
    img = tex.resize((W, H), Image.BICUBIC) if tex.size != (W, H) else tex
    arr = np.asarray(img, np.float32)
    yy, xx = np.mgrid[0:H, 0:W]
    rake = 1.06 - 0.16 * (xx / W) - 0.06 * (yy / H)
    arr = arr * rake[..., None] + rng.normal(0, 2.5, arr.shape)
    to_img(arr).save(path, quality=80, optimize=True)


def samples(path, rng):
    """Architop sample boards on the showroom table, for the client to pick from."""
    W, H = 1280, 960
    table = raw_concrete(1280, rng).resize((W, H))
    table = to_img(np.asarray(table, np.float32) * np.array([0.72, 0.62, 0.5]))
    d = ImageDraw.Draw(table)
    boards = [("#cfc6b6", "Beige Grey"), ("#9d9489", "Tortora"), ("#6f6e6b", "Smoke"), ("#dedbd3", "Ash White")]
    for k, (col, name) in enumerate(boards):
        x, y = 90 + (k % 2) * 570, 80 + (k // 2) * 430
        tex = architop(480, col, rng).resize((500, 360))
        d.rectangle([x + 10, y + 12, x + 510, y + 372], fill=(40, 34, 28))
        table.paste(tex, (x, y))
        d = ImageDraw.Draw(table)
        d.rectangle([x + 16, y + 300, x + 260, y + 346], fill=(250, 249, 246))
        d.text((x + 28, y + 308), name, font=font("bold", 24), fill=(40, 38, 35), features=NO_LIGA)
    table.save(path, quality=80, optimize=True)


def main():
    os.makedirs(OUT, exist_ok=True)
    for key, (project, address, floors) in PLANS.items():
        for k, (floor, rooms) in enumerate(floors):
            blueprint(key, project, address, floor, rooms, os.path.join(OUT, f"{key}_plan{k + 1}.png"))
    rng = np.random.default_rng(7)
    N = 1400
    P = lambda name: os.path.join(OUT, name)
    # Punavuori: primer and quartz, the base coat over the mesh, the finish
    room_photo(epoxy_quartz(N, rng), P("punavuori_1.jpg"), gloss=0.5, rng=rng)
    room_photo(microtopping(N, "#a9a69f", rng, mesh=True), P("punavuori_2.jpg"), gloss=0.25, rng=rng)
    room_photo(microtopping(N, "#dcd8d0", rng), P("punavuori_3.jpg"), gloss=0.55, rng=rng)
    # Sointu: primed, the first grind, polished
    close_up(epoxy_quartz(1280, rng), P("sointu_1.jpg"), rng)
    room_photo(lixio(N, "#d9d6cf", "Carrara White", rng, chip=(3, 6), polished=False), P("sointu_2.jpg"), gloss=0.2, rng=rng)
    room_photo(lixio(N, "#e6e3dc", "Carrara White", rng, chip=(3, 6)), P("sointu_3.jpg"), gloss=0.7, rng=rng)
    # Tapiola: the old screed, the sample boards, a test area of coat 1
    room_photo(raw_concrete(N, rng), P("tapiola_1.jpg"), gloss=0.1, rng=rng)
    samples(P("tapiola_2.jpg"), rng)
    close_up(architop(1280, "#cfc6b6", rng), P("tapiola_3.jpg"), rng)
    # Oulu: finished lobby, corridor, landing detail
    room_photo(lixio(N, "#e4d8c3", "Botticino", rng, chip=(7, 16), density=0.8), P("oulu_1.jpg"), gloss=0.75, rng=rng)
    room_photo(lixio(N, "#cfc6b6", "Carrara White", rng, chip=(3, 6)), P("oulu_2.jpg"), gloss=0.6, rng=rng)
    close_up(lixio(1280, "#e4d8c3", "Botticino", rng, chip=(10, 24), density=0.8), P("oulu_3.jpg"), rng)
    # Saimaa: the terrace cleaned, the Lixio+ sample, the bathroom walls in base coat
    close_up(raw_concrete(1280, rng), P("saimaa_1.jpg"), rng)
    close_up(lixio(1280, "#2b2b2d", "Nero Ebano", rng, chip=(10, 24), density=0.8), P("saimaa_2.jpg"), rng)
    room_photo(microtopping(N, "#b9b1a5", rng), P("saimaa_3.jpg"), wall="#e2ddd4", gloss=0.3, rng=rng,
               wall_tex=microtopping(900, "#a39a8e", rng, mesh=True))


if __name__ == "__main__":
    main()
