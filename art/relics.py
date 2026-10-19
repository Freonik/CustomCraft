import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *
from PIL import Image

OUT = sys.argv[1]          # item texture dir
EQ = sys.argv[2]           # equipment texture dir
V = '/home/claude/ref/vanilla'

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

# ============================================================ Stasis Hourglass
GOLD = [(84, 50, 8), (140, 92, 18), (200, 150, 44), (244, 206, 100), (255, 244, 196)]
WOOD = [(46, 24, 16), (74, 42, 26), (104, 64, 38)]
GLASS_EDGE = (226, 246, 255, 220)
GLASS = [(150, 196, 226, 90), (190, 225, 245, 120)]
SAND = [(176, 118, 34), (226, 170, 64), (255, 214, 120), (255, 240, 190)]
GEM = [(40, 90, 160), (70, 150, 230), (170, 220, 255)]
def hourglass(f, n):
    c = Canvas()
    level = f / n
    sparkle = f % 4 == 0
    for y in range(32):
        for x in range(32):
            cx = x + 0.5 - 16
            # ornate caps: gold rim + dark wood band + gem in the middle
            if y in (1, 30) and abs(cx) <= 8:
                c.set(x, y, ramp(GOLD, 0.3)); continue
            if y in (2, 29) and abs(cx) <= 10:
                c.set(x, y, ramp(GOLD, 0.95 if cx < 0 else 0.7)); continue
            if y in (3, 4, 27, 28) and abs(cx) <= 10:
                if abs(cx) <= 1.5:
                    c.set(x, y, ramp(GEM, 0.95 if (y in (3, 27) and cx < 0) else 0.5)); continue
                c.set(x, y, ramp(WOOD, 0.8 if y in (3, 27) else 0.3)); continue
            if y in (5, 26) and abs(cx) <= 9:
                c.set(x, y, ramp(GOLD, 0.45)); continue
            # twisted pillars
            if 6 <= y <= 25 and abs(abs(cx) - 8.5) <= 0.8:
                twist = (y + (0 if cx < 0 else 2)) % 4 < 2
                c.set(x, y, ramp(GOLD, 0.85 if twist else 0.4)); continue
            # glass bulbs narrowing to the neck at y = 16
            if 6 <= y <= 25:
                d = abs(y + 0.5 - 16) / 10
                w = 0.8 + 5.4 * math.sin(d * math.pi / 2) ** 1.25
                if abs(cx) > w:
                    continue
                top = y < 16
                fill_top = 6 + 10 * level
                fill_bot = 25.5 - 9.5 * level
                in_sand = (top and y >= fill_top) or (not top and y >= fill_bot)
                stream = abs(cx) <= 0.5 and 15 <= y < fill_bot and level < 0.97
                if in_sand or stream:
                    t = 0.35 + 0.3 * ((x * 3 + y * 5 + f) % 4 == 0)
                    if (top and int(fill_top) == y) or (not top and int(fill_bot) == y):
                        t = 0.85                          # bright sand surface
                    c.set(x, y, ramp(SAND, t))
                elif abs(cx) > w - 0.9:
                    c.set(x, y, GLASS_EDGE)
                elif cx < -w + 2.2 and (top or y > 18):
                    c.set(x, y, (240, 252, 255, 170))     # glass highlight
                else:
                    c.set(x, y, GLASS[(x + y) % 5 == 0])
    if sparkle:
        for (x, y) in [(6, 3), (25, 28)]:
            c.set(x, y, ramp(GOLD, 0.99))
    c.outline((36, 20, 6, 255))
    return c.image()
frames = [hourglass(i, 16) for i in range(16)]
save(frames, f'{OUT}/stasis_hourglass.png', frametime=5)
previews.append(frames[5])

