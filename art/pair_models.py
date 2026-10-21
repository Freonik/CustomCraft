"""Hand-built 3D in-hand models, two weapons at a time, drawn like the Echo Blade and Graviton Hammer (weapons3d.py)
but laid out in the sprite's own picture plane: every part is a box turned on its own (haft diagonal, a scythe blade
flat and bending in 22.5 degree steps), so the silhouette follows the 2D sprite.
Coordinates are sprite pixels / 2 (model units), y up."""
import sys, os, json, math
sys.path.insert(0, '/home/claude/art')
from px import *
from PIL import Image

CC = sys.argv[1]
TEX = f'{CC}/textures/item'
HANDHELD = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}


def noise(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) % 1000 / 1000


class Sheet:
    """A 64x64 texture split into named rectangles; uv() gives a rectangle in model uv units (0..16)."""
    def __init__(self, name):
        self.name = name
        self.img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.rects = {}

    def paint(self, key, x, y, w, h, fn):
        for yy in range(h):
            for xx in range(w):
                c = fn(xx, yy, w, h)
                self.img.putpixel((x + xx, y + yy), tuple(int(v) for v in c[:3]) + (255,))
        self.rects[key] = (x, y, w, h)

    def uv(self, key):
        x, y, w, h = self.rects[key]
        k = 16 / 64
        return [x * k, y * k, (x + w) * k, (y + h) * k]

    def save(self):
        self.img.save(f'{TEX}/{self.name}.png')


def seg(cx, cy, length, thick, z0, z1, direction, side, ends=None, front=None):
    """A box whose long side points at `direction` degrees (0 = right, 90 = up; multiples of 22.5).
    side / ends / front are (texture, uv) for the long faces, the two end faces and the two big faces
    (front defaults to side)."""
    d = direction % 180
    if 45 <= d <= 135:
        frm, to, angle, vertical = [cx - thick / 2, cy - length / 2, z0], [cx + thick / 2, cy + length / 2, z1], d - 90, True
    else:
        frm, to, angle, vertical = [cx - length / 2, cy - thick / 2, z0], [cx + length / 2, cy + thick / 2, z1], \
            (d if d <= 45 else d - 180), False
    front = front or side
    ends = ends or side
    if vertical:
        faces = {"north": front, "south": front, "east": side, "west": side, "up": ends, "down": ends}
    else:
        faces = {"north": front, "south": front, "up": side, "down": side, "east": ends, "west": ends}
    el = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to],
          "faces": {f: {"uv": uv, "texture": "#" + t} for f, (t, uv) in faces.items()}}
    if angle:
        el["rotation"] = {"angle": angle, "axis": "z", "origin": [cx, cy, 8]}
    return el


def write_model(name, textures, els):
    with open(f'{CC}/models/item/{name}_3d.json', 'w') as f:
        json.dump({"textures": textures | {"particle": textures["t"]}, "elements": els, "display": HANDHELD}, f, indent=1)


# ============================================================ Soulbinder
ICEB = [(10, 40, 70), (24, 84, 120), (50, 150, 190), (110, 210, 235), (210, 250, 255), (255, 255, 255)]
DARKW = [(14, 10, 16), (30, 24, 34), (52, 44, 58)]
SILV = [(90, 94, 108), (150, 156, 170), (215, 220, 230), (250, 250, 255)]

s = Sheet('soulbinder_3d')
# blade face: dark blue spine on top, cyan body, a white cutting edge at the bottom; faint frost streaks
s.paint('blade', 0, 0, 32, 16, lambda x, y, w, h: ramp(ICEB, min(0.99, 0.12 + 0.75 * (y / (h - 1)) ** 1.3
                                                                   + (0.12 if (x * 3 + y * 7) % 11 == 0 else 0))))
