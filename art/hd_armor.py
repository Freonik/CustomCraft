"""HD (4x) armor layers with painted depth. Each box face is painted at K x resolution."""
import sys, math, random
sys.path.insert(0, '/home/claude/art')
from PIL import Image

K = 4
W, H = 64 * K, 32 * K

def clamp(v):
    return max(0, min(255, int(v)))

def shade(c, k):
    if k >= 0:
        return (clamp(c[0] + (255 - c[0]) * k), clamp(c[1] + (255 - c[1]) * k), clamp(c[2] + (255 - c[2]) * k), 255)
    return (clamp(c[0] * (1 + k)), clamp(c[1] * (1 + k)), clamp(c[2] * (1 + k)), 255)

def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)

class Face:
    """A drawable face of a box (w x h in pixels at K resolution)."""
    def __init__(self, img, x, y, w, h):
        self.img, self.x, self.y, self.w, self.h = img, x, y, w, h
    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img.putpixel((self.x + x, self.y + y), c)
    def get(self, x, y):
        return self.img.getpixel((self.x + x, self.y + y))
    def fill(self, c, noise=0.0, seed=1):
        rnd = random.Random(seed)
        for y in range(self.h):
            for x in range(self.w):
                k = -0.18 * (y / max(1, self.h - 1))      # light from above
                k += (rnd.random() - 0.5) * noise
                self.set(x, y, shade(c, k))
    def plate(self, x0, y0, x1, y1, c, bevel=1, radius=1, rivets=False):
        """Raised plate with a lit top-left bevel, dark bottom-right bevel and a soft vertical gradient."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                # rounded corners
                cx = min(x - x0, x1 - x)
                cy = min(y - y0, y1 - y)
                if cx + cy < radius:
                    continue
                if y - y0 < bevel or x - x0 < bevel:
                    col = shade(c, 0.45)
                elif y1 - y < bevel or x1 - x < bevel:
                    col = shade(c, -0.55)
                else:
                    t = (y - y0) / max(1, y1 - y0)
                    col = shade(c, 0.12 - 0.3 * t)
                    if (x - x0) == bevel and (y - y0) < (y1 - y0) * 0.6:
                        col = shade(c, 0.28)            # soft specular streak
                self.set(x, y, col)
        if rivets:
            for (x, y) in [(x0 + 2, y0 + 2), (x1 - 2, y0 + 2), (x0 + 2, y1 - 2), (x1 - 2, y1 - 2)]:
                self.set(x, y, shade(rivets, 0.5)); self.set(x + 1, y + 1, shade(rivets, -0.5))
    def trim(self, x0, y0, x1, y1, c):
        """Metal trim strip with a bright top edge and a dark lower edge."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                t = (y - y0) / max(1, y1 - y0)
                self.set(x, y, shade(c, 0.5 - 1.0 * t) if y1 > y0 else shade(c, 0.2))
    def gem(self, cx, cy, r, colors):
        """Faceted diamond-shaped gem: 4 facets, bright upper-left, glowing core, gold socket."""
        dark, mid, light, core = colors
        for y in range(int(cy - r - 2), int(cy + r + 3)):
            for x in range(int(cx - r - 2), int(cx + r + 3)):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                d = abs(dx) + abs(dy)
                if d <= r:
                    if d < r * 0.35:
                        col = core
                    elif dx <= 0 and dy <= 0:
                        col = light
                    elif dx > 0 and dy <= 0:
                        col = mid
                    elif dx <= 0 and dy > 0:
                        col = mid
                    else:
                        col = dark
                    self.set(x, y, col + (255,) if len(col) == 3 else col)
                elif d <= r + 1.6:
                    self.set(x, y, shade(GOLD, 0.4 if dy < 0 else -0.4))
    def line(self, pts, c, glow=None):
        for (x, y) in pts:
            if glow:
                for gx, gy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    px, py = x + gx, y + gy
                    if 0 <= px < self.w and 0 <= py < self.h:
                        self.set(px, py, mix(self.get(px, py), glow, 0.45))
        for (x, y) in pts:
            self.set(x, y, c)

def crack(x0, y0, length, dx, seed):
    rnd = random.Random(seed)
    pts, x, y = [], x0, y0
    for _ in range(length):
        pts.append((x, y))
        y += 1
        x += rnd.choice([-1, 0, 0, 1]) + (dx if rnd.random() < 0.35 else 0)
    return pts

GOLD = (190, 136, 58)

