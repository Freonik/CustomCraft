"""Legend Tracker: a dark steel compass with a glowing red rim and a red needle - 32 needle angles (like vanilla)."""
import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *

OUT = sys.argv[1]
STEEL = [(16, 16, 20), (30, 30, 36), (48, 48, 56), (72, 72, 82), (104, 104, 116)]
RIM = [(70, 8, 12), (140, 20, 24), (210, 44, 40), (255, 110, 90)]
GOLD = [(120, 80, 20), (200, 150, 50), (250, 220, 120)]
FACE = [(26, 22, 24), (38, 32, 34)]


def frame(i):
    c = Canvas()
    cx, cy = 15.5, 16.5
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - cx, y - cy)
            light = 0.5 - ((x - cx) + (y - cy)) * 0.025
            if d <= 11.5:
                if d > 10.2:
                    c.set(x, y, ramp(STEEL, light + 0.25))            # outer steel ring
                elif d > 8.9:
                    c.set(x, y, ramp(RIM, 0.45 + light * 0.6), 'rim')  # glowing red rim
                else:
                    c.set(x, y, FACE[(x + y) % 2] + (255,))           # dark face
    # hanging loop at the top
    for (x, y) in [(14, 3), (15, 3), (16, 3), (17, 3), (13, 4), (18, 4), (14, 5), (17, 5)]:
        c.set(x, y, ramp(STEEL, 0.75))
    # ticks: gold at the cardinal points, dim at the others
    for k in range(8):
        a = k * math.pi / 4
        x, y = cx + math.sin(a) * 7.6, cy - math.cos(a) * 7.6
        c.set(int(round(x)), int(round(y)), ramp(GOLD, 0.9 if k % 2 == 0 else 0.3), 'tick')
    # needle: i / 32 of a turn clockwise from straight up
    a = i * 2 * math.pi / 32
    ux, uy = math.sin(a), -math.cos(a)
    px_, py_ = -uy, ux
    for step in range(0, 70):
        t = step / 10 - 1.5                                          # -1.5 .. 5.5
        w = 0.55 * (1 - max(0, t) / 7)                               # tapers to the tip
        for o in (-w, 0, w):
            x, y = cx + ux * t + px_ * o, cy + uy * t + py_ * o
            col = (230, 36, 36, 255) if t > 0.6 else (190, 190, 200, 255)
            if t > 4.6:
                col = (255, 150, 130, 255)                           # bright tip
            c.set(int(round(x)), int(round(y)), col, 'needle')
    c.set(int(round(cx)), int(round(cy)), ramp(GOLD, 0.9), 'pin')
    c.outline((8, 6, 8, 255))
    return c.image()


for i in range(32):
    frame(i).save(f'{OUT}/legend_tracker_{i:02d}.png')
preview([frame(0), frame(4), frame(12), frame(20)], '/tmp/claude-0/tracker_prev.png', scale=8)
print('tracker done')
