"""Clawline Pick, Silk Needle, Soul Vessel Mask (icon + worn), Stitchguard (3D shield texture) and their materials."""
import sys, math, os
sys.path.insert(0, '/home/claude/art')
from px import *
from PIL import Image

OUT = sys.argv[1]          # textures/item
EQ = sys.argv[2]           # textures/entity/equipment
previews = []

IRON = [(18, 18, 24), (34, 34, 44), (56, 56, 70), (86, 86, 102), (130, 130, 148), (190, 190, 206), (240, 240, 250)]
RED = [(70, 8, 14), (130, 16, 26), (190, 34, 44), (235, 80, 80)]
SILK = [(170, 165, 155), (215, 210, 200), (245, 242, 235), (255, 255, 255)]
WOOD = [(40, 26, 20), (66, 44, 32), (96, 66, 46)]
SOULC = [(60, 90, 140), (120, 160, 220), (200, 225, 255), (255, 255, 255)]
PALE = [(120, 118, 112), (170, 168, 160), (214, 212, 204), (240, 238, 232), (255, 255, 252)]


def on_diag(fn, OX=3.0, OY=29.0):
    def f(x, y):
        u, v = diag(x, y, OX, OY)
        return fn(u, v)
    return f


# ============================================================ Clawline Pick
def pick(f, n):
    c = Canvas()
    glint = (f / n) * 30 - 10
    HU = 23.0

    def head(u, v):
        if abs(v) > 12.2:
            return None
        center = HU - 0.05 * v * v                   # arched head
        th = 2.1 - abs(v) * 0.08                       # tapering toward the tips
        if abs(u - center) > th:
            # hooked tips curling back toward the handle
            if abs(v) > 9.8 and center - th - 1.8 < u < center - th and abs(v) < 11.6:
                return ramp(RED, 0.75)
            return None
        if abs(v) > 10.8:
            return ramp(RED, 0.9)                      # red claw tips
        top = u > center + th - 0.8
        g = max(0, 1 - abs(v - glint * 0.7 + 10) / 2.0)
        return ramp(IRON, min(0.99, (0.75 if top else 0.3 + 0.15 * (u > center)) + 0.25 * g))
    c.fill(on_diag(head), 'head')

    def handle(u, v):
        if 1.6 <= u <= HU - 1.0 and abs(v) <= 0.95:
            if u > HU - 3.2:
                return ramp(RED, 0.55 if v < 0 else 0.3)            # red binding under the head
            wrap = (u * 1.1 + v * 1.4) % 3.0                         # silk thread spiral
            if 4 < u < 13 and wrap < 0.8:
                return ramp(SILK, 0.9 if v < 0 else 0.5)
            return ramp(WOOD, 0.8 if v < -0.2 else 0.35)
        if math.hypot(u - 0.9, v) <= 1.55:
            return ramp(IRON, 0.85 if u + v < 0.6 else 0.35)
        return None
    c.fill(on_diag(handle), 'handle')
    c.outline((10, 6, 8, 255))
    return c.image()


frames = [pick(i, 10) for i in range(10)]
save(frames, f'{OUT}/clawline_pick.png', frametime=3)
previews.append(frames[4])


# ============================================================ Silk Needle
def needle(f, n):
    c = Canvas()
    phase = 2 * math.pi * f / n

    def blade(u, v):
        if u < 10.0 or u > 38.5:
            return None
        w = 1.25 if u < 30 else 1.25 * ((38.5 - u) / 8.5) ** 0.8
        if abs(v) > w:
            return None
        if v < -w + 0.6:
            return ramp(IRON, 0.98)
        if v > w - 0.55:
            return ramp(IRON, 0.35)
        return ramp(IRON, 0.7)
    c.fill(on_diag(blade), 'blade')

    def guard(u, v):
        d = math.hypot(u - 9.3, v)
        if 1.2 <= d <= 2.4:
            return ramp(SILK, 0.85 if v < 0 else 0.45)     # ring guard
        return None
    c.fill(on_diag(guard), 'guard')

    def grip(u, v):
        if 2.4 <= u <= 8.0 and abs(v) <= 0.95:
            band = int(u * 1.5) % 2
            return ramp(RED, 0.75 if (band and v < 0) else 0.45 if band else 0.15)
        if math.hypot(u - 1.3, v) <= 1.3:
            return ramp(SILK, 0.9)
        return None
    c.fill(on_diag(grip), 'grip')
    # trailing silk thread from the pommel, waving
    for i in range(70):
        t = i / 69
        x = 3.2 + 22 * t
        y = 29.5 - 1.5 * t + 2.2 * math.sin(t * 5.5 - phase) * t
        px_, py_ = int(round(x)), int(round(y))
        if 0 <= py_ < 32 and (px_, py_) not in c.px:
            c.set(px_, py_, ramp(SILK, 0.95 if i % 7 else 0.6), 'thread')
    c.outline((8, 6, 10, 255), skip=('thread',))
    return c.image()


