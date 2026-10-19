import sys, math, random
sys.path.insert(0, '/home/claude/art')
from px import *

BLADE = [(10,26,36),(18,56,72),(32,100,120),(70,170,190),(150,230,240),(232,255,255)]
FLAME = [(20,90,110),(40,170,190),(110,235,245),(220,255,255)]
HANDLE = [(24,18,22),(40,30,36),(58,46,52)]
SILVER = [(80,86,96),(140,148,160),(205,212,222)]
OUT = (4,10,14,255)
OX, OY = 3.0, 29.0
FRAMES = 8
C1, R1 = (14.0, 19.5), 15.6     # outer edge of the blade
C2, R2 = (10.5, 24.5), 16.4     # inner (cutting) edge

def frame(f):
    c = Canvas()
    rnd = random.Random(f * 7 + 3)
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / FRAMES)

    def blade(x, y):
        px, py = x + 0.5, y + 0.5
        d1 = math.hypot(px - C1[0], py - C1[1])
        d2 = math.hypot(px - C2[0], py - C2[1])
        if d1 > R1 or d2 < R2 or py > 16.5 or px > 25.2 or px < 1 or px + py > 33.5:
            return None
        if d2 - R2 < 1.0:
            return ramp(BLADE, 0.97)                 # glowing cutting edge
        if R1 - d1 < 0.9:
            return ramp(BLADE, 0.2)                  # dark back
        t = (d2 - R2) / max(0.1, (R1 - d1) + (d2 - R2))
        return ramp(BLADE, 0.75 - 0.4 * t)
    c.fill(blade, 'blade')

    def handle(x, y):
        u, v = diag(x, y, OX, OY)
        if 1.2 <= u <= 29.0 and abs(v) <= 1.25:
            if 5.5 <= u <= 7.6 or 15.5 <= u <= 17.6 or u >= 26.5:
                return ramp(SILVER, 0.9 if v < 0 else 0.4)
            return ramp(HANDLE, 0.85 if v < -0.4 else 0.5 if v < 0.4 else 0.15)
        # spiked pommel
        if 0 <= u < 1.2 and abs(v) <= 0.6:
            return ramp(SILVER, 0.7)
        return None
    c.fill(handle, 'handle')

    # soul gem where the blade meets the handle
    gx, gy = 23.8, 9.2
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - gx, y + 0.5 - gy)
            if d <= 2.1:
                c.set(x, y, ramp(FLAME, 0.99 if d < 0.9 else 0.4 + 0.5 * pulse), 'gem')
    c.outline(OUT)

    # flickering soul flames rising from the back of the blade
    for y in range(32):
        for x in range(32):
            if (x, y) in c.px:
                continue
            px, py = x + 0.5, y + 0.5
            d1 = math.hypot(px - C1[0], py - C1[1])
            if R1 + 1.0 < d1 < R1 + 2.8 and py < 11 and 2 < px < 23:
                h = (x * 13 + y * 7 + f * 5) % 11
                if h < 3 and rnd.random() < 0.8:
                    c.set(x, y, ramp(FLAME, 0.3 + 0.2 * h + 0.3 * pulse), 'flame')
    return c.image()

frames = [frame(i) for i in range(FRAMES)]
save(frames, sys.argv[1], frametime=3)
preview([frames[0], frames[3], frames[6]], '/tmp/claude-0/scythe_prev.png')
gif(frames, '/tmp/claude-0/scythe.gif')
