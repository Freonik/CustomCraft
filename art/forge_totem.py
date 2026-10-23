"""Totem of the Forge: an iron-and-gold totem with a molten heart and ember eyes (32x32, animated)."""
import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *

OUT = sys.argv[1]

IRON = [(18, 16, 20), (32, 30, 36), (50, 48, 56), (74, 72, 82), (104, 102, 114), (140, 138, 150)]
GOLD = [(84, 48, 10), (138, 88, 22), (192, 136, 40), (236, 186, 72), (255, 228, 140), (255, 250, 214)]
MAGMA = [(120, 20, 0), (200, 60, 0), (255, 130, 20), (255, 200, 80), (255, 248, 210)]


def totem(f, n):
    c = Canvas()
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    cx = 15.5

    def body(x, y):
        dx = x + 0.5 - 16.0
        ax = abs(dx)
        # head: a rounded block with a gold crown band
        if 3 <= y <= 12 and ax <= 6.5 - (1.5 if y in (3, 12) else 0):
            if y == 4 or y == 5:
                return ramp(GOLD, 0.45 + 0.4 * (1 - (dx + 6) / 13))             # crown band
            if y in (8, 9) and 1.5 <= ax <= 4.0:
                return ramp(MAGMA, (0.75 if y == 8 else 0.45) + 0.25 * pulse)  # ember eyes
            if y == 11 and ax <= 2.5:
                return ramp(IRON, 0.1)                                          # mouth
            if dx < -4.5:
                return ramp(IRON, 0.85)                                         # lit edge
            if y in (6, 7) and ax in (5.5, 6.0):
                return ramp(GOLD, 0.7)
            return ramp(IRON, 0.2 + 0.55 * (1 - (dx + 6.5) / 13))
        if y == 2 and ax <= 4 and int(ax) % 2 == 0:
            return ramp(GOLD, 0.7)                                              # crown spikes
        # arms / wings out to the sides, raised
        top = 14 - (ax - 6.5) * 0.7                                             # wings sweep upwards
        if 6.5 < ax <= 13.5 and top <= y + 0.5 <= top + 5.2 - (ax - 6.5) * 0.35:
            if y + 0.5 < top + 1:
                return ramp(GOLD, 0.75 - (ax - 6.5) * 0.04)
            if int(ax) % 3 == 0 and y + 0.5 > top + 2.2:
                return ramp(IRON, 0.18)                                         # feather gaps
            return ramp(IRON, 0.55 - (ax - 6.5) * 0.04)
        # torso with the molten heart
        if 13 <= y <= 25 and ax <= 6.5 - max(0, y - 22) * 0.9:
            hd = math.hypot(dx, (y + 0.5 - 18.5) * 1.1)
            r = 2.3 + 0.5 * pulse
            if hd <= r:
                return ramp(MAGMA, 0.99 - 0.85 * (hd / r) ** 1.5)             # molten heart, bright centre
            if hd <= 3.6:
                return ramp(GOLD, 0.35 if dx > 0 else 0.6)
            if dx < -5.0:
                return ramp(IRON, 0.8)
            if y in (13, 25):
                return ramp(GOLD, 0.5)
            return ramp(IRON, 0.2 + 0.5 * (1 - (dx + 6.5) / 13))
        # base: an anvil-like foot
        if 26 <= y <= 28 and ax <= 4.5 + (y - 26) * 1.2:
            return ramp(GOLD if y == 26 else IRON, 0.5 if y == 26 else 0.35)
        return None
    c.fill(body, 'body')
    c.outline((10, 6, 4, 255))
    # sparks rising from the heart
    for i in range(3):
        a = (f / n + i / 3) % 1.0
        x = int(cx + math.sin(a * 6 + i) * 3)
        y = int(16 - a * 14)
        if (x, y) not in c.px and a < 0.9:
            c.set(x, y, ramp(MAGMA, 0.95 - a * 0.6), 'spark')
    return c.image()


if __name__ == '__main__':
    frames = [totem(i, 12) for i in range(12)]
    save(frames, f'{OUT}/forge_totem.png', frametime=3)
    if len(sys.argv) > 2:
        preview([frames[0], frames[6]], sys.argv[2])
