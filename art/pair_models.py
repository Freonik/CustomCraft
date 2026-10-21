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

# ============================================================ Butcher's Cleaver
STEELC = [(26, 24, 30), (44, 42, 50), (66, 64, 74), (96, 94, 104), (140, 138, 146), (200, 200, 208), (245, 245, 250)]
BLOODC = [(70, 4, 12), (120, 10, 22), (170, 24, 34)]
RUSTC = [(90, 44, 20), (130, 70, 30)]
PLAGUEC = [(70, 34, 96), (120, 70, 170), (170, 120, 220)]
BONEC = [(150, 140, 110), (205, 196, 165), (238, 232, 206)]
WRAPC = [(80, 66, 50), (130, 112, 88), (180, 164, 134)]

def cleaver_face(x, y, w, h):
    # hammered steel, darker toward the spine (right), blood splashes and a rust stain, a plague sheen
    t_ = 0.3 + 0.25 * (1 - x / (w - 1)) + 0.06 * ((x // 3 + y // 3) % 2)
    col = ramp(STEELC, t_)
    if (x - 9) ** 2 / 9 + (y - 18) ** 2 / 16 < 1 or (x - 4) ** 2 / 4 + (y - 30) ** 2 / 9 < 1 or (x - 12) ** 2 / 3 + (y - 8) ** 2 / 5 < 1:
        col = ramp(BLOODC, 0.3 + 0.6 * noise(x, y, 40))
    elif (x - 6) ** 2 / 4 + (y - 7) ** 2 / 3 < 1:
        col = ramp(RUSTC, 0.6)
    elif x > w - 5 and (x + y) % 5 == 0:
        col = ramp(PLAGUEC, 0.5)
    return col

s = Sheet('butcher_cleaver_3d')
s.paint('face', 0, 0, 16, 44, cleaver_face)
s.paint('edge', 16, 0, 4, 44, lambda x, y, w, h: ramp(STEELC, 0.92 if (y + 3) % 9 else 0.99))
s.paint('spine', 20, 0, 4, 44, lambda x, y, w, h: ramp(STEELC, 0.08 + 0.08 * noise(x, y, 41)))
s.paint('wrap', 24, 0, 8, 24, lambda x, y, w, h: ramp(WRAPC, 0.15 if y % 4 == 0 else 0.55 + 0.3 * (1 - abs(x - 3.5) / 3.5)))
s.paint('bone', 32, 0, 8, 8, lambda x, y, w, h: ramp(BONEC, 0.35 + 0.6 * (1 - (x + y) / 14)))
s.paint('bolster', 40, 0, 8, 8, lambda x, y, w, h: ramp(STEELC, 0.35 + 0.35 * (1 - y / 7)))
s.paint('rivet', 48, 0, 4, 4, lambda x, y, w, h: ramp(PLAGUEC, 0.95 if (x, y) == (1, 1) else 0.55))
s.paint('red', 52, 0, 4, 4, lambda x, y, w, h: ramp(BLOODC, 0.95))
s.save()
t = lambda k: ("t", s.uv(k))
els = []
d45 = (math.sqrt(0.5), math.sqrt(0.5))          # along the weapon
perp = (-math.sqrt(0.5), math.sqrt(0.5))        # across it, toward the cutting edge
els.append(seg(1.25, 1.45, 1.9, 1.9, 7.0, 9.0, 45, t('bone')))                                  # bone knob
els.append(seg(3.6, 3.85, 5.6, 1.3, 7.35, 8.65, 45, t('wrap')))                                # wrapped handle
els.append(seg(6.2, 6.45, 1.6, 2.2, 7.1, 8.9, 45, t('bolster')))                               # bolster
bc = (8.9, 10.4)                                                                                # blade centre
els.append(seg(bc[0], bc[1], 10.8, 4.4, 7.62, 8.38, 45, t('edge'), ends=t('spine'), front=t('face')))
def along(c, a, p):
    return (c[0] + d45[0] * a + perp[0] * p, c[1] + d45[1] * a + perp[1] * p)
ex, ey = along(bc, 0, 2.35)
els.append(seg(ex, ey, 10.4, 0.45, 7.68, 8.32, 45, t('edge')))                                 # the cutting edge
sx, sy = along(bc, -0.2, -2.35)
els.append(seg(sx, sy, 10.6, 0.7, 7.3, 8.7, 45, t('spine')))                                   # the thick spine
for a in (-3.2, 1.0):
    rx, ry = along(bc, a, -1.9)
    els.append(seg(rx, ry, 0.6, 0.6, 7.2, 8.8, 45, t('rivet')))                                # plague rivets
hx, hy = along(bc, 3.6, -0.9)
els.append(seg(hx, hy, 0.8, 0.8, 7.55, 8.45, 45, ("t", s.uv('spine'))))                        # the hanging hole
kx, ky = along(bc, -4.6, 2.6)
els.append(seg(kx, ky, 0.6, 0.6, 7.6, 8.4, 45, t('red')))                                      # the red notch
write_model('butcher_cleaver', {"t": "customcraft:item/butcher_cleaver_3d"}, els)

# ============================================================ Abyssal Maw
ABY = [(4, 12, 22), (8, 24, 40), (14, 40, 62), (22, 62, 88), (40, 96, 120), (90, 160, 170)]
GLOWC = [(20, 110, 120), (40, 200, 200), (140, 255, 240), (230, 255, 250)]
TOOTHC = [(170, 172, 150), (222, 224, 204), (252, 252, 242)]
LUREY = [(160, 120, 20), (240, 200, 60), (255, 240, 150), (255, 255, 230)]
SKINC = [(8, 26, 32), (16, 48, 56), (30, 80, 90)]

s = Sheet('abyssal_maw_3d')
s.paint('blade', 0, 0, 8, 48, lambda x, y, w, h: ramp(ABY, min(0.99, 0.38 + 0.3 * (1 - abs(x - 3.5) / 3.5)
                                                                    + (0.1 if (y // 2) % 2 else 0) + (0.35 if x == 0 else 0))))
s.paint('bladeside', 8, 0, 4, 48, lambda x, y, w, h: ramp(ABY, 0.45))
s.paint('tooth', 12, 0, 4, 4, lambda x, y, w, h: ramp(TOOTHC, 0.95 - 0.25 * y / 3))
s.paint('skin', 16, 0, 8, 8, lambda x, y, w, h: ramp(SKINC, 0.25 + 0.6 * (1 - y / 7) + 0.1 * noise(x, y, 50)))
s.paint('grip', 24, 0, 8, 16, lambda x, y, w, h: ramp(SKINC, 0.9 if (y + (x > 3)) % 3 == 0 else 0.25))
s.paint('eye', 32, 0, 4, 4, lambda x, y, w, h: ramp(LUREY, 0.9))
s.paint('pommel', 36, 0, 8, 8, lambda x, y, w, h: ramp(GLOWC, 0.5 + 0.45 * (1 - (x + y) / 14)))
s.save()
t = lambda k: ("t", s.uv(k))
els = []
els.append(seg(1.4, 1.4, 1.8, 1.8, 7.1, 8.9, 45, t('pommel')))                                 # glowing pommel
els.append(seg(3.1, 3.1, 3.4, 1.25, 7.38, 8.62, 45, t('grip')))                                # grip
els.append(seg(5.3, 5.3, 4.4, 1.5, 7.0, 9.0, 135, t('skin')))                                  # the fish-jaw guard
els.append(seg(4.0, 6.7, 0.6, 0.6, 6.9, 9.1, 135, t('eye')))                                   # its eye
bc = (10.0, 10.0)
els.append(seg(bc[0], bc[1], 12.8, 2.4, 7.6, 8.4, 45, t('bladeside'), front=t('blade')))
els.append(seg(14.75, 14.75, 1.4, 1.4, 7.7, 8.3, 45, t('bladeside'), front=t('blade')))
els.append(seg(bc[0] + 0.3, bc[1] + 0.3, 11.2, 0.55, 7.45, 8.55, 45, ("glow", [5, 0, 11, 16])))             # the glowing line
for k in range(6):                                                                             # fangs on both edges
    a = -4.4 + k * 1.85
    for side, off in ((1, 1.45), (-1, -1.45)):
        if side < 0 and k == 5:
            continue
        tx, ty = along(bc, a + (0.9 if side < 0 else 0), off)
        els.append(seg(tx, ty, 0.55, 0.7, 7.75, 8.25, 45 if side > 0 else 45, t('tooth')))
# the lure: a stalk curling up from the guard, and its glowing bulb
stalk = [(4.2, 6.6), (3.1, 7.9), (2.6, 9.4), (2.9, 10.9), (3.9, 12.0), (5.1, 12.4)]
for i in range(len(stalk) - 1):
    (x0, y0), (x1, y1) = stalk[i], stalk[i + 1]
    ang = round((math.degrees(math.atan2(y1 - y0, x1 - x0)) % 180) / 22.5) * 22.5
    els.append(seg((x0 + x1) / 2, (y0 + y1) / 2, math.hypot(x1 - x0, y1 - y0) + 0.35, 0.45, 7.78, 8.22, ang, t('skin')))
els.append(seg(6.1, 12.1, 1.5, 1.5, 7.25, 8.75, 0, ("glow2", [0, 0, 16, 16])))
glow = []
for f in range(12):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        w = ((15 - y) / 16 - f / 12) % 1.0
        g = max(0, 1 - abs(w - 0.5) * 5)
        for x in range(16):
            im.putpixel((x, y), tuple(ramp(GLOWC, 0.35 + 0.6 * g)[:3]) + (255,))
    glow.append(im)
save(glow, f'{TEX}/abyssal_maw_glow.png', frametime=2)
bulb = []
for f in range(12):
    p_ = 0.5 + 0.5 * math.sin(2 * math.pi * f / 12)
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 8
            im.putpixel((x, y), tuple(ramp(LUREY, max(0, min(0.99, 0.95 - d * 0.6 + 0.2 * p_ - 0.1)))[:3]) + (255,))
    bulb.append(im)
save(bulb, f'{TEX}/abyssal_maw_lure.png', frametime=2)
write_model('abyssal_maw', {"t": "customcraft:item/abyssal_maw_3d", "glow": "customcraft:item/abyssal_maw_glow",
                            "glow2": "customcraft:item/abyssal_maw_lure"}, els)

def chain(points, heights, z, mat, ends=None, front=None, overlap=0.4, depthfn=None):
    """Boxes along a bent path (one per pair of points), angles rounded to 22.5 degrees."""
    out = []
    for i in range(len(points) - 1):
        (x0, y0), (x1, y1) = points[i], points[i + 1]
        ang = round((math.degrees(math.atan2(y1 - y0, x1 - x0)) % 180) / 22.5) * 22.5
        h = (heights[i] + heights[i + 1]) / 2
        zz = depthfn(i) if depthfn else z
        out.append(seg((x0 + x1) / 2, (y0 + y1) / 2, math.hypot(x1 - x0, y1 - y0) + overlap, h, 8 - zz, 8 + zz, ang,
                       mat, ends=ends, front=front))
    return out


# ============================================================ Clawline Pick
PSTEEL = [(40, 42, 50), (70, 72, 84), (110, 112, 126), (160, 162, 176), (215, 216, 228)]
PWOOD = [(36, 22, 14), (66, 44, 28), (96, 66, 42), (126, 90, 60)]
PRED = [(110, 14, 20), (200, 40, 44), (250, 100, 96)]

s = Sheet('clawline_pick_3d')
s.paint('head', 0, 0, 32, 8, lambda x, y, w, h: ramp(PSTEEL, min(0.99, 0.25 + 0.65 * (1 - y / (h - 1)) ** 1.4
                                                                  + 0.05 * noise(x, y, 60))))
s.paint('headside', 32, 0, 16, 4, lambda x, y, w, h: ramp(PSTEEL, 0.5 + 0.1 * noise(x, y, 61)))
s.paint('wood', 0, 8, 8, 40, lambda x, y, w, h: ramp(PWOOD, 0.25 + 0.5 * (1 - abs(x - 3.5) / 3.5)
                                                   + (0.12 if (x * 5 + y) % 9 == 0 else 0)))
s.paint('red', 8, 8, 8, 8, lambda x, y, w, h: ramp(PRED, 0.4 + 0.55 * (1 - (x + y) / 14)))
s.paint('cap', 16, 8, 8, 8, lambda x, y, w, h: ramp(PSTEEL, 0.4 + 0.5 * (1 - y / 7)))
s.save()
t = lambda k: ("t", s.uv(k))
els = []
els.append(seg(1.3, 1.3, 1.2, 1.6, 7.2, 8.8, 45, t('cap')))                                     # end cap
els.append(seg(5.3, 5.3, 10.4, 1.15, 7.42, 8.58, 45, t('wood'), ends=t('cap')))                # haft
els.append(seg(9.1, 9.1, 1.8, 1.9, 7.05, 8.95, 45, t('red')))                                   # red binding
arc = [(2.6, 11.2), (4.6, 12.1), (7.0, 12.25), (9.2, 11.7), (10.8, 10.5), (11.65, 8.7), (11.85, 6.5), (11.7, 4.4),
       (11.45, 3.0)]
heights = [0.9, 1.35, 1.6, 1.75, 1.8, 1.75, 1.6, 1.3, 0.9]
els += chain(arc, heights, 0.55, t('headside'), ends=t('headside'), front=t('head'),
             depthfn=lambda i: 0.6 - 0.12 * abs(i - 3.5) / 3.5)
els.append(seg(2.2, 10.9, 1.2, 1.0, 7.5, 8.5, 22.5, t('red')))                                 # red tips
els.append(seg(11.4, 2.5, 1.2, 1.0, 7.5, 8.5, 90, t('red')))
write_model('clawline_pick', {"t": "customcraft:item/clawline_pick_3d"}, els)

# ============================================================ Silk Needle
NSTEEL = [(60, 62, 74), (120, 124, 138), (190, 194, 206), (235, 238, 245), (255, 255, 255)]
NRED = [(60, 8, 14), (110, 16, 26), (170, 34, 44)]
SILKC = [(205, 198, 182), (238, 234, 222), (255, 255, 250)]

s = Sheet('silk_needle_3d')
s.paint('blade', 0, 0, 4, 56, lambda x, y, w, h: ramp(NSTEEL, [0.95, 0.75, 0.5, 0.3][x]))
s.paint('bladeside', 4, 0, 4, 56, lambda x, y, w, h: ramp(NSTEEL, 0.55))
s.paint('ring', 8, 0, 8, 8, lambda x, y, w, h: ramp(SILKC, 0.35 + 0.6 * (1 - y / 7)))
s.paint('grip', 16, 0, 8, 16, lambda x, y, w, h: ramp(NRED, 0.85 if (y + (x > 3)) % 3 == 0 else 0.3))
s.paint('silk', 24, 0, 8, 8, lambda x, y, w, h: ramp(SILKC, 0.6 + 0.35 * noise(x, y, 70)))
s.save()
t = lambda k: ("t", s.uv(k))
els = []
els.append(seg(1.3, 1.6, 1.0, 1.0, 7.5, 8.5, 45, t('ring')))                                    # pommel bead
els.append(seg(3.0, 3.3, 3.6, 0.95, 7.52, 8.48, 45, t('grip'), ends=t('ring')))                 # red grip
# the ring guard: an open loop of silk-white metal around the base of the blade
rc = (4.9, 5.2)
for k in range(8):
    a = math.radians(k * 45 + 22.5)
    els.append(seg(rc[0] + math.cos(a) * 1.25, rc[1] + math.sin(a) * 1.25, 1.15, 0.45, 7.6, 8.4,
                   (k * 45 + 22.5 + 90) % 180, t('ring')))
# the needle: long, thin, a white edge on one side, narrowing to a fine point
els.append(seg(10.0, 10.3, 12.0, 0.95, 7.7, 8.3, 45, t('bladeside'), front=t('blade')))
els.append(seg(14.85, 15.15, 1.5, 0.62, 7.78, 8.22, 45, t('bladeside'), front=t('blade')))
els.append(seg(15.55, 15.85, 1.0, 0.36, 7.84, 8.16, 45, t('bladeside')))
# silk thread trailing from the pommel, rippling out to the right
thread = [(1.0, 1.3), (2.4, 0.8), (4.0, 0.7), (5.6, 1.0), (7.0, 1.3), (8.6, 1.4), (10.0, 1.8), (11.6, 2.3)]
els += chain(thread, [0.38] * len(thread), 0.16, t('silk'), overlap=0.25)
write_model('silk_needle', {"t": "customcraft:item/silk_needle_3d"}, els)
print('pair models done')
