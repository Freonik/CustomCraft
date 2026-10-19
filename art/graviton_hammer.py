import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *

OX, OY = 3.0, 29.0
HEAD = [(12,9,22),(20,15,36),(30,24,52),(44,36,74),(62,52,100),(92,82,136)]
CAP = [(34,34,46),(62,62,78),(104,104,124),(160,160,180),(215,215,230)]
CORE = [(50,16,96),(92,36,170),(140,76,236),(196,150,255),(245,228,255)]
HANDLE = [(22,18,26),(38,32,44),(56,48,64)]
GOLD = [(110,72,18),(176,128,36),(236,196,86)]
OUT = (6,4,12,255)
FRAMES = 8
HU = 23.2   # head center along the axis

def frame(f):
    c = Canvas()
    ang = 2 * math.pi * f / FRAMES
    pulse = 0.5 + 0.5 * math.sin(ang)

    def head(x, y):
        u, v = diag(x, y, OX, OY)
        du = u - HU
        if abs(du) > 4.3 or abs(v) > 9.2:
            return None
        if abs(v) > 8.0 and abs(du) > 3.1:      # beveled corners
            return None
        # core orb + orbiting sparks
        d = math.hypot(du, v)
        if d <= 2.1:
            if d <= 0.9:
                return ramp(CORE, 0.99)
            return ramp(CORE, 0.45 + 0.4 * pulse)
        # orbit ring with one bright spark circling the core
        if 2.6 <= d <= 3.3:
            a = math.atan2(v, du)
            diff = (a - ang + math.pi) % (2 * math.pi) - math.pi
            if abs(diff) < 0.6:
                return ramp(CORE, 0.8)
            return ramp(CORE, 0.12)
        # steel end caps
        if abs(v) > 6.6:
            if abs(v) > 8.6 or du < -3.4:
                return ramp(CAP, 0.2)
            return ramp(CAP, 0.8 if du > 0 else 0.45)
        # glowing seams toward the caps
        if abs(du) <= 0.45 and 3.3 < abs(v) <= 6.6:
            return ramp(CORE, 0.1 + 0.35 * pulse)
        # obsidian body: lit on the top face
        t = 0.38 + 0.07 * du
        if du > 3.4:
            t = 0.95
        if du < -3.4:
            t = 0.05
        return ramp(HEAD, t)
    c.fill(head, 'head')

    def handle(x, y):
        u, v = diag(x, y, OX, OY)
        if 1.0 <= u <= HU - 3.3 and abs(v) <= 0.95:
            if 3.0 <= u <= 4.4 or 13.5 <= u <= 14.9 or u >= HU - 4.6:
                return ramp(GOLD, 0.9 if v < 0 else 0.4)
            return ramp(HANDLE, 0.8 if v < -0.2 else 0.35)
        d = math.hypot(u - 0.6, v)
        if d <= 1.5:
            return ramp(GOLD, 0.9 if u + v < 0.5 else 0.35)
        return None
    c.fill(handle, 'handle')
    c.outline(OUT)
    return c.image()

frames = [frame(i) for i in range(FRAMES)]
save(frames, sys.argv[1], frametime=3)
preview([frames[0], frames[2], frames[5]], '/tmp/claude-0/hammer_prev.png')
gif(frames, '/tmp/claude-0/hammer.gif')
