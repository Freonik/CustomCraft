"""Recon Spyglass: a brass spyglass with a leather grip, a cyan lens with a moving glint and a red reticle mark."""
import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *

OUT = sys.argv[1]
BRASS = [(58, 32, 10), (104, 62, 20), (152, 98, 34), (198, 142, 58), (236, 194, 104), (255, 236, 170)]
LEATHER = [(40, 24, 16), (66, 40, 26), (96, 60, 38)]
LENS = [(10, 40, 50), (30, 110, 130), (90, 200, 210), (200, 250, 250)]
RED = (230, 50, 50)


def frame(f, n):
    c = Canvas()
    # the tube runs from bottom-left (eyepiece) to top-right (objective), getting wider
    ax, ay, bx, by = 6.0, 26.0, 24.0, 8.0
    length = math.hypot(bx - ax, by - ay)
    ux, uy = (bx - ax) / length, (by - ay) / length
    px_, py_ = -uy, ux
    shine = (f / n) * 1.6 - 0.3
    for y in range(32):
        for x in range(32):
            rx, ry = x + 0.5 - ax, y + 0.5 - ay
            t = rx * ux + ry * uy                     # along the tube
            s = rx * px_ + ry * py_                   # across
            if t < -0.5 or t > length + 1.5:
                continue
            k = t / length
            half = 1.6 + 1.4 * k                      # widening tube
            if t > length - 1.2:
                half += 0.8                           # objective rim
            if t < 1.6:
                half += 0.5                           # eyepiece rim
            if abs(s) > half:
                continue
            light = 0.5 - s / half * 0.42
            if 0.36 < k < 0.56:
                col = ramp(LEATHER, light * 0.9)       # leather grip
                if int(t) % 2 == 0 and abs(s) < half - 0.6:
                    col = ramp(LEATHER, light * 0.6)
                c.set(x, y, col, 'leather')
                continue
            if 0.33 < k < 0.36 or 0.56 < k < 0.6 or t > length - 1.2 or t < 1.6:
                light -= 0.22                          # darker brass bands
            g = max(0, 1 - abs(k - shine) * 6) * (1 if s < 0 else 0.4)
            c.set(x, y, ramp(BRASS, max(0, min(0.99, light + 0.3 * g))), 'brass')
    # objective lens (an ellipse across the wide end)
    cx, cy = bx + ux * 0.9, by + uy * 0.9
    for y in range(32):
        for x in range(32):
            rx, ry = x + 0.5 - cx, y + 0.5 - cy
            t = rx * ux + ry * uy
            s = rx * px_ + ry * py_
            if (t / 1.2) ** 2 + (s / 3.3) ** 2 <= 1:
                glint = (math.sin(f / n * 2 * math.pi) + 1) / 2
                col = ramp(LENS, 0.35 + 0.4 * (1 - abs(s) / 3.1) + 0.25 * glint * (s < 0))
                c.set(x, y, col, 'lens')
    # red reticle mark on the grip side
    mx, my = 9, 9
    for (dx, dy) in [(0, -2), (0, 2), (-2, 0), (2, 0), (0, -3), (0, 3), (-3, 0), (3, 0)]:
        c.set(mx + dx, my + dy, RED + (255,), 'mark')
    c.set(mx, my, (255, 120, 120, 255), 'mark')
    c.outline((26, 16, 8, 255), skip=('mark',))
    return c.image()


frames = [frame(i, 12) for i in range(12)]
save(frames, f'{OUT}/recon_spyglass.png', frametime=3)
preview([frames[0], frames[4], frames[8]], '/tmp/claude-0/recon_prev.png', scale=8)
print('recon done')
