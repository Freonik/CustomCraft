# CustomCraft

Paper plugin with custom weapons that have their own special abilities, mystery crates (shulker boxes) and a forge menu with recipes.

- **Server:** Paper 1.21.11 (Java 21); `pom.xml` targets `paper-api 1.21.11-R0.1-SNAPSHOT`
- **Forge:** shift + right-click a crafting table (admin command `/forge`)
- Works **without a resource pack** (the menu uses vanilla items). The optional pack adds weapon textures and a styled menu.

## Building

```bash
mvn clean package
```

The plugin ends up in `target/CustomCraft.jar`. Copy it to `plugins/` and restart the server.
Pushing to GitHub also builds it automatically (Actions → Build plugin → artifact).

## Weapons

Every weapon has two active abilities with cooldowns: **F** (swap hands key) and **Shift + F**, plus on-hit / passive effects.
Cooldowns show in the action bar while you hold the weapon, and the ability list is added to the item lore automatically.

### Echo Blade (sword)
| | |
|---|---|
| **On hit** | *Echo*: 1.5 s later the hit repeats for 40 % of its damage. |
| **F** | *Rewind* (30 s): snap back to where you stood 4 s ago and regain the health you had then. |
| **Shift + F** | *Resonance* (40 s): everyone you hurt in the last 10 s takes 50 % of that damage again (max 14). |

### Graviton Hammer (axe)
| | |
|---|---|
| **Passive** | Resistance I while held. |
| **On hit** | *Crushing Weight*: +1.5 dmg per block you are above the target (max +8). Airborne targets get slammed down. |
| **F** | *Gravity Anchor* (35 s): a 7-block gravity field for 6 s. Enemies inside are slowed and yanked back if they try to leave. |
| **Shift + F** | *Upheaval* (45 s): lift every enemy around you, then slam them down. The higher they are, the more damage (max 12). |

### Hexbow (bow)
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
| **F** | *Soul Link* (40 s): chain your soul to a target for 6 s; 50 % of the damage you take hits them instead. |
| **Shift + F** | *Harvest* (15 s): spend all souls to heal 1 heart each, gain absorption and launch homing soul wisps (3 dmg per soul). |

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
          trigger: SWAP
          cooldown: 30
          description: ["Pulls enemies back in."]
          radius: 6
          duration: 5
    forge:                       # materials (max 5), amount can exceed 64
      - { item: DIAMOND_SWORD, amount: 1 }
      - { item: DIAMOND_BLOCK, amount: 8 }
```

## How forging works

The forge menu opens **only from a crafting table**: shift + right-click it with an empty hand.
(`/forge` just tells players this; the subcommands are admin tools.)

```
[name] [result] ◄ [material] [material] [material] [material] [material] [crate: possible loot]
...  (5 recipes per page)
[Close x2] [<] [Weapons] [page] [Crates] [>] [Forge x2]
```

1. Click a recipe; the row gets highlighted.
2. Each material shows "Needed / You have" (green when you have enough).
3. **Forge** lights up when you have everything and the recipe is available.
   - **Crates** are made instantly.
   - **Weapons** start a **forge ritual** on that crafting table (15 min by default).

### Forge ritual
- The materials are taken immediately and the whole server gets an announcement: **which weapon** is being forged,
  but not **who** forges it ("Someone", configurable with `ritual.show-player`).
- The **location is revealed step by step** in chat and in the boss bar (`ritual.location-reveal`). For 15 minutes:
  | Time | Shown |
  |---|---|
  | 0 min | only the dimension ("somewhere in the Overworld") |
  | 5 min | an area of ±500 blocks |
  | 10 min | an area of ±100 blocks |
  | 13 min | exact coordinates |
  The areas are randomly shifted, so the middle of the area is not the table.
- A boss bar shows the weapon, the time left and the current location hint (everyone / nearby / nobody).
- The weapon floats and spins above the table, with runes and particles around it.
- The crafting table can't be broken, burned, blown up or moved by pistons while the ritual runs.
- When the time is up, lightning strikes and the weapon **drops on the table** (it can't despawn or burn).
  Whoever picks it up gets it, so defend your ritual.
- The timer only counts while the server is running and survives restarts (`data.yml`).
- One ritual per crafting table. Length per weapon: `ritual-minutes: 30` in the weapon's config.

### One of each weapon
- Every weapon can exist **only once** on the whole server. As soon as a ritual starts, nobody else can forge it.
- Crates stop dropping weapons that already exist (and reserve the weapon the moment you roll it).
- If the weapon is destroyed as a dropped item (lava, fire, cactus, void, despawn), it becomes forgeable again
  (`unique-weapons.release-when-destroyed`).
- Admins: `/forge status` shows every weapon's state, `/forge release <weapon>` frees it, `/forge finish <weapon>` ends a ritual now.
- Exclude a weapon with `unique: false`. Turn rituals or uniqueness off in `ritual.enabled` / `unique-weapons.enabled`.

## Commands and permissions

| Command | Permission | Description |
|---|---|---|
| shift + right-click a crafting table | `customcraft.use` (everyone) | opens the forge |
| `/forge` | everyone | how to open the forge |
| `/forge reload` | `customcraft.admin` (OP) | reloads `config.yml` |
| `/forge status` | `customcraft.admin` | which weapons exist / are being forged |
| `/forge release <weapon>` | `customcraft.admin` | makes a weapon forgeable again (stops its ritual) |
| `/forge finish <weapon>` | `customcraft.admin` | finishes a ritual immediately |
| `/forge give <player> <weapon:id\|box:id> [amount]` | `customcraft.admin` | gives an item or crate (ignores uniqueness) |
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
- animated 32×32 textures for all 4 weapons (`custom-model-data` 1001 to 1004), the Hexbow has its own draw animation,
- 3D crate blocks for the mystery crates (`custom-model-data` 1101 to 1103),
- a forge-themed menu: stone panel, gold result slots, ember arrows, CLOSE / FORGE buttons, pulsing Proceed and custom page arrows.

**Try it yourself:**
1. Copy `CustomCraft-pack.zip` (don't unzip) to `.minecraft/resourcepacks`.
2. In Minecraft: Options → Resource Packs, enable the pack.
3. In `plugins/CustomCraft/config.yml` set `menu.resource-pack: true` and run `/forge reload`.

**For players on the server:** upload the zip to a direct link (e.g. a GitHub Release) and set
`resource-pack=<URL>`, `resource-pack-sha1=<sha1 of the file>` and optionally `require-resource-pack=true` in `server.properties`.
Only turn on `menu.resource-pack: true` when players actually have the pack, otherwise they see odd characters in the title.

**Custom textures:** replace the PNGs in `assets/customcraft/textures/item/` (animated ones are vertical strips with a `.png.mcmeta`). To add another weapon, create a model in
`assets/customcraft/models/item/` and a file in `assets/minecraft/items/<vanilla_item>.json` with a new `custom-model-data`
(see `netherite_sword.json`). Menu backgrounds are 256×256 images in `assets/customcraft/textures/font/`.

## Limits

- A plugin can't add a second button to the **vanilla crafting table window** (that's client-side), so the menu opens via command or shift-click on the table.
- A recipe row has room for 5 different materials.
- The loot preview shows up to 45 entries per crate.
