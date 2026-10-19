"""Collector's Trophy: a golden anvil statuette on a dark pedestal with orbiting gems."""
import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *

OUT = sys.argv[1]
GOLD = [(84, 48, 12), (140, 92, 24), (196, 144, 48), (238, 196, 92), (255, 236, 168), (255, 252, 230)]
STONE = [(20, 18, 28), (34, 30, 46), (52, 46, 68), (74, 66, 94)]
GEMS = [(70, 220, 230), (150, 90, 240), (80, 220, 110), (255, 90, 90), (255, 210, 80), (240, 240, 255), (60, 140, 255)]


def frame(f, n):
    c = Canvas()
    shine = (f / n) * 40 - 8
    # pedestal
    for y in range(22, 29):
        half = 8 if y >= 24 else 7
        for x in range(16 - half, 16 + half):
            t = 0.55 if y == 22 or y == 24 else 0.3 - 0.04 * (y - 24)
            if x == 16 - half:
                t += 0.2
            c.set(x, y, ramp(STONE, max(0, t)))
    for x in range(10, 22):
        c.set(x, 23, ramp(GOLD, 0.55))                       # gold trim on the pedestal
    # anvil statuette
    shape = [
        (9, 23, 9, 12),   # (x0, x1, y0, y1) face/top block
        (13, 19, 13, 16), # waist
        (11, 21, 17, 21), # foot
    ]
    for (x0, x1, y0, y1) in shape:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                t = 0.62 - 0.03 * (y - y0) + (0.18 if x <= x0 + 1 else 0) - (0.2 if x >= x1 - 1 else 0)
                g = max(0, 1 - abs((x + y) - shine) / 2.2)
                c.set(x, y, ramp(GOLD, min(0.99, t + 0.4 * g)))
    for x in range(6, 9):                                     # horn
        c.set(x, 10 + (8 - x) // 2, ramp(GOLD, 0.7))
        c.set(x, 11, ramp(GOLD, 0.45))
    # orbiting gems
    for k in range(7):
        a = 2 * math.pi * (k / 7 + f / n)
        x = 16 + math.cos(a) * 12
        y = 12 + math.sin(a) * 4
        if math.sin(a) < -0.2 and 9 <= x <= 23 and 9 <= y <= 21:
            continue                                          # behind the statuette
        c.set(int(x), int(y), GEMS[k] + (255,), 'gem')
    c.outline((20, 12, 4, 255), skip=('gem',))
    return c.image()


frames = [frame(i, 14) for i in range(14)]
save(frames, f'{OUT}/collectors_trophy.png', frametime=2)
preview([frames[0], frames[5]], '/tmp/claude-0/trophy_prev.png', scale=8)
print('trophy done')
