"""Knightfall, Rimebreaker, Pinning Repeater, Valkyrie's Lance, Trident of the Great Sea + their materials."""
import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *
from PIL import Image, ImageOps
import arsenal_hd as hd     # detailed versions of the mace, axe, lance and trident

OUT = sys.argv[1]
previews = []

STEEL = [(16, 16, 22), (30, 30, 40), (50, 50, 64), (78, 78, 96), (118, 118, 138), (170, 170, 190), (230, 230, 242)]
GOLD = [(90, 56, 14), (150, 100, 30), (212, 160, 56), (246, 210, 110), (255, 246, 200)]
CRIMSON = [(70, 6, 16), (140, 14, 30), (210, 40, 54), (255, 120, 120)]
LEATHER = [(36, 22, 14), (62, 40, 24), (92, 62, 38)]
ICE = [(16, 40, 70), (34, 80, 130), (70, 140, 200), (140, 205, 245), (225, 248, 255)]
RUNE = [(30, 120, 170), (60, 200, 240), (180, 250, 255)]
PEARL = [(170, 176, 190), (215, 222, 232), (248, 250, 255)]
SEA = [(6, 20, 50), (12, 40, 90), (22, 70, 140), (40, 110, 190), (100, 170, 230), (190, 230, 255)]
WOOD = [(42, 28, 18), (70, 48, 30), (104, 74, 46)]


def U(fn, OX=3.0, OY=29.0):
    def f(x, y):
        u, v = diag(x, y, OX, OY)
        return fn(u, v)
    return f


def mirror(img):
    return ImageOps.mirror(img)


# ============================================================ Knightfall (mace)
def knightfall(f, n):
    c = Canvas()
    glint = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    HU, HV = 21.0, 0.0
    R = 7.0

    def handle(u, v):
        if 1.5 <= u <= HU - 5 and abs(v) <= 0.95:
            if int(u) in (5, 6, 11, 12):
                return ramp(GOLD, 0.85 if v < 0 else 0.45)          # gold rings
            band = int(u * 1.5) % 2
            return ramp(LEATHER, 0.9 if (band and v < 0) else 0.5 if band else 0.2)
        if math.hypot(u - 0.8, v) <= 1.5:
            return ramp(GOLD, 0.9 if u + v < 0.5 else 0.4)            # pommel
        return None
    c.fill(U(handle), 'handle')

    def head(u, v):
        du, dv = u - HU, v - HV
        d = math.hypot(du, dv)
        a = math.atan2(dv, du)
        # six flanges sticking out past the round head
        flange = abs(((a * 6 / (2 * math.pi)) % 1) - 0.5) < 0.16
        if d <= R or (flange and d <= R + 2.4):
            if d > R:
                t = 0.75 if dv < 0 else 0.35
                return ramp(GOLD, t + 0.2 * (d > R + 1.8))             # gold-tipped flanges
            if d <= 2.4:                                              # crest: red gem in a gold setting
                if d <= 1.3:
                    return ramp(CRIMSON, 0.55 + 0.45 * glint if du + dv < 0 else 0.4)
                return ramp(GOLD, 0.9 if dv < 0 else 0.55)
            t = 0.35 + 0.35 * (-(du + dv) / (R * 1.4))                 # lit from the upper left
            if abs(d - 4.2) < 0.45:
                t = 0.15                                              # engraved ring
            return ramp(STEEL, max(0.05, min(0.95, t + 0.25)))
        return None
    c.fill(U(head), 'head')
    # small crown on top of the gem setting
    c.outline((10, 6, 4, 255))
    return c.image()


frames = [hd.knightfall(i, 12) for i in range(12)]
save(frames, f'{OUT}/knightfall.png', frametime=3)
previews.append(frames[0])


