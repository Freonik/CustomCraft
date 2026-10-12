# CustomCraft

Paper plugin with custom weapons that have their own special abilities, mystery crates (shulker boxes) and a forge menu with recipes.

- **Server:** Paper 1.21.11 (Java 21); `pom.xml` targets `paper-api 1.21.11-R0.1-SNAPSHOT`
- **Command:** `/forge` (aliases `/recipes`, `/recepty`)
- Works **without a resource pack** (the menu uses vanilla items). The optional pack adds weapon textures and a styled menu.

## Building

```bash
mvn clean package
```

The plugin ends up in `target/CustomCraft.jar`. Copy it to `plugins/` and restart the server.
Pushing to GitHub also builds it automatically (Actions → Build plugin → artifact).

## Weapons

Every weapon has two active abilities with cooldowns plus on-hit / passive effects.
Cooldowns show in the action bar while you hold the weapon, and the ability list is added to the item lore automatically.

### Echo Blade (sword)
| | |
|---|---|
| **On hit** | *Echo*: 1.5 s later the hit repeats for 40 % of its damage. |
| **Right-click** | *Rewind* (30 s): snap back to where you stood 4 s ago and regain the health you had then. |
| **Shift + Right-click** | *Resonance* (40 s): everyone you hurt in the last 10 s takes 50 % of that damage again (max 14). |

### Graviton Hammer (axe)
| | |
|---|---|
| **Passive** | Resistance I while held. |
| **On hit** | *Crushing Weight*: +1.5 dmg per block you are above the target (max +8). Airborne targets get slammed down. |
| **Right-click** | *Gravity Anchor* (35 s): a 7-block gravity field for 6 s. Enemies inside are slowed and yanked back if they try to leave. |
| **Shift + Right-click** | *Upheaval* (45 s): lift every enemy around you, then slam them down. The higher they are, the more damage (max 12). |

### Hexbow (bow, uses the F key)
| | |
|---|---|
| **Passive** | Speed I while held, witch-particle arrow trail. |
| **On hit** | *Ricochet* (40 %): the hex jumps to the nearest other enemy for 60 % damage. |
| **F** | *Mirror Shot* (22 s): your next arrow (within 10 s) swaps your place with whatever it hits. |
| **Shift + F** | *Hex Mark* (35 s): curse the target you look at; it glows and takes +30 % damage from everything for 10 s. |

### Soulbinder (scythe)
| | |
|---|---|
| **On hit** | Collect a soul per hit, 2 per kill (max 5), and heal 15 % of damage dealt. |
| **Right-click** | *Soul Link* (40 s): chain your soul to a target for 6 s; 50 % of the damage you take hits them instead. |
| **Shift + Right-click** | *Harvest* (15 s): spend all souls to heal 1 heart each, gain absorption and launch homing soul wisps (3 dmg per soul). |

### Rules
- On-hit effects only trigger on a **charged** melee hit (no spam-clicking), or on an arrow shot from that bow.
- Ability damage goes through the normal damage event, so region protection and disabled PvP are respected.
- Global settings live in `ability-settings` (on/off, HUD, PvP damage multiplier, disabled worlds).
- Permission `customcraft.nocooldown` = no cooldowns (for testing).

### Making your own weapons
Every ability has its numbers in the config (damage, radius, cooldown, trigger, ...), and you can mix them freely on new weapons.

- Triggers: `RIGHT_CLICK`, `SHIFT_RIGHT_CLICK`, `SWAP` (F key), `SHIFT_SWAP`
- Active types: `REWIND, RESONANCE, GRAVITY_ANCHOR, UPHEAVAL, MIRROR_SHOT, HEX_MARK, SOUL_LINK, HARVEST`
- On-hit types: `ECHO, CRUSH, RICOCHET, SOUL, LIFESTEAL`
- Passive: `passive.effects: { effect_name: level-1 }`, e.g. `{ speed: 0 }` = Speed I

