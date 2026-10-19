"""Tiny pixel-art toolkit for 32x32 item textures."""
import math, json, os
from PIL import Image

N = 32
S2 = 1 / math.sqrt(2)

def diag(x, y, ox, oy):
    """Coordinates along the bottom-left -> top-right diagonal.
    u = distance along the item axis from origin, v = perpendicular (negative = upper-left, lit side)."""
    px, py = x + 0.5 - ox, y + 0.5 - oy
    u = (px - py) * S2
    v = (px + py) * S2
    return u, v

def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)

def ramp(colors, t):
    """Pick from a color ramp, t in [0,1]."""
    t = max(0.0, min(1.0, t))
    i = min(len(colors) - 1, int(t * len(colors)))
    c = colors[i]
    return c if len(c) == 4 else tuple(c) + (255,)

class Canvas:
    def __init__(self, n=N):
        self.n = n
        self.px = {}      # (x,y) -> rgba
        self.kind = {}    # (x,y) -> material tag

    def set(self, x, y, c, kind=None):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.px[(x, y)] = c if len(c) == 4 else tuple(c) + (255,)
            if kind:
                self.kind[(x, y)] = kind

    def fill(self, fn, kind=None):
        """fn(x,y) -> color or None"""
        for y in range(self.n):
            for x in range(self.n):
                c = fn(x, y)
                if c is not None:
                    self.set(x, y, c, kind)

    def outline(self, color, diagonal=False, skip=()):
        add = {}
        dirs = [(1,0),(-1,0),(0,1),(0,-1)] + ([(1,1),(1,-1),(-1,1),(-1,-1)] if diagonal else [])
        for (x, y), k in list(self.px.items()):
            if self.kind.get((x, y)) in skip:
                continue
            for dx, dy in dirs:
                p = (x + dx, y + dy)
                if 0 <= p[0] < self.n and 0 <= p[1] < self.n and p not in self.px:
                    add[p] = color
        for p, c in add.items():
            self.set(p[0], p[1], c, 'outline')

    def image(self):
        im = Image.new('RGBA', (self.n, self.n), (0, 0, 0, 0))
        for (x, y), c in self.px.items():
            im.putpixel((x, y), c)
        return im

def save(frames, path, frametime=2, interpolate=False):
    """frames: list of Images (same size). Saves a vertical strip + .mcmeta when animated."""
    n = frames[0].size[0]
    if len(frames) == 1:
        frames[0].save(path)
        if os.path.exists(path + '.mcmeta'):
            os.remove(path + '.mcmeta')
        return
    strip = Image.new('RGBA', (n, n * len(frames)))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * n))
    strip.save(path)
    with open(path + '.mcmeta', 'w') as fh:
        json.dump({"animation": {"frametime": frametime, "interpolate": interpolate}}, fh, indent=2)

def preview(images, path, scale=8, bg=(54, 54, 64, 255), gap=12):
    w = len(images) * (images[0].size[0] * scale + gap)
    h = images[0].size[1] * scale
    out = Image.new('RGBA', (w, h), bg)
    for i, im in enumerate(images):
        big = im.resize((im.size[0] * scale, im.size[1] * scale), Image.NEAREST)
        out.paste(big, (i * (im.size[0] * scale + gap), 0), big)
    out.save(path)

def gif(frames, path, scale=8, ms=100, bg=(54, 54, 64)):
    out = []
    for f in frames:
        b = Image.new('RGB', f.size, bg)
        b.paste(f, (0, 0), f)
        out.append(b.resize((f.size[0] * scale, f.size[1] * scale), Image.NEAREST))
    out[0].save(path, save_all=True, append_images=out[1:], duration=ms, loop=0)