s.paint('edge', 32, 0, 16, 4, lambda x, y, w, h: ramp(ICEB, 0.95 - 0.15 * y / 3))
s.paint('spine', 48, 0, 16, 4, lambda x, y, w, h: ramp(ICEB, 0.08 + 0.1 * noise(x, y, 1)))
s.paint('haft', 32, 8, 8, 32, lambda x, y, w, h: ramp(DARKW, 0.2 + 0.5 * (1 - abs(x - 3.5) / 3.5)
                                                    + (0.25 if (y + x // 3) % 6 == 0 else 0)))
s.paint('silver', 40, 8, 8, 8, lambda x, y, w, h: ramp(SILV, 0.3 + 0.6 * (1 - y / 7)))
s.paint('wisp', 48, 8, 8, 8, lambda x, y, w, h: ramp(ICEB, 0.85 + 0.14 * (1 - abs(x - 3.5) / 3.5)))
s.save()
t = lambda k: ("t", s.uv(k))
els = []
# the haft: pommel at the bottom left, up to the blade at the top right
els.append(seg(6.6, 6.6, 14.6, 1.2, 7.4, 8.6, 45, t('haft'), ends=t('silver')))
els.append(seg(1.3, 1.3, 1.5, 1.8, 7.1, 8.9, 45, t('silver')))                                 # pommel
for k in (0.33, 0.62):
    p = 1.3 + 10.6 * k
    els.append(seg(p, p, 0.7, 1.7, 7.2, 8.8, 45, t('silver')))                                  # rings
els.append(seg(11.9, 11.9, 1.9, 2.0, 7.0, 9.0, 45, t('silver')))                               # the collar
# the blade sweeps out to the left from the collar, bending down toward its point
# (centre x, centre y, length, height, direction): along the sprite's crescent, overlapping so no seams show
path = [(11.4, 12.4), (9.6, 12.95), (7.8, 13.35), (6.0, 13.45), (4.3, 13.2), (2.8, 12.6), (1.6, 11.8), (0.6, 11.0)]
heights = [2.6, 2.6, 2.5, 2.3, 2.0, 1.6, 1.15, 0.7]
blade = []
for i in range(len(path) - 1):
    (x0, y0), (x1, y1) = path[i], path[i + 1]
    ang = math.degrees(math.atan2(y1 - y0, x1 - x0)) % 180
    ang = round(ang / 22.5) * 22.5
    blade.append(((x0 + x1) / 2, (y0 + y1) / 2, math.hypot(x1 - x0, y1 - y0) + 0.7, (heights[i] + heights[i + 1]) / 2, ang))
for i, (cx, cy, ln, th, dr) in enumerate(blade):
    z = 0.42 - i * 0.03
    els.append(seg(cx, cy, ln, th, 8 - z, 8 + z, dr, t('spine'), ends=t('spine'), front=t('blade')))
    # the white cutting edge along the underside, a hair proud of the blade
    a = math.radians(dr)
    ex, ey = cx + math.sin(a) * th / 2, cy - math.cos(a) * th / 2
    els.append(seg(ex, ey, ln * 0.96, 0.45, 8 - z - 0.05, 8 + z + 0.05, dr, t('edge')))
    # a glowing streak through the middle of the blade (animated)
    if i < 5:
        els.append(seg(cx, cy, ln * 0.9, th * 0.22, 8 - z - 0.03, 8 + z + 0.03, dr, ("glow", [0, 0, 16, 4])))
# soul wisps hovering over the blade
for (wx, wy, sz) in [(1.6, 14.6, 0.55), (4.6, 15.4, 0.6), (7.4, 15.9, 0.5), (9.6, 15.2, 0.4)]:
    els.append(seg(wx, wy, sz, sz, 8 - sz / 2, 8 + sz / 2, 0, t('wisp')))
glow = []
for f in range(16):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            w = (x / 16 + f / 16) % 1.0
            g = max(0, 1 - abs(w - 0.5) * 4)
            im.putpixel((x, y), tuple(ramp(ICEB, 0.55 + 0.42 * g)[:3]) + (255,))
    glow.append(im)
save(glow, f'{TEX}/soulbinder_glow.png', frametime=2)
write_model('soulbinder', {"t": "customcraft:item/soulbinder_3d", "glow": "customcraft:item/soulbinder_glow"}, els)

# ============================================================ Hydra Edge
GRN = [(8, 34, 22), (14, 60, 36), (26, 96, 52), (50, 140, 70), (100, 196, 110), (190, 240, 200)]
VEN = [(40, 120, 20), (90, 200, 40), (170, 255, 90), (230, 255, 180)]
GRIPC = [(36, 24, 18), (64, 44, 30), (92, 66, 44)]
FANG = [(170, 168, 140), (225, 222, 200), (250, 250, 236)]

s = Sheet('hydra_edge_3d')
# blade face: overlapping scales in diagonal rows, a pale bevel along one edge, darker toward the other
def scales(x, y, w, h):
    row = (y + x) // 3
    t_ = 0.35 + 0.18 * (row % 2) + 0.2 * (1 - x / (w - 1))
    if x == 0:
        t_ = 0.92
    elif x == w - 1:
        t_ = 0.12
    return ramp(GRN, min(0.99, t_))
s.paint('blade', 0, 0, 8, 48, scales)
s.paint('bladeside', 8, 0, 4, 48, lambda x, y, w, h: ramp(GRN, 0.75 if y % 3 else 0.55))
s.paint('guard', 16, 0, 16, 8, lambda x, y, w, h: ramp(GRN, 0.08 + 0.22 * (1 - y / 7) + 0.08 * noise(x, y, 4)))
s.paint('head', 32, 0, 8, 8, lambda x, y, w, h: ramp(GRN, 0.12 + 0.3 * (1 - (x + y) / 14)
                                                    + (0.15 if (x // 2 + y // 2) % 2 else 0)))
s.paint('eye', 40, 0, 4, 4, lambda x, y, w, h: ramp(FANG, 0.95))
s.paint('fang', 44, 0, 4, 4, lambda x, y, w, h: ramp(FANG, 0.9 - 0.2 * y / 3))
s.paint('grip', 16, 8, 8, 24, lambda x, y, w, h: ramp(GRIPC, 0.85 if (y + (x > 3)) % 3 == 0 else 0.35))
s.paint('gem', 24, 8, 8, 8, lambda x, y, w, h: ramp(VEN, min(0.99, 0.55 + 0.4 * (1 - (x + y) / 14)
                                                            + (0.3 if (x, y) in ((2, 2), (2, 3), (3, 2)) else 0))))
s.save()
t = lambda k: ("t", s.uv(k))
els = []
els.append(seg(2.5, 2.5, 1.6, 1.6, 7.2, 8.8, 45, t('gem')))                                    # venom gem pommel
els.append(seg(4.6, 4.6, 4.2, 1.3, 7.35, 8.65, 45, t('grip'), ends=t('guard')))                # grip
els.append(seg(7.3, 7.3, 6.0, 1.1, 7.2, 8.8, 135, t('guard')))                                  # cross guard
# the two serpent heads at the ends of the guard, looking outward, and the middle one up the blade
els.append(seg(5.0, 9.6, 1.9, 1.6, 7.1, 8.9, 135, t('head')))
els.append(seg(4.4, 10.3, 0.55, 0.55, 7.0, 9.0, 135, t('eye')))
els.append(seg(9.6, 5.0, 1.9, 1.6, 7.1, 8.9, 135, t('head')))
els.append(seg(10.3, 4.4, 0.55, 0.55, 7.0, 9.0, 135, t('eye')))
els.append(seg(4.4, 9.1, 0.8, 0.35, 7.5, 8.5, 90, t('fang')))
els.append(seg(10.0, 4.4, 0.8, 0.35, 7.5, 8.5, 0, t('fang')))
els.append(seg(8.2, 8.2, 1.4, 1.5, 7.2, 8.8, 45, t('head')))
# the blade, a scale-plated slab, and its point
els.append(seg(11.7, 11.7, 8.6, 2.6, 7.55, 8.45, 45, t('bladeside'), ends=t('bladeside'), front=t('blade')))
els.append(seg(15.6, 15.6, 1.2, 1.6, 7.65, 8.35, 45, t('bladeside'), front=t('blade')))
els.append(seg(16.3, 16.3, 0.7, 0.8, 7.75, 8.25, 45, t('bladeside')))
els.append(seg(11.6, 11.6, 7.6, 0.45, 7.47, 8.53, 45, ("glow", [6, 0, 10, 16])))                 # the venom fuller
glow = []
for f in range(12):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        w = ((15 - y) / 16 - f / 12) % 1.0
        g = max(0, 1 - abs(w - 0.5) * 5)
        for x in range(16):
            im.putpixel((x, y), tuple(ramp(VEN, 0.3 + 0.65 * g)[:3]) + (255,))
    glow.append(im)
save(glow, f'{TEX}/hydra_edge_glow.png', frametime=2)
write_model('hydra_edge', {"t": "customcraft:item/hydra_edge_3d", "glow": "customcraft:item/hydra_edge_glow"}, els)
print('pair models done')
