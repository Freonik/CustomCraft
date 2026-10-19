"""Detailed textures for Knightfall, Rimebreaker, Valkyrie's Lance and Trident of the Great Sea."""
import math
from px import *
from PIL import ImageOps

# shared palettes (cool steel, warm gold, ...)
NSTEEL = [(14, 14, 24), (26, 28, 44), (42, 46, 68), (64, 70, 96), (96, 104, 132), (140, 148, 176), (196, 204, 226), (244, 248, 255)]
GOLDH = [(72, 40, 10), (122, 76, 20), (176, 122, 36), (222, 172, 64), (250, 214, 112), (255, 244, 196)]
RED = [(60, 4, 14), (110, 10, 26), (170, 24, 40), (226, 64, 70), (255, 150, 140)]
CLOTH = [(40, 6, 14), (78, 14, 26), (118, 26, 40)]
FROST = [(10, 26, 52), (20, 50, 92), (36, 84, 140), (64, 128, 188), (112, 180, 230), (180, 226, 252), (238, 252, 255)]
RUNEG = [(20, 90, 140), (50, 170, 230), (150, 240, 255), (240, 255, 255)]
LEATH = [(28, 18, 12), (50, 32, 20), (78, 52, 32), (108, 76, 48)]
SILVER = [(80, 86, 100), (140, 148, 164), (200, 206, 220), (246, 248, 255)]
IVORY = [(150, 140, 118), (200, 192, 170), (236, 230, 212), (255, 252, 240)]
SKY = [(40, 80, 150), (80, 140, 220), (170, 215, 255)]
DEEP = [(4, 14, 34), (8, 28, 62), (14, 48, 96), (24, 76, 136), (44, 112, 176), (90, 160, 214), (170, 220, 245)]
TEAL = [(10, 60, 60), (20, 110, 100), (50, 170, 150), (140, 235, 210)]
PEARLH = [(150, 160, 176), (200, 208, 222), (236, 240, 248), (255, 255, 255)]


def U(fn, OX=3.0, OY=29.0):
    def f(x, y):
        u, v = diag(x, y, OX, OY)
        return fn(u, v)
    return f


def lit(v, w):
    """0..1 shading across a cylinder of half-width w, light from the upper-left (v < 0)."""
    t = (w - v) / (2 * w) if w > 0 else 0.5
    return max(0.0, min(1.0, t))


# ============================================================ Knightfall: gothic flanged mace with a crowned head
def knightfall(f, n):
    c = Canvas()
    gem = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    shine = (f / n) * 30 - 6

    def handle(u, v):
        if 2.0 <= u <= 15.0 and abs(v) <= 1.05:
            if 13.4 <= u:                                   # gold collar under the head
                return ramp(GOLDH, 0.3 + 0.6 * lit(v, 1.05))
            if int(u * 1.4) % 3 == 0:                       # red cloth wrap with gold thread
                return ramp(GOLDH, 0.55 + 0.3 * lit(v, 1.05))
            return ramp(CLOTH, 0.15 + 0.85 * lit(v, 1.05))
        d = math.hypot(u - 1.0, v)
        if d <= 1.75:                                       # pommel: gold with a red dot
            if d < 0.6:
                return ramp(RED, 0.7)
            return ramp(GOLDH, 0.25 + 0.7 * lit(v + (u - 1.0) * 0.4, 1.8))
        return None
    c.fill(U(handle), 'handle')

    def head(u, v):
        if not (14.6 <= u <= 31.6):
            return None
        # finial spike on top
        if u > 27.2:
            w = 1.3 * (31.6 - u) / 4.4 + 0.2
            if abs(v) <= w:
                return ramp(NSTEEL, 0.3 + 0.65 * lit(v, w))
            return None
        core_w = 2.6 if 16.2 < u < 26.6 else 1.6
        # 4 flanges per side: pointed blades sticking out sideways
        fl = 0.0
        for fu in (18.0, 21.3, 24.6):
            k = 1 - abs(u - fu) / 1.55
            if k > 0:
                fl = max(fl, k)
        w = core_w + 3.4 * min(1.0, fl * 1.4)
        if abs(v) > w:
            return None
        if abs(v) > core_w:                                  # flange blade: light face / dark face + bright edge
            if abs(v) > w - 0.7:
                return ramp(NSTEEL, 0.92 if v < 0 else 0.6)
            near = abs(v) - core_w                          # light face near the core, darker toward the tip
            base = (0.62 if v < 0 else 0.28) - 0.04 * near
            g = 0.25 if abs(u - (shine * 0.5 + 14)) < 0.6 and v < 0 else 0
            return ramp(NSTEEL, min(0.99, base + g))
        # core: gold bands top & bottom, red crowned gem in the middle
        if u < 16.3 or 26.4 < u:
            return ramp(GOLDH, 0.3 + 0.65 * lit(v, core_w))
        gu, gv = u - 21.3, v
        if math.hypot(gu * 0.8, gv) <= 1.6:
            return ramp(RED, 0.35 + 0.6 * gem if gu + gv < 0.3 else 0.3)
        if math.hypot(gu * 0.8, gv) <= 2.3:
            return ramp(GOLDH, 0.45 + 0.5 * lit(v, 2.3))
        return ramp(NSTEEL, 0.2 + 0.6 * lit(v, core_w))
    c.fill(U(head), 'head')
    c.outline((6, 4, 10, 255))
    return c.image()


