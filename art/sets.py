"""Relic sets: the other three pieces of Heartsteel and Thorn (icons), their worn leggings layers, and set details
on the worn helmet/boots. Runs after relics.py (which paints the humanoid layers of both)."""
import sys, os
sys.path.insert(0, '/home/claude/art')
from px import *
from PIL import Image

OUT = sys.argv[1]          # item textures
EQ = sys.argv[2]           # textures/entity/equipment (humanoid / humanoid_leggings below it)
V = '/home/claude/ref/vanilla'

STEEL = [(18, 12, 16), (34, 24, 30), (56, 40, 48), (86, 64, 72), (128, 100, 108)]
CORE = [(70, 0, 10), (140, 6, 22), (210, 24, 36), (255, 80, 70), (255, 200, 180)]
GOLD = (214, 160, 64)
GOLD_D = (150, 104, 36)
THORN = [(14, 26, 14), (28, 48, 24), (46, 72, 34), (70, 100, 46), (110, 140, 70)]
BARK = [(12, 22, 12), (22, 38, 18), (34, 56, 26), (52, 80, 36), (80, 112, 52)]
VINE = [(40, 90, 30), (70, 130, 46), (110, 170, 70)]
SPIKE = [(150, 160, 90), (210, 220, 140), (245, 250, 200)]


