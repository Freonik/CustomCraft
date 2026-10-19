import sys, math, random
sys.path.insert(0, '/home/claude/art')
from px import *

OUT = sys.argv[1]
N = 32

def outline_canvas(c, color):
    c.outline(color)
    return c.image()

def poly_inside(x, y, pts):
    inside = False
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]; xj, yj = pts[j]
        if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi):
            inside = not inside
        j = i
    return inside

icons = {}

# 1 Resonant Shard: tall cyan crystal with a pulsing core line
def resonant(f, n):
    c = Canvas()
    P = [(16, 2), (22, 10), (20, 28), (12, 28), (10, 10)]
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    pal = [(10, 50, 60), (20, 100, 112), (40, 170, 175), (120, 235, 230), (230, 255, 255)]
    for y in range(N):
        for x in range(N):
            if poly_inside(x + 0.5, y + 0.5, P):
                t = 0.35 + 0.25 * (x < 16) - 0.15 * (x > 18)
                if abs(x + 0.5 - 16) < 1 and 6 < y < 26:
                    wave = (y / 20 - f / n) % 1
                    t = 0.6 + 0.4 * max(0, 1 - abs(wave - 0.5) * 4)
                if x + 0.5 < 12.5 + (y - 10) * 0.05 and y > 10:
                    t = 0.15
                c.set(x, y, ramp(pal, t))
    for (x, y) in [(13, 9), (14, 8)]:
        c.set(x, y, ramp(pal, 0.99))
    if f % n < 2:
        c.set(24, 6, ramp(pal, 0.99)); c.set(25, 5, ramp(pal, 0.7))
    return outline_canvas(c, (4, 20, 26, 255))
icons['resonant_shard'] = (resonant, 12, 2)

# 2 Graviton Core: dark orb, purple core, orbiting spark
def graviton(f, n):
    c = Canvas()
    pal = [(12, 8, 26), (30, 22, 60), (60, 44, 110), (140, 90, 230), (230, 210, 255)]
    a = 2 * math.pi * f / n
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            if d <= 9:
                t = 0.15 + 0.25 * (1 - d / 9) + (0.15 if (x < 14 and y < 14) else 0)
                if d <= 3.5:
                    t = 0.75 + 0.25 * (0.5 + 0.5 * math.sin(a * 2))
                c.set(x, y, ramp(pal, t))
            ex, ey = (x + 0.5 - 16), (y + 0.5 - 16) * 2.6
            r = math.hypot(ex, ey)
            if 11.5 <= r <= 12.6 and (y > 16 or d > 9):
                ang = math.atan2(ey, ex)
                diff = (ang - a + math.pi) % (2 * math.pi) - math.pi
                c.set(x, y, ramp(pal, 0.95 if abs(diff) < 0.5 else 0.5))
    return outline_canvas(c, (6, 2, 14, 255))
icons['graviton_core'] = (graviton, 12, 2)

# 3 Hexed Thread: green glowing thread spool with a rune
def hexed(f, n):
    c = Canvas()
    wood = [(50, 32, 20), (84, 56, 34), (120, 84, 52)]
    th = [(20, 80, 40), (40, 150, 80), (90, 230, 130), (200, 255, 210)]
    shimmer = (f / n) * 30
    for y in range(N):
        for x in range(N):
            if (6 <= y <= 8 or 24 <= y <= 26) and 7 <= x <= 24:
                c.set(x, y, ramp(wood, 0.8 if y in (6, 24) else 0.3))
            elif 9 <= y <= 23 and 9 <= x <= 22:
                t = 0.35 + 0.2 * ((y % 2) == 0) + (0.3 if abs((x + y) - shimmer) < 2 else 0)
                c.set(x, y, ramp(th, t))
    # loose thread curling out
    for i, (x, y) in enumerate([(23, 14), (24, 15), (25, 17), (26, 19), (26, 21), (25, 23), (24, 25), (25, 27), (27, 28)]):
        c.set(x, y, ramp(th, 0.6))
    # rune on the spool
    for (x, y) in [(15, 12), (16, 12), (15, 13), (16, 15), (15, 16), (16, 17), (17, 14), (14, 14)]:
        c.set(x, y, ramp(th, 0.99))
    return outline_canvas(c, (10, 20, 12, 255))
