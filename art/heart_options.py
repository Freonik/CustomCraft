import sys, math
sys.path.insert(0, '/home/claude/art')
from px import ramp
from PIL import Image

V = '/home/claude/ref/vanilla'
base = Image.open(f'{V}/eq_netherite.png').convert('RGBA')
def lum(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255
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
def paint(style):
    out = Image.new('RGBA', base.size, (0, 0, 0, 0))
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            if base.getpixel((x, y))[3] and face_of(x, y):
                c = style(*face_of(x, y), lum(base.getpixel((x, y))))
                out.putpixel((x, y), c if len(c) == 4 else tuple(c) + (255,))
    return out
def edge(lx, ly, w, h):
    return lx == 0 or ly == 0 or lx == w - 1 or ly == h - 1

# ---------------- A: Crimson Knight - deep red lacquered plate, gold trim, small gold heart crest
RED = [(52, 6, 12), (86, 12, 20), (124, 20, 30), (164, 34, 40), (206, 66, 66)]
GOLD = [(120, 80, 20), (190, 140, 50), (240, 200, 100)]
def crimson(part, face, lx, ly, w, h, l):
    col = ramp(RED, 0.35 + 0.4 * l)
    if edge(lx, ly, w, h):
        return ramp(GOLD, 0.6 if (lx + ly) % 2 else 0.35)
    if ly % 5 == 4:
        col = ramp(RED, 0.08)
    if part == 'body' and face == 'front':
        crest = [".G.G.", "GGGGG", ".GGG.", "..G.."]
        hx, hy = lx - 2, ly - 3
        if 0 <= hy < 4 and 0 <= hx < 5 and crest[hy][hx] == 'G':
            return ramp(GOLD, 0.95 if hy == 0 else 0.7)
        if lx in (3, 4) and ly >= 8:
            return ramp(GOLD, 0.45)
    if part == 'arm' and face == 'top':
        return ramp(GOLD, 0.7)
    return col

# ---------------- B: Molten Core - black obsidian plate, glowing core gem, lava cracks spreading out
OBS = [(10, 8, 14), (20, 16, 26), (32, 26, 40), (48, 40, 58)]
LAVA = [(110, 10, 10), (190, 40, 20), (250, 110, 40), (255, 210, 120)]
CRACKS = {(1, 2), (2, 3), (2, 4), (3, 5), (6, 2), (5, 3), (5, 4), (4, 5), (3, 9), (2, 10), (1, 11), (4, 9), (5, 10), (6, 11), (0, 6), (7, 6)}
def molten(part, face, lx, ly, w, h, l):
    col = ramp(OBS, 0.25 + 0.5 * l)
    if edge(lx, ly, w, h):
        col = ramp(OBS, 0.0)
    if part == 'body' and face == 'front':
        d = math.hypot(lx - 3.5, ly - 6.5)
        if d <= 1.0:
            return ramp(LAVA, 0.99)
        if d <= 1.8:
            return ramp(LAVA, 0.7)
        if (lx, ly) in CRACKS:
            return ramp(LAVA, 0.45)
    if part == 'body' and face == 'back' and (lx, ly) in {(3, 3), (4, 4), (3, 5), (4, 6), (3, 7), (4, 8)}:
        return ramp(LAVA, 0.35)
    if part == 'body' and face in ('left', 'right') and (lx + ly) % 7 == 0:
        return ramp(LAVA, 0.3)
    if part == 'arm' and face in ('front', 'right', 'back', 'left') and ly in (2, 3) and (lx + ly) % 3 == 0:
        return ramp(LAVA, 0.4)
    return col

# ---------------- C: Iron Ribcage - steel plate shaped like armored ribs with a red heart glowing between them
STEEL = [(30, 30, 36), (58, 58, 66), (96, 96, 106), (150, 150, 160), (205, 205, 214)]
FLESH = [(60, 4, 10), (120, 10, 22), (190, 26, 36), (240, 80, 70)]
def ribcage(part, face, lx, ly, w, h, l):
    col = ramp(STEEL, 0.25 + 0.4 * l)
    if edge(lx, ly, w, h):
        col = ramp(STEEL, 0.05)
    if part == 'body' and face in ('front', 'back'):
        rib = ly in (3, 5, 7, 9) and lx not in (3, 4)
        sternum = lx in (3, 4)
        if face == 'front' and 4 <= ly <= 8 and lx in (3, 4) and ly in (5, 6):
            return ramp(FLESH, 0.95 if ly == 5 else 0.7)     # heart glowing behind the sternum
        if rib:
            return ramp(STEEL, 0.85)
        if sternum:
            return ramp(STEEL, 0.7)
        if 2 <= ly <= 10:
            return ramp(FLESH, 0.25 + 0.15 * ((lx + ly) % 2))  # dark red between the ribs
    if part == 'arm' and face == 'top':
        return ramp(STEEL, 0.75)
    return col

sys.path.insert(0, '/home/claude/art')
import importlib.util
spec = importlib.util.spec_from_file_location('ra', '/home/claude/art/render_armor.py')
# reuse the renderer without running its sheet() at import: exec only the definitions
src = open('/home/claude/art/render_armor.py').read().split("sheet([outfit(")[0]
ns = {}
exec(src, ns)
EQ = ns['EQ']
angel = Image.open(EQ + 'angels_wings.png').convert('RGBA')
sets = []
for name, style in [('A', crimson), ('B', molten), ('C', ribcage)]:
    layer = paint(style)
    layer.save(f'/tmp/claude-0/heart_{name}.png')
    armor = {}
    for p in ('body', 'rarm', 'larm'):
        armor[p] = (layer, 1.0)
    for p in ('rleg', 'lleg'):
        armor[p] = (angel, 1.0)
    sets.append(armor)
S = ns['S']
views = ['front', 'side', 'back']
cell_w, cell_h = 22 * S, 36 * S
from PIL import ImageDraw
out = Image.new('RGBA', (len(sets) * (2 * cell_w) + 60, cell_h + 70), (44, 42, 52, 255))
dr = ImageDraw.Draw(out)
for i, armor in enumerate(sets):
    for j, v in enumerate(['front', 'back']):
        out.alpha_composite(ns['render'](v, armor), (20 + i * (2 * cell_w + 20) + j * cell_w, 50))
    dr.text((20 + i * (2 * cell_w + 20) + cell_w - 60, 15), ["A  Crimson Knight", "B  Molten Core", "C  Iron Ribcage"][i], fill=(240, 230, 210))
out = out.resize((out.size[0] * 3 // 4, out.size[1] * 3 // 4), Image.NEAREST)
out.save('/tmp/claude-0/heartsteel_options.png')
