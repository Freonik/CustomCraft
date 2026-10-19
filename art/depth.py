"""Painted-depth armor: raised plates with bevels, trims, insets and glowing gems, painted per box face."""
import sys, math
sys.path.insert(0, '/home/claude/art')
from px import ramp
from PIL import Image

V = '/home/claude/ref/vanilla'
base = Image.open(f'{V}/eq_netherite.png').convert('RGBA')
FACES = []
def box(part, u, v, w, h, d):
    FACES.extend([(part, 'top', u + d, v, w, d), (part, 'bottom', u + d + w, v, w, d),
                  (part, 'right', u, v + d, d, h), (part, 'front', u + d, v + d, w, h),
                  (part, 'left', u + d + w, v + d, d, h), (part, 'back', u + 2 * d + w, v + d, w, h)])
box('head', 0, 0, 8, 8, 8); box('body', 16, 16, 8, 12, 4); box('arm', 40, 16, 4, 12, 4); box('leg', 0, 16, 4, 12, 4)
def face_of(x, y):
    for part, face, fx, fy, fw, fh in FACES:
        if fx <= x < fx + fw and fy <= y < fy + fh:
            return part, face, x - fx, y - fy, fw, fh

def shade(c, k):
    """k > 0 lighten, k < 0 darken."""
    if k >= 0:
        return tuple(int(c[i] + (255 - c[i]) * k) for i in range(3)) + (255,)
    return tuple(int(c[i] * (1 + k)) for i in range(3)) + (255,)

def paint(style):
    out = Image.new('RGBA', base.size, (0, 0, 0, 0))
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            if base.getpixel((x, y))[3] and face_of(x, y):
                out.putpixel((x, y), style(*face_of(x, y)))
    return out

def plate(lx, ly, x0, y0, x1, y1):
    """Position inside a raised plate [x0..x1]x[y0..y1]: 'hi' (top/left bevel), 'lo' (bottom/right bevel), 'in', or None."""
    if not (x0 <= lx <= x1 and y0 <= ly <= y1):
        return None
    if ly == y0 or lx == x0:
        return 'hi'
    if ly == y1 or lx == x1:
        return 'lo'
    return 'in'

# ---------------- Heartsteel B with depth: obsidian plates, gold-bronze trim, molten core gem
OBS = (34, 26, 44)
OBS_D = (18, 13, 24)
TRIM = (176, 120, 52)
LAVA = [(120, 14, 10), (200, 50, 20), (255, 120, 40), (255, 220, 140)]

def molten_depth(part, face, lx, ly, w, h):
    # ambient occlusion toward the bottom of each face, light from the top
    ao = -0.25 * (ly / max(1, h - 1))
    if part == 'body' and face == 'front':
        # chest core gem (diamond shape) set into a gold socket
        dx, dy = abs(lx - 3.5), abs(ly - 5.5)
        if dx + dy <= 1.5:
            return ramp(LAVA, 0.99 if (lx, ly) in ((3, 5), (3, 4)) else 0.8)
        if dx + dy <= 2.5:
            return shade(TRIM, 0.35 if ly < 5.5 else -0.35)
        # two raised pectoral plates
        for (x0, x1) in ((0, 3), (4, 7)):
            p = plate(lx, ly, x0, 1, x1, 4)
            if p:
                return shade(OBS, {'hi': 0.35, 'lo': -0.45, 'in': 0.05 + ao}[p])
        # abdomen bands
        if ly in (7, 9):
            return shade(OBS, 0.3)
        if ly in (8, 10):
            return shade(OBS_D, ao)
        # lava cracks running down from the core
        if (lx, ly) in {(2, 8), (1, 9), (5, 8), (6, 9), (3, 10), (4, 11)}:
            return ramp(LAVA, 0.45)
        if ly == 0 or ly == h - 1:
            return shade(TRIM, 0.25 if ly == 0 else -0.3)
        return shade(OBS_D, ao)
    if part == 'body' and face == 'back':
        p = plate(lx, ly, 1, 1, 6, 9)
        if p:
            if lx in (3, 4) and 3 <= ly <= 7:
                return ramp(LAVA, 0.5 if ly % 2 else 0.3)       # glowing spine seam
            return shade(OBS, {'hi': 0.3, 'lo': -0.45, 'in': ao}[p])
        if ly == h - 1:
            return shade(TRIM, -0.3)
        return shade(OBS_D, ao)
    if part == 'body' and face in ('left', 'right'):
        if ly in (0, h - 1):
            return shade(TRIM, 0.1)
        return shade(OBS_D if lx % 3 else OBS, ao)
    if part == 'body' and face == 'top':
        return shade(TRIM, 0.3)
    if part == 'arm':
        # big pauldron: gold rimmed shoulder plate over the top rows, darker sleeve below
        if face == 'top':
            return shade(OBS, 0.25) if (lx in (1, 2) and ly in (1, 2)) else shade(TRIM, 0.2)
        if ly == 0:
            return shade(TRIM, 0.45)
        if ly in (1, 2, 3):
            if ly == 3:
                return shade(TRIM, -0.35)
            if face == 'front' and lx in (1, 2) and ly == 2:
                return ramp(LAVA, 0.6)                          # small ember stud
            return shade(OBS, 0.25 if ly == 1 else 0.0)
        return shade(OBS_D, ao + (0.15 if lx == 0 else -0.1 if lx == w - 1 else 0))
    return shade(OBS_D, ao)

if __name__ == '__main__':
    src = open('/home/claude/art/render_armor.py').read().split("sheet([outfit(")[0]
    ns = {}
    exec(src, ns)
    angel = Image.open(ns['EQ'] + 'angels_wings.png').convert('RGBA')
    old = Image.open('/tmp/claude-0/heart_B.png').convert('RGBA')
    new = paint(molten_depth)
    new.save('/tmp/claude-0/heart_B_depth.png')
    S = ns['S']
    cell_w, cell_h = 22 * S, 36 * S
    out = Image.new('RGBA', (4 * cell_w + 80, cell_h + 40), (44, 42, 52, 255))
    for i, layer in enumerate([old, new]):
        armor = {p: (layer, 1.0) for p in ('body', 'rarm', 'larm')}
        armor.update({p: (angel, 1.0) for p in ('rleg', 'lleg')})
        for j, v in enumerate(['front', 'back']):
            out.alpha_composite(ns['render'](v, armor), (20 + i * (2 * cell_w + 40) + j * cell_w, 20))
    out.resize((out.size[0] * 3 // 4, out.size[1] * 3 // 4), Image.NEAREST).save('/tmp/claude-0/heartsteel_depth.png')