# ============================================================ Rimebreaker (war axe) + thrown look
def rimebreaker(f, n, thrown=False):
    c = Canvas()
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    HU = 22.0

    def handle(u, v):
        if 1.6 <= u <= HU + 4.5 and abs(v) <= 0.95:
            if HU - 2.5 < u < HU + 2.8:
                if thrown:
                    return ramp(RUNE, 0.4 + 0.5 * pulse)              # empty glowing socket
                return ramp(STEEL, 0.6 if v < 0 else 0.3)
            band = int(u * 1.3) % 3
            return ramp(LEATHER, 0.85 if (band == 0 and v < 0) else 0.5 if band else 0.15)
        if math.hypot(u - 0.9, v) <= 1.5:
            return ramp(ICE, 0.9 if u + v < 0.5 else 0.45)
        return None
    c.fill(U(handle), 'handle')
    if not thrown:
        def blade(u, v):
            du = u - HU
            # bearded blade on the upper-left side: wide crescent edge
            if v < -0.9 and v > -11.0:
                depth = -v
                top = 3.2 + 0.18 * depth * depth / 3          # flares out toward the edge
                bottom = -2.0 - 0.32 * depth                    # the "beard" hangs down
                if bottom <= du <= top:
                    edge = depth > 9.6 - 0.3 * abs(du) * 0.4
                    if edge:
                        return ramp(ICE, 0.95)
                    if abs(du - (top + bottom) / 2) < 0.5 and 2.5 < depth < 8:
                        return ramp(RUNE, 0.35 + 0.6 * pulse)    # frost rune line
                    if (int(depth) + int(du)) % 5 == 0 and 3 < depth < 8:
                        return ramp(RUNE, 0.25 + 0.4 * pulse)
                    return ramp(ICE, 0.25 + 0.35 * (du > 0) + 0.04 * depth)
            # back spike
            if 0.9 < v < 5.5 and abs(du) < 1.6 - (v - 0.9) * 0.33:
                return ramp(STEEL, 0.7 if du > 0 else 0.35)
            return None
        c.fill(U(blade), 'blade')
    c.outline((6, 10, 20, 255))
    return c.image()


frames = [hd.rimebreaker(i, 12) for i in range(12)]
save(frames, f'{OUT}/rimebreaker.png', frametime=3)
previews.append(frames[0])
frames = [hd.rimebreaker(i, 12, thrown=True) for i in range(12)]
save(frames, f'{OUT}/rimebreaker_thrown.png', frametime=3)
previews.append(frames[0])


# ============================================================ Pinning Repeater (crossbow states)
def repeater(pull, load=None):
    """pull: 0 = relaxed .. 3 = fully drawn; load: None / 'arrow' / 'rocket'."""
    c = Canvas()
    LU = 21.0                                   # where the limbs sit on the stock
    bend = [1.3, 1.9, 2.5, 3.1][pull]
    string_u = [19.8, 17.0, 14.5, 12.0][pull]

    def stock(u, v):
        if 1.5 <= u <= 27 and abs(v) <= 1.05:
            if abs(u - LU) < 1.2:
                return ramp(STEEL, 0.75 if v < 0 else 0.4)           # limb mount
            if u < 9:
                return ramp(WOOD, 0.85 if v < -0.2 else 0.45)        # grip
            return ramp(STEEL, 0.55 if v < -0.2 else 0.25)
        if 9.5 <= u <= 12 and 1.0 < v <= 2.6:
            return ramp(STEEL, 0.35)                                 # trigger
        return None
    c.fill(U(stock), 'stock')

    def limbs(u, v):
        for side in (-1, 1):
            vv = v * side
            if 0.9 < vv <= 11.5:
                center = LU - (bend + 0.25) * (vv / 11.5) ** 1.6 * 2.2
                if abs(u - center) <= 1.1 - vv * 0.04:
                    if vv > 10.3:
                        return ramp(GOLD, 0.8)                        # steel tips
                    return ramp(STEEL, 0.75 if u > center else 0.4)
        return None
    c.fill(U(limbs), 'limbs')

    def string(u, v):
        tip_u = LU - (bend + 0.25) * 2.2
        for side in (-1, 1):
            vv = v * side
            if 0 <= vv <= 11.2:
                su = string_u + (tip_u - string_u) * (vv / 11.2)
                if abs(u - su) < 0.45:
                    return ramp(PEARL, 0.7)
        return None
    for y in range(32):
        for x in range(32):
            if (x, y) not in c.px:
                col = U(string)(x, y)
                if col:
                    c.set(x, y, col, 'string')
    if load == 'arrow':
        def bolt(u, v):
            if string_u - 0.5 <= u <= 30 and abs(v) <= 0.45:
                if u > 27.5:
                    return ramp(STEEL, 0.95)                          # barbed head
                return ramp(PEARL, 0.85) if int(u) % 3 else ramp(STEEL, 0.5)   # chain-wrapped shaft
            if 27.5 < u < 29 and 0.45 < abs(v) < 1.6:
                return ramp(STEEL, 0.8)
            return None
        for y in range(32):
            for x in range(32):
                col = U(bolt)(x, y)
                if col:
                    c.set(x, y, col, 'bolt')
    if load == 'rocket':
        def rocket(u, v):
            if string_u - 0.5 <= u <= 28 and abs(v) <= 1.0:
                return ramp(CRIMSON, 0.7 if v < 0 else 0.4)
            return None
        for y in range(32):
            for x in range(32):
                col = U(rocket)(x, y)
                if col:
                    c.set(x, y, col, 'rocket')
    c.outline((8, 8, 12, 255), skip=('string',))
    return c.image()


