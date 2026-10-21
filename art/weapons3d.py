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



