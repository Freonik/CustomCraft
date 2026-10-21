"""Thick Hide set (Hide Cowl / Mantle / Stompers + the Thick Hide leggings) and the Pale Seraph set
(Soul Vessel Mask + Seraph Mantle + Seraph Greaves + Angel's Wings): icons and worn layers.
Runs after relics.py, tank.py and hk.py (it reuses the mask and wings layers they paint)."""
import sys, os, math
sys.path.insert(0, '/home/claude/art')
from px import *
from PIL import Image

OUT = sys.argv[1]          # item textures
EQ = sys.argv[2]           # textures/entity/equipment
V = '/home/claude/ref/vanilla'
previews = []

HIDE = [(24, 22, 16), (44, 40, 28), (68, 62, 42), (96, 88, 60), (128, 118, 82), (162, 150, 110)]
STITCH = [(150, 120, 80), (206, 180, 130)]
TEAL = [(18, 90, 90), (40, 150, 140), (110, 220, 200)]
BELT = [(78, 50, 30), (104, 70, 42)]

PORC = [(58, 64, 92), (92, 100, 132), (138, 146, 172), (182, 188, 206), (218, 220, 230), (240, 238, 236), (255, 252, 244)]
GOLD = [(96, 62, 20), (150, 104, 36), (206, 158, 62), (244, 214, 120)]
SOULC = [(20, 40, 80), (30, 80, 150), (60, 150, 230), (120, 210, 255), (230, 250, 255)]
FEATHER = [(170, 176, 196), (214, 220, 234), (240, 244, 252), (255, 255, 255)]
HOOD = [(14, 16, 30), (24, 28, 50), (38, 44, 74), (58, 66, 104)]