for name, pull, load in [('pinning_repeater', 0, None), ('pinning_repeater_pulling_0', 1, None),
                         ('pinning_repeater_pulling_1', 2, None), ('pinning_repeater_pulling_2', 3, None),
                         ('pinning_repeater_arrow', 3, 'arrow'), ('pinning_repeater_firework', 3, 'rocket')]:
    img = repeater(pull, load)
    save([img], f'{OUT}/{name}.png')
    if name in ('pinning_repeater', 'pinning_repeater_arrow'):
        previews.append(img)


# ============================================================ Valkyrie's Lance (icon + in hand)
def lance(f, n, long_=False):
    c = Canvas()
    shine = (f / n) * 40 - 6
    end = 38.5 if long_ else 36.0
    tip0 = end - (9.0 if long_ else 8.5)

    def shaft(u, v):
        if 1.0 <= u <= tip0 + 0.5 and abs(v) <= (0.7 if long_ else 0.95):
            if int(u) % 6 == 0:
                return ramp(GOLD, 0.8)
            return ramp(PEARL, 0.75 if v < 0 else 0.35)
        return None
    c.fill(U(shaft, 1.5 if long_ else 3.0, 30.5 if long_ else 29.0), 'shaft')

    def head(u, v):
        if tip0 <= u <= end:
            w = 2.0 * (1 - (u - tip0) / (end - tip0)) ** 0.7 + 0.25
            if abs(v) <= w:
                g = max(0, 1 - abs(u - shine) / 2)
                return ramp(GOLD, min(0.99, (0.8 if v < 0 else 0.45) + 0.3 * g))
        # little feathered wings at the socket
        du = u - (tip0 - 0.5)
        for side in (-1, 1):
            vv = v * side
            if 0.8 < vv < 4.6 and -3.2 + vv * 0.35 < du < -1.6 + vv * 0.7:
                return ramp(PEARL, 0.95 if int(vv + du) % 2 else 0.6)
        return None
    c.fill(U(head, 1.5 if long_ else 3.0, 30.5 if long_ else 29.0), 'head')
    c.outline((30, 22, 10, 255))
    return c.image()


frames = [hd.lance(i, 16) for i in range(16)]
save(frames, f'{OUT}/valkyries_lance.png', frametime=2)
previews.append(frames[4])
frames = [mirror(hd.lance(i, 16, long_=True)) for i in range(16)]   # in hand: tip toward the top-left like vanilla
save(frames, f'{OUT}/valkyries_lance_in_hand.png', frametime=2)


