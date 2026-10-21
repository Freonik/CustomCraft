"""The Last Act: an ornate gold-and-ivory rifle (crossbow states) + Gilded Mask Shard (its legendary material)."""
import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *

OUT = sys.argv[1]
GOLD = [(84, 52, 12), (146, 98, 28), (206, 156, 52), (244, 206, 104), (255, 244, 196)]
IVORY = [(150, 140, 124), (196, 188, 170), (230, 224, 208), (250, 247, 238)]
STEEL = [(18, 18, 24), (34, 34, 44), (58, 58, 72), (92, 92, 110), (140, 140, 160)]
CRIMSON = [(70, 6, 16), (140, 14, 30), (210, 40, 54), (255, 130, 130)]
GLOW = [(200, 140, 30), (255, 210, 90), (255, 250, 210)]


def U(fn, OX=3.5, OY=28.0):
    def f(x, y):
        u, v = diag(x, y, OX, OY)
        return fn(u, v)
    return f


def rifle(pull, load=None):
    """pull 0..3 = hammer drawn back further (crossbow pulling states); load None / 'arrow' / 'rocket'."""
    c = Canvas()
    hammer = [0.7, 1.4, 2.1, 2.8][pull]

    def body(u, v):
        # stock: a real rifle stock - it widens and drops towards the butt (ivory, gold inlay, gold butt plate)
        if 0.6 <= u <= 11.0:
            top = -1.5
            bot = 1.2 + (11.0 - u) * 0.30                    # drops: 1.2 at the wrist .. ~4.2 at the butt
            if top <= v <= bot:
                if u < 1.8:
                    return ramp(GOLD, 0.75 if v < 1 else 0.45)   # butt plate
                if abs(v - (top + 0.9)) < 0.3 and 2.5 < u < 9.5:
                    return ramp(GOLD, 0.85)                  # inlay along the comb
                if 4.0 < u < 7.0 and abs(v - (bot - 1.3)) < 0.45:
                    return ramp(GOLD, 0.6)                   # small gold medallion
                return ramp(IVORY, 0.85 - max(0, v) * 0.1)
        # pistol grip under the wrist, slanted back
        if 9.0 <= u <= 11.6:
            g = (u - 9.0) + (v - 1.2) * 0.45
            if 1.0 < v <= 3.6 and 0 <= g <= 2.2:
                return ramp(IVORY, 0.55 if g > 1.1 else 0.75)
        # trigger guard (lower side)
        if 11.6 <= u <= 14.0 and 1.2 < v <= 2.8 and not (12.2 < u < 13.4 and v < 2.2):
            return ramp(GOLD, 0.55)
        # receiver: gold with a red gem
        if 10.5 <= u <= 19.0 and abs(v) <= 1.45:
            if (u - 15.0) ** 2 + v ** 2 < 1.3:
                return ramp(CRIMSON, 0.8 if v < 0 else 0.5)
            if abs(abs(v) - 1.2) < 0.3 and int(u * 2) % 3 == 0:
                return ramp(GOLD, 0.98)                          # filigree
            return ramp(GOLD, 0.8 if v < -0.3 else 0.45)
        # barrel: dark steel with gold bands
        if 18.5 <= u <= 36.0 and abs(v) <= 0.95:
            if int(u) in (24, 31):
                return ramp(GOLD, 0.7)
            return ramp(STEEL, 0.7 if v < 0 else 0.35)
        # muzzle flare
        if 36.0 < u <= 38.2 and abs(v) <= 1.35:
            return ramp(GOLD, 0.85 if v < 0 else 0.55)
        return None
    c.fill(U(body), 'body')

    def ham(u, v):
        if 11.0 <= u <= 12.6 and -1.45 - hammer <= v < -1.45:
            return ramp(STEEL, 0.75) if v > -1.45 - hammer + 0.6 else ramp(GOLD, 0.9)
        return None
    for y in range(32):
        for x in range(32):
            if (x, y) not in c.px:
                col = U(ham)(x, y)
                if col:
                    c.set(x, y, col, 'hammer')

    if load:
        pal = GLOW if load == 'arrow' else CRIMSON
        # the loaded round glows in the chamber window and at the muzzle
        def shot(u, v):
            if 16.5 <= u <= 18.8 and abs(v) <= 0.8:
                return ramp(pal, 0.95)
            if 19.5 <= u <= 35.5 and abs(v) <= 0.3:
                return ramp(pal, 0.55 + 0.4 * ((u - 19.5) / 16))   # glowing line down the barrel
            return None
            return None
        for y in range(32):
            for x in range(32):
                col = U(shot)(x, y)
                if col:
                    c.set(x, y, col, 'shot')
    c.outline((10, 8, 6, 255), skip=('shot',))
    return c.image()


previews = []
for name, pull, load in [('the_last_act', 0, None), ('the_last_act_pulling_0', 1, None),
                         ('the_last_act_pulling_1', 2, None), ('the_last_act_pulling_2', 3, None),
                         ('the_last_act_arrow', 3, 'arrow'), ('the_last_act_firework', 3, 'rocket')]:
    img = rifle(pull, load)
    save([img], f'{OUT}/{name}.png')
    previews.append(img)


def shard():
    """Half of an ivory theatre mask, broken along a jagged line, gold rim, one empty eye with a red tear."""
    c = Canvas()
    cx, cy = 15.5, 15.0
    for y in range(32):
        for x in range(32):
            dx, dy = (x + 0.5 - cx) / 10.5, (y + 0.5 - cy) / 13.0
            r = dx * dx + dy * dy
            crack = 15.5 + 2.2 * math.sin(y * 1.3) + (1.5 if y % 4 < 2 else -0.5)
            if r <= 1.0 and x + 0.5 <= crack and 3 <= y <= 28:
                light = 0.75 - dx * 0.25 - dy * 0.2
                if r > 0.80:
                    c.set(x, y, ramp(GOLD, 0.55 + light * 0.4), 'rim')
                else:
                    c.set(x, y, ramp(IVORY, light), 'face')
    # eye hole + tear
    for y in range(32):
        for x in range(32):
            ex, ey = (x + 0.5 - 11.5) / 3.0, (y + 0.5 - 12.0) / 1.7
            if ex * ex + ey * ey <= 1.0 and (x, y) in c.px:
                c.set(x, y, (16, 10, 10, 255), 'eye')
    for y in range(14, 19):
        c.set(11, y, ramp(CRIMSON, 0.4 + (y - 14) * 0.12), 'tear')
    c.set(11, 19, ramp(CRIMSON, 0.95), 'tear')
    # gold brow line
    for x in range(7, 15):
        if (x, 9) in c.px:
            c.set(x, 9, ramp(GOLD, 0.85), 'brow')
    c.outline((12, 8, 6, 255))
    return c.image()


s = shard()
save([s], f'{OUT}/gilded_mask_shard.png')
preview(previews + [s], '/tmp/claude-0/lastact_prev.png', scale=8)
print('the last act done')
