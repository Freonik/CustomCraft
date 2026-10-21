"""3D in-hand models for Echo Blade and Graviton Hammer (the inventory keeps the 2D sprite), and the textures and
models for display-entity effects (echo slash, echo ring, gravity ring).
Models are built from boxes standing upright, then every box is turned -45 degrees around z (same origin), so the
weapon lies on the same diagonal as a 2D sprite and the vanilla 'handheld' hold angles fit."""
import sys, os, json, math
sys.path.insert(0, '/home/claude/art')
from px import *
from PIL import Image

CC = sys.argv[1]                       # assets/customcraft
TEX = f'{CC}/textures/item'
os.makedirs(f'{TEX}/fx', exist_ok=True)
os.makedirs(f'{CC}/models/item/fx', exist_ok=True)
os.makedirs(f'{CC}/items', exist_ok=True)

HANDHELD = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}


def wjson(path, data):
    with open(path, 'w') as f:
        json.dump(data, f, indent=1)


def noise(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) % 1000 / 1000


def sheet():
    return Image.new('RGBA', (32, 32), (0, 0, 0, 0))


def fill(img, x0, y0, w, h, fn):
    for y in range(h):
        for x in range(w):
            c = fn(x, y)
            img.putpixel((x0 + x, y0 + y), tuple(c[:3]) + ((c[3],) if len(c) > 3 else (255,)))


def box(frm, to, faces, shade=True):
    """faces: {face: (texture key, [u0, v0, u1, v1])}; all six unless given otherwise."""
    return {"from": frm, "to": to, "shade": shade,
            "rotation": {"angle": -45, "axis": "z", "origin": [8, 8, 8]},
            "faces": {f: {"uv": uv, "texture": "#" + t} for f, (t, uv) in faces.items()}}


def all_faces(t, uv, top=None, bottom=None):
    d = {f: (t, uv) for f in ("north", "south", "east", "west")}
    d["up"] = (t, top or uv)
    d["down"] = (t, bottom or top or uv)
    return d


# ============================================================ Echo Blade (dark teal blade, bright cyan edge)
TEAL = [(6, 26, 30), (12, 44, 50), (20, 66, 74), (34, 96, 104), (70, 150, 156)]
CYAN = [(30, 140, 160), (60, 200, 220), (140, 240, 250), (225, 255, 255)]
GRIP = [(20, 16, 18), (40, 32, 36), (64, 52, 58)]
STEEL = [(36, 40, 48), (64, 70, 80), (110, 118, 130)]