# ============================================================ Trident of the Great Sea (icon + in hand)
def trident(f, n, long_=False):
    c = Canvas()
    wave = 2 * math.pi * f / n
    OX, OY = (1.5, 30.5) if long_ else (3.0, 29.0)
    base = 25.0 if long_ else 21.5
    end = base + (11.5 if long_ else 10.5)

    def shaft(u, v):
        if 1.0 <= u <= base + 1 and abs(v) <= (0.75 if long_ else 0.95):
            if base - 2.6 < u < base - 0.6:
                return ramp(GOLD, 0.9 if v < 0 else 0.5)               # gold crown band
            flow = 0.5 + 0.5 * math.sin(u * 0.8 - wave)
            return ramp(SEA, 0.25 + 0.35 * flow + (0.15 if v < 0 else 0))
        if math.hypot(u - 0.9, v) <= 1.3:
            return ramp(PEARL, 0.9)
        return None
    c.fill(U(shaft, OX, OY), 'shaft')

    def prongs(u, v):
        if base <= u <= base + 1.6 and abs(v) <= 4.6:
            return ramp(GOLD, 0.75 if v < 0 else 0.4)                  # cross bar
        for pv, length in ((-3.9, 8.0), (0.0, 10.5), (3.9, 8.0)):
            pend = base + length
            if base + 1.5 <= u <= pend and abs(v - pv) <= 0.8:
                if u > pend - 2.4:
                    w = 0.8 * (pend - u) / 2.4 + 0.15
                    if abs(v - pv) <= w + (0.7 if (pend - 2.4 < u < pend - 1.6 and v * pv >= 0 and pv != 0) else 0):
                        return ramp(PEARL, 0.95 if v < pv else 0.6)     # barbed pearl tips
                    return None
                return ramp(SEA, 0.65 if v < pv else 0.35)
        return None
    c.fill(U(prongs, OX, OY), 'prongs')
    c.outline((2, 8, 20, 255))
    return c.image()


frames = [hd.trident(i, 12) for i in range(12)]
save(frames, f'{OUT}/great_sea_trident.png', frametime=2)
previews.append(frames[0])
frames = [mirror(hd.trident(i, 12, long_=True)) for i in range(12)]
save(frames, f'{OUT}/great_sea_trident_in_hand.png', frametime=2)


# ============================================================ materials
def crest(f, n):
    c = Canvas()
    shine = (f / n) * 44 - 6
    for y in range(32):
        for x in range(32):
            px_, py_ = x + 0.5 - 16, y + 0.5
            # heater shield shape
            if 4 <= py_ <= 28 and abs(px_) <= (10 if py_ < 16 else 10 * math.sqrt(max(0, 1 - ((py_ - 16) / 12.5) ** 2))):
                t = 0.55 + 0.25 * (px_ < 0) - 0.01 * py_
                if abs(px_) > 8.6 or py_ < 5.5:
                    t = 0.25                                        # dark rim
                if abs(px_) < 1 and 7 < py_ < 25 or abs(py_ - 13) < 1 and abs(px_) < 7:
                    col = ramp(CRIMSON, 0.6)                          # red cross
                else:
                    g = max(0, 1 - abs((x + y) - shine) / 2.5)
                    col = ramp(GOLD, min(0.99, t + 0.4 * g))
                c.set(x, y, col)
    # a crack: the knight fell
    for (x, y) in [(20, 6), (19, 7), (19, 8), (18, 9), (19, 10), (18, 11)]:
        c.set(x, y, (40, 24, 8, 255))
    c.outline((30, 18, 4, 255))
    return c.image()


def rime(f, n):
    c = Canvas()
    a0 = 2 * math.pi * f / n
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - 16, y + 0.5 - 16
            d = math.hypot(dx, dy)
            if d <= 9.5:
                a = math.atan2(dy, dx)
                swirl = 0.5 + 0.5 * math.sin(a * 3 + d * 0.7 - a0)
                t = 0.3 + 0.35 * swirl + (0.25 if dx + dy < -6 else 0) - 0.02 * d
                c.set(x, y, ramp(ICE, min(0.99, t)))
    # ice spikes
    for k in range(6):
        a = k * math.pi / 3 + 0.3
        for r in range(10, 13):
            c.set(int(16 + math.cos(a) * r), int(16 + math.sin(a) * r), ramp(ICE, 0.9 - (r - 10) * 0.2))
    c.outline((6, 18, 36, 255))
    return c.image()


