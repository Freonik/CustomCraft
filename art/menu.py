import sys, random
from PIL import Image

OUT = sys.argv[1]
W, H = 176, 222

# 5x7 pixel font (uppercase)
FONT = {
 'A': ["01110","10001","10001","11111","10001","10001","10001"],
 'B': ["11110","10001","10001","11110","10001","10001","11110"],
 'C': ["01111","10000","10000","10000","10000","10000","01111"],
 'E': ["11111","10000","10000","11110","10000","10000","11111"],
 'F': ["11111","10000","10000","11110","10000","10000","10000"],
 'G': ["01111","10000","10000","10111","10001","10001","01111"],
 'I': ["11111","00100","00100","00100","00100","00100","11111"],
 'K': ["10001","10010","10100","11000","10100","10010","10001"],
 'L': ["10000","10000","10000","10000","10000","10000","11111"],
 'M': ["10001","11011","10101","10101","10001","10001","10001"],
 'N': ["10001","11001","10101","10011","10001","10001","10001"],
 'O': ["01110","10001","10001","10001","10001","10001","01110"],
 'P': ["11110","10001","10001","11110","10000","10000","10000"],
 'R': ["11110","10001","10001","11110","10100","10010","10001"],
 'S': ["01111","10000","10000","01110","00001","00001","11110"],
 'T': ["11111","00100","00100","00100","00100","00100","00100"],
 'W': ["10001","10001","10001","10101","10101","10101","01010"],
 'Y': ["10001","10001","01010","00100","00100","00100","00100"],
 'D': ["11110","10001","10001","10001","10001","10001","11110"],
 'H': ["10001","10001","10001","11111","10001","10001","10001"],
 'U': ["10001","10001","10001","10001","10001","10001","01110"],
 'V': ["10001","10001","10001","10001","01010","01010","00100"],
 'X': ["10001","01010","00100","00100","00100","01010","10001"],
 ' ': ["000","000","000","000","000","000","000"],
 '•': ["000","000","000","010","000","000","000"],
 '|': ["0","0","0","0","0","0","0"],
}

def text_width(s, gap=1):
    return sum(len(FONT[ch][0]) + gap for ch in s) - gap

def draw_text(img, x, y, s, color, shadow=None, gap=1):
    for ch in s:
        g = FONT[ch]
        for r, row in enumerate(g):
            for cidx, bit in enumerate(row):
                if bit == '1':
                    if shadow:
                        img.putpixel((x + cidx + 1, y + r + 1), shadow)
                    img.putpixel((x + cidx, y + r), color)
        x += len(g[0]) + gap

def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), c)

def bevel(img, x0, y0, x1, y1, light, dark, fill=None):
    if fill:
        rect(img, x0, y0, x1, y1, fill)
    for x in range(x0, x1 + 1):
        img.putpixel((x, y0), light)
        img.putpixel((x, y1), dark)
    for y in range(y0, y1 + 1):
        img.putpixel((x0, y), light)
        img.putpixel((x1, y), dark)

# palette
BG = (36, 32, 40, 255)
BG2 = (42, 37, 46, 255)
EDGE = (10, 8, 12, 255)
HI = (78, 70, 84, 255)
LO = (20, 17, 23, 255)
SLOT_IN = (24, 21, 28, 255)
SLOT_HI = (90, 82, 96, 255)
SLOT_LO = (12, 10, 14, 255)
EMBER = [(120, 40, 10, 255), (200, 80, 20, 255), (255, 150, 50, 255), (255, 220, 140, 255)]
GOLD_HI = (240, 200, 100, 255)
GOLD_LO = (120, 80, 24, 255)
GOLD_IN = (40, 30, 20, 255)
PLATE = (48, 42, 54, 255)
TEXT = (245, 230, 205, 255)
SHADOW = (12, 10, 14, 255)

