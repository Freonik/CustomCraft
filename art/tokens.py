"""Bounty Token (gold coin with a crosshair skull mark) and Warden's Seal (iron seal with a red wax hammer stamp)."""
import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *

OUT = sys.argv[1]
GOLD = [(70, 38, 8), (128, 80, 20), (184, 128, 40), (226, 180, 76), (250, 222, 140), (255, 248, 220)]
DARK = [(30, 16, 6), (60, 32, 10)]
IRON = [(18, 18, 24), (34, 34, 42), (56, 56, 66), (84, 84, 96), (120, 120, 134), (170, 170, 184)]
WAX = [(54, 6, 10), (98, 14, 18), (150, 26, 28), (200, 50, 42), (240, 100, 70)]
EMBER = [(255, 120, 30), (255, 190, 70), (255, 240, 170)]


def coin(f, n):
    c = Canvas()
    shine = (f / n) * 60 - 14
    cx, cy, r = 15.5, 15.5, 12.5
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - cx, y - cy)
            if d > r:
                continue
            light = 0.55 - (x + y - 31) * 0.012
            if d > r - 2:                                   # raised rim
                t = light + 0.15 + (0.12 if x + y < 30 else -0.18)
            elif d > r - 3:                                 # groove
                t = light - 0.25
            else:
                t = light - 0.05
            g = max(0, 1 - abs((x - y) - shine) / 3)
            c.set(x, y, ramp(GOLD, max(0, min(0.99, t + 0.35 * g))))
    # crosshair ring
    for a in range(48):
        ang = a * math.pi / 24
        x, y = cx + math.cos(ang) * 8.2, cy + math.sin(ang) * 8.2
        if abs(math.cos(ang)) > 0.93 or abs(math.sin(ang)) > 0.93:
            continue                                        # gaps where the ticks are
        c.set(int(round(x)), int(round(y)), DARK[1] + (255,), 'mark')
    for i in range(2):                                      # ticks
        for (x, y) in [(15, 5 + i), (16, 5 + i), (15, 25 + i), (16, 25 + i),
                       (5 + i, 15), (5 + i, 16), (25 + i, 15), (25 + i, 16)]:
            c.set(x, y, DARK[1] + (255,), 'mark')
    # small skull in the middle
    skull = ["..######..",
             ".########.",
             "##########",
             "##..##..##",
             "##..##..##",
             "####..####",
             ".########.",
             "..#.##.#..",
             "..#.##.#.."]
    for j, row in enumerate(skull):
        for i, ch in enumerate(row):
            if ch == '#':
                c.set(11 + i, 11 + j, DARK[0] + (255,), 'mark')
            elif ch == '.' and 0 < i < 9 and 0 < j < 8 and row[i-1] == '#' :
                pass
    c.outline((40, 20, 4, 255), skip=('mark',))
    return c.image()


def seal(f, n):
    c = Canvas()
    cx, cy = 15.5, 15.5
    # iron plate, octagonal
    for y in range(32):
        for x in range(32):
            dx, dy = abs(x - cx), abs(y - cy)
            if max(dx, dy) > 13 or dx + dy > 18.5:
                continue
            edge = max(dx, dy) > 11.5 or dx + dy > 17
            light = 0.5 - (x + y - 31) * 0.014
            t = light + (0.2 if edge and x + y < 31 else -0.15 if edge else -0.05)
            c.set(x, y, ramp(IRON, max(0, min(0.99, t))))
    for (x, y) in [(5, 9), (26, 9), (5, 22), (26, 22), (9, 4), (22, 4), (9, 27), (22, 27)]:   # rivets
        c.set(x, y, ramp(IRON, 0.95), 'rivet')
    # red wax blob with irregular edge
    for y in range(32):
        for x in range(32):
            a = math.atan2(y - cy, x - cx)
            rr = 9.2 + 0.6 * math.sin(a * 7 + 1.3)
            d = math.hypot(x - cx, y - cy)
            if d <= rr:
                t = 0.62 - (x + y - 31) * 0.02 - (0.2 if d > rr - 1 else 0)
                c.set(x, y, ramp(WAX, max(0, min(0.99, t))), 'wax')
    # stamped hammer (pressed in = darker)
    pattern = ["#########",
               "#########",
               "#########",
               "...###...",
               "....#....",
               "....#....",
               "....#....",
               "....#....",
               "...###..."]
    hammer = [(11 + i, 11 + j) for j, row in enumerate(pattern) for i, ch in enumerate(row) if ch == '#']
    pts = {(x, y) for (x, y) in hammer}
    for (x, y) in pts:                                      # light lip below-right of the pressed shape
        if (x + 1, y + 1) not in pts and c.kind.get((x + 1, y + 1)) == 'wax':
            c.set(x + 1, y + 1, WAX[4] + (255,), 'stamp')
    for (x, y) in pts:
        c.set(x, y, WAX[0] + (255,), 'stamp')
    # pulsing embers around the edge
    for k in range(5):
        a = 2 * math.pi * (k / 5 + f / n)
        x, y = cx + math.cos(a) * 14.5, cy + math.sin(a) * 14.5
        glow = (math.sin(f / n * 2 * math.pi * 2 + k) + 1) / 2
        col = EMBER[min(2, int(glow * 3))]
        c.set(int(x), int(y), col + (255,), 'ember')
    c.outline((10, 8, 12, 255), skip=('ember',))
    return c.image()


coins = [coin(i, 16) for i in range(16)]
save(coins, f'{OUT}/bounty_token.png', frametime=2)
seals = [seal(i, 16) for i in range(16)]
save(seals, f'{OUT}/wardens_seal.png', frametime=3)
preview([coins[0], coins[7], seals[0], seals[8]], '/tmp/claude-0/tokens_prev.png', scale=8)
print('tokens done')