# ============================================================ Angel's Wings (boots)
IVORY = [(120, 104, 80), (186, 172, 150), (228, 220, 204), (248, 244, 234), (255, 255, 255)]
GOLDS = [(140, 96, 24), (205, 160, 60), (245, 210, 110)]
FEATHER = [(170, 176, 196), (214, 220, 234), (240, 244, 252), (255, 255, 255)]
c = canvas_from(up2(recolor(Image.open(f'{V}/netherite_boots.png'), IVORY)))
for (x, y), p in list(c.px.items()):
    if y >= 24 and lum(p) < 0.62:
        c.set(x, y, ramp(GOLDS, 0.35))            # gold sole
    if y in (6, 7) and lum(p) > 0.5:
        c.set(x, y, ramp(GOLDS, 0.85))            # gold cuff
# wings growing out of the outer side of each boot (root touches the boot at x=5 / x=26)
WING = [
    "W.....",
    "WW....",
    "WWW...",
    ".WWW..",
    "WWWWW.",
    ".WWWWG",
    "..WWWG",
    "WWWWWG",
    ".WWWWG",
    "..WWWG",
    "...WWG",
    "....WG",
    ".....G",
]
for flip in (False, True):
    for r, row in enumerate(WING):
        for k, ch in enumerate(row):
            if ch == '.':
                continue
            x = (31 - k) if flip else k
            y = 2 + r
            if ch == 'G':
                col = ramp(GOLDS, 0.9 if r % 2 else 0.55)
            else:
                tier = 0 if r < 4 else 1 if r < 7 else 2
                tip = (k <= r - 4 * tier) or k == 0
                col = ramp(FEATHER, [0.99, 0.75, 0.5][tier] + (0.0 if not tip else 0.0))
                if (k + r) % 4 == 0:
                    col = ramp(FEATHER, 0.3)              # feather separations
            c.set(x, y, col, 'wing')
c.outline((70, 56, 34, 255))
wings_icon = c.image()
save([wings_icon], f'{OUT}/angels_wings.png')
previews.append(wings_icon)

# ============================================================ Thorn Plate
THORN = [(14, 26, 14), (28, 48, 24), (46, 72, 34), (70, 100, 46), (110, 140, 70)]
SPIKE = [(150, 160, 90), (210, 220, 140), (245, 250, 200)]
c = canvas_from(up2(recolor(Image.open(f'{V}/netherite_chestplate.png'), THORN)))
for (x, y) in [(3, 3), (2, 2), (28, 3), (29, 2), (9, 12), (22, 12), (15, 18), (16, 18), (12, 23), (19, 23), (5, 8), (26, 8)]:
    c.set(x, y, ramp(SPIKE, 0.95))
    if (x, y + 1) in c.px:
        c.set(x, y + 1, ramp(SPIKE, 0.4))
c.outline((6, 12, 6, 255))
thorn_icon = c.image()
save([thorn_icon], f'{OUT}/thorn_plate.png')
previews.append(thorn_icon)

# ============================================================ Heartsteel (chestplate with a beating heart core)
STEEL = [(18, 12, 16), (34, 24, 30), (56, 40, 48), (86, 64, 72), (128, 100, 108)]
CORE = [(70, 0, 10), (140, 6, 22), (210, 24, 36), (255, 80, 70), (255, 200, 180)]
base_chest = up2(recolor(Image.open(f'{V}/netherite_chestplate.png'), STEEL))
def heart_in(px, py, cx, cy, s):
    hx = (px - cx) / s
    hy = -(py - cy) / s
    return (hx * hx + hy * hy - 1) ** 3 - hx * hx * hy ** 3