def base(title, H=H):
    img = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    rnd = random.Random(7)
    # panel with subtle stone noise
    for y in range(H):
        for x in range(W):
            n = rnd.random()
            img.putpixel((x, y), BG2 if n < 0.12 else BG)
    # outer bevel
    for i, (l, d) in enumerate([(EDGE, EDGE), (HI, LO), ((58, 52, 64, 255), (28, 24, 32, 255))]):
        bevel(img, i, i, W - 1 - i, H - 1 - i, l, d)
    # title bar
    rect(img, 3, 3, W - 4, 14, (26, 22, 30, 255))
    for x in range(3, W - 3):
        t = 1 - abs(x - W / 2) / (W / 2)
        img.putpixel((x, 15), EMBER[min(3, int(t * 3.99))])
    tw = text_width(title)
    draw_text(img, (W - tw) // 2, 5, title, TEXT, SHADOW)
    # little ember diamonds beside the title
    for cx in ((W - tw) // 2 - 8, (W + tw) // 2 + 6):
        for dx, dy in [(0, -1), (-1, 0), (0, 0), (1, 0), (0, 1)]:
            img.putpixel((cx + dx, 8 + dy), EMBER[2] if (dx, dy) != (0, 0) else EMBER[3])
    return img

def slot(img, col, row_y, style='normal'):
    x0 = 7 + 18 * col
    y0 = row_y
    if style == 'gold':
        bevel(img, x0, y0, x0 + 17, y0 + 17, GOLD_HI, GOLD_LO, GOLD_IN)
        bevel(img, x0 + 1, y0 + 1, x0 + 16, y0 + 16, (200, 150, 60, 255), (90, 60, 20, 255))
        bevel(img, x0 + 2, y0 + 2, x0 + 15, y0 + 15, (18, 12, 8, 255), (70, 52, 30, 255))
    elif style == 'plate':
        bevel(img, x0, y0, x0 + 17, y0 + 17, (100, 70, 140, 255), (40, 24, 60, 255), (46, 34, 62, 255))
    else:
        bevel(img, x0, y0, x0 + 17, y0 + 17, SLOT_LO, SLOT_HI, SLOT_IN)

def chevron(img, col, row_y):
    cx, cy = 7 + 18 * col + 9, row_y + 9
    for i in range(5):
        x = cx - 3 + i
        for y in range(cy - i, cy + i + 1):
            edge = (y == cy - i or y == cy + i)
            img.putpixel((x, y), EMBER[1] if edge else EMBER[2] if i > 1 else EMBER[3])
    for y in range(cy - 1, cy + 2):
        for x in range(cx + 2, cx + 5):
            img.putpixel((x, y), EMBER[1] if y != cy else EMBER[2])

def button(img, col0, col1, row_y, label, face, light, dark, icon=None):
    x0, x1 = 7 + 18 * col0, 7 + 18 * col1 + 17
    y0, y1 = row_y, row_y + 17
    bevel(img, x0, y0, x1, y1, EDGE, EDGE)
    bevel(img, x0 + 1, y0 + 1, x1 - 1, y1 - 1, light, dark, face)
    tw = text_width(label)
    draw_text(img, x0 + (x1 - x0 + 1 - tw) // 2, y0 + 5, label, TEXT, SHADOW)

def player_inventory(img, rows=6):
    top = 17 + rows * 18 + 14
    for r in range(3):
        for c in range(9):
            slot(img, c, top + 18 * r)
    for c in range(9):
        slot(img, c, top + 58)


def forge_menu(title, crate):
    img = base(title)
    for row in range(5):
        y = 17 + 18 * row
        # row plate
        rect(img, 6, y, W - 7, y + 17, (40, 35, 45, 255) if row % 2 else (44, 39, 49, 255))
        slot(img, 0, y, 'plate')
        slot(img, 1, y, 'gold')
        chevron(img, 2, y)
        for c in range(3, 8):
            slot(img, c, y)
        if crate:
            slot(img, 8, y, 'plate')
    y = 17 + 18 * 5
    button(img, 0, 1, y, 'CLOSE', (110, 30, 30, 255), (190, 70, 60, 255), (60, 14, 14, 255))
    for c in range(2, 7):
        slot(img, c, y)
    button(img, 7, 8, y, 'FORGE', (36, 110, 50, 255), (90, 200, 100, 255), (16, 56, 24, 255))
    player_inventory(img)
    return img

def loot_menu():
    img = base('POSSIBLE LOOT')
    for row in range(5):
        for c in range(9):
            slot(img, c, 17 + 18 * row, 'normal')
    y = 17 + 18 * 5
    for c in range(9):
        if c == 4:
            slot(img, c, y, 'gold')
    player_inventory(img)
    return img


DIM = (120, 108, 118, 255)

def label(img, row_y, text, col0=0, col1=8, color=TEXT):
    x0, x1 = 7 + 18 * col0, 7 + 18 * col1 + 17
    tw = text_width(text)
    x = x0 + (x1 - x0 + 1 - tw) // 2
    draw_text(img, x, row_y + 6, text, color, SHADOW)
    # ember underline
    for xx in range(x - 2, x + tw + 2):
        t = 1 - abs(xx - (x + tw / 2)) / (tw / 2 + 2)
        img.putpixel((xx, row_y + 15), EMBER[max(0, min(3, int(t * 3.5)))])

def shop_menu(market):
    img = base('TOKEN SHOP')
    y = lambda r: 17 + 18 * r
    slot(img, 0, y(0), 'gold')                        # your tokens
    label(img, y(0), 'ALWAYS AVAILABLE', 1, 7)
    slot(img, 8, y(0), 'plate')                       # bounty exchange
    for r in (1, 2):
        for c in range(1, 8):
            slot(img, c, y(r))
    label(img, y(3), 'THIS WEEK', 0, 7)
    slot(img, 8, y(3), 'plate')                       # clock
    for c in range(1, 8):
        slot(img, c, y(4), 'gold')
    if market:
        slot(img, 0, y(5), 'plate')                   # bell
        for c in range(1, 8):
            slot(img, c, y(5), 'plate')
    else:
        label(img, y(5), 'FORGE MARKET CLOSED', 0, 8, DIM)
    player_inventory(img)
    return img

def exchange_menu():
    rows = 3
    img = base('BOUNTY EXCHANGE', 114 + rows * 18)
    y = lambda r: 17 + 18 * r
    slot(img, 4, y(0), 'gold')
    for c in (2, 4, 6):
        slot(img, c, y(1) + 0, 'plate')
    # little ember diamonds under the offers
    for c in (2, 4, 6):
        cx = 7 + 18 * c + 9
        for dx, dy in [(0, -1), (-1, 0), (0, 0), (1, 0), (0, 1)]:
            img.putpixel((cx + dx, y(2) + 8 + dy), EMBER[2] if (dx, dy) != (0, 0) else EMBER[3])
    player_inventory(img, rows)
    return img

def picker_menu(rows):
    img = base('CHOOSE A MATERIAL', 114 + rows * 18)
    for r in range(rows):
        for c in range(9):
            slot(img, c, 17 + 18 * r, 'plate')
    player_inventory(img, rows)
    return img

shop_menu(False).save(f'{OUT}/shop.png')
shop_menu(True).save(f'{OUT}/shop_market.png')
exchange_menu().save(f'{OUT}/bounty_exchange.png')
for rows in range(1, 7):
    picker_menu(rows).save(f'{OUT}/picker_{rows}.png')
prev = Image.new('RGBA', (176 * 3 + 20, 222), (54, 54, 64, 255))
for i, n in enumerate(['shop', 'shop_market', 'bounty_exchange']):
    im = Image.open(f'{OUT}/{n}.png').crop((0, 0, 176, 222))
    prev.alpha_composite(im, (i * 186, 0))
prev.resize((prev.width * 2, prev.height * 2), Image.NEAREST).save('/tmp/claude-0/shop_prev.png')

forge_menu('FORGE • WEAPONS', False).save(f'{OUT}/forge_weapons.png')
forge_menu('FORGE • RELICS', False).save(f'{OUT}/forge_relics.png')
for n in ['forge_weapons', 'forge_relics']:
    Image.open(f'{OUT}/{n}.png').crop((0, 0, W, H)).resize((W * 3, H * 3), Image.NEAREST).save(f'/tmp/claude-0/new_{n}.png')