def faces(img):
    """Map of (part, face) -> Face for the humanoid layer at K resolution."""
    out = {}
    def box(part, u, v, w, h, d):
        u, v, w, h, d = u * K, v * K, w * K, h * K, d * K
        out[(part, 'top')] = Face(img, u + d, v, w, d)
        out[(part, 'bottom')] = Face(img, u + d + w, v, w, d)
        out[(part, 'right')] = Face(img, u, v + d, d, h)
        out[(part, 'front')] = Face(img, u + d, v + d, w, h)
        out[(part, 'left')] = Face(img, u + d + w, v + d, d, h)
        out[(part, 'back')] = Face(img, u + 2 * d + w, v + d, w, h)
    box('body', 16, 16, 8, 12, 4)
    box('arm', 40, 16, 4, 12, 4)
    box('leg', 0, 16, 4, 12, 4)
    box('head', 0, 0, 8, 8, 8)
    return out

# ============================================================ Heartsteel - Molten Core (HD)
OBS = (40, 30, 52)
OBS_D = (22, 16, 30)
LAVA = ((150, 24, 12), (220, 70, 24), (255, 150, 60), (255, 236, 170))
EMBER = (255, 120, 40, 255)

def heartsteel_hd():
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    F = faces(img)
    # ---- chest front (32 x 48)
    f = F[('body', 'front')]
    f.fill(OBS_D, noise=0.08, seed=3)
    f.trim(0, 0, 31, 2, GOLD)                                   # collar
    f.plate(1, 4, 15, 19, OBS, bevel=1, radius=3, rivets=GOLD)   # left pectoral
    f.plate(16, 4, 30, 19, OBS, bevel=1, radius=3, rivets=GOLD)  # right pectoral
    for i, y in enumerate((24, 31, 38)):                         # abdomen lames, overlapping
        f.plate(3 + i, y, 28 - i, y + 7, OBS, bevel=1, radius=1)
        f.trim(3 + i, y + 7, 28 - i, y + 7, GOLD)
    f.gem(15.5, 15, 7, ((140, 20, 14), (210, 60, 26), (255, 150, 70), (255, 240, 190)))
    for seed, (x, dx) in enumerate([(10, -1), (21, 1), (13, 0), (18, 0)]):
        f.line(crack(x, 23, 14 + seed * 3, dx, seed + 7), LAVA[2] + (255,), glow=LAVA[0] + (255,))
    f.trim(0, 46, 31, 47, GOLD)
    # ---- back (32 x 48)
    b = F[('body', 'back')]
    b.fill(OBS_D, noise=0.08, seed=5)
    b.trim(0, 0, 31, 2, GOLD)
    b.plate(2, 4, 29, 30, OBS, bevel=1, radius=3, rivets=GOLD)
    b.line([(15, y) for y in range(7, 28)] + [(16, y) for y in range(7, 28)], LAVA[2] + (255,), glow=LAVA[0] + (255,))
    for y in (34, 40):
        b.plate(4, y, 27, y + 5, OBS, bevel=1)
    b.trim(0, 46, 31, 47, GOLD)
    # ---- sides (16 x 48)
    for side in ('left', 'right'):
        s = F[('body', side)]
        s.fill(OBS_D, noise=0.08, seed=9)
        s.trim(0, 0, 15, 2, GOLD)
        for y in range(5, 44, 7):
            s.plate(1, y, 14, y + 5, OBS, bevel=1)
        s.trim(0, 46, 15, 47, GOLD)
    # ---- top/bottom of the torso
    F[('body', 'top')].fill(GOLD, noise=0.1, seed=11)
    F[('body', 'bottom')].fill(OBS_D, noise=0.05)
    # ---- arms: big layered pauldrons (16 x 48 per face)
    for face in ('front', 'back', 'left', 'right'):
        a = F[('arm', face)]
        a.fill(OBS_D, noise=0.08, seed=13)
        a.plate(0, 0, 15, 9, OBS, bevel=1, radius=2)
        a.trim(0, 9, 15, 10, GOLD)
        a.plate(1, 11, 14, 17, OBS, bevel=1, radius=1)
        a.trim(1, 17, 14, 18, GOLD)
        if face == 'front':
            a.gem(7.5, 4.5, 2.6, ((140, 20, 14), (210, 60, 26), (255, 150, 70), (255, 236, 170)))
        for y in (26, 34):
            a.trim(0, y, 15, y + 1, shade(OBS, 0.2)[:3])
        a.trim(0, 44, 15, 47, GOLD)                              # vambrace cuff
    t = F[('arm', 'top')]
    t.fill(OBS, noise=0.1, seed=17)
    t.trim(0, 0, 15, 1, GOLD)
    F[('arm', 'bottom')].fill(OBS_D)
    return img

if __name__ == '__main__':
    out = heartsteel_hd()
    out.save('/tmp/claude-0/heartsteel_hd.png')
    out.resize((W * 3, H * 3), Image.NEAREST).save('/tmp/claude-0/heartsteel_hd_flat.png')