def heartsteel(f, n):
    c = canvas_from(base_chest)
    beat = [0, 1, 0.4, 0.9, 0.2, 0, 0, 0, 0, 0][f % n]   # lub-dub
    # glowing veins from the heart across the plate
    veins = [((16, 15), (8, 9)), ((16, 15), (24, 9)), ((16, 17), (10, 24)), ((16, 17), (22, 24)), ((16, 14), (16, 5))]
    for (a, b) in veins:
        steps = 24
        for i in range(steps + 1):
            t = i / steps
            x = round(a[0] + (b[0] - a[0]) * t + math.sin(t * 9) * 0.8)
            y = round(a[1] + (b[1] - a[1]) * t)
            if (x, y) in c.px:
                fade = 1 - t
                c.set(x, y, ramp(CORE, 0.15 + fade * (0.35 + 0.4 * beat)))
    # heart core in the middle of the chest
    s = 4.6 + 0.5 * beat
    for y in range(32):
        for x in range(32):
            v = heart_in(x + 0.5, y + 0.5, 16, 15.5, s)
            if v <= 0:
                depth = min(1, -v * 4)
                t = 0.22 + 0.3 * depth + 0.18 * beat
                hx = (x + 0.5 - 16) / s
                hy = -(y + 0.5 - 15.5) / s
                if -0.55 < hx < -0.3 and 0.4 < hy < 0.6:
                    t = 0.9                                  # tiny glint
                elif abs(hx + hy * 0.3) < 0.12 and depth > 0.35:
                    t = 0.62 + 0.3 * beat                    # glowing crack through the core
                c.set(x, y, ramp(CORE, t))
            elif v <= 0.9 and (x, y) in c.px:
                c.set(x, y, ramp(STEEL, 0.95))            # polished steel rim around the heart
    c.outline((10, 4, 8, 255))
    return c.image()
frames = [heartsteel(i, 10) for i in range(10)]
save(frames, f'{OUT}/heartsteel_core.png', frametime=2)
previews.append(frames[1])

# ============================================================ Hydra Edge (main-hand sword)
SCALE = [(8, 34, 26), (14, 58, 42), (24, 92, 64), (44, 136, 90), (96, 196, 138), (190, 240, 200)]
VENOM = [(40, 120, 20), (90, 200, 40), (170, 255, 90), (230, 255, 180)]
BONE = [(150, 150, 120), (200, 200, 170), (240, 240, 216)]
GRIP = [(30, 22, 18), (52, 38, 30), (78, 58, 44)]
def hydra(f, n):
    c = Canvas()
    OX, OY = 3.0, 29.0
    phase = f / n
    def blade(x, y):
        u, v = diag(x, y, OX, OY)
        if u < 12.0 or u > 37.5:
            return None
        w = 2.7 if u < 33.5 else 2.7 * ((37.5 - u) / 4.0) ** 0.6   # full width almost to the end, short strong tip
        # serrated back edge (teeth every 2.5 units)
        if v > 0 and u < 33:
            w += 0.7 if (u % 2.5) < 1.1 else 0
        if abs(v) > w:
            return None
        if 13.5 < u < 34.5 and abs(v) <= 0.45:
            wave = (u / 20.0 - phase) % 1.0
            return ramp(VENOM, 0.35 + 0.6 * max(0, 1 - abs(wave - 0.5) * 4))   # venom fuller
        if v < -w + 0.85:
            return ramp(SCALE, 0.98)                                           # bright edge
        if v > w - 0.8:
            return ramp(SCALE, 0.08)
        scale = (int(u * 1.2) + (1 if v > 0 else 0)) % 2
        return ramp(SCALE, 0.35 + 0.2 * scale + (0.15 if v < 0 else 0))
    c.fill(blade, 'blade')
    # guard: three serpent heads (center one up the blade, two to the sides)
    def guard(x, y):
        u, v = diag(x, y, OX, OY)
        for (hu, hv, du, dv) in [(10.5, -4.8, -0.5, -1), (10.5, 4.8, -0.5, 1), (12.6, 0, 1, 0)]:
            d = math.hypot((u - hu) * 1.15, v - hv)
            if d <= 2.1:
                # eye
                eu, ev = hu + 0.5 * (1 if du >= 0 else 0.4), hv + (-0.9 if hv <= 0 else 0.9) * (0 if hv == 0 else 1)
                if hv == 0:
                    ev = -0.9
                if math.hypot(u - eu, v - ev) < 0.6:
                    return ramp(VENOM, 0.99 if (f % 12) not in (0, 1) else 0.3)
                return ramp(SCALE, 0.3 if d < 1.2 else 0.12)
        # neck bar connecting the heads
        if 9.3 <= u <= 11.2 and abs(v) <= 4.5:
            return ramp(SCALE, 0.05)
        return None
    c.fill(guard, 'guard')
    # fangs
    for (u0, v0) in [(8.6, -5.6), (8.6, 5.6)]:
        for y in range(32):
            for x in range(32):
                u, v = diag(x, y, OX, OY)
                if math.hypot(u - u0, v - v0) < 0.7:
                    c.set(x, y, ramp(BONE, 0.9), 'fang')
    def grip(x, y):
        u, v = diag(x, y, OX, OY)
        if 2.4 <= u <= 9.2 and abs(v) <= 1.0:
            return ramp(GRIP, 0.9 if (int(u * 1.6) % 2 and v < 0) else 0.5 if int(u * 1.6) % 2 else 0.15)
        if math.hypot(u - 1.0, v) <= 1.7:
            return ramp(VENOM, 0.95 if u + v < 0.6 else 0.45)
        return None
    c.fill(grip, 'grip')
    c.outline((3, 14, 9, 255))
    return c.image()
