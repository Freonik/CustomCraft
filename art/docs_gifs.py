"""README animations (docs/weapons.gif, relics.gif, materials.gif) from the built pack textures."""
import json, os, sys
from PIL import Image
T = '/home/claude/pack/assets/customcraft/textures/item'
OUT = sys.argv[1]
BG = (24, 22, 30)


def frames_of(name):
    im = Image.open(f'{T}/{name}.png').convert('RGBA')
    if name == 'stitchguard':                       # 3D shield texture: show its front face
        icon = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        icon.paste(im.crop((0, 0, 24, 44)).resize((13, 24), Image.NEAREST), (9, 4))
        return [icon], 1
    n = im.size[1] // im.size[0]
    ft = 1
    meta = f'{T}/{name}.png.mcmeta'
    if os.path.exists(meta):
        ft = json.load(open(meta))['animation'].get('frametime', 1)
    w = im.size[0]
    return [im.crop((0, i * w, w, (i + 1) * w)) for i in range(n)], ft


def strip(names, path, scale, gap, pad, steps=48, tick_per_step=2):
    data = [frames_of(n) for n in names]
    cell = 32 * scale
    W = len(names) * cell + (len(names) - 1) * gap + 2 * pad
    H = cell + 2 * pad
    out = []
    for t in range(steps):
        img = Image.new('RGB', (W, H), BG)
        for i, (fr, ft) in enumerate(data):
            f = fr[(t * tick_per_step // ft) % len(fr)].resize((cell, cell), Image.NEAREST)
            img.paste(f, (pad + i * (cell + gap), pad), f)
        out.append(img)
    out[0].save(path, save_all=True, append_images=out[1:], duration=100, loop=0, optimize=True)


strip(['echo_blade', 'graviton_hammer', 'hex_bow', 'soulbinder', 'hydra_edge', 'butcher_cleaver', 'abyssal_maw',
       'clawline_pick', 'silk_needle', 'rimebreaker', 'pinning_repeater', 'valkyries_lance',
       'great_sea_trident'],
      f'{OUT}/weapons.gif', 4, 8, 8)
strip(['stasis_hourglass', 'angels_wings', 'thorn_plate', 'heartsteel_core', 'thick_hide',
       'soul_vessel_mask', 'stitchguard'], f'{OUT}/relics.gif', 4, 8, 8)
strip(['resonant_shard', 'graviton_core', 'hexed_thread', 'soul_ember', 'hydra_scale', 'chrono_sand', 'seraph_feather',
       'thornheart_seed', 'crimson_heart', 'ravenous_marrow', 'abyssal_lure', 'behemoth_hide',
       'pale_fragment', 'carapace_shard', 'deepsteel_claw', 'weavers_silk', 'rime_core',
       'hunters_bolt', 'valkyrie_plume', 'sea_kings_doubloon'], f'{OUT}/materials.gif', 3, 6, 6)
print('docs gifs done')