icons['hexed_thread'] = (hexed, 15, 2)

# 4 Soul Ember: cyan soul flame
def ember(f, n):
    c = Canvas()
    pal = [(10, 40, 60), (20, 110, 140), (60, 200, 220), (170, 250, 255), (240, 255, 255)]
    rnd = random.Random(f)
    for y in range(N):
        for x in range(N):
            cx = x + 0.5 - 16
            h = (29 - y) / 26                     # 0 at the bottom, 1 at the tip
            if h < 0 or h > 1:
                continue
            if h < 0.2:
                w = 7.5 * math.sqrt(h / 0.2)       # rounded base
            else:
                w = 7.5 * (1 - (h - 0.2) / 0.8) ** 0.9
            w += 0.7 * math.sin(h * 10 + f * 1.3) * h
            sway = 2.2 * math.sin(h * 3.5 + f * 0.8) * h * h
            if abs(cx - sway) <= w:
                inner = abs(cx - sway) / max(0.5, w)
                t = 0.98 - 0.55 * inner - 0.35 * h
                c.set(x, y, ramp(pal, t))
    for _ in range(3):
        c.set(rnd.randint(11, 21), rnd.randint(2, 8), ramp(pal, 0.8))
    return outline_canvas(c, (4, 18, 30, 255))
icons['soul_ember'] = (ember, 8, 2)

# 5 Hydra Scale: green scale with a sheen sweeping across
def scale(f, n):
    c = Canvas()
    pal = [(10, 44, 30), (18, 80, 52), (34, 126, 82), (80, 186, 126), (200, 250, 220)]
    sweep = (f / n) * 40 - 6
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - 16, y + 0.5 - 13
            if dy < 0:
                inside = math.hypot(dx / 10, dy / 9) <= 1
            else:
                inside = abs(dx) <= 10 * (1 - (dy / 16) ** 1.6) if dy <= 16 else False
            if inside:
                ridge = abs(dx) < 0.8
                t = 0.4 + 0.15 * (dx < 0) - 0.15 * (dy / 16)
                if ridge:
                    t = 0.7
                if abs((x - y * 0.5) - sweep) < 2:
                    t = 0.95
                ring = (int(math.hypot(dx, dy + 6)) % 4 == 0)
                if ring and not ridge:
                    t -= 0.15
                c.set(x, y, ramp(pal, t))
    return outline_canvas(c, (4, 20, 12, 255))
icons['hydra_scale'] = (scale, 12, 2)

# 6 Chrono Sand: small glass vial with golden sand swirling
def chrono(f, n):
    c = Canvas()
    gold = [(120, 80, 20), (200, 150, 50), (250, 210, 110), (255, 245, 200)]
    glass = (200, 235, 255, 130)
    cork = [(90, 60, 36), (140, 100, 60)]
    for y in range(N):
        for x in range(N):
            cx = x + 0.5 - 16
            if 3 <= y <= 6 and abs(cx) <= 3:
                c.set(x, y, ramp(cork, 0.7 if y < 5 else 0.2)); continue
            if 7 <= y <= 10 and abs(cx) <= 2.5:
                c.set(x, y, glass); continue
            if 11 <= y <= 28:
                w = 2.5 + 5.5 * math.sin(math.pi * min(1, (y - 10) / 12))
                if y > 22:
                    w = 8 - (y - 22) * 0.6
                if abs(cx) <= w:
                    if abs(cx) > w - 1:
                        c.set(x, y, (230, 248, 255, 220))
                    elif y >= 18:
                        sparkle = (x * 5 + y * 3 + f) % 7 == 0
                        c.set(x, y, ramp(gold, 0.95 if sparkle else 0.45 + 0.2 * (cx < 0)))
                    else:
                        swirl = abs(cx - 3 * math.sin((y + f) * 0.8)) < 0.7 and (y + f) % 3 == 0
                        c.set(x, y, ramp(gold, 0.8) if swirl else glass)
    return outline_canvas(c, (30, 22, 10, 255))
icons['chrono_sand'] = (chrono, 8, 3)