frames = [hydra(i, 12) for i in range(12)]
save(frames, f'{OUT}/hydra_edge.png', frametime=2)
previews.append(frames[3])
preview(previews, '/tmp/claude-0/relics_prev.png', scale=6)

# ============================================================ worn armor layers (painted per box face)
base = Image.open(f'{V}/eq_netherite.png').convert('RGBA')
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

def paint(style):
    out = Image.new('RGBA', base.size, (0, 0, 0, 0))
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            if base.getpixel((x, y))[3] == 0:
                continue
            f = face_of(x, y)
            if f is None:
                continue
            c = style(*f, lum(base.getpixel((x, y))))
            if c is not None:
                out.putpixel((x, y), c if len(c) == 4 else tuple(c) + (255,))
    return out

def edge(lx, ly, w, h):
    return lx == 0 or ly == 0 or lx == w - 1 or ly == h - 1

# ---- Thorn Plate: dark bark armor, thorny vines, bone spikes on shoulders and spine
BARK = [(12, 22, 12), (22, 38, 18), (34, 56, 26), (52, 80, 36), (80, 112, 52)]
VINE = [(40, 90, 30), (70, 130, 46), (110, 170, 70)]
def thorn_style(part, face, lx, ly, w, h, l):
    noise = ((lx * 7 + ly * 13) % 5) / 10
    col = ramp(BARK, 0.25 + 0.4 * l + noise * 0.3)
    if edge(lx, ly, w, h):
        col = ramp(BARK, 0.05)
    if part == 'body' and face == 'front':
        if lx == ly - 1 or lx == (w - 1) - (ly - 1):            # crossed vines
            col = ramp(VINE, 0.6)
            if ly in (3, 6, 9):
                col = ramp(SPIKE, 0.95)                          # thorns on the vines
    if part == 'body' and face == 'back':
        if lx in (3, 4):
            col = ramp(VINE, 0.35)                                        # spine vine
            if ly % 3 == 1 and lx == (3 if (ly // 3) % 2 else 4):
                col = ramp(SPIKE, 0.95)                                   # thorns along it
    if part == 'arm' and face == 'top' and (lx + ly) % 2 == 0:
        col = ramp(SPIKE, 0.9)                                            # spikes on top of the shoulders
    if part == 'arm' and face in ('right', 'front', 'back') and ly == 0 and lx % 2 == 1:
        col = ramp(SPIKE, 0.75)
    if part == 'arm' and face in ('right', 'front', 'back') and ly == 1 and lx % 2 == 1:
        col = ramp(SPIKE, 0.3)
    return col
thorn_layer = paint(thorn_style)
thorn_layer.save(f'{EQ}/thorn_plate.png')

# ---- Heartsteel: dark steel (netherite shape) with a shaded heart framed by a gold rim
heart_layer = recolor(base, STEEL)
HEART_RIMMED = [
    ".RR..RR.",
    "RHBRRBMR",
    "RBBMMMDR",
    "RBMCCMDR",
    ".RMMMDR.",
    "..RMDR..",
    "...RR...",
]
HEART_COLORS = {
    'R': (214, 160, 64),   # gold rim
    'H': (255, 214, 200),  # glossy highlight
    'B': (240, 64, 64),    # bright red
    'M': (196, 24, 38),    # red
    'D': (120, 8, 22),     # shadow
    'C': (255, 120, 90),   # glowing core
}
for r, row in enumerate(HEART_RIMMED):
    for k, ch in enumerate(row):
        if ch != '.':
            col = HEART_COLORS[ch]
            if ch == 'R' and (r >= 4 or k >= 6):
                col = (150, 104, 36)                     # rim in shadow on the lower right
            heart_layer.putpixel((20 + k, 21 + r), col + (255,))
# small dark-red veins under the heart and on the shoulders
for (x, y) in [(21, 29), (26, 29), (22, 30), (25, 30), (44, 22), (45, 24), (48, 22), (49, 24)]:
    heart_layer.putpixel((x, y), (130, 12, 26, 255))
# gold collar line
for x in range(20, 28):
    if heart_layer.getpixel((x, 20))[3]:
        heart_layer.putpixel((x, 20), (176, 126, 48, 255))
# back: a smaller rimmed heart between the shoulder blades, a glowing spine and gold rivets
BACK = [
    "R..........R",  # placeholder row (unused width guard)
]
BACK_HEART = [
    ".RR.RR.",
    "RBMRMDR",
    "RMCMMDR",
    ".RMMDR.",
    "..RDR..",
    "...R...",
]
def put(x, y, col):
    if heart_layer.getpixel((x, y))[3]:
        heart_layer.putpixel((x, y), col + (255,))
for x in range(32, 40):
    put(x, 20, (176, 126, 48))                                # gold collar on the back
for r, row in enumerate(BACK_HEART):
    for k, ch in enumerate(row):
        if ch != '.':
            col = HEART_COLORS[ch]
            if ch == 'R' and (r >= 3 or k >= 5):
                col = (150, 104, 36)
            put(32 + k + 0, 21 + r, col)
for y in range(27, 31):
    put(35, y, (220, 40, 46) if y % 2 else (130, 12, 26))     # glowing spine
    put(36, y, (130, 12, 26) if y % 2 else (220, 40, 46))
put(35, 31, (176, 126, 48)); put(36, 31, (176, 126, 48))
for (x, y) in [(39, 22), (39, 25), (32, 28), (39, 28)]:
    put(x, y, (226, 180, 80))                                 # gold rivets
heart_layer.save(f'{EQ}/heartsteel.png')

# ---- Angel's Wings: ivory boots, gold cuff and sole, feathers painted on the outer side
def angel_style(part, face, lx, ly, w, h, l):
    if part != 'leg':
        return ramp(IVORY, 0.3 + 0.6 * l)
    col = ramp(IVORY, 0.45 + 0.5 * l)
    if face == 'bottom':
        return ramp(GOLDS, 0.5)
    if ly == 6:                                                   # boot top edge (layer row 26)
        return ramp(GOLDS, 0.95)
    if ly == 11:
        return ramp(GOLDS, 0.45)                                  # sole
    if face == 'right' and ly >= 7:                               # feathers on the outer side
        feather = (lx + (ly - 7)) % 3
        return ramp(FEATHER, [0.99, 0.75, 0.35][feather])
    if face == 'front' and ly == 8 and lx in (1, 2):
        return ramp(GOLDS, 0.99)                                  # small gold emblem
    return col
angel_layer = paint(angel_style)
angel_layer.save(f'{EQ}/angels_wings.png')