def lum(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255


def shades(vanilla):
    """The vanilla icon at 32x32 with every pixel's brightness normalized to 0..1."""
    img = Image.open(f'{V}/{vanilla}.png').convert('RGBA').resize((32, 32), Image.NEAREST)
    vals = [lum(img.getpixel((x, y))) for y in range(32) for x in range(32) if img.getpixel((x, y))[3]]
    lo, hi = min(vals), max(vals)
    return {(x, y): (lum(img.getpixel((x, y))) - lo) / (hi - lo)
            for y in range(32) for x in range(32) if img.getpixel((x, y))[3]}


# ============================================================ Thick Hide set icons
# like the Thick Hide leggings: leathery hide with lighter patches, a brown strap, stitched seams and teal studs
def hide_piece(vanilla, name, strap_rows, seams, studs, buckle):
    sh = shades(vanilla)
    c = Canvas()
    for (x, y), t in sh.items():
        col = ramp(HIDE, 0.1 + 0.8 * t)
        if ((x // 3) * 7 + (y // 3) * 5) % 9 == 0 and t > 0.35:
            col = ramp(HIDE, min(0.99, 0.25 + t * 0.9))           # lighter hide patches
        c.set(x, y, col)
    for (x, y) in list(c.px):
        if y in strap_rows:
            c.set(x, y, ramp(BELT, 0.99 if y == strap_rows[0] else 0.2))
        if x in seams and y > max(strap_rows) + 1 and y % 2 == 0:
            c.set(x, y, ramp(STITCH, 1))
    for (x, y) in buckle:
        if (x, y) in c.px:
            c.set(x, y, ramp(TEAL, 0.9 if y == min(b[1] for b in buckle) else 0.5))
    for (x, y) in studs:
        if (x, y) in c.px:
            c.set(x, y, ramp(TEAL, 0.6))
    c.outline((14, 12, 10, 255))
    img = c.image()
    save([img], f'{OUT}/{name}.png')
    previews.append(img)


hide_piece('netherite_helmet', 'hide_cowl', (10, 11), (10, 21), [(7, 15), (24, 15), (7, 19), (24, 19)],
           [(15, 10), (16, 10), (15, 11), (16, 11)])
hide_piece('netherite_chestplate', 'hide_mantle', (16, 17), (9, 22), [(4, 6), (27, 6), (4, 11), (27, 11), (8, 25), (23, 25)],
           [(15, 16), (16, 16), (15, 17), (16, 17)])
hide_piece('netherite_leggings', 'thick_hide', (4, 5), (9, 22), [(6, 13), (25, 12), (6, 21), (25, 22)],
           [(15, 4), (16, 4), (15, 5), (16, 5)])
hide_piece('netherite_boots', 'hide_stompers', (12, 13), (8, 23), [(4, 20), (27, 20), (7, 9), (24, 9)],
           [(9, 12), (9, 13), (22, 12), (22, 13)])


# ============================================================ Pale Seraph set icons
# porcelain like the Soul Vessel Mask (gold filigree, a soul-blue gem that pulses with the mask) and
# white feathers like Angel's Wings
def feather_tuft(c, x0, y0, side, length):
    """A small fan of feathers growing out sideways (side -1 = to the left)."""
    for r in range(length):
        for k in range(length - r):
            x = x0 + side * k
            y = y0 + r
            col = ramp(FEATHER, 0.99 if k == length - r - 1 else 0.7 if (k + r) % 3 else 0.35)
            c.set(x, y, col, 'feather')


def soul_gem(c, cx, cy, pulse):
    for (dx, dy, t) in [(0, 0, 0.99), (-1, 0, 0.7), (1, 0, 0.7), (0, -1, 0.75), (0, 1, 0.6)]:
        c.set(cx + dx, cy + dy, ramp(SOULC, t * (0.6 + 0.4 * pulse)))


def mantle(f, n):
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    sh = shades('netherite_chestplate')
    c = Canvas()
    for (x, y), t in sh.items():
        c.set(x, y, ramp(PORC, 0.35 + 0.6 * t + (0.08 if x < 12 and y < 14 else 0)))
    for (x, y) in list(c.px):
        if y in (10, 11) and 6 <= x <= 25:                       # gold collar
            c.set(x, y, ramp(GOLD, 0.85 if y == 10 else 0.5))
        elif x in (15, 16) and 12 <= y <= 27:                     # gold line down the middle
            c.set(x, y, ramp(GOLD, 0.8 if x == 15 else 0.45))
        elif y == 27 and 9 <= x <= 22:
            c.set(x, y, ramp(GOLD, 0.6))
        elif 12 <= y <= 25 and abs(x - 15.5) in (5.5, 6.5) and y % 3 == 0:
            c.set(x, y, ramp(GOLD, 0.7))                           # filigree dots
    soul_gem(c, 16, 17, pulse)
    # feathered pauldrons: white feathers fanning out of both shoulders
    feather_tuft(c, 2, 4, -1, 4)
    feather_tuft(c, 29, 4, 1, 4)
    for x in range(2, 12):
        c.set(x, 4, ramp(FEATHER, 0.99 if x % 2 else 0.75), 'feather')
    for x in range(20, 30):
        c.set(x, 4, ramp(FEATHER, 0.99 if x % 2 else 0.75), 'feather')
    c.outline((20, 20, 28, 255))
    return c.image()


def greaves(f, n):
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    sh = shades('netherite_leggings')
    c = Canvas()
    for (x, y), t in sh.items():
        c.set(x, y, ramp(PORC, 0.35 + 0.6 * t + (0.08 if x < 12 else 0)))
    for (x, y) in list(c.px):
        if y in (4, 5):
            c.set(x, y, ramp(GOLD, 0.85 if y == 4 else 0.5))     # gold belt
        elif y in (17, 18):
            c.set(x, y, ramp(GOLD, 0.7 if y == 17 else 0.4))     # gold knee bands
        elif y > 6 and x in (8, 23) and y % 2 == 1:
            c.set(x, y, ramp(GOLD, 0.6))                           # filigree down the legs
    soul_gem(c, 16, 5, pulse)
    # feather tassets hanging at the hips
    for side, x0 in ((-1, 5), (1, 26)):
        for r in range(6):
            for k in range(2):
                c.set(x0 + side * k, 7 + r, ramp(FEATHER, 0.99 if (r + k) % 2 else 0.6), 'feather')
        c.set(x0 + side * 2, 9, ramp(FEATHER, 0.8), 'feather')
    c.outline((20, 20, 28, 255))
    return c.image()


for name, fn in (('seraph_mantle', mantle), ('seraph_greaves', greaves)):
    frames = [fn(i, 12) for i in range(12)]
    save(frames, f'{OUT}/{name}.png', frametime=3)            # same pulse speed as the mask
    previews.append(frames[0])


# ============================================================ worn layers
FACES = []


def box(part, u, v, w, h, d):
    FACES.extend([
        (part, 'top', u + d, v, w, d), (part, 'bottom', u + d + w, v, w, d),
        (part, 'right', u, v + d, d, h), (part, 'front', u + d, v + d, w, h),
        (part, 'left', u + d + w, v + d, d, h), (part, 'back', u + 2 * d + w, v + d, w, h)])


box('head', 0, 0, 8, 8, 8)
box('body', 16, 16, 8, 12, 4)
box('arm', 40, 16, 4, 12, 4)
box('leg', 0, 16, 4, 12, 4)


def face_of(x, y):
    for part, face, fx, fy, fw, fh in FACES:
        if fx <= x < fx + fw and fy <= y < fy + fh:
            return part, face, x - fx, y - fy, fw, fh
    return None


def paint(base, style, parts):
    out = Image.new('RGBA', base.size, (0, 0, 0, 0))
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            if base.getpixel((x, y))[3] == 0:
                continue
            f = face_of(x, y)
            if f and f[0] in parts:
                col = style(*f, lum(base.getpixel((x, y))))
                if col is not None:
                    out.putpixel((x, y), col if len(col) == 4 else tuple(col) + (255,))
    return out


armor_base = Image.open(f'{V}/eq_netherite.png').convert('RGBA')
legs_base = Image.open(f'{V}/eq_netherite_leggings.png').convert('RGBA')


# ---- Thick Hide: the same hide as the leggings layer (tank.py) on helmet, chest, arms and boots
def hide_style(part, face, lx, ly, w, h, l):
    noise = ((lx * 5 + ly * 7) % 6) / 10
    base = 0.45 + 0.25 * l + noise * 0.15
    col = ramp(HIDE, base)
    if ly % 3 == 2 and face not in ('top', 'bottom'):
        col = ramp(HIDE, base - 0.12)                             # overlapping hide plates
    if face in ('right', 'left') and lx == w // 2 and ly % 2 == 0:
        col = ramp(STITCH, 1)                                     # side seams
    if part == 'head' and face in ('front', 'right', 'left', 'back') and ly in (2, 3):
        col = BELT[1] if ly == 2 else BELT[0]                     # leather brow strap
        if face == 'front' and lx in (3, 4):
            col = ramp(TEAL, 0.85 if ly == 2 else 0.5)
    if part == 'body' and face in ('front', 'back') and (lx == ly - 1 or lx == ly - 2):
        col = BELT[1] if lx == ly - 1 else BELT[0]                # strap across the chest
        if face == 'front' and ly == 5:
            col = ramp(TEAL, 0.85)                                # buckle on it
    if part == 'arm' and face in ('top',) and (lx + ly) % 3 == 0:
        col = ramp(TEAL, 0.6)                                     # studs on the shoulders
    if part == 'leg' and ly in (6, 7) and face != 'bottom':
        col = BELT[1] if ly == 6 else BELT[0]                     # boot strap
        if face == 'front' and lx in (1, 2) and ly == 6:
            col = ramp(TEAL, 0.85)
    if part == 'leg' and ly == 11:
        col = ramp(HIDE, 0.15)                                    # sole
    return col


paint(armor_base, hide_style, ('head', 'body', 'arm', 'leg')).save(f'{EQ}/humanoid/thick_hide.png')

# ---- Pale Seraph: mask head from hk.py, wings boots from relics.py, a new porcelain mantle in between
mask_layer = Image.open(f'{EQ}/humanoid/soul_mask.png').convert('RGBA')
wings_layer = Image.open(f'{EQ}/humanoid/angels_wings.png').convert('RGBA')


def porc(t, x=0, y=0):
    t += 0.04 if (x * 3 + y * 5) % 7 == 0 else 0
    return ramp(PORC, max(0, min(0.99, t)))


def mantle_style(part, face, lx, ly, w, h, l):
    light = 0.72 - 0.02 * lx - 0.015 * ly
    if part == 'body':
        if face == 'front':
            if ly == 0:
                return ramp(GOLD, 0.85)                           # gold collar
            if lx in (3, 4) and ly >= 1:
                if ly in (4, 5):
                    return ramp(SOULC, 0.99 if lx == 3 else 0.75)  # soul gem
                return ramp(GOLD, 0.8 if lx == 3 else 0.5)        # gold line down the middle
            if ly == 11:
                return ramp(GOLD, 0.55)
            return porc(light + (0.12 if lx < 3 and ly < 4 else 0), lx, ly)
        if face == 'back':
            # two small folded wings on the back
            dx = min(lx, w - 1 - lx)
            if 1 <= ly <= 8 and dx <= 3 - (ly - 1) // 3 and lx not in (3, 4):
                return ramp(FEATHER, 0.99 if (dx + ly) % 3 else 0.55)
            if ly == 0:
                return ramp(GOLD, 0.6)
            return porc(0.55 - 0.02 * ly, lx, ly)
        if face in ('top', 'bottom'):
            return ramp(GOLD, 0.5)
        return porc(0.55, lx, ly)
    # arms: feather pauldrons on top, porcelain with a gold band below
    if face == 'top' or ly <= 2:
        return ramp(FEATHER, 0.99 if (lx + ly) % 2 else 0.65)
    if ly == 3:
        return ramp(GOLD, 0.75)
    if ly == 9:
        return ramp(GOLD, 0.5)
    return porc(light - 0.05, lx, ly)


seraph = paint(armor_base, mantle_style, ('body', 'arm'))
for y in range(32):
    for x in range(64):
        f = face_of(x, y)
        if x >= 32 and y < 16:
            continue                                             # no hat layer
        if f and f[0] == 'head' and mask_layer.getpixel((x, y))[3]:
            seraph.putpixel((x, y), mask_layer.getpixel((x, y)))
        if f and f[0] == 'leg' and wings_layer.getpixel((x, y))[3]:
            seraph.putpixel((x, y), wings_layer.getpixel((x, y)))
seraph.save(f'{EQ}/humanoid/pale_seraph.png')
# old items still point at their own models: give them the same full look
seraph.save(f'{EQ}/humanoid/soul_mask.png')
seraph.save(f'{EQ}/humanoid/angels_wings.png')


def greaves_style(part, face, lx, ly, w, h, l):
    if part == 'body':
        if ly in (7, 8) and face not in ('top', 'bottom'):
            if face == 'front' and lx in (3, 4):
                return ramp(SOULC, 0.95 if ly == 7 else 0.6)      # gem on the belt
            return ramp(GOLD, 0.85 if ly == 7 else 0.5)
        return porc(0.6 - 0.02 * ly, lx, ly)
    if face == 'right' and 1 <= ly <= 7:                         # feather tassets on the outer side
        return ramp(FEATHER, [0.99, 0.75, 0.4][(lx + ly) % 3])
    if ly in (5, 6) and face == 'front':
        return ramp(GOLD, 0.75 if ly == 5 else 0.45)              # gold knee band
    return porc(0.7 - 0.02 * ly - 0.03 * lx, lx, ly)


os.makedirs(f'{EQ}/humanoid_leggings', exist_ok=True)
paint(legs_base, greaves_style, ('body', 'leg')).save(f'{EQ}/humanoid_leggings/pale_seraph.png')

preview(previews, '/tmp/claude-0/sets2_prev.png', scale=6)
print('sets2 done')