```yaml
weapons:
  my_weapon:
    material: DIAMOND_SWORD
    name: "&cMy Weapon"
    lore: ["&7Description"]
    custom-model-data: 2001      # optional (resource pack)
    unbreakable: true            # optional
    attack-damage: 9             # optional, total value
    attack-speed: 1.6            # optional, total value
    enchants:
      sharpness: 5
    abilities:
      on-hit:
        - { type: LIFESTEAL, chance: 100, percent: 20, description: "Heals 20% of damage dealt" }
      active:
        anchor:
          type: GRAVITY_ANCHOR
          name: "&5Anchor"
          trigger: RIGHT_CLICK
          cooldown: 30
          description: ["Pulls enemies back in."]
          radius: 6
          duration: 5
    forge:                       # materials (max 5), amount can exceed 64
      - { item: DIAMOND_SWORD, amount: 1 }
      - { item: DIAMOND_BLOCK, amount: 8 }
```

## How forging works

`/forge` (or shift + right-click a crafting table with an empty hand) opens the menu:

```
[name] [result] ◄ [material] [material] [material] [material] [material] [crate: possible loot]
...  (5 recipes per page)
[Cancel x2] [<] [Weapons] [page] [Crates] [>] [Proceed x2]
```

1. Click a recipe; the row gets highlighted.
2. Each material shows "Needed / You have" (green when you have enough).
3. **Proceed** lights up when you have everything. Clicking it takes the materials and gives the item.
4. Mystery crates have a **Possible loot** button showing chances in %.

Crates are opened by right-clicking with the crate in hand (action bar animation, weighted reward, rare wins can be broadcast to the whole server).

## Commands and permissions

| Command | Permission | Description |
|---|---|---|
| `/forge` | `customcraft.use` (everyone) | opens the menu |
| `/forge reload` | `customcraft.admin` (OP) | reloads `config.yml` |
| `/forge give <player> <weapon:id\|box:id> [amount]` | `customcraft.admin` | gives an item or crate |
| | `customcraft.nocooldown` | abilities without cooldowns |

## Mystery crates

```yaml
boxes:
  my_crate:
    color: RED                   # NONE = default purple shulker
    name: "&cMy Crate"
    forge:
      - { item: "box:common", amount: 3 }
      - { item: GOLD_INGOT, amount: 64 }
    loot:
      - { item: DIAMOND, amount: 4, weight: 50 }
      - { item: "weapon:echo_blade", weight: 5, broadcast: true }
```

- A material can be a vanilla material or another custom item (`weapon:id`, `box:id`), so crates can be chained.
- Vanilla materials only count plain items; this plugin's custom items never count as e.g. a "diamond sword".
- Win chance = `weight` ÷ the sum of all `weight`s in that crate.
- Enchant names are vanilla keys in lower case (`sharpness`, `power`, `unbreaking`...).
- Optionally add a vanilla shaped crafting-table recipe: `recipe: { shape: [...], ingredients: {"X": MATERIAL} }` (keys in quotes). It also shows in the vanilla recipe book.

## Resource pack (optional, Minecraft 1.21.11)

`CustomCraft-pack.zip` (pack format 75.0) adds:
- textures and models for the 4 weapons (`custom-model-data` 1001 to 1004),
- a dark forge menu background, transparent panels and coloured Cancel / Proceed buttons.

**Try it yourself:**
1. Copy `CustomCraft-pack.zip` (don't unzip) to `.minecraft/resourcepacks`.
2. In Minecraft: Options → Resource Packs, enable the pack.
3. In `plugins/CustomCraft/config.yml` set `menu.resource-pack: true` and run `/forge reload`.

**For players on the server:** upload the zip to a direct link (e.g. a GitHub Release) and set
`resource-pack=<URL>`, `resource-pack-sha1=<sha1 of the file>` and optionally `require-resource-pack=true` in `server.properties`.
Only turn on `menu.resource-pack: true` when players actually have the pack, otherwise they see odd characters in the title.

**Custom textures:** replace the PNGs in `assets/customcraft/textures/item/`. To add another weapon, create a model in
`assets/customcraft/models/item/` and a file in `assets/minecraft/items/<vanilla_item>.json` with a new `custom-model-data`
(see `netherite_sword.json`). Menu backgrounds are 256×256 images in `assets/customcraft/textures/font/`.

## Limits

- A plugin can't add a second button to the **vanilla crafting table window** (that's client-side), so the menu opens via command or shift-click on the table.
- A recipe row has room for 5 different materials.
- The loot preview shows up to 45 entries per crate.