def bolt(f, n):
    c = Canvas()
    glint = (f / n) * 36

    def fn(u, v):
        if 2 <= u <= 30 and abs(v) <= 0.6:
            if u > 25:
                return ramp(STEEL, 0.95 if max(0, 1 - abs(u - glint) / 2) > 0.3 else 0.7)
            if u < 6:
                return ramp(PEARL, 0.8) if abs(v) < 0.4 else ramp(CRIMSON, 0.6)
            return ramp(WOOD, 0.8 if v < 0 else 0.4)
        if 25 < u < 28 and 0.6 < abs(v) < 2.2 - (u - 25) * 0.5:
            return ramp(STEEL, 0.75)                                  # barbs
        if 2 <= u <= 6 and 0.6 < abs(v) < 2.2:
            return ramp(CRIMSON, 0.55)                                # fletching
        return None
    c.fill(U(fn), 'bolt')
    for i in range(5):                                                 # chain links hanging off it
        c.set(10 + i, 22 + (i % 2), ramp(STEEL, 0.6 if i % 2 else 0.85))
    c.outline((10, 8, 8, 255))
    return c.image()


def plume(f, n):
    c = Canvas()
    glow = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)

    def fn(u, v):
        if 1 <= u <= 31:
            w = 4.2 * math.sin(math.pi * min(1, (u - 3) / 27)) if u > 3 else 0
            if abs(v) <= 0.45 and u <= 29:
                return ramp(GOLD, 0.85)
            if abs(v) <= w and u > 3:
                barb = (u * 1.3 + abs(v) * 0.9) % 2.2 < 0.4
                t = 0.95 if v < 0 else 0.6
                if barb:
                    t -= 0.3
                return ramp(PEARL, t) if u < 22 else ramp(GOLD, min(0.99, 0.55 + 0.4 * glow))
        return None
    c.fill(U(fn), 'plume')
    c.outline((40, 30, 12, 255))
    return c.image()


def doubloon(f, n):
    c = Canvas()
    shine = (f / n) * 48 - 8
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - 16, y + 0.5 - 16
            d = math.hypot(dx, dy)
            if d <= 11:
                t = 0.55 + 0.2 * (dx + dy < 0)
                if 9.3 < d:
                    t = 0.3 if (int(math.degrees(math.atan2(dy, dx))) // 15) % 2 else 0.5   # milled edge
                # embossed trident in the middle
                if (abs(dx) < 0.8 and -5 < dy < 7) or (abs(dy + 3) < 0.7 and abs(dx) < 4) or \
                        (abs(abs(dx) - 3.5) < 0.7 and -7 < dy < -2.5):
                    t = 0.25
                # a little crown above it
                if -9 < dy < -6.5 and abs(dx) < 3 and (abs(dx) < 0.6 or abs(abs(dx) - 2.4) < 0.6 or dy > -7.5):
                    t = 0.9
                g = max(0, 1 - abs((x - y) + 16 - shine) / 2.4)
                c.set(x, y, ramp(GOLD, min(0.99, t + 0.45 * g)))
    c.outline((40, 24, 4, 255))
    return c.image()


for name, fn, n, ft in [('fallen_crest', crest, 16, 2), ('rime_core', rime, 12, 2), ('hunters_bolt', bolt, 12, 2),
                        ('valkyrie_plume', plume, 12, 3), ('sea_kings_doubloon', doubloon, 16, 2)]:
    frames = [fn(i, n) for i in range(n)]
    save(frames, f'{OUT}/{name}.png', frametime=ft)
    previews.append(frames[n // 3])

preview(previews, '/tmp/claude-0/arsenal_prev.png', scale=5)
inhand = [Image.open(f'{OUT}/valkyries_lance_in_hand.png').crop((0, 0, 32, 32)),
          Image.open(f'{OUT}/great_sea_trident_in_hand.png').crop((0, 0, 32, 32))] + \
         [Image.open(f'{OUT}/{n}.png') for n in ['pinning_repeater_pulling_0', 'pinning_repeater_pulling_1',
                                                   'pinning_repeater_pulling_2', 'pinning_repeater_firework']]
preview(inhand, '/tmp/claude-0/arsenal_prev2.png', scale=5)
print('arsenal done')
