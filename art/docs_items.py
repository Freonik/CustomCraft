"""One animated PNG (APNG, transparent) per weapon / relic / material for the README."""
import json, os, sys
from PIL import Image
T = '/home/claude/pack/assets/customcraft/textures/item'
OUT = sys.argv[1]
SCALE = 2
os.makedirs(OUT, exist_ok=True)


def frames_of(name):
    im = Image.open(f'{T}/{name}.png').convert('RGBA')
    if name == 'stitchguard':
        icon = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        icon.paste(im.crop((0, 0, 24, 44)).resize((13, 24), Image.NEAREST), (9, 4))
        return [icon], 1
    w = im.size[0]
    n = im.size[1] // w
    ft = 1
    if os.path.exists(f'{T}/{name}.png.mcmeta'):
        ft = json.load(open(f'{T}/{name}.png.mcmeta'))['animation'].get('frametime', 1)
    return [im.crop((0, i * w, w, (i + 1) * w)) for i in range(n)], ft


NAMES = ['echo_blade', 'graviton_hammer', 'hex_bow', 'soulbinder', 'hydra_edge', 'butcher_cleaver', 'abyssal_maw',
         'clawline_pick', 'silk_needle', 'rimebreaker', 'pinning_repeater', 'valkyries_lance', 'great_sea_trident',
         'stasis_hourglass', 'angels_wings', 'thorn_plate', 'heartsteel_core', 'soul_vessel_mask', 'stitchguard', 'thick_hide',
         'resonant_shard', 'graviton_core', 'hexed_thread', 'soul_ember', 'hydra_scale', 'chrono_sand', 'seraph_feather',
         'thornheart_seed', 'crimson_heart', 'ravenous_marrow', 'abyssal_lure', 'behemoth_hide', 'pale_fragment',
         'carapace_shard', 'deepsteel_claw', 'weavers_silk', 'rime_core', 'hunters_bolt', 'valkyrie_plume', 'sea_kings_doubloon']
for name in NAMES:
    frames, ft = frames_of(name)
    big = [f.resize((32 * SCALE, 32 * SCALE), Image.NEAREST) for f in frames]
    path = f'{OUT}/{name}.png'
    if len(big) == 1:
        big[0].save(path, optimize=True)
    else:
        big[0].save(path, save_all=True, append_images=big[1:], duration=ft * 50, loop=0, disposal=1, blend=0)
print(len(NAMES), 'icons')