s = sheet()
# 0..7 x 0..31: blade flat (dark teal, lighter toward the edges, hammered)
fill(s, 0, 0, 8, 32, lambda x, y: ramp(TEAL, 0.3 + 0.12 * abs(x - 3.5) / 3.5 + 0.15 * noise(x, y // 2)) if 0 < x < 7
     else ramp(CYAN, 0.75))
# 8..15 x 0..31: blade edge (the thin sides): bright cyan with glints
fill(s, 8, 0, 8, 32, lambda x, y: ramp(CYAN, 0.95 if (y + x) % 7 == 0 else 0.7))
# 16..23 x 0..7: guard metal
fill(s, 16, 0, 8, 8, lambda x, y: ramp(TEAL, 0.25 + 0.3 * (1 - y / 7) + 0.1 * noise(x, y, 2)))
# 24..31 x 0..7: gem
fill(s, 24, 0, 8, 8, lambda x, y: ramp(CYAN, 0.98 if (x, y) in ((2, 2), (3, 2), (2, 3)) else 0.55 + 0.3 * (1 - (x + y) / 14)))
# 16..23 x 8..15: grip wraps
fill(s, 16, 8, 8, 8, lambda x, y: ramp(GRIP, 0.85 if (y + x // 4) % 3 == 0 else 0.35))
# 24..31 x 8..15: dark steel
fill(s, 24, 8, 8, 8, lambda x, y: ramp(STEEL, 0.3 + 0.4 * noise(x, y, 5)))
s.save(f'{TEX}/echo_blade_3d.png')

# the glowing fuller: an echo pulse running up the blade (animated)
frames = []
for f in range(12):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        wave = ((15 - y) / 16 - f / 12) % 1.0
        g = max(0, 1 - abs(wave - 0.5) * 5)
        for x in range(16):
            im.putpixel((x, y), tuple(ramp(CYAN, 0.35 + 0.6 * g)[:3]) + (255,))
    frames.append(im)
save(frames, f'{TEX}/echo_blade_glow.png', frametime=2)

E = "echo"
els = [
    # pommel gem and grip
    box([7, -1, 7], [9, 0.6, 9], all_faces(E, [12, 0, 16, 4])),
    box([7.4, 0.6, 7.4], [8.6, 4.6, 8.6], all_faces(E, [8, 4, 12, 8], top=[12, 4, 16, 8])),
    # swept cross-guard: a centre block and two arms reaching up a little
    box([5.6, 4.6, 7], [10.4, 5.8, 9], all_faces(E, [8, 0, 12, 1.5], top=[8, 0, 12, 2])),
    box([4.2, 5.2, 7.2], [5.6, 6.6, 8.8], all_faces(E, [8, 0, 10, 2])),
    box([10.4, 5.2, 7.2], [11.8, 6.6, 8.8], all_faces(E, [8, 0, 10, 2])),
    box([7.5, 4.8, 6.8], [8.5, 5.6, 9.2], all_faces(E, [12, 0, 14, 2])),          # the gem in the guard
    # blade: flat faces north/south, thin edges east/west
    box([6.8, 5.8, 7.6], [9.2, 18.5, 8.4], {
        "north": (E, [0, 0, 4, 16]), "south": (E, [0, 0, 4, 16]),
        "east": (E, [4, 0, 6, 16]), "west": (E, [4, 0, 6, 16]), "up": (E, [4, 0, 6, 1]), "down": (E, [4, 0, 6, 1])}),
    # the glowing fuller, a hair proud of the blade on both sides
    box([7.75, 6.4, 7.5], [8.25, 17.6, 8.5], {"north": ("glow", [6, 0, 10, 16]), "south": ("glow", [6, 0, 10, 16]),
                                              "east": ("glow", [6, 0, 10, 16]), "west": ("glow", [6, 0, 10, 16])}),
    # tapering tip
    box([7.3, 18.5, 7.7], [8.7, 20, 8.3], {
        "north": (E, [0, 0, 4, 2]), "south": (E, [0, 0, 4, 2]), "east": (E, [4, 0, 6, 2]), "west": (E, [4, 0, 6, 2]),
        "up": (E, [4, 0, 6, 1])}),
    box([7.7, 20, 7.8], [8.3, 21, 8.2], all_faces(E, [4, 0, 6, 1])),
]
wjson(f'{CC}/models/item/echo_blade_3d.json', {
    "textures": {"echo": "customcraft:item/echo_blade_3d", "glow": "customcraft:item/echo_blade_glow",
                 "particle": "customcraft:item/echo_blade_3d"},
    "elements": els, "display": HANDHELD})

# ============================================================ Graviton Hammer (dark purple head, silver caps, purple core)
VOID = [(10, 6, 16), (22, 14, 34), (38, 24, 58), (58, 38, 86), (88, 62, 120)]
SILVER = [(70, 70, 86), (130, 130, 146), (196, 196, 210), (240, 240, 250)]
GOLDS = [(110, 70, 20), (180, 130, 40), (240, 200, 90)]
HAFT = [(16, 12, 18), (30, 24, 34), (50, 42, 56)]
CORE = [(60, 20, 120), (130, 60, 220), (200, 140, 255), (245, 225, 255)]

s = sheet()
fill(s, 0, 0, 8, 8, lambda x, y: ramp(VOID, 0.3 + 0.35 * noise(x // 2, y // 2, 7) + (0.2 if x in (0, 7) or y in (0, 7) else 0)))
fill(s, 8, 0, 8, 8, lambda x, y: ramp(SILVER, 0.35 + 0.5 * (1 - y / 7) + (0.1 if x % 3 == 0 else 0)))
fill(s, 16, 0, 8, 8, lambda x, y: ramp(GOLDS, 0.4 + 0.5 * (1 - y / 7)))
fill(s, 24, 0, 8, 8, lambda x, y: ramp(HAFT, 0.8 if (y + x // 3) % 4 == 0 else 0.3 + 0.2 * noise(x, y, 3)))
s.save(f'{TEX}/graviton_hammer_3d.png')

frames = []
for f in range(16):
    im = Image.new('RGBA', (16, 16))
    a = 2 * math.pi * f / 16
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 8
            swirl = 0.5 + 0.5 * math.sin(math.atan2(y - 7.5, x - 7.5) * 3 + d * 9 - a)
            t = max(0, min(0.99, 0.95 - d * 0.7 + 0.18 * swirl + 0.1 * math.sin(a)))
            im.putpixel((x, y), tuple(ramp(CORE, t)[:3]) + (255,))
    frames.append(im)
save(frames, f'{TEX}/graviton_core_glow.png', frametime=2)

H = "hammer"
els = [
    box([6.9, -1.2, 6.9], [9.1, 0.4, 9.1], all_faces(H, [8, 0, 12, 4])),                     # gold pommel
    box([7.35, 0.4, 7.35], [8.65, 11.6, 8.65], all_faces(H, [12, 0, 16, 4])),                 # wrapped haft
    box([7.0, 4.0, 7.0], [9.0, 4.8, 9.0], all_faces(H, [8, 0, 12, 1])),                        # gold rings
    box([7.0, 8.4, 7.0], [9.0, 9.2, 9.0], all_faces(H, [8, 0, 12, 1])),
    box([6.8, 11.2, 6.8], [9.2, 12.4, 9.2], all_faces(H, [8, 0, 12, 2])),                      # collar
    box([2.8, 12.2, 5.4], [13.2, 17.4, 10.6], all_faces(H, [0, 0, 4, 4])),                     # the head
    box([2.0, 11.8, 5.0], [3.0, 17.8, 11.0], all_faces(H, [4, 0, 8, 4])),                      # silver end caps
    box([13.0, 11.8, 5.0], [14.0, 17.8, 11.0], all_faces(H, [4, 0, 8, 4])),
    box([1.4, 12.8, 6.0], [2.0, 16.8, 10.0], all_faces(H, [4, 0, 8, 2])),                      # striking faces
    box([14.0, 12.8, 6.0], [14.6, 16.8, 10.0], all_faces(H, [4, 0, 8, 2])),
    box([5.8, 13.0, 5.1], [10.2, 16.6, 10.9], {"north": ("core", [0, 0, 16, 16]), "south": ("core", [0, 0, 16, 16]),
                                               "up": ("core", [0, 0, 16, 16])}),               # the glowing core
]
wjson(f'{CC}/models/item/graviton_hammer_3d.json', {
    "textures": {"hammer": "customcraft:item/graviton_hammer_3d", "core": "customcraft:item/graviton_core_glow",
                 "particle": "customcraft:item/graviton_hammer_3d"},
    "elements": els, "display": HANDHELD})

# ============================================================ effect textures (flat, seen through display entities)
def ring_tex(path, inner, outer, palette, ticks=0, alpha=255):
    n = 64
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c) / c
            if inner <= d <= outer:
                t = 1 - abs(d - (inner + outer) / 2) / ((outer - inner) / 2)
                col = ramp(palette, 0.3 + 0.65 * t)
                im.putpixel((x, y), tuple(col[:3]) + (alpha,))
            elif ticks and outer + 0.02 < d < outer + 0.12:
                a = math.atan2(y - c, x - c)
                if (a * ticks / (2 * math.pi)) % 1 < 0.18:
                    im.putpixel((x, y), tuple(ramp(palette, 0.95)[:3]) + (alpha,))
    im.save(path)


ring_tex(f'{TEX}/fx/echo_ring.png', 0.8, 0.95, CYAN)
ring_tex(f'{TEX}/fx/gravity_ring.png', 0.78, 0.9, CORE, ticks=12)

# slashes: a crescent, bright on the outer edge, fading toward the tips (one per colour)
def crescent(path, palette):
    n = 64
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    for y in range(n):
        for x in range(n):
            dx, dy = x - 32, y - 44
            d = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(-dy, dx))
            if 15 <= ang <= 165 and 16 <= d <= 31:
                along = 1 - abs(ang - 90) / 75                 # 1 in the middle, 0 at the tips
                thick = 16 + 14 * (1 - along)
                if d >= thick:
                    edge = (d - thick) / max(0.5, 31 - thick)
                    col = ramp(palette, 0.45 + 0.5 * edge)
                    im.putpixel((x, y), tuple(col[:3]) + (int(255 * min(1, along * 1.6)),))
    im.save(path)


SLASHES = {
    'echo': CYAN,
    'blood': [(90, 6, 16), (170, 20, 34), (235, 60, 60), (255, 170, 160)],
    'silk': [(170, 160, 150), (225, 220, 210), (250, 248, 240), (255, 255, 255)],
    'soul': [(40, 90, 120), (90, 200, 220), (170, 245, 255), (240, 255, 255)],
    'venom': [(40, 110, 20), (90, 200, 40), (170, 255, 90), (235, 255, 190)],
    'frost': [(80, 140, 200), (150, 210, 245), (215, 240, 255), (255, 255, 255)],
}
for key, pal in SLASHES.items():
    crescent(f'{TEX}/fx/{key}_slash.png', pal)
ring_tex(f'{TEX}/fx/soul_ring.png', 0.8, 0.95, SLASHES['soul'], ticks=8)
ring_tex(f'{TEX}/fx/blood_ring.png', 0.78, 0.95, SLASHES['blood'])

for name in ['echo_ring', 'gravity_ring', 'soul_ring', 'blood_ring'] + [k + '_slash' for k in SLASHES]:
    wjson(f'{CC}/models/item/fx/{name}.json', {"parent": "minecraft:item/generated",
                                                "textures": {"layer0": f"customcraft:item/fx/{name}"}})
    wjson(f'{CC}/items/fx_{name}.json', {"model": {"type": "minecraft:model", "model": f"customcraft:item/fx/{name}"}})
print('weapons3d done')


# ============================================================ the other melee weapons
# Each one: up to 16 materials (8x8 cells on a 32x32 sheet), boxes standing upright (handle at the bottom),
# optionally an animated glow texture. Same -45 degree turn and hold angles as above.
def cell_uv(i):
    return [i % 4 * 4, i // 4 * 4, i % 4 * 4 + 4, i // 4 * 4 + 4]


def solid(pal, base=0.5, var=0.25, seed=0, light_top=0.0):
    return lambda x, y: ramp(pal, max(0, min(0.99, base + var * (noise(x, y, seed) - 0.5) + light_top * (1 - y / 7))))


def bands(pal, a=0.3, b=0.8, every=3):
    return lambda x, y: ramp(pal, b if y % every == 0 else a)


def glow_anim(path, pal, kind='pulse', n=12):
    frames = []
    for f in range(n):
        im = Image.new('RGBA', (16, 16))
        for y in range(16):
            for x in range(16):
                if kind == 'flow':
                    w = ((15 - y) / 16 - f / n) % 1.0
                    t = 0.35 + 0.6 * max(0, 1 - abs(w - 0.5) * 4)
                else:
                    t = 0.5 + 0.35 * math.sin(2 * math.pi * f / n) + 0.12 * (noise(x // 2, y // 2, 9) - 0.5)
                im.putpixel((x, y), tuple(ramp(pal, max(0, min(0.99, t)))[:3]) + (255,))
        frames.append(im)
    save(frames, path, frametime=2)


def weapon(name, mats, parts, glow=None):
    keys = [k for k, _ in mats]
    s = sheet()
    for i, (_, fn) in enumerate(mats):
        fill(s, i % 4 * 8, i // 4 * 8, 8, 8, fn)
    s.save(f'{TEX}/{name}_3d.png')
    textures = {"t": f"customcraft:item/{name}_3d", "particle": f"customcraft:item/{name}_3d"}
    if glow:
        glow_anim(f'{TEX}/{name}_glow.png', glow[0], glow[1])
        textures["glow"] = f"customcraft:item/{name}_glow"
    els = []
    for part in parts:
        frm, to, mat = part[0], part[1], part[2]
        if mat == 'glow':
            faces = {f: ("glow", [0, 0, 16, 16]) for f in ("north", "south", "east", "west", "up", "down")}
        else:
            faces = {f: ("t", cell_uv(keys.index(mat))) for f in ("north", "south", "east", "west", "up", "down")}
        els.append(box(frm, to, faces))
    wjson(f'{CC}/models/item/{name}_3d.json', {"textures": textures, "elements": els, "display": HANDHELD})


def steps(points, half, z0, z1, mat):
    """A bent bar (blade, hook, arc) as a chain of small boxes through the given (x, y) points."""
    out = []
    for (x, y) in points:
        out.append(([x - half, y - half, z0], [x + half, y + half, z1], mat))
    return out


ICE = [(40, 90, 130), (80, 160, 210), (150, 220, 250), (230, 250, 255)]
DARK = [(14, 12, 16), (30, 26, 32), (52, 46, 56)]
SILV = [(80, 84, 96), (140, 146, 160), (210, 214, 224), (250, 250, 255)]
GREEN = [(10, 40, 20), (24, 80, 40), (50, 140, 60), (110, 210, 100), (200, 255, 170)]
VENOM = [(40, 120, 20), (90, 200, 40), (170, 255, 90), (230, 255, 180)]
BONE = [(150, 140, 110), (200, 192, 160), (238, 232, 206)]
WRAP = [(70, 58, 44), (120, 104, 82), (176, 160, 130)]
BLOOD = [(70, 4, 12), (130, 10, 24), (190, 30, 40)]
ABYSS = [(4, 12, 22), (10, 30, 48), (20, 56, 80), (40, 96, 120)]
TOOTH = [(170, 172, 150), (220, 222, 200), (250, 252, 240)]
LUREC = [(160, 120, 20), (240, 200, 60), (255, 240, 150), (255, 255, 230)]
REDS = [(90, 10, 16), (170, 24, 34), (230, 60, 60)]
WOOD = [(40, 26, 18), (70, 48, 32), (100, 72, 50)]
GOLDS2 = [(110, 70, 20), (180, 130, 40), (240, 200, 90)]
STEELD = [(28, 30, 36), (60, 62, 72), (110, 112, 124)]

# ---- Soulbinder: a scythe, dark haft with silver rings, an ice-blue blade sweeping back from the top
weapon('soulbinder', [
    ('haft', solid(DARK, 0.45, 0.3, 1)), ('silver', solid(SILV, 0.6, 0.3, 2, 0.3)),
    ('blade', lambda x, y: ramp(ICE, 0.35 + 0.5 * (1 - y / 7) + 0.1 * (noise(x, y, 3) - 0.5))),
    ('edge', solid(ICE, 0.9, 0.1, 4)),
], [
    ([7, -1, 7], [9, 0.5, 9], 'silver'),
    ([7.4, 0.5, 7.4], [8.6, 19.5, 8.6], 'haft'),
    ([7.1, 6, 7.1], [8.9, 6.8, 8.9], 'silver'), ([7.1, 12, 7.1], [8.9, 12.8, 8.9], 'silver'),
    ([7.0, 18.2, 7.0], [9.0, 20.2, 9.0], 'silver'),
    ([1.5, 18.4, 7.6], [7.2, 20.0, 8.4], 'blade'),
    ([-1.5, 17.2, 7.6], [1.6, 19.4, 8.4], 'blade'),
    ([-3.6, 15.6, 7.6], [-1.4, 18.2, 8.4], 'blade'),
    ([-5.0, 13.6, 7.65], [-3.4, 16.2, 8.35], 'blade'),
    ([-5.8, 12.0, 7.7], [-4.8, 14.0, 8.3], 'edge'),
    ([1.5, 17.6, 7.7], [7.0, 18.4, 8.3], 'glow'), ([-1.5, 16.6, 7.7], [1.5, 17.2, 8.3], 'glow'),
    ([-3.4, 15.0, 7.7], [-1.5, 15.6, 8.3], 'glow'),
], glow=(ICE, 'flow'))

# ---- Hydra Edge: wide green blade with scales and a venom fuller, a guard of serpent heads
weapon('hydra_edge', [
    ('blade', lambda x, y: ramp(GREEN, 0.4 + 0.18 * ((x // 2 + y // 2) % 2) + 0.1 * (noise(x, y, 5) - 0.5))),
    ('edge', solid(GREEN, 0.85, 0.15, 6)), ('guard', solid(GREEN, 0.15, 0.15, 7)),
    ('grip', bands(DARK, 0.3, 0.8, 3)), ('gem', solid(VENOM, 0.75, 0.3, 8)), ('fang', solid(BONE, 0.7, 0.2, 9)),
], [
    ([7, -1, 7], [9, 0.6, 9], 'gem'),
    ([7.4, 0.6, 7.4], [8.6, 4.6, 8.6], 'grip'),
    ([5.4, 4.6, 6.9], [10.6, 5.8, 9.1], 'guard'),
    ([4.0, 5.0, 7.0], [5.6, 6.8, 9.0], 'guard'), ([10.4, 5.0, 7.0], [12.0, 6.8, 9.0], 'guard'),   # side heads
    ([4.2, 6.6, 7.4], [5.0, 7.2, 8.6], 'gem'), ([11.0, 6.6, 7.4], [11.8, 7.2, 8.6], 'gem'),         # their eyes
    ([3.6, 4.4, 7.6], [4.2, 5.2, 8.4], 'fang'), ([11.8, 4.4, 7.6], [12.4, 5.2, 8.4], 'fang'),
    ([7.0, 5.8, 6.8], [9.0, 7.2, 9.2], 'guard'),                                                     # centre head
    ([6.5, 7.2, 7.55], [9.5, 18.0, 8.45], 'blade'),
    ([9.5, 8.0, 7.7], [10.1, 9.0, 8.3], 'edge'), ([9.5, 10.5, 7.7], [10.1, 11.5, 8.3], 'edge'),   # serrated back
    ([9.5, 13.0, 7.7], [10.1, 14.0, 8.3], 'edge'), ([9.5, 15.5, 7.7], [10.1, 16.5, 8.3], 'edge'),
    ([7.0, 18.0, 7.65], [9.0, 19.6, 8.35], 'blade'), ([7.6, 19.6, 7.75], [8.4, 20.6, 8.25], 'edge'),
    ([7.75, 7.6, 7.45], [8.25, 17.4, 8.55], 'glow'),
], glow=(VENOM, 'flow'))

# ---- Butcher's Cleaver: a big rectangular blade with blood on it, a thick spine, a wrapped handle with a bone knob
weapon('butcher_cleaver', [
    ('steel', lambda x, y: ramp(STEELD, 0.45 + 0.2 * (noise(x, y, 11) - 0.5)) if noise(x // 2, y // 2, 12) > 0.22
     else ramp(BLOOD, 0.4 + 0.3 * noise(x, y, 13))),
    ('edge', solid(SILV, 0.85, 0.15, 14)), ('spine', solid(STEELD, 0.15, 0.1, 15)),
    ('wrap', bands(WRAP, 0.3, 0.9, 3)), ('bone', solid(BONE, 0.7, 0.2, 16)), ('rivet', solid([(60, 30, 80), (130, 80, 170)], 0.7, 0.3, 17)),
], [
    ([6.8, -1.4, 6.8], [9.2, 0.6, 9.2], 'bone'),
    ([7.3, 0.6, 7.3], [8.7, 7.0, 8.7], 'wrap'),
    ([7.0, 7.0, 7.0], [9.0, 8.2, 9.0], 'spine'),
    ([6.6, 8.2, 7.2], [8.0, 19.4, 8.8], 'spine'),                     # thick spine above the handle
    ([8.0, 8.2, 7.6], [14.6, 19.4, 8.4], 'steel'),                    # the blade
    ([14.6, 8.6, 7.7], [15.3, 19.0, 8.3], 'edge'),                     # the cutting edge
    ([6.8, 11.0, 7.0], [7.8, 12.0, 9.0], 'rivet'), ([6.8, 15.0, 7.0], [7.8, 16.0, 9.0], 'rivet'),
])

# ---- Abyssal Maw: a dark blade with fangs on both edges, a fish-jaw guard and a glowing lure on a stalk
weapon('abyssal_maw', [
    ('blade', lambda x, y: ramp(ABYSS, 0.4 + 0.25 * (1 - abs(x - 3.5) / 3.5) + 0.1 * (noise(x, y, 18) - 0.5))),
    ('tooth', solid(TOOTH, 0.8, 0.2, 19)), ('skin', solid(ABYSS, 0.2, 0.2, 20)), ('grip', bands(ABYSS, 0.1, 0.5, 2)),
    ('eye', solid(LUREC, 0.8, 0.2, 21)),
], [
    ([7, -1, 7], [9, 0.6, 9], 'glow'),
    ([7.4, 0.6, 7.4], [8.6, 4.6, 8.6], 'grip'),
    ([5.2, 4.6, 6.8], [10.8, 6.6, 9.2], 'skin'),
    ([5.6, 5.6, 6.6], [6.4, 6.4, 9.4], 'eye'),
    ([6.8, 6.6, 7.6], [9.2, 19.2, 8.4], 'blade'),
    ([7.4, 19.2, 7.7], [8.6, 20.6, 8.3], 'blade'),
] + [([6.3, y, 7.7], [6.8, y + 0.9, 8.3], 'tooth') for y in (7.5, 10, 12.5, 15, 17.5)]
  + [([9.2, y + 1.2, 7.7], [9.7, y + 2.1, 8.3], 'tooth') for y in (7.5, 10, 12.5, 15)]
  + steps([(5.0, 7.4), (4.2, 8.6), (3.6, 9.9), (3.4, 11.2), (3.6, 12.4)], 0.3, 7.7, 8.3, 'skin')
  + [([3.0, 12.6, 7.2], [4.8, 14.4, 8.8], 'glow')], glow=(LUREC, 'pulse'))

# ---- Clawline Pick: a curved grey pick head with red tips on a brown haft
weapon('clawline_pick', [
    ('haft', solid(WOOD, 0.45, 0.25, 22)), ('head', lambda x, y: ramp(SILV, 0.25 + 0.5 * (1 - y / 7) + 0.1 * (noise(x, y, 23) - 0.5))),
    ('tip', solid(REDS, 0.6, 0.3, 24)), ('band', solid(STEELD, 0.4, 0.2, 25)),
], [
    ([7.4, -0.5, 7.4], [8.6, 17.0, 8.6], 'haft'),
    ([7.1, 2.0, 7.1], [8.9, 2.8, 8.9], 'band'),
    ([6.8, 15.6, 6.8], [9.2, 18.4, 9.2], 'band'),
    ([4.6, 16.4, 7.3], [11.4, 18.2, 8.7], 'head'),
    ([2.4, 15.4, 7.35], [4.8, 17.6, 8.65], 'head'), ([11.2, 15.4, 7.35], [13.6, 17.6, 8.65], 'head'),
    ([0.9, 13.6, 7.4], [2.7, 16.0, 8.6], 'head'), ([13.3, 13.6, 7.4], [15.1, 16.0, 8.6], 'head'),
    ([0.2, 11.8, 7.5], [1.4, 13.8, 8.5], 'tip'), ([14.6, 11.8, 7.5], [15.8, 13.8, 8.5], 'tip'),
])

# ---- Silk Needle: a long thin needle-blade, a red grip, a small ring guard and a loop of silk thread
weapon('silk_needle', [
    ('needle', lambda x, y: ramp(SILV, 0.45 + 0.4 * (1 - abs(x - 3.5) / 3.5))), ('grip', bands(REDS, 0.3, 0.8, 2)),
    ('guard', solid(SILV, 0.75, 0.2, 26)), ('silk', solid([(220, 214, 200), (245, 240, 230), (255, 255, 250)], 0.7, 0.3, 27)),
    ('eye', solid(DARK, 0.2, 0.1, 28)),
], [
    ([7.3, -0.6, 7.3], [8.7, 0.6, 8.7], 'guard'),
    ([7.45, 0.6, 7.45], [8.55, 4.8, 8.55], 'grip'),
    ([6.2, 4.8, 7.2], [9.8, 5.4, 8.8], 'guard'), ([6.0, 5.4, 7.2], [6.6, 6.4, 8.8], 'guard'),
    ([9.4, 5.4, 7.2], [10.0, 6.4, 8.8], 'guard'),
    ([7.55, 5.4, 7.65], [8.45, 21.0, 8.35], 'needle'),
    ([7.75, 21.0, 7.75], [8.25, 22.4, 8.25], 'needle'),
    ([7.75, 19.0, 7.6], [8.25, 20.0, 8.4], 'eye'),
] + steps([(7.0, -1.2), (6.0, -1.8), (5.0, -1.6), (4.0, -2.2), (3.0, -2.0), (2.0, -2.6)], 0.25, 7.75, 8.25, 'silk'))

# ---- Rimebreaker: a war axe, dark haft with frost bands, a big ice blade and a back spike
weapon('rimebreaker', [
    ('haft', solid(DARK, 0.4, 0.3, 29)), ('band', solid(ICE, 0.7, 0.3, 30)),
    ('blade', lambda x, y: ramp(ICE, 0.3 + 0.45 * (x / 7) + 0.12 * (noise(x, y, 31) - 0.5))),
    ('edge', solid(ICE, 0.95, 0.05, 32)), ('frost', solid([(200, 230, 245), (240, 250, 255)], 0.7, 0.3, 33)),
], [
    ([7.1, -1.0, 7.1], [8.9, 0.4, 8.9], 'band'),
    ([7.4, 0.4, 7.4], [8.6, 19.0, 8.6], 'haft'),
    ([7.1, 5.0, 7.1], [8.9, 5.8, 8.9], 'band'), ([7.1, 10.0, 7.1], [8.9, 10.8, 8.9], 'band'),
    ([6.8, 13.4, 6.8], [9.2, 18.2, 9.2], 'frost'),
    ([1.6, 12.6, 7.55], [6.8, 19.0, 8.45], 'blade'),
    ([0.6, 11.4, 7.6], [2.2, 20.2, 8.4], 'blade'),
    ([-0.2, 11.0, 7.7], [0.8, 20.6, 8.3], 'edge'),
    ([9.2, 14.6, 7.6], [11.6, 16.6, 8.4], 'blade'), ([11.6, 15.0, 7.7], [13.0, 16.2, 8.3], 'edge'),
    ([7.6, 19.0, 7.6], [8.4, 20.4, 8.4], 'edge'),
])

# ---- Knightfall: a mace, a gold and red haft, a dark spiked head with a red gem
weapon('knightfall', [
    ('haft', bands(GOLDS2, 0.35, 0.85, 3)), ('red', solid(REDS, 0.5, 0.3, 34)),
    ('head', solid(STEELD, 0.4, 0.3, 35, 0.3)), ('spike', solid(SILV, 0.75, 0.25, 36)), ('gem', solid(REDS, 0.85, 0.15, 37)),
], [
    ([7.0, -1.2, 7.0], [9.0, 0.6, 9.0], 'red'),
    ([7.4, 0.6, 7.4], [8.6, 13.0, 8.6], 'haft'),
    ([7.0, 6.0, 7.0], [9.0, 7.0, 9.0], 'red'),
    ([5.2, 13.0, 5.2], [10.8, 18.6, 10.8], 'head'),
    ([7.2, 14.8, 4.8], [8.8, 16.6, 5.2], 'gem'), ([7.2, 14.8, 10.8], [8.8, 16.6, 11.2], 'gem'),
    ([3.8, 15.0, 7.4], [5.2, 16.4, 8.6], 'spike'), ([10.8, 15.0, 7.4], [12.2, 16.4, 8.6], 'spike'),
    ([7.4, 18.6, 7.4], [8.6, 20.0, 8.6], 'spike'),
    ([5.6, 17.6, 5.6], [6.6, 18.8, 6.6], 'spike'), ([9.4, 17.6, 9.4], [10.4, 18.8, 10.4], 'spike'),
    ([5.6, 12.8, 9.4], [6.6, 14.0, 10.4], 'spike'), ([9.4, 12.8, 5.6], [10.4, 14.0, 6.6], 'spike'),
])
print('melee 3d done')
