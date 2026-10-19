"""Butcher's Cleaver, Abyssal Maw, Thick Hide (icon + worn leggings) and their legendary materials."""
import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *
from PIL import Image

OUT = sys.argv[1]                      # item textures
EQ = sys.argv[2]                       # textures/entity/equipment (humanoid_leggings goes in a subfolder)
V = '/home/claude/ref/vanilla'
previews = []


def lum(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255


def disk(c, cx, cy, r, col, kind=None):
    for y in range(32):
        for x in range(32):
            if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
                c.set(x, y, col, kind)


# ============================================================ Butcher's Cleaver
STEEL = [(26, 24, 30), (44, 42, 50), (66, 64, 74), (96, 94, 104), (140, 138, 146), (196, 196, 204), (240, 240, 246)]
RUST = [(70, 30, 16), (104, 50, 24), (140, 74, 34)]
BLOOD = [(70, 4, 12), (110, 8, 20), (160, 16, 30), (210, 40, 50)]
PLAGUE = [(60, 30, 80), (98, 52, 128), (150, 90, 190)]
WRAP = [(70, 58, 44), (120, 104, 82), (176, 160, 130), (222, 210, 182)]
BONE_C = [(150, 140, 110), (200, 192, 160), (238, 232, 206)]


def cleaver(f, n):
    c = Canvas()
    OX, OY = 3.0, 29.0
    shine = (f / n) * 26 + 6           # highlight sliding along the edge
    U0, U1, E, S = 12.6, 30.2, -8.6, 1.4

    def blade(x, y):
        u, v = diag(x, y, OX, OY)
        if u < U0 or u > U1 or v < E or v > S:
            return None
        # rounded front corner on the edge side, angled heel at the back
        if u > U1 - 2.2 and v < E + 2.2 and math.hypot(u - (U1 - 2.2), v - (E + 2.2)) > 2.2:
            return None
        if u < U0 + 1.6 and v < E + (U0 + 1.6 - u) * 1.4:
            return None
        if math.hypot(u - 27.2, v - (-0.6)) < 0.95:        # the hanging hole
            return None
        if v > S - 1.0:
            return ramp(STEEL, 0.12)                       # thick dark spine
        if v < E + 1.0:                                    # cutting edge, with a sliding glint
            g = max(0, 1 - abs(u - shine) / 2.5)
            return ramp(STEEL, 0.82 + 0.18 * g)
        if v < E + 1.8:
            return ramp(STEEL, 0.62)                       # bevel
        # blood splashes and rust on the flat
        if (u - 17) ** 2 / 9 + (v + 5.2) ** 2 / 2.2 < 1 or (u - 23.5) ** 2 / 3 + (v + 3) ** 2 / 1.3 < 1:
            return ramp(BLOOD, 0.45 if (int(u) + int(v)) % 3 else 0.2)
        if (u - 26) ** 2 / 4 + (v + 6.4) ** 2 / 1.2 < 1:
            return ramp(RUST, 0.5)
        # dark hammered steel, lighter toward the edge, a purple plague sheen near the spine
        if v > -2.2 and (int(u * 1.3) % 3 == 0):
            return ramp(PLAGUE, 0.35)
        t = 0.18 + 0.06 * ((int(u * 0.8) + int(v * 0.8)) % 2) + 0.035 * (-v)
        return ramp(STEEL, t)
    c.fill(blade, 'blade')
    # purple plague rivets along the spine
    for uu in (15.2, 20.2):
        for y in range(32):
            for x in range(32):
                u, v = diag(x, y, OX, OY)
                if math.hypot(u - uu, v - 0.2) < 0.75:
                    c.set(x, y, ramp(PLAGUE, 0.9), 'rivet')

    def handle(x, y):
        u, v = diag(x, y, OX, OY)
        if 2.2 <= u <= U0 + 0.2 and abs(v - 0.2) <= 1.05:
            if u > U0 - 1.6:
                return ramp(STEEL, 0.55 if v < 0 else 0.3)     # bolster
            band = int(u * 1.5) % 3
            if band == 0:
                return ramp(WRAP, 0.25)                         # wrap gaps
            return ramp(WRAP, 0.95 if v < -0.1 else 0.6)
        d = math.hypot(u - 1.1, v - 0.2)
        if d <= 1.65:
            return ramp(BONE_C, 0.95 if u + v < 0.8 else 0.4)    # bone knob
        return None
    c.fill(handle, 'handle')
    # a blood drop falling off the edge (screen space)
    drop_y = 16 + f
    if f < n - 1:
        c.set(5, drop_y, ramp(BLOOD, 0.75), 'drop')
        if f > 0:
            c.set(5, drop_y - 1, ramp(BLOOD, 0.35), 'drop')
    c.outline((10, 4, 8, 255))
    return c.image()


frames = [cleaver(i, 10) for i in range(10)]
save(frames, f'{OUT}/butcher_cleaver.png', frametime=3)
previews.append(frames[2])
gif(frames, '/tmp/claude-0/cleaver.gif')

# ============================================================ Abyssal Maw
ABYSS = [(4, 12, 22), (8, 24, 40), (14, 40, 62), (22, 62, 88), (40, 96, 120), (90, 160, 170)]
TOOTH = [(170, 172, 150), (220, 222, 200), (250, 252, 240)]
GLOWC = [(20, 110, 120), (40, 200, 200), (140, 255, 240)]
LURE = [(120, 90, 20), (220, 170, 40), (255, 230, 110), (255, 255, 220)]
SKIN = [(10, 30, 36), (20, 56, 64), (34, 86, 96)]


def maw(f, n):
    c = Canvas()
    OX, OY = 3.0, 29.0
    a = 2 * math.pi * f / n
    pulse = 0.5 + 0.5 * math.sin(a)

    def blade(x, y):
        u, v = diag(x, y, OX, OY)
        if u < 11.0 or u > 37.2:
            return None
        w = 2.5 if u < 32.5 else 2.5 * ((37.2 - u) / 4.7) ** 0.6
        # fangs: triangles sticking out of both edges
        tooth = 0
        if 12.5 < u < 32:
            k = (u - 12.5) % 3.2
            tooth = max(0, 1.3 - abs(k - 1.6) * 1.2)
        if abs(v) > w + tooth:
            return None
        if abs(v) > w:
            return ramp(TOOTH, 0.9 if v < 0 else 0.45)
        if abs(v) <= 0.5 and 12 < u < 34:                      # bioluminescent line
            blink = (int(u * 0.9) + f) % 6 == 0
            return ramp(GLOWC, 0.95 if blink else 0.4)
        if v < -w + 0.8:
            return ramp(ABYSS, 0.95)
        if v > w - 0.8:
            return ramp(ABYSS, 0.1)
        return ramp(ABYSS, 0.35 + (0.15 if v < 0 else 0) + 0.06 * ((int(u) // 2) % 2))
    c.fill(blade, 'blade')

    # guard: a fish jaw seen from the side, with an eye
    def guard(x, y):
        u, v = diag(x, y, OX, OY)
        if 8.0 <= u <= 11.8 and abs(v) <= 5.4 - (u - 8.0) * 0.45:
            if math.hypot(u - 9.8, v + 2.6) < 0.8:
                return ramp(LURE, 0.6 + 0.4 * pulse)          # eye
            return ramp(SKIN, 0.85 if v < 0 else 0.35)
        return None
    c.fill(guard, 'guard')

    # the lure stalk curving over the blade, ending with a glowing bulb
    bulb_u, bulb_v = 21.0, -7.4
    for y in range(32):
        for x in range(32):
            u, v = diag(x, y, OX, OY)
            # stalk: quadratic bezier from the guard, arching away from the blade, down to the bulb
            for s in range(0, 31):
                t = s / 30
                su = (1 - t) ** 2 * 10.4 + 2 * (1 - t) * t * 13.5 + t * t * (bulb_u - 1.0)
                sv = (1 - t) ** 2 * -4.4 + 2 * (1 - t) * t * -10.5 + t * t * (bulb_v - 0.6)
                if math.hypot(u - su, v - sv) < 0.6:
                    c.set(x, y, ramp(SKIN, 0.55), 'stalk')
                    break
    halo = 2.2 + 0.5 * pulse
    for y in range(32):
        for x in range(32):
            u, v = diag(x, y, OX, OY)
            d = math.hypot(u - bulb_u, v - bulb_v)
            if d <= 1.9:
                c.set(x, y, ramp(LURE, 0.99 if d < 0.9 else 0.55 + 0.4 * pulse), 'bulb')
            elif d <= halo and (x + y + f) % 2 == 0 and (x, y) not in c.px:
                c.set(x, y, ramp(LURE, 0.25 + 0.3 * pulse), 'halo')

    def grip(x, y):
        u, v = diag(x, y, OX, OY)
        if 2.4 <= u <= 8.8 and abs(v) <= 1.0:
            scale = (int(u * 1.4) + (1 if v > 0 else 0)) % 2
            return ramp(SKIN, 0.9 if (scale and v < 0) else 0.5 if scale else 0.15)
        d = math.hypot(u - 1.0, v)
        if d <= 1.7:
            return ramp(GLOWC, 0.9 if u + v < 0.6 else 0.35 + 0.3 * pulse)
        return None
    c.fill(grip, 'grip')
    c.outline((2, 6, 12, 255), skip=('halo',))
    return c.image()


frames = [maw(i, 12) for i in range(12)]
save(frames, f'{OUT}/abyssal_maw.png', frametime=2)
previews.append(frames[3])
gif(frames, '/tmp/claude-0/maw.gif')

# ============================================================ Thick Hide (leggings)
HIDE = [(24, 22, 16), (44, 40, 28), (68, 62, 42), (96, 88, 60), (128, 118, 82), (162, 150, 110)]
STITCH = [(150, 120, 80), (206, 180, 130)]
TEAL = [(18, 90, 90), (40, 150, 140), (110, 220, 200)]


def recolor(img, palette):
    img = img.convert('RGBA')
    vals = [lum(img.getpixel((x, y))) for y in range(img.size[1]) for x in range(img.size[0]) if img.getpixel((x, y))[3] > 0]
    lo, hi = min(vals), max(vals)
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            p = img.getpixel((x, y))
            if p[3]:
                col = ramp(palette, (lum(p) - lo) / max(1e-6, hi - lo))
                out.putpixel((x, y), (col[0], col[1], col[2], p[3]))
    return out


base = recolor(Image.open(f'{V}/netherite_leggings.png'), HIDE).resize((32, 32), Image.NEAREST)
c = Canvas()
for y in range(32):
    for x in range(32):
        p = base.getpixel((x, y))
        if p[3]:
            c.set(x, y, p, 'base')
# leathery patches, stitched seams and a teal belt buckle
for (x, y), p in list(c.px.items()):
    if y in (4, 5):
        c.set(x, y, ramp(HIDE, 0.15))                        # belt
    elif ((x // 3) * 7 + (y // 3) * 5) % 9 == 0 and lum(p) > 0.2:
        c.set(x, y, ramp(HIDE, min(0.99, lum(p) * 2.2 + 0.1)))  # lighter hide patches
    if y > 7 and x in (9, 22) and y % 2 == 0:
        c.set(x, y, ramp(STITCH, 1))                         # stitched seams down each leg
for (x, y) in [(15, 4), (16, 4), (15, 5), (16, 5)]:
    c.set(x, y, ramp(TEAL, 0.9 if y == 4 else 0.5))
for (x, y) in [(6, 13), (7, 14), (25, 12), (24, 13), (6, 21), (25, 22)]:
    if (x, y) in c.px:
        c.set(x, y, ramp(TEAL, 0.6))                         # scale studs
c.outline((14, 12, 10, 255))
hide_icon = c.image()
save([hide_icon], f'{OUT}/thick_hide.png')
previews.append(hide_icon)

# worn layer (humanoid_leggings): paint per face like the other relic armor
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


def hide_style(part, face, lx, ly, w, h, l):
    noise = ((lx * 5 + ly * 7) % 6) / 10
    col = ramp(HIDE, 0.38 + 0.3 * l + noise * 0.2)
    if part == 'body':
        if face in ('front', 'back', 'right', 'left') and ly in (7, 8):
            col = ramp(HIDE, 0.1)                            # thick belt
            if face == 'front' and lx in (3, 4):
                col = ramp(TEAL, 0.85 if ly == 7 else 0.5)    # buckle
        return col
    if ly % 3 == 2 and face != 'bottom':
        col = ramp(HIDE, 0.22)                               # overlapping hide plates
    if (lx == 0 or lx == w - 1) and face in ('front', 'back'):
        col = ramp(HIDE, 0.28)
    if face in ('right', 'left') and lx == w // 2 and ly % 2 == 0:
        col = ramp(STITCH, 1)                                # side seam stitches
    if face == 'front' and ly in (5, 6) and 0 < lx < w - 1:
        col = ramp(HIDE, 0.75)                               # knee plate
        if lx == 1 and ly == 5:
            col = ramp(TEAL, 0.7)
    return col


worn = Image.new('RGBA', leg_base.size, (0, 0, 0, 0))
for y in range(leg_base.size[1]):
    for x in range(leg_base.size[0]):
        if leg_base.getpixel((x, y))[3] == 0:
            continue
        f = face_of(x, y)
        if f:
            col = hide_style(*f, lum(leg_base.getpixel((x, y))))
            worn.putpixel((x, y), col if len(col) == 4 else tuple(col) + (255,))
import os
os.makedirs(f'{EQ}/humanoid_leggings', exist_ok=True)
worn.save(f'{EQ}/humanoid_leggings/thick_hide.png')
worn.resize((256, 128), Image.NEAREST).save('/tmp/claude-0/thick_hide_layer.png')

# ============================================================ materials
MARROW = [(90, 6, 18), (160, 18, 34), (220, 50, 60), (255, 130, 120)]


def marrow(f, n):
    c = Canvas()
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    OX, OY = 4.0, 28.0
    for y in range(32):
        for x in range(32):
            u, v = diag(x, y, OX, OY)
            knob = min(math.hypot(u - 3, v - 1.6), math.hypot(u - 3, v + 1.6),
                       math.hypot(u - 28, v - 1.6), math.hypot(u - 28, v + 1.6))
            if knob <= 2.2 or (3 <= u <= 28 and abs(v) <= 1.7):
                t = 0.45 + (0.4 if v < -0.6 else 0) - (0.3 if v > 1.0 else 0)
                col = ramp(BONE_C, t)
                # cracked open in the middle, glowing red marrow
                if 12.5 <= u <= 18.5 and abs(v) <= 1.0 + 0.4 * math.sin(u * 2):
                    col = ramp(MARROW, 0.3 + 0.65 * pulse if abs(v) < 0.6 else 0.25)
                # bite marks
                if (u - 22) ** 2 + (v - 1.8) ** 2 < 0.5 or (u - 24) ** 2 + (v - 1.8) ** 2 < 0.5 or (u - 8) ** 2 + (v + 1.9) ** 2 < 0.5:
                    continue
                c.set(x, y, col)
    if pulse > 0.6:
        c.set(16, 13, ramp(MARROW, 0.99))
    c.outline((30, 12, 10, 255))
    return c.image()


def lure(f, n):
    c = Canvas()
    a = 2 * math.pi * f / n
    pulse = 0.5 + 0.5 * math.sin(a)
    # curved stalk from the lower-left
    for i in range(60):
        t = i / 59
        x = 6 + 12 * t
        y = 27 - 15 * t - 5 * math.sin(math.pi * t)
        for dx in (0, 1):
            c.set(int(x) + dx, int(y), ramp(SKIN, 0.75 if dx == 0 else 0.35), 'stalk')
    cx, cy = 20.5, 10.5
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= 5.2:
                col = ramp(LURE, 0.45 + 0.35 * pulse + (0.2 if x + y < cx + cy - 2 else 0) - d * 0.05)
                if d < 2:
                    col = ramp(LURE, 0.99)
                c.set(x, y, col, 'bulb')
    # halo sparks orbiting
    for k in range(3):
        b = a + k * 2 * math.pi / 3
        c.set(int(cx + math.cos(b) * 8), int(cy + math.sin(b) * 8), ramp(GLOWC, 0.9), 'spark')
    c.outline((4, 10, 16, 255), skip=('spark',))
    return c.image()


def behemoth(f, n):
    c = Canvas()
    shine = (f / n) * 40 - 4
    # a thick folded hide, slightly irregular
    P = [(3, 8), (8, 7), (10, 3), (15, 6), (21, 4), (23, 8), (29, 8), (27, 15), (29, 22), (24, 23), (22, 28),
         (16, 25), (10, 28), (8, 23), (3, 23), (5, 15)]

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
                # overlapping armor plates (scales) in rows
                row = y // 4
                cell = (x + (2 if row % 2 else 0)) % 5
                t = 0.42 + 0.1 * (cell / 4) + (0.2 if cell == 0 else 0) - 0.008 * y
                if y % 4 == 3:
                    t = 0.22
                g = max(0, 1 - abs((x + y) - shine) / 2.5)
                c.set(x, y, ramp(HIDE, min(0.99, t + 0.45 * g)))
    # a stitched scar across
    for i in range(14):
        x, y = 8 + i, 20 - i // 2
        c.set(x, y, ramp(STITCH, 0.8))
        if i % 3 == 0:
            c.set(x, y - 1, ramp(STITCH, 1)); c.set(x, y + 1, ramp(STITCH, 1))
    c.set(23, 11, ramp(TEAL, 0.9)); c.set(10, 10, ramp(TEAL, 0.7))
    c.outline((12, 10, 8, 255))
    return c.image()


for name, fn, n, ft in [('ravenous_marrow', marrow, 12, 2), ('abyssal_lure', lure, 12, 2), ('behemoth_hide', behemoth, 16, 2)]:
    frames = [fn(i, n) for i in range(n)]
    save(frames, f'{OUT}/{name}.png', frametime=ft)
    previews.append(frames[n // 3])
    gif(frames, f'/tmp/claude-0/{name}.gif')

preview(previews, '/tmp/claude-0/tank_prev.png', scale=6)
print('tank textures done')
