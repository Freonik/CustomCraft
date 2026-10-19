import sys, math
sys.path.insert(0, '/home/claude/art')
from px import save, gif
from PIL import Image

OUT = sys.argv[1]

def tile(fn):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            c = fn(x, y)
            if c:
                im.putpixel((x, y), c)
    return im

# clear: fully transparent (filler + cancel, the button is painted in the background)
tile(lambda x, y: None).save(f'{OUT}/clear.png')
tile(lambda x, y: None).save(f'{OUT}/cancel.png')

# select: soft purple band with bright top/bottom lines
def select(x, y):
    if y in (0, 15):
        return (200, 140, 255, 230)
    if y in (1, 14):
        return (160, 100, 240, 120)
    return (140, 80, 220, 55)
tile(select).save(f'{OUT}/select.png')

# proceed_on: pulsing green glow
frames = []
for f in range(10):
    a = 0.5 + 0.5 * math.sin(2 * math.pi * f / 10)
    def on(x, y, a=a):
        edge = y in (0, 15)
        return (90, 255, 120, int(200 * a + 40)) if edge else (60, 230, 90, int(25 + 55 * a))
    frames.append(tile(on))
save(frames, f'{OUT}/proceed_on.png', frametime=2)

# proceed_off: dark, hatched (disabled)
tile(lambda x, y: (0, 0, 0, 175) if (x + y) % 4 else (0, 0, 0, 215)).save(f'{OUT}/proceed_off.png')

# page arrows (ember)
EMB = [(120, 40, 10, 255), (200, 80, 20, 255), (255, 150, 50, 255), (255, 220, 140, 255)]
def arrow_left(x, y):
    cx, cy = 7, 7.5
    # head
    i = x - 2
    if 0 <= i <= 5 and abs(y + 0.5 - (cy + 0.5)) <= i + 0.5 and i <= 5:
        d = abs(y - cy)
        if x <= 7:
            return EMB[3] if d < 1 and i < 3 else EMB[2] if d < i - 0.5 else EMB[1]
    if 8 <= x <= 13 and 6 <= y <= 9:
        return EMB[2] if y in (7, 8) else EMB[1]
    return None
left = tile(arrow_left)
# outline
def outlined(im):
    out = im.copy()
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3] == 0:
                for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and im.getpixel((nx, ny))[3] > 0:
                        out.putpixel((x, y), (40, 12, 4, 255))
                        break
    return out
left = outlined(left)
left.save(f'{OUT}/arrow_left.png')
left.transpose(Image.FLIP_LEFT_RIGHT).save(f'{OUT}/arrow_right.png')