def lum(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255


def recolor(img, palette):
    img = img.convert('RGBA')
    vals = [lum(img.getpixel((x, y))) for y in range(img.size[1]) for x in range(img.size[0]) if img.getpixel((x, y))[3] > 0]
    lo, hi = min(vals), max(vals)
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            p = img.getpixel((x, y))
            if p[3]:
                c = ramp(palette, (lum(p) - lo) / max(1e-6, hi - lo))
                out.putpixel((x, y), (c[0], c[1], c[2], p[3]))
    return out


def up2(img):
    return img.resize((img.size[0] * 2, img.size[1] * 2), Image.NEAREST)


def canvas_from(img):
    c = Canvas()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            p = img.getpixel((x, y))
            if p[3]:
                c.set(x, y, p, 'base')
    return c


previews = []

# ============================================================ icons
# All four pieces of a set share one look: Heartsteel = dark steel, a pulsing heart gem in a polished rim and red veins
# running out of it (the pulse is in sync on every piece); Thorn = bark texture, thorny vines winding over it and
# bone spikes growing out of the silhouette. The chestplates (made in relics.py) are redrawn here the same way.
import math


def heart_in(px, py, cx, cy, s):
    hx = (px - cx) / s
    hy = -(py - cy) / s
    return (hx * hx + hy * hy - 1) ** 3 - hx * hx * hy ** 3


def wobble_line(a, b, amp=0.8, freq=9):
    steps = int(max(abs(b[0] - a[0]), abs(b[1] - a[1])) * 3) + 2
    pts = []
    for i in range(steps + 1):
        t = i / steps
        x = round(a[0] + (b[0] - a[0]) * t + math.sin(t * freq) * amp)
        y = round(a[1] + (b[1] - a[1]) * t)
        if not pts or pts[-1][:2] != (x, y):
            pts.append((x, y, t))
    return pts


BEAT = [0, 1, 0.4, 0.9, 0.2, 0, 0, 0, 0, 0]          # lub-dub, the same on every piece


def heart_frame(vanilla, gems, veins, f):
    c = canvas_from(up2(recolor(Image.open(f'{V}/{vanilla}.png'), STEEL)))
    beat = BEAT[f]
    for (a, b) in veins:
        for (x, y, t) in wobble_line(a, b):
            if (x, y) in c.px:
                c.set(x, y, ramp(CORE, 0.15 + (1 - t) * (0.35 + 0.4 * beat)))
    for (cx, cy, s0) in gems:
        s = s0 * (1 + 0.1 * beat)
        for y in range(32):
            for x in range(32):
                v = heart_in(x + 0.5, y + 0.5, cx, cy, s)
                if v <= 0:
                    depth = min(1, -v * 4)
                    t = 0.22 + 0.3 * depth + 0.18 * beat
                    hx = (x + 0.5 - cx) / s
                    hy = -(y + 0.5 - cy) / s
                    if -0.6 < hx < -0.2 and 0.3 < hy < 0.7:
                        t = 0.9                                          # glint
                    elif s0 > 3 and abs(hx + hy * 0.3) < 0.12 and depth > 0.35:
                        t = 0.62 + 0.3 * beat                            # crack (only the big core)
                    c.set(x, y, ramp(CORE, t))
                elif v <= (0.9 if s0 > 3 else 1.6) and (x, y) in c.px:
                    c.set(x, y, (GOLD if y < cy else GOLD_D) + (255,))   # gold rim, like on the worn armor
    c.outline((10, 4, 8, 255))
    return c.image()


def heart_piece(vanilla, name, gems, veins):
    frames = [heart_frame(vanilla, gems, veins, f) for f in range(10)]
    save(frames, f'{OUT}/{name}.png', frametime=2)
    previews.append(frames[1])


heart_piece('netherite_helmet', 'heartsteel_helm', [(16, 12.5, 2.9)],
            [((16, 12), (7, 9)), ((16, 12), (25, 9)), ((15, 14), (9, 21)), ((17, 14), (23, 21)), ((16, 11), (16, 6))])
heart_piece('netherite_chestplate', 'heartsteel_core', [(16, 15.5, 4.6)],
            [((16, 15), (8, 9)), ((16, 15), (24, 9)), ((16, 17), (10, 24)), ((16, 17), (22, 24)), ((16, 14), (16, 5))])
heart_piece('netherite_leggings', 'heartsteel_greaves', [(16, 8, 2.6)],
            [((15, 9), (9, 27)), ((17, 9), (23, 27)), ((16, 8), (7, 6)), ((16, 8), (25, 6))])
heart_piece('netherite_boots', 'heartsteel_treads', [(9, 15.5, 2.3), (23, 15.5, 2.3)],
            [((9, 16), (4, 24)), ((23, 16), (28, 24)), ((9, 14), (8, 7)), ((23, 14), (24, 7))])


def thorn_piece(vanilla, name, vines, spikes, studs=()):
    src = up2(Image.open(f'{V}/{vanilla}.png').convert('RGBA'))
    c = Canvas()
    vals = [lum(src.getpixel((x, y))) for y in range(32) for x in range(32) if src.getpixel((x, y))[3]]
    lo, hi = min(vals), max(vals)
    for y in range(32):
        for x in range(32):
            p = src.getpixel((x, y))
            if p[3]:
                shade = (lum(p) - lo) / (hi - lo)                            # keep the vanilla shading
                noise = ((x // 2 * 7 + y // 2 * 13) % 5) / 10              # bark grain on the 2x2 grid
                c.set(x, y, ramp(BARK, 0.05 + 0.75 * shade + noise * 0.2), 'base')
    for (a, b, amp) in vines:
        pts = wobble_line(a, b, amp, 7)
        for i, (x, y, t) in enumerate(pts):
            if (x, y) not in c.px:
                continue
            c.set(x, y, ramp(VINE, 0.65))
            if (x, y + 1) in c.px:
                c.set(x, y + 1, ramp(VINE, 0.15))                     # shadow under the vine
            if i % 4 == 2:
                tx, ty = (x - 1, y - 1) if i % 8 == 2 else (x + 1, y - 1)
                if (tx, ty) in c.px:
                    c.set(tx, ty, ramp(SPIKE, 0.95))                  # a thorn on the vine
    for (x, y, dx, dy) in spikes:                                      # bone spikes growing out of the edge
        c.set(x, y, ramp(SPIKE, 0.45))
        c.set(x + dx, y + dy, ramp(SPIKE, 0.95))
    for (x, y) in studs:
        c.set(x, y, ramp(SPIKE, 0.95))
    c.outline((6, 12, 6, 255))
    img = c.image()
    save([img], f'{OUT}/{name}.png')
    previews.append(img)


thorn_piece('netherite_helmet', 'thorn_crown',
            [((6, 11), (25, 11), 0.9)],
            [(11, 5, 0, -1), (14, 5, 0, -1), (17, 5, 0, -1), (20, 5, 0, -1), (8, 7, -1, -1), (23, 7, 1, -1)])
thorn_piece('netherite_chestplate', 'thorn_plate',
            [((5, 8), (22, 28), 0.8), ((26, 8), (9, 28), 0.8)],
            [(3, 3, 0, -1), (6, 3, 0, -1), (9, 3, 0, -1), (22, 3, 0, -1), (25, 3, 0, -1), (28, 3, 0, -1),
             (1, 12, -1, 0), (30, 12, 1, 0)])
thorn_piece('netherite_leggings', 'thorn_greaves',
            [((7, 7), (25, 7), 0.6), ((8, 9), (12, 27), 1.0), ((24, 9), (20, 27), 1.0)],
            [(5, 8, -1, 0), (26, 8, 1, 0), (5, 18, -1, 0), (26, 18, 1, 0), (5, 25, -1, 0), (26, 25, 1, 0)])
thorn_piece('netherite_boots', 'thorn_treads',
            [((6, 9), (12, 9), 0.5), ((20, 9), (26, 9), 0.5), ((7, 12), (3, 23), 0.7), ((25, 12), (29, 23), 0.7)],
            [(6, 5, 0, -1), (9, 5, 0, -1), (22, 5, 0, -1), (25, 5, 0, -1), (1, 21, -1, 0), (30, 21, 1, 0)])

SMALL_HEART = ["RR.RR", "RBMMR", "RMCMR", ".RMR.", "..R.."]
HC = {'R': GOLD, 'B': (240, 64, 64), 'M': (196, 24, 38), 'C': (255, 120, 90)}

# ============================================================ worn: helmet / boots details on the existing layers
heart_layer = Image.open(f'{EQ}/humanoid/heartsteel.png').convert('RGBA')
# head front face is x 8..15, y 8..15: a small gold-rimmed heart on the forehead, gold brow band
for r, row in enumerate(SMALL_HEART):
    for k, ch in enumerate(row):
        if ch != '.':
            heart_layer.putpixel((10 + k, 8 + r), HC[ch] + (255,))
for x in range(8, 16):
    if heart_layer.getpixel((x, 13))[3]:
        heart_layer.putpixel((x, 13), GOLD_D + (255,))
# boots (leg region x 0..15, y 16..31; boots are the lower part): gold cuff and a red vein
for x in range(0, 16):
    if heart_layer.getpixel((x, 26))[3]:
        heart_layer.putpixel((x, 26), GOLD + (255,))
for (x, y) in [(5, 28), (6, 29), (13, 28), (14, 29)]:
    heart_layer.putpixel((x, y), (200, 24, 38, 255))
heart_layer.save(f'{EQ}/humanoid/heartsteel.png')

thorn_layer = Image.open(f'{EQ}/humanoid/thorn_plate.png').convert('RGBA')
# head top face is x 8..15, y 0..7: a ring of spikes = the crown; spikes around the boot cuffs
for (x, y) in [(8, 0), (10, 0), (12, 0), (14, 0), (15, 2), (15, 4), (15, 6), (8, 7), (10, 7), (12, 7), (14, 7), (8, 3), (8, 5)]:
    thorn_layer.putpixel((x, y), tuple(ramp(SPIKE, 0.95)[:3]) + (255,))
for x in range(0, 16, 2):
    if thorn_layer.getpixel((x, 26))[3]:
        thorn_layer.putpixel((x, 26), tuple(ramp(SPIKE, 0.85)[:3]) + (255,))
# a thorny vine around the head (all four sides, row 2 of the head faces), like the band on the Thorn Crown icon
for x in range(0, 32):
    if thorn_layer.getpixel((x, 10))[3]:
        thorn_layer.putpixel((x, 10), tuple(ramp(VINE, 0.65)[:3]) + (255,))
        thorn_layer.putpixel((x, 11), tuple(ramp(VINE, 0.15)[:3]) + (255,))
        if x % 4 == 1:
            thorn_layer.putpixel((x, 9), tuple(ramp(SPIKE, 0.95)[:3]) + (255,))
thorn_layer.save(f'{EQ}/humanoid/thorn_plate.png')

# ============================================================ worn: leggings layers
leg_base = Image.open(f'{V}/eq_netherite_leggings.png').convert('RGBA')
FACES = []


def box(part, u, v, w, h, d):
    FACES.extend([
        (part, 'top', u + d, v, w, d), (part, 'bottom', u + d + w, v, w, d),
        (part, 'right', u, v + d, d, h), (part, 'front', u + d, v + d, w, h),
        (part, 'left', u + d + w, v + d, d, h), (part, 'back', u + 2 * d + w, v + d, w, h)])


box('body', 16, 16, 8, 12, 4)
box('leg', 0, 16, 4, 12, 4)


def face_of(x, y):
    for part, face, fx, fy, fw, fh in FACES:
        if fx <= x < fx + fw and fy <= y < fy + fh:
            return part, face, x - fx, y - fy, fw, fh
    return None


def paint_legs(style):
    out = Image.new('RGBA', leg_base.size, (0, 0, 0, 0))
    for y in range(leg_base.size[1]):
        for x in range(leg_base.size[0]):
            if leg_base.getpixel((x, y))[3] == 0:
                continue
            f = face_of(x, y)
            if f:
                col = style(*f, lum(leg_base.getpixel((x, y))))
                out.putpixel((x, y), col if len(col) == 4 else tuple(col) + (255,))
    return out


def heart_legs(part, face, lx, ly, w, h, l):
    col = ramp(STEEL, 0.25 + 0.6 * l)
    if part == 'body' and ly in (7, 8):
        col = GOLD if ly == 7 else GOLD_D                       # gold belt
        if face == 'front' and lx in (3, 4):
            col = ramp(CORE, 0.6)                               # red buckle
    elif part == 'leg' and face in ('front', 'right', 'left') and lx == w // 2 and ly % 3 == 0:
        col = ramp(CORE, 0.45)                                  # vein down the leg
    elif part == 'leg' and face == 'front' and ly in (5, 6):
        col = ramp(STEEL, 0.9)                                  # knee plate
    return col


def thorn_legs(part, face, lx, ly, w, h, l):
    noise = ((lx * 7 + ly * 13) % 5) / 10
    col = ramp(BARK, 0.25 + 0.4 * l + noise * 0.3)
    if part == 'body' and ly in (7, 8):
        col = ramp(VINE, 0.55 if ly == 7 else 0.3)              # vine belt
    elif part == 'leg' and face in ('front', 'right') and (lx + ly) % 5 == 0:
        col = ramp(VINE, 0.6)                                   # vines winding down
        if ly % 4 == 1:
            col = ramp(SPIKE, 0.95)                             # thorns on them
    return col


os.makedirs(f'{EQ}/humanoid_leggings', exist_ok=True)
paint_legs(heart_legs).save(f'{EQ}/humanoid_leggings/heartsteel.png')
paint_legs(thorn_legs).save(f'{EQ}/humanoid_leggings/thorn_plate.png')

preview(previews, '/tmp/claude-0/sets_prev.png', scale=6)
print('sets done')
