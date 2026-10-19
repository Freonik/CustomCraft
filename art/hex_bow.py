import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *

WOOD = [(24,14,22),(40,24,34),(60,38,48),(86,58,64),(116,84,84)]
RUNE = [(16,70,40),(30,140,76),(70,230,130),(190,255,210)]
BONE = [(120,118,104),(180,176,156),(226,222,204)]
STRING = (196,232,212,255)
STRING_DARK = (120,160,140,255)
SHAFT = [(150,140,130),(200,192,176)]
OUT = (8,4,10,255)
CX = CY = 29.5
R = 26.0
P = 4.0
FRAMES = 6
TIP_A = (27, 4)     # top-right limb end (string anchor)
TIP_B = (4, 27)     # bottom-left limb end

def sr(x, y):
    dx, dy = CX - (x + 0.5), CY - (y + 0.5)
    if dx < 0 or dy < 0:
        return None, None
    r = (dx ** P + dy ** P) ** (1 / P)
    s = math.atan2(dx, dy) / (math.pi / 2)   # 0 at bottom-left end .. 1 at top-right end
    return r, s

def line(c, a, b, color, kind):
    n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1])) * 2) + 1
    for i in range(n + 1):
        t = i / n
        x = round(a[0] + (b[0] - a[0]) * t)
        y = round(a[1] + (b[1] - a[1]) * t)
        c.set(x, y, color, kind)

def frame(f, pull):
    c = Canvas()
    glow = 0.5 + 0.5 * math.sin(2 * math.pi * f / FRAMES)

    def wood(x, y):
        r, s = sr(x, y)
        if r is None:
            return None
        if not (0.03 <= s <= 0.97):
            return None
        thick = 1.9 if 0.38 < s < 0.62 else 1.45
        if abs(r - R) > thick:
            return None
        # grip in the middle of the arc
        if 0.42 < s < 0.58:
            band = int(s * 46) % 3
            return ramp(BONE, 0.95 if band == 0 else 0.5 if r < R else 0.1)
        # runes along the limbs
        k = (s * 9) % 1.0
        if abs(r - R) < 0.8 and 0.35 < k < 0.6:
            return ramp(RUNE, 0.35 + 0.6 * glow * (0.6 + 0.4 * math.sin(s * 20 + f)))
        t = 0.75 if r > R else 0.35   # outer edge lit
        return ramp(WOOD, t)
    c.fill(wood, 'wood')

    # bone hooks at both limb ends
    for (x, y) in [(28, 3), (29, 3), (28, 2), (3, 28), (3, 29), (2, 28)]:
        c.set(x, y, ramp(BONE, 0.8), 'bone')

    nock = (16 + pull, 16 + pull)
    if pull == 0:
        line(c, TIP_A, TIP_B, STRING, 'string')
    else:
        line(c, TIP_A, nock, STRING, 'string')
        line(c, nock, TIP_B, STRING, 'string')
        # arrow: hexed head at the top-left corner, fletching at the nock
        tip = 1
        n = nock[0] - tip
        for i in range(n + 1):
            x = y = tip + i
            if i <= 2:
                col = ramp(RUNE, 0.99 if i == 0 else 0.75 + 0.25 * glow)
            else:
                col = ramp(SHAFT, 1 if i % 2 else 0)
            c.set(x, y, col, 'arrow')
        # arrowhead barbs
        for p in [(3, 1), (1, 3), (4, 2), (2, 4)]:
            c.set(p[0], p[1], ramp(RUNE, 0.6), 'arrow')
        # fletching: two feather lines beside the nock
        for i in range(n - 4, n):
            c.set(tip + i + 1, tip + i, ramp(RUNE, 0.45), 'arrow')
            c.set(tip + i, tip + i + 1, ramp(RUNE, 0.3), 'arrow')
    c.outline(OUT, skip=('string', 'arrow'))
    return c.image()

out = sys.argv[1]   # directory
names = [('hex_bow', 0), ('hex_bow_pulling_0', 3), ('hex_bow_pulling_1', 5), ('hex_bow_pulling_2', 7)]
first = []
for name, pull in names:
    frames = [frame(i, pull) for i in range(FRAMES)]
    save(frames, f'{out}/{name}.png', frametime=4)
    first.append(frames[0])
preview(first, '/tmp/claude-0/bow_prev.png')
