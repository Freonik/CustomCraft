"""docs/armor.png: every armor set worn by Steve (front, side, back), straight from the pack's layer textures."""
import sys
from PIL import Image, ImageDraw
PACK = sys.argv[1]            # resourcepack folder
OUT = sys.argv[2]
SKIN = Image.open('/tmp/claude-0/mcassets/assets/minecraft/textures/entity/player/wide/steve.png').convert('RGBA')
EQ = f'{PACK}/assets/customcraft/textures/entity/equipment'
SETS = ['heartsteel', 'thorn_plate', 'thick_hide', 'pale_seraph']

def face(img, box, flip=False):
    r = img.crop((box[0], box[1], box[0] + box[2], box[1] + box[3]))
    return r.transpose(Image.FLIP_LEFT_RIGHT) if flip else r

def view(set_, which):
    arm = Image.open(f'{EQ}/humanoid/{set_}.png').convert('RGBA')
    legs = Image.open(f'{EQ}/humanoid_leggings/{set_}.png').convert('RGBA')
    o = Image.new('RGBA', (16, 32))
    if which == 'front':
        parts = [  # (texture, box, position, flip)
            (SKIN, (8, 8, 8, 8), (4, 0), False), (SKIN, (20, 20, 8, 12), (4, 8), False),
            (SKIN, (44, 20, 4, 12), (0, 8), False), (SKIN, (36, 52, 4, 12), (12, 8), False),
            (SKIN, (4, 20, 4, 12), (4, 20), False), (SKIN, (20, 52, 4, 12), (8, 20), False),
            (legs, (20, 20, 8, 12), (4, 8), False), (legs, (4, 20, 4, 12), (4, 20), False), (legs, (4, 20, 4, 12), (8, 20), True),
            (arm, (20, 20, 8, 12), (4, 8), False), (arm, (44, 20, 4, 12), (0, 8), False), (arm, (44, 20, 4, 12), (12, 8), True),
            (arm, (4, 20, 4, 12), (4, 20), False), (arm, (4, 20, 4, 12), (8, 20), True), (arm, (8, 8, 8, 8), (4, 0), False)]
    elif which == 'back':
        parts = [
            (SKIN, (24, 8, 8, 8), (4, 0), False), (SKIN, (32, 20, 8, 12), (4, 8), False),
            (SKIN, (52, 20, 4, 12), (12, 8), False), (SKIN, (44, 52, 4, 12), (0, 8), False),
            (SKIN, (12, 20, 4, 12), (8, 20), False), (SKIN, (28, 52, 4, 12), (4, 20), False),
            (legs, (32, 20, 8, 12), (4, 8), False), (legs, (12, 20, 4, 12), (8, 20), False), (legs, (12, 20, 4, 12), (4, 20), True),
            (arm, (32, 20, 8, 12), (4, 8), False), (arm, (52, 20, 4, 12), (12, 8), False), (arm, (52, 20, 4, 12), (0, 8), True),
            (arm, (12, 20, 4, 12), (8, 20), False), (arm, (12, 20, 4, 12), (4, 20), True), (arm, (24, 8, 8, 8), (4, 0), False)]
    else:  # the right side
        parts = [
            (SKIN, (0, 8, 8, 8), (4, 0), False), (SKIN, (16, 20, 4, 12), (6, 8), False),
            (SKIN, (0, 20, 4, 12), (6, 20), False), (legs, (16, 20, 4, 12), (6, 8), False), (legs, (0, 20, 4, 12), (6, 20), False),
            (arm, (16, 20, 4, 12), (6, 8), False), (arm, (0, 20, 4, 12), (6, 20), False),
            (SKIN, (40, 20, 4, 12), (6, 8), False), (arm, (40, 20, 4, 12), (6, 8), False), (arm, (0, 8, 8, 8), (4, 0), False)]
    for tex, box, at, flip in parts:
        o.alpha_composite(face(tex, box, flip), at)
    return o

S = 9
W = 3 * (16 * S + 40) + 20
H = len(SETS) * (32 * S + 40) + 20
img = Image.new('RGBA', (W, H), (40, 40, 52, 255))
for r, set_ in enumerate(SETS):
    for c, which in enumerate(('front', 'side', 'back')):
        v = view(set_, which).resize((16 * S, 32 * S), Image.NEAREST)
        img.alpha_composite(v, (20 + c * (16 * S + 40), 20 + r * (32 * S + 40)))
img.save(OUT)