# 7 Seraph Feather: white-gold feather on the diagonal with a glow
def feather(f, n):
    c = Canvas()
    white = [(170, 176, 196), (214, 220, 234), (240, 244, 252), (255, 255, 255)]
    gold = [(160, 110, 30), (230, 190, 90), (255, 236, 160)]
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    OX, OY = 6.0, 26.0
    for y in range(N):
        for x in range(N):
            u, v = diag(x, y, OX, OY)
            if u < 0 or u > 30:
                continue
            w = 5.2 * math.sin(math.pi * min(1, max(0, (u - 3) / 27))) if u > 3 else 0
            if abs(v) <= 0.55 and u <= 29:
                c.set(x, y, ramp(gold, 0.6 + 0.35 * pulse)); continue
            if u > 4 and abs(v) <= w:
                barb = int(u * 1.3 + abs(v) * 0.8) % 3 == 0
                t = 0.95 - 0.5 * (abs(v) / max(0.1, w)) - (0.25 if barb else 0) - (0.15 if v > 0 else 0)
                c.set(x, y, ramp(white, t))
    return outline_canvas(c, (90, 80, 70, 255))
icons['seraph_feather'] = (feather, 10, 2)

# 8 Thornheart Seed: green seed with thorns and a glowing heart
def seed(f, n):
    c = Canvas()
    pal = [(16, 40, 14), (34, 72, 26), (60, 110, 40), (110, 170, 70)]
    core = [(80, 140, 30), (170, 230, 60), (240, 255, 160)]
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    for y in range(N):
        for x in range(N):
            dx, dy = (x + 0.5 - 16) / 8, (y + 0.5 - 17) / 10
            d = dx * dx + dy * dy
            if d <= 1:
                t = 0.3 + 0.35 * (dx < 0) - 0.2 * dy
                if d < 0.12:
                    c.set(x, y, ramp(core, 0.5 + 0.5 * pulse)); continue
                c.set(x, y, ramp(pal, t))
    for (x, y) in [(16, 5), (16, 6), (8, 12), (7, 11), (24, 12), (25, 11), (9, 22), (8, 23), (23, 22), (24, 23), (16, 28), (16, 29)]:
        c.set(x, y, (225, 230, 170, 255))
    return outline_canvas(c, (8, 18, 6, 255))
icons['thornheart_seed'] = (seed, 10, 2)

# 9 Crimson Heart: anatomical-ish red heart, beating
def heart(f, n):
    c = Canvas()
    pal = [(60, 4, 12), (120, 8, 24), (180, 22, 38), (230, 70, 70), (255, 190, 180)]
    vein = (90, 20, 60, 255)
    beat = [0, 1, 0.4, 0.9, 0.2, 0, 0, 0, 0, 0][f % n]
    s = 10.5 + 0.8 * beat
    for y in range(N):
        for x in range(N):
            hx = (x + 0.5 - 16) / s
            hy = -(y + 0.5 - 16) / s
            v = (hx * hx + hy * hy - 1) ** 3 - hx * hx * hy ** 3
            if v <= 0:
                depth = min(1, -v * 5)
                t = 0.1 + 0.4 * depth + 0.15 * beat
                if -0.5 < hx < -0.3 and 0.4 < hy < 0.6:
                    t = 0.9
                c.set(x, y, ramp(pal, t))
    # vessels on top and veins
    for (x, y) in [(13, 3), (13, 4), (14, 4), (18, 2), (18, 3), (19, 3), (19, 4)]:
        c.set(x, y, ramp(pal, 0.45))
    for (x, y) in [(18, 12), (19, 13), (19, 14), (20, 15), (12, 16), (13, 17), (14, 19)]:
        if (x, y) in c.px:
            c.set(x, y, vein)
    return outline_canvas(c, (30, 2, 6, 255))
icons['crimson_heart'] = (heart, 10, 2)

previews = []
for name, (fn, n, ft) in icons.items():
    frames = [fn(i, n) for i in range(n)]
    save(frames, f'{OUT}/{name}.png', frametime=ft)
    previews.append(frames[n // 3])
preview(previews, '/tmp/claude-0/materials_prev.png', scale=4)
