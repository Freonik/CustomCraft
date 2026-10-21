"""3D in-hand models made from the weapons' own 2D sprites: every pixel becomes a little column, thin at the outline
and thicker toward the middle (like a bevel), with the sprite itself as the texture - so the model looks exactly like
the icon, keeps its animation, and lies on the same diagonal (the vanilla 'handheld' hold angles fit as they are)."""
import sys, os, json
from PIL import Image

CC = sys.argv[1]                       # assets/customcraft
NAMES = sys.argv[2:]
HANDHELD = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}


def depth_map(img, max_depth):
    """Chebyshev distance of every opaque pixel to the nearest transparent one (1 = on the outline)."""
    n = img.width
    opaque = [[img.getpixel((x, y))[3] > 40 for x in range(n)] for y in range(n)]
    dist = [[0] * n for _ in range(n)]
    frontier = []
    for y in range(n):
        for x in range(n):
            if opaque[y][x]:
                edge = any(not (0 <= x + dx < n and 0 <= y + dy < n and opaque[y + dy][x + dx])
                           for dx in (-1, 0, 1) for dy in (-1, 0, 1))
                if edge:
                    dist[y][x] = 1
                    frontier.append((x, y))
    d = 1
    while frontier:
        nxt = []
        for (x, y) in frontier:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < n and 0 <= yy < n and opaque[yy][xx] and dist[yy][xx] == 0:
                        dist[yy][xx] = d + 1
                        nxt.append((xx, yy))
        frontier = nxt
        d += 1
    return [[min(max_depth, v) for v in row] for row in dist]


def build(name, max_depth=3, unit=0.5):
    tex = Image.open(f'{CC}/textures/item/{name}.png').convert('RGBA')
    frame = tex.crop((0, 0, tex.width, tex.width))
    n = frame.width
    px = 16 / n                                     # model units per sprite pixel
    depth = depth_map(frame, max_depth)
    els = []
    for row in range(n):
        x = 0
        while x < n:
            dpt = depth[row][x]
            if dpt == 0:
                x += 1
                continue
            end = x
            while end + 1 < n and depth[row][end + 1] == dpt:
                end += 1
            thick = dpt * unit * px * 2                # total thickness of this run
            z0, z1 = 8 - thick / 2, 8 + thick / 2
            u0, u1 = x * px, (end + 1) * px
            v0, v1 = row * px, (row + 1) * px
            y0, y1 = 16 - (row + 1) * px, 16 - row * px
            faces = {
                "south": {"uv": [u0, v0, u1, v1], "texture": "#s"},
                "north": {"uv": [u1, v0, u0, v1], "texture": "#s"},
                "up": {"uv": [u0, v0, u1, v1], "texture": "#s"},
                "down": {"uv": [u0, v0, u1, v1], "texture": "#s"},
                "west": {"uv": [u0, v0, u0 + px, v1], "texture": "#s"},
                "east": {"uv": [u1 - px, v0, u1, v1], "texture": "#s"},
            }
            els.append({"from": [round(u0, 4), round(y0, 4), round(z0, 4)], "to": [round(u1, 4), round(y1, 4), round(z1, 4)],
                        "faces": faces})
            x = end + 1
    model = {"textures": {"s": f"customcraft:item/{name}", "particle": f"customcraft:item/{name}"},
             "elements": els, "display": HANDHELD}
    with open(f'{CC}/models/item/{name}_3d.json', 'w') as f:
        json.dump(model, f, separators=(',', ':'))
    return len(els)


for nm in NAMES:
    print(nm, build(nm), 'boxes')
