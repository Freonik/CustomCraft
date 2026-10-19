import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *

OUTDIR = sys.argv[1]
THEMES = {
    'common': dict(
        wood=[(46,36,30),(70,54,42),(96,76,58),(120,98,76)],
        metal=[(48,50,56),(80,84,92),(124,128,138),(180,184,194)],
        gem=[(60,64,72),(110,116,128),(170,176,188),(230,234,240)], frames=1),
    'rare': dict(
        wood=[(22,30,52),(34,46,76),(48,64,102),(66,86,130)],
        metal=[(40,52,78),(70,90,130),(120,150,200),(190,215,250)],
        gem=[(20,50,140),(40,100,220),(110,170,255),(220,240,255)], frames=8),
    'legendary': dict(
        wood=[(56,28,16),(86,44,22),(118,64,30),(150,88,40)],
        metal=[(110,70,16),(170,120,30),(225,180,60),(255,236,150)],
        gem=[(150,50,10),(230,110,20),(255,180,60),(255,245,200)], frames=8),
}

def face(theme, f, kind):
    th = THEMES[theme]
    n = th['frames']
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / max(1, n))
    sweep = (f / max(1, n)) * 64 - 16    # shimmer band position
    c = Canvas()
    def px(x, y):
        edge = min(x, y, 31 - x, 31 - y)
        shimmer = th['frames'] > 1 and abs((x + y) - sweep) < 2.5
        # metal frame
        if edge < 3:
            if edge == 0:
                return ramp(th['metal'], 0.05)
            corner = (x < 6 or x > 25) and (y < 6 or y > 25)
            t = 0.85 if corner else 0.55
            if (x in (1, 30) or y in (1, 30)) and corner:
                t = 0.99 if shimmer else 0.9
            elif shimmer:
                t = 0.95
            return ramp(th['metal'], t)
        # rivets
        if (x, y) in [(4, 4), (27, 4), (4, 27), (27, 27)]:
            return ramp(th['metal'], 0.99)
        if kind == 'side':
            # center lock plate with gem
            dx, dy = x - 15.5, y - 16.5
            if abs(dx) <= 4.5 and abs(dy) <= 5.5:
                d = math.hypot(dx, dy + 0.5)
                if d <= 2.4:
                    return ramp(th['gem'], 0.99 if d < 1.1 else 0.45 + 0.5 * pulse)
                if abs(dx) > 3.5 or abs(dy) > 4.5:
                    return ramp(th['metal'], 0.15)
                return ramp(th['metal'], 0.99 if shimmer else 0.65 if dy < 0 else 0.45)
            # horizontal straps
            if y in (9, 10, 22, 23):
                return ramp(th['metal'], 0.95 if shimmer else 0.5 if y in (9, 22) else 0.3)
        if kind == 'top':
            dx, dy = x - 15.5, y - 15.5
            d = math.hypot(dx, dy)
            if d <= 3.2:
                return ramp(th['gem'], 0.99 if d < 1.4 else 0.4 + 0.55 * pulse)
            if abs(dx) <= 1.0 or abs(dy) <= 1.0:
                return ramp(th['metal'], 0.95 if shimmer else 0.55)
        # planks
        plank = (y - 3) // 5
        seam = (y - 3) % 5 == 4
        if seam:
            return ramp(th['wood'], 0.05)
        grain = ((x * 7 + plank * 13) % 9 == 0)
        return ramp(th['wood'], 0.3 if grain else 0.55 + 0.1 * (plank % 2))
    c.fill(px)
    return c.image()

previews = []
for theme, th in THEMES.items():
    for kind in ('side', 'top', 'bottom'):
        frames = [face(theme, f, kind) for f in range(th['frames'])]
        save(frames, f'{OUTDIR}/crate_{theme}_{kind}.png', frametime=3)
        if kind != 'bottom':
            previews.append(frames[0])
preview(previews, '/tmp/claude-0/crates_prev.png', scale=5)