frames = [needle(i, 12) for i in range(12)]
save(frames, f'{OUT}/silk_needle.png', frametime=2)
previews.append(frames[2])


# ============================================================ Soul Vessel Mask (icon)
ICON_PORC = [(58, 64, 92), (92, 100, 132), (138, 146, 172), (182, 188, 206), (218, 220, 230), (240, 238, 236), (255, 252, 244)]
ICON_GOLD = [(96, 62, 20), (150, 104, 36), (206, 158, 62), (244, 214, 120)]


def mask(f, n):
    """Pale porcelain helm with a T-shaped visor glowing soul-blue and a single fin crest."""
    c = Canvas()
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    cx = 16.0
    for y in range(32):
        for x in range(32):
            px_, py_ = x + 0.5 - cx, y + 0.5
            # helm outline: round dome on top, straight cheek guards, flared at the bottom
            if 7 <= py_ <= 28:
                half = 10.5 if py_ > 14 else math.sqrt(max(0, 10.5 ** 2 - (14 - py_) ** 2 * 1.5))
                if py_ > 24:
                    half += (py_ - 24) * 0.5
                if abs(px_) > half:
                    continue
                t = 0.65 + 0.25 * (px_ < -3) - 0.25 * (px_ > 5) - 0.08 * (py_ > 22)
                # T visor: horizontal eye slit + vertical breathing slit
                if (15.5 <= py_ <= 17.5 and abs(px_) <= 7.5) or (17.5 < py_ <= 24.5 and abs(px_) <= 1.2):
                    glow = 0.35 + 0.55 * pulse if (abs(px_) < 6 and py_ < 17.6) else 0.15
                    c.set(x, y, ramp(SOULC, glow) if glow > 0.2 else (14, 16, 26, 255))
                    continue
                # gold-ish filigree line along the brow and cheeks
                if (abs(py_ - 13.2) < 0.5 and abs(px_) < 8.5) or (abs(abs(px_) - 6.5) < 0.5 and 19 < py_ < 26):
                    c.set(x, y, ramp(ICON_GOLD, 0.85 if px_ < 0 else 0.5))   # gold filigree
                    continue
                if px_ < -4 and 9 < py_ < 13:
                    t += 0.12                                                 # specular highlight
                c.set(x, y, ramp(ICON_PORC, t))
    # fin crest along the top
    for k in range(6):
        for dx in (-1, 0):
            c.set(15 + dx, 7 - k, ramp(ICON_GOLD, 0.95 if dx else 0.6) if k > 3 else ramp(ICON_PORC, 0.9 if dx else 0.55))
        if k < 3:
            c.set(17, 7 - k, ramp(ICON_PORC, 0.4))
    # soul gem on the brow
    for (gx, gy, t) in [(16, 11, 0.99), (15, 11, 0.7), (17, 11, 0.7), (16, 10, 0.75), (16, 12, 0.6)]:
        c.set(gx, gy, ramp(SOULC, t * (0.6 + 0.4 * pulse)))
    c.outline((20, 20, 28, 255))
    return c.image()


frames = [mask(i, 12) for i in range(12)]
save(frames, f'{OUT}/soul_vessel_mask.png', frametime=3)
previews.append(frames[0])

# worn: humanoid layer, head only - porcelain helm with gold trim, glowing visor, cloth hood at the back
VISOR = [(20, 40, 80), (30, 80, 150), (60, 150, 230), (120, 210, 255), (200, 245, 255)]
PORC = [(58, 64, 92), (92, 100, 132), (138, 146, 172), (182, 188, 206), (218, 220, 230), (240, 238, 236), (255, 252, 244)]
GOLD = [(96, 62, 20), (150, 104, 36), (206, 158, 62), (244, 214, 120)]
HOOD = [(14, 16, 30), (24, 28, 50), (38, 44, 74), (58, 66, 104)]
layer = Image.new('RGBA', (64, 32), (0, 0, 0, 0))


