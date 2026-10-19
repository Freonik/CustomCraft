import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *

OX, OY = 3.0, 29.0          # pommel position
BLADE = [(8,14,20),(14,26,34),(22,38,46),(32,52,60),(46,70,78),(90,124,126),(176,210,204)]
CORE = [(14,70,82),(18,120,132),(30,185,190),(90,240,235),(210,255,250)]
GUARD = [(14,30,36),(24,52,60),(38,80,88),(60,120,124)]
GRIP = [(30,22,28),(48,36,44),(70,54,62)]
OUT = (6,10,14,255)
FRAMES = 12

def frame(f):
    c = Canvas()
    phase = f / FRAMES
    def blade(x, y):
        u, v = diag(x, y, OX, OY)
        if u < 11.2 or u > 37.5:
            return None
        w = 2.3 if u < 33.5 else 2.3 * ((37.5 - u) / 4.0) ** 0.6   # full width almost to the end, short strong tip
        if abs(v) > w:
            return None
        # fuller (glowing core)
        if 12.5 < u < 34 and abs(v) <= 0.45:
            wave = (u / 20.0 - phase) % 1.0
            glow = 0.45 + 0.55 * max(0, 1 - abs(wave - 0.5) * 5)
            return ramp(CORE, glow)
        t = (w - v) / (2 * w)          # 1 = lit upper-left edge
        if v < -w + 0.85:
            # bright edge with echo notches
            return ramp(BLADE, 0.97) if int(u) % 5 else ramp(CORE, 0.8)
        if v > w - 0.8:
            return ramp(BLADE, 0.12)   # dark edge
        return ramp(BLADE, 0.3 + 0.4 * t)
    c.fill(blade, 'blade')

    def guard(x, y):
        u, v = diag(x, y, OX, OY)
        # crescent guard curving toward the blade, like sculk tendrils
        center = 10.0 + 0.14 * v * v
        if abs(v) <= 6.2 and abs(u - center) <= 1.05 + (0.4 if abs(v) < 1.5 else 0):
            if abs(v) > 5.0:
                return ramp(CORE, 0.75)          # glowing tips
            return ramp(GUARD, 0.75 - abs(v) / 10)
        return None
    c.fill(guard, 'guard')

    def grip(x, y):
        u, v = diag(x, y, OX, OY)
        if 2.4 <= u <= 9.4 and abs(v) <= 0.95:
            band = int(u * 1.6) % 2
            return ramp(GRIP, 0.8 if (band and v < 0) else 0.45 if band else 0.15)
        return None
    c.fill(grip, 'grip')

    def pommel(x, y):
        u, v = diag(x, y, OX, OY)
        d = math.hypot(u - 1.0, v)
        if d <= 1.75:
            pulse = 0.5 + 0.5 * math.sin(2 * math.pi * phase)
            return ramp(CORE, 0.95 if (u + v < 0 and d < 1.1) else 0.35 + 0.4 * pulse)
        return None
    c.fill(pommel, 'pommel')
    c.outline(OUT)
    return c.image()

frames = [frame(i) for i in range(FRAMES)]
save(frames, sys.argv[1], frametime=2)
preview([frames[0], frames[4], frames[8]], '/tmp/claude-0/echo_prev.png')
gif(frames, '/tmp/claude-0/echo.gif')
