"""Orthographic front / side / back render of a player wearing armor layers."""
from PIL import Image, ImageDraw

S = 10  # screen pixels per model pixel
SKIN = Image.open('/home/claude/ref/vanilla/steve.png').convert('RGBA')
EQ = '/home/claude/pack/assets/customcraft/textures/entity/equipment/humanoid/'

# part: (name, x0, y0, z0, w, h, d, skin_uv, mirror_for_armor)
PARTS = {
    'head':  (-4, 24, -4, 8, 8, 8, (0, 0)),
    'body':  (-4, 12, -2, 8, 12, 4, (16, 16)),
    'rarm':  (-8, 12, -2, 4, 12, 4, (40, 16)),
    'larm':  (4, 12, -2, 4, 12, 4, (32, 48)),
    'rleg':  (-4, 0, -2, 4, 12, 4, (0, 16)),
    'lleg':  (0, 0, -2, 4, 12, 4, (16, 48)),
}
# armor layer UVs (64x32 humanoid layer); left limbs use the right limb UV mirrored
ARMOR_UV = {'head': ((0, 0), False), 'body': ((16, 16), False), 'rarm': ((40, 16), False),
            'larm': ((40, 16), True), 'rleg': ((0, 16), False), 'lleg': ((0, 16), True)}

def region(tex, uv, w, h, d, face, mirror):
    u, v = uv
    if mirror:
        face = {'left': 'right', 'right': 'left'}.get(face, face)
    box = {
        'front': (u + d, v + d, u + d + w, v + d + h),
        'back': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
        'left': (u + d + w, v + d, u + 2 * d + w, v + d + h),    # entity's left side (+x)
        'right': (u, v + d, u + d, v + d + h),                  # entity's right side (-x)
    }[face]
    k = tex.size[0] // 64
    img = tex.crop(tuple(c * k for c in box))
    return img.transpose(Image.FLIP_LEFT_RIGHT) if mirror else img

def draw_face(canvas, img, x0, y_top, width, height, ox, oy):
    big = img.resize((max(1, int(round(width * S))), max(1, int(round(height * S)))), Image.NEAREST)
    canvas.alpha_composite(big, (int(round(ox + x0 * S)), int(round(oy - y_top * S))))

def render(view, armor):
    """armor: dict part -> (texture, dilation)"""
    W, H = 22 * S, 36 * S
    canvas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ox, oy = W // 2, H - 2 * S
    if view == 'front':
        order = ['rleg', 'lleg', 'body', 'rarm', 'larm', 'head']
    elif view == 'back':
        order = ['rleg', 'lleg', 'body', 'rarm', 'larm', 'head']
    else:  # side, looking at the entity's left
        order = ['rarm', 'rleg', 'body', 'head', 'lleg', 'larm']
    for name in order:
        x0, y0, z0, w, h, d, uv = PARTS[name]
        layers = [(SKIN, uv, False, 0.0)]
        if name in armor:
            tex, dil = armor[name]
            auv, mir = ARMOR_UV[name]
            layers.append((tex, auv, mir, dil))
        for tex, tuv, mir, dil in layers:
            if view == 'front':
                img = region(tex, tuv, w, h, d, 'front', mir)
                draw_face(canvas, img, x0 - dil, y0 + h + dil, w + 2 * dil, h + 2 * dil, ox, oy)
            elif view == 'back':
                img = region(tex, tuv, w, h, d, 'back', mir)
                draw_face(canvas, img, -(x0 + w) - dil, y0 + h + dil, w + 2 * dil, h + 2 * dil, ox, oy)
            else:
                img = region(tex, tuv, w, h, d, 'left', mir)
                # viewer x = -z (front of the entity on the left)
                draw_face(canvas, img, -(z0 + d) - dil, y0 + h + dil, d + 2 * dil, h + 2 * dil, ox, oy)
    return canvas

def outfit(chest=None, boots=None):
    armor = {}
    if chest:
        t = Image.open(EQ + chest + '.png').convert('RGBA')
        for p in ('body', 'rarm', 'larm'):
            armor[p] = (t, 1.0)
    if boots:
        t = Image.open(EQ + boots + '.png').convert('RGBA')
        # boots only show the lower part of the leg layer
        for p in ('rleg', 'lleg'):
            armor[p] = (t, 1.0)
    return armor

def sheet(sets, path):
    views = ['front', 'side', 'back']
    cell_w, cell_h = 22 * S, 36 * S
    out = Image.new('RGBA', (len(views) * cell_w + 40, len(sets) * (cell_h + 20) + 20), (44, 42, 52, 255))
    for r, armor in enumerate(sets):
        for c, v in enumerate(views):
            img = render(v, armor)
            out.alpha_composite(img, (20 + c * cell_w, 20 + r * (cell_h + 20)))
    out.save(path)

sheet([outfit('thorn_plate', 'angels_wings'), outfit('heartsteel', 'angels_wings')], '/tmp/claude-0/armor_worn.png')