def put(x, y, col):
    layer.putpixel((x, y), col if len(col) == 4 else tuple(col) + (255,))


def porc(t, x=0, y=0):
    """porcelain tone with a slight dither so big faces aren't flat"""
    t += 0.04 if (x * 3 + y * 5) % 7 == 0 else 0
    return ramp(PORC, max(0, min(0.99, t)))


# --- top (8..16, 0..8): dome lit from the front-left, crest down the middle (x 3-4)
for y in range(8):
    for x in range(8):
        t = 0.78 - 0.05 * y - 0.04 * abs(x - 2.5)          # brightest at the front-left
        col = porc(t, x, y)
        if x in (3, 4):
            col = ramp(GOLD, 0.95 if x == 3 else 0.6) if y < 2 else porc(0.95 if x == 3 else 0.6)   # fin crest
        if x in (0, 7):
            col = ramp(GOLD, 0.45)                         # gold rim along the edges
        put(8 + x, y, col)
        put(16 + x, y, ramp(HOOD, 0.2))                    # bottom (inside)

# --- front (8..16, 8..16): face plate with a T visor
FACE = [
    "rrrgGgrr",
    "PPPPPPPP",
    "PPPPPPPP",
    "EVVVVVVE",
    "PPPVVPPP",
    "PPPVVPPP",
    "PbPVVPbP",
    "rrrrrrrr",
]
for y, row in enumerate(FACE):
    for x, ch in enumerate(row):
        light = 0.74 - 0.035 * x - 0.03 * y                # light from the left, darker toward the chin
        if ch == 'P':
            col = porc(light + (0.18 if (x, y) in ((1, 1), (2, 1), (1, 2)) else 0), x, y)   # specular spot
        elif ch == 'r':
            col = ramp(GOLD, 0.75 - 0.05 * x)
        elif ch == 'g':
            col = ramp(GOLD, 0.9)
        elif ch == 'G':
            col = ramp(SOULC, 0.99)                        # soul gem
        elif ch == 'E':
            col = (16, 18, 30)
        elif ch == 'V':
            col = ramp(VISOR, 0.85 if y == 3 else 0.55 - 0.08 * (y - 4))   # glowing visor fading down
        elif ch == 'b':
            col = ramp(GOLD, 0.6)                          # cheek rivets
        put(8 + x, 8 + y, col)