# ============================================================ Rimebreaker: bearded war axe of frozen steel
def rimebreaker(f, n, thrown=False):
    c = Canvas()
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    shine = (f / n) * 26 - 4
    HU = 22.5

    def handle(u, v):
        if 1.8 <= u <= HU + 5.2 and abs(v) <= 1.0:
            if HU - 3.0 < u < HU + 3.2:
                if thrown:
                    return ramp(RUNEG, 0.35 + 0.6 * pulse if abs(v) < 0.6 else 0.25)
                return ramp(NSTEEL, 0.25 + 0.6 * lit(v, 1.0))
            if int(u) in (6, 7, 13, 14):
                return ramp(SILVER, 0.2 + 0.75 * lit(v, 1.0))          # silver bands
            return ramp(LEATH, 0.1 + 0.85 * lit(v, 1.0) - (0.2 if int(u * 2) % 3 == 0 else 0))
        if u > HU + 5.2 and u < HU + 7.4 and abs(v) <= 1.0 - (u - HU - 5.2) * 0.4:
            return ramp(SILVER, 0.4 + 0.5 * lit(v, 1.0))               # top cap point
        d = math.hypot(u - 1.0, v)
        if d <= 1.6:
            return ramp(FROST, 0.5 + 0.45 * lit(v, 1.6))
        return None
    c.fill(U(handle), 'handle')
    if not thrown:
        def blade(u, v):
            du = u - HU
            if v >= -1.0:
                # back: a cluster of ice crystals
                for (cu, cv, r) in ((0.6, 2.4, 1.5), (-1.4, 3.6, 1.1), (1.9, 3.9, 1.0)):
                    if math.hypot(du - cu, (v - cv) * 0.8) <= r:
                        return ramp(FROST, 0.6 + 0.35 * lit(v - cv, r))
                return None
            depth = -v
            # crescent: outer arc around (HU, -1)
            R = 10.6
            dd = math.hypot(du * 0.92, depth - 0.5)
            if dd > R or depth > 10.9:
                return None
            # beard: cut the lower inner part away with a circle
            if math.hypot(du + 7.6, depth - 2.6) < 5.0:
                return None
            if du > 3.6 and depth < 7.5:                           # straight top line near the handle
                return None
            if R - dd < 1.15:                                      # bright cutting edge + sliding glint
                g = max(0, 1 - abs((du - depth) - shine + 14) / 2.5)
                return ramp(FROST, min(0.99, 0.82 + 0.17 * g))
            if R - dd < 1.9:
                return ramp(FROST, 0.62)                           # bevel
            # frost rune: a glowing ring + line engraved in the middle of the blade
            rr = math.hypot(du + 0.2, depth - 5.6)
            if abs(rr - 2.0) < 0.45 or (abs(du + 0.2) < 0.4 and 3.0 < depth < 8.2):
                return ramp(RUNEG, 0.3 + 0.65 * pulse)
            t = 0.06 + 0.03 * depth + (0.08 if du > 0 else 0) - (0.04 if du < -4 else 0)
            if (int(du * 1.3) + int(depth * 1.1)) % 5 == 0:
                t += 0.08                                          # frost speckle
            return ramp(FROST, min(0.45, t))
        c.fill(U(blade), 'blade')
    c.outline((4, 8, 18, 255))
    return c.image()


# ============================================================ Valkyrie's Lance: winged leaf-blade spear
def lance(f, n, long_=False):
    c = Canvas()
    shine = (f / n) * 46 - 6
    gem = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    OX, OY = (1.5, 30.5) if long_ else (3.0, 29.0)
    end = 39.0 if long_ else 37.0
    blade_len = 9.5 if long_ else 10.0
    b0 = end - blade_len
    sw = 0.75 if long_ else 1.0

    def shaft(u, v):
        if 1.2 <= u <= b0 - 1.0 and abs(v) <= sw:
            if (u * 1.2 + v * 1.5) % 4.0 < 0.9:
                return ramp(GOLDH, 0.35 + 0.6 * lit(v, sw))           # gold spiral wrap
            return ramp(IVORY, 0.15 + 0.85 * lit(v, sw))
        if math.hypot(u - 1.0, v) <= sw + 0.6:
            return ramp(GOLDH, 0.4 + 0.5 * lit(v, sw + 0.6))
        return None
    c.fill(U(shaft, OX, OY), 'shaft')

    def head(u, v):
        # socket + blue gem
        if b0 - 1.6 <= u <= b0 + 0.3 and abs(v) <= sw + 0.5:
            if abs(u - (b0 - 0.6)) < 0.6 and abs(v) < 0.6:
                return ramp(SKY, 0.5 + 0.5 * gem)
            return ramp(GOLDH, 0.35 + 0.6 * lit(v, sw + 0.5))
        # leaf-shaped blade
        if b0 <= u <= end:
            t = (u - b0) / blade_len
            w = (2.6 if not long_ else 2.0) * math.sin(math.pi * min(1, t * 1.15)) ** 0.8 + 0.2 if t < 0.87 else \
                (end - u) / (end - (b0 + 0.87 * blade_len)) * 0.9
            if abs(v) <= w:
                if abs(v) < 0.35 and t < 0.8:
                    return ramp(SILVER, 0.55)                          # fuller ridge
                g = max(0, 1 - abs(u - abs(v) - shine) / 2.2)
                if abs(v) > w - 0.6:
                    return ramp(SILVER, min(0.99, 0.85 + 0.15 * g))    # edges
                return ramp(SILVER, min(0.99, (0.62 if v < 0 else 0.3) + 0.35 * g))
        # golden wings sweeping back from the socket
        du = u - (b0 - 1.0)
        for side in (-1, 1):
            vv = v * side
            if 0.9 < vv < 5.6:
                lo = -4.2 + vv * 0.25
                hi = -1.6 + vv * 0.55 - max(0, vv - 4.0) * 1.2
                if lo < du < hi:
                    feather = int((du - vv * 0.6) * 1.1) % 2
                    return ramp(GOLDH, (0.85 if side < 0 else 0.55) - 0.25 * feather)
        return None
    c.fill(U(head, OX, OY), 'head')
    c.outline((28, 18, 6, 255))
    return c.image()


# ============================================================ Trident of the Great Sea: crowned sea-king trident
def trident(f, n, long_=False):
    c = Canvas()
    wave = 2 * math.pi * f / n
    gem = 0.5 + 0.5 * math.sin(wave)
    OX, OY = (1.5, 30.5) if long_ else (3.0, 29.0)
    base = 25.5 if long_ else 20.5
    sw = 0.8 if long_ else 1.0

    def shaft(u, v):
        if 1.2 <= u <= base and abs(v) <= sw:
            if base - 2.8 < u:
                return ramp(GOLDH, 0.3 + 0.65 * lit(v, sw))            # crown band
            flow = 0.5 + 0.5 * math.sin(u * 0.9 - wave + v)          # flowing water inside the shaft
            return ramp(DEEP, 0.15 + 0.45 * lit(v, sw) + 0.3 * flow)
        if math.hypot(u - 1.0, v) <= sw + 0.7:
            return ramp(PEARLH, 0.3 + 0.65 * lit(v, sw + 0.7))       # pearl pommel
        return None
    c.fill(U(shaft, OX, OY), 'shaft')

    def head(u, v):
        du = u - base
        # crown: gold cross-guard with three points and a blue gem
        if 0 <= du <= 2.0 and abs(v) <= 5.2 - du * 0.3:
            if math.hypot(du - 1.0, v) < 0.9:
                return ramp(SKY, 0.45 + 0.55 * gem)
            return ramp(GOLDH, 0.3 + 0.6 * lit(v, 5.2))
        if 2.0 < du <= 3.0 and min(abs(v), abs(abs(v) - 4.3)) < 0.7 - (du - 2.0) * 0.5:
            return ramp(GOLDH, 0.85)                                  # crown points
        # prongs: outer ones curve outward, middle one longest
        for pv, length, curve in ((-4.0, 9.0, -0.9), (0.0, 12.0, 0.0), (4.0, 9.0, 0.9)):
            if 1.5 <= du <= length:
                cv = pv + curve * ((du - 1.5) / length) ** 2 * 2.0
                tip = length - 2.6
                if du > tip:                                          # barbed pearl tips
                    w = 0.95 * (length - du) / 2.6 + 0.1
                    barb = pv != 0 and tip < du < tip + 0.9 and (v - cv) * pv > 0 and abs(v - cv) < 1.6
                    if abs(v - cv) <= w or barb:
                        return ramp(PEARLH, 0.95 if v < cv else 0.55)
                    continue
                if abs(v - cv) <= 0.8:
                    return ramp(TEAL, 0.15 + 0.75 * lit(v - cv, 0.8))
        return None
    c.fill(U(head, OX, OY), 'head')
    c.outline((2, 8, 20, 255))
    return c.image()


def mirror(img):
    return ImageOps.mirror(img)