# --- sides: porcelain cheek guard toward the face, cloth hood toward the back
def side(ox, front_at_right):
    for y in range(8):
        for x in range(8):
            d = (7 - x) if front_at_right else x            # distance from the face edge
            if d < 4 and y < 7:                              # cheek plate
                t = 0.62 - 0.07 * d - 0.03 * y + (0.15 if d == 0 else 0)
                col = porc(t, x, y)
                if d == 3 or y == 0:
                    col = ramp(GOLD, 0.7 if y == 0 else 0.5)     # gold trim around the plate
                if d == 1 and y == 3:
                    col = ramp(GOLD, 0.95)                        # ear rivet
            else:                                            # hood cloth with folds
                fold = ((x + (y // 2)) % 3 == 0)
                col = ramp(HOOD, 0.55 - 0.06 * y + (0.25 if fold else 0))
            put(ox + x, 8 + y, col)


side(0, True)       # entity's right side: the face is toward x=7
side(16, False)     # entity's left side: the face is toward x=0

# --- back (24..32, 8..16): porcelain neck guard with the crest ending in a gold point, hood below
for y in range(8):
    for x in range(8):
        if y < 3:
            col = porc(0.55 - 0.05 * y - 0.03 * abs(x - 3.5), x, y)
            if x in (3, 4):
                col = porc(0.85 if x == 4 else 0.6)             # crest runs over the back
            if y == 2:
                col = ramp(GOLD, 0.65 if x % 2 else 0.4)        # gold rim
        elif y == 3 and x in (3, 4):
            col = ramp(GOLD, 0.95)                               # crest tip
        else:
            fold = (x in (1, 5) or (x == 3 and y > 4))
            col = ramp(HOOD, 0.5 - 0.05 * (y - 3) + (0.28 if fold else 0) + (0.1 if x < 3 else 0))
        put(24 + x, 8 + y, col)
os.makedirs(f'{EQ}/humanoid', exist_ok=True)
layer.save(f'{EQ}/humanoid/soul_mask.png')


# ============================================================ Stitchguard (64x64 texture for the 3D shield model)
SHELL = [(52, 36, 22), (92, 66, 38), (140, 106, 62), (186, 150, 96), (222, 192, 136), (246, 226, 180), (255, 248, 224)]
RIM = [(60, 36, 12), (110, 74, 26), (170, 124, 48), (226, 186, 96), (255, 236, 160)]
WEBC = [(40, 4, 12), (84, 10, 24), (130, 20, 36)]
tex = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
FW, FH = 24, 44                       # front face 12x22 model px at 2x
CX, CY = FW / 2, 17.0                 # emblem centre (upper half)
for y in range(FH):
    for x in range(FW):
        px_, py_ = x + 0.5 - FW / 2, y + 0.5
        # carapace plates bow downward; convex: darker toward the sides, glossy streak on the upper left
        bow = 0.05 * px_ * px_
        local = ((py_ - bow) % 7) / 7
        t = 0.78 - 0.4 * local - 0.32 * (abs(px_) / 12) ** 1.5
        if -7.5 < px_ < -4.5 and local < 0.45:
            t += 0.22                                                    # specular streak
        col = ramp(SHELL, t)
        if local > 0.86:
            col = ramp(SHELL, 0.08)                                      # seam shadow
            if int(px_ + 12) % 4 == 1 and abs(px_) < 9:
                col = ramp(SILK, 0.85)                                   # silk stitches holding the plates
        # medallion: deep red disc with a silk web and a gold ring
        d = math.hypot(x + 0.5 - CX, y + 0.5 - CY)
        if d <= 7.2:
            a = math.atan2(y + 0.5 - CY, x + 0.5 - CX)
            col = ramp(WEBC, 0.9 - 0.12 * d / 7)
            spoke = min(abs(((a * 4 / (2 * math.pi)) % 1) - 0.5) * 2, 1)   # 4 silk spokes
            if (spoke > 0.8 and 1.5 < d < 6) or 3.1 < d < 3.9 or d < 1.1:
                col = ramp(SILK, 0.95 - 0.25 * (d / 7))                  # silk web: hub, ring, spokes
            if 6.0 < d <= 7.2:
                col = ramp(RIM, 0.85 if (x + 0.5 - CX) + (y + 0.5 - CY) < 0 else 0.45)
        # silver needle through the medallion
        nd = abs((x + 0.5 - CX) - (y + 0.5 - CY) * 0.55)
        if nd < 0.65 and 6 < y < 31:
            col = ramp([(120, 124, 140), (190, 194, 208), (246, 248, 255)], 0.95 if (x + 0.5 - CX) < 0.1 else 0.5)
            if y in (7, 8):
                col = ramp(RED, 0.85)                                    # red-thread eye of the needle
        # gold rim with rivets
        edge = min(x, FW - 1 - x, y, FH - 1 - y)
        if edge == 0:
            col = ramp(RIM, 0.25)
        elif edge == 1:
            col = ramp(RIM, 0.75 if (x < FW / 2 or y < 3) else 0.5)
            if (y % 6 == 3 and x in (1, FW - 2)) or (x % 6 == 3 and y in (1, FH - 2)):
                col = ramp(RIM, 0.99)                                    # rivets
        tex.putpixel((x, y), col if len(col) == 4 else tuple(col) + (255,))
for y in range(FH):                    # rim strip for the plate edges
    for x in range(26, 28):
        col = ramp(RIM, (0.7 if x == 26 else 0.45) + (0.2 if y % 6 == 3 else 0))
        tex.putpixel((x, y), tuple(col[:3]) + (255,))
for y in range(12):                    # handle
    for x in range(32, 36):
        col = ramp(WOOD, 0.8 if x < 34 else 0.4)
        if y % 4 == 0:
            col = ramp(SILK, 0.7)
        tex.putpixel((x, y), tuple(col[:3]) + (255,))
tex.save(f'{OUT}/stitchguard.png')
face = tex.crop((0, 0, FW, FH))
shield_prev = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
shield_prev.paste(face.resize((13, 24), Image.NEAREST), (9, 4))
previews.append(shield_prev)


# ============================================================ materials
def pale_fragment(f, n):
    c = Canvas()
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    P = [(15, 3), (21, 9), (19, 27), (12, 28), (9, 12)]

    def inside(x, y):
        ins = False
        j = len(P) - 1
        for i in range(len(P)):
            xi, yi = P[i]; xj, yj = P[j]
            if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi):
                ins = not ins
            j = i
        return ins
    for y in range(32):
        for x in range(32):
            if inside(x + 0.5, y + 0.5):
                t = 0.55 + 0.3 * (x < 14) - 0.2 * (x > 17)
                if abs(x - 14 - (y - 8) * 0.15) < 0.6 and 6 < y < 25:
                    t = 0.99 if (y + f) % 6 < 2 else 0.8
                c.set(x, y, ramp(PALE, t))
    c.set(23, 6, ramp(SOULC, 0.6 + 0.4 * pulse)); c.set(7, 20, ramp(SOULC, 0.4 + 0.4 * (1 - pulse)))
    c.outline((30, 30, 40, 255))
    return c.image()


def carapace(f, n):
    c = Canvas()
    shine = (f / n) * 36 - 4
    for y in range(32):
        for x in range(32):
            px_, py_ = x + 0.5 - 16, y + 0.5 - 18
            if (px_ / 12) ** 2 + (py_ / 9) ** 2 <= 1 and py_ < 6:
                seg = int((py_ + 9) // 3)
                t = 0.3 + 0.1 * seg + (0.2 if px_ < 0 else 0)
                if int(py_ + 9) % 3 == 0:
                    t = 0.12
                g = max(0, 1 - abs((x - y) + 16 - shine) / 2.2)
                c.set(x, y, ramp(SHELL, min(0.99, t + 0.5 * g)))
    for x in range(10, 23, 3):
        c.set(x, 20, ramp(SILK, 0.9))
    c.outline((30, 20, 10, 255))
    return c.image()


def claw(f, n):
    c = Canvas()
    glow = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    for i in range(80):
        t = i / 79
        a = math.pi * 0.95 * t
        cx_, cy_ = 15 + 9 * math.cos(a + 0.4), 17 - 10 * math.sin(a + 0.4) * 0.9
        w = 2.6 * (1 - t) + 0.6
        for y in range(32):
            for x in range(32):
                if math.hypot(x + 0.5 - cx_, y + 0.5 - cy_) <= w:
                    if t > 0.82:
                        c.set(x, y, ramp(RED, 0.5 + 0.45 * glow))
                    elif (x, y) not in c.px:
                        c.set(x, y, ramp(IRON, 0.3 + 0.5 * (y < cy_)))
    # a knot of silk where the claw was tied
    for (x, y) in [(23, 23), (24, 24), (22, 24), (23, 25), (25, 26)]:
        c.set(x, y, ramp(SILK, 0.9))
    c.outline((10, 6, 8, 255))
    return c.image()


def weavers_silk(f, n):
    c = Canvas()
    phase = 2 * math.pi * f / n
    # a skein (coiled ring of silk)
    for y in range(32):
        for x in range(32):
            px_, py_ = x + 0.5 - 15, y + 0.5 - 17
            d = math.hypot(px_ / 1.15, py_)
            if 4.0 <= d <= 9.5:
                strand = (math.atan2(py_, px_) * 6 + d * 1.3) % 2
                t = 0.55 + 0.35 * (strand < 0.8) + (0.1 if py_ < 0 else -0.15)
                c.set(x, y, ramp(SILK, t))
    # a loose shimmering end and a red needle through it
    for i in range(30):
        t = i / 29
        x = 24 + 5 * t
        y = 18 + 9 * t + math.sin(t * 6 - phase)
        c.set(int(x), int(y), ramp(SILK, 0.95), 'loose')
    for i in range(14):
        c.set(6 + i, 6 + i, ramp(RED, 0.75 if i % 4 else 0.4), 'needle')
    c.outline((40, 36, 34, 255), skip=('loose',))
    return c.image()


for name, fn, n, ft in [('pale_fragment', pale_fragment, 12, 2), ('carapace_shard', carapace, 16, 2),
                        ('deepsteel_claw', claw, 12, 2), ('weavers_silk', weavers_silk, 12, 2)]:
    frames = [fn(i, n) for i in range(n)]
    save(frames, f'{OUT}/{name}.png', frametime=ft)
    previews.append(frames[n // 3])

preview(previews, '/tmp/claude-0/hk_prev.png', scale=6)
layer.resize((256, 128), Image.NEAREST).save('/tmp/claude-0/hk_mask_layer.png')
print('hk textures done')
