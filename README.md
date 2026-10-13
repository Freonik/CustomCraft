<p align="center">
  <img src="docs/banner.png" alt="CustomCraft" width="100%">
</p>

<p align="center">
  <b>Legendary one-of-a-kind weapons, relics and forge rituals for Paper servers.</b><br>
  Every weapon has its own abilities, exists only <b>once</b> on the whole server and has to be earned:
  hunt bosses for legendary materials, then forge it in a 15-minute ritual the whole server can see.
</p>

<p align="center">
  <img alt="Paper" src="https://img.shields.io/badge/Paper-1.21.11-blue">
  <img alt="Java" src="https://img.shields.io/badge/Java-21-orange">
  <img alt="Resource pack" src="https://img.shields.io/badge/resource%20pack-optional-brightgreen">
</p>

---

## Table of contents

- [Features](#features)
- [Installation](#installation)
- [How it works](#how-it-works)
- [Weapons](#weapons)
- [Relics](#relics)
- [Legendary materials](#legendary-materials)
- [Commands and permissions](#commands-and-permissions)
- [Configuration](#configuration)
- [Resource pack](#resource-pack)
- [Building from source](#building-from-source)
- [Limitations](#limitations)

---

## Features

- **5 legendary weapons** with original abilities on **F** and **Shift + F**, plus on-hit effects and passives
- **4 relics**, armor and off-hand items inspired by MOBA items (stasis, revive, thorns, a heart that keeps growing)
- **9 legendary materials** that drop from bosses and rare mobs or hide in structure chests, **45 % max chance**
- **Forge rituals**: forging takes 15 minutes on a crafting table and the location is revealed step by step
- **One of each**: every weapon and relic can exist only once per server
- **Forge menu** opened from any crafting table, with its own look when the resource pack is used
- **Resource pack** with animated textures, custom worn armor, particles and a themed menu
- **Fully configurable**: numbers, cooldowns, drop chances, ritual length, messages, on/off switches

<p align="center"><img src="docs/weapons.gif" alt="Weapons"></p>

---

## Installation

1. Download `CustomCraft.jar` from the [**latest release**](../../releases/latest) (or build it yourself, see below).
2. Put it into your server's `plugins/` folder and start the server (Paper **1.21.11**, Java **21**).
3. *(Optional, recommended)* Add the resource pack to `server.properties`. The release notes contain the exact two lines
   to copy (link + SHA1), see [Resource pack](#resource-pack). Then set `menu.resource-pack: true` in
   `plugins/CustomCraft/config.yml`.
4. Run `/forge reload` after any config change. No restart needed.

> **Updating:** when the default config changes between versions, delete `plugins/CustomCraft/config.yml` so the new one is
> generated. Your forged items and running rituals are kept in `data.yml`.

---

## How it works

```
 kill bosses / loot structures  →  legendary material  →  forge at a crafting table
                                                              │
                                     15 min ritual, location revealed step by step
                                                              │
                                              weapon drops on the table (only one exists)
```

### The forge
Shift + right-click any **crafting table** with an empty hand. Pick a recipe and the materials show *needed / you have*.
Press **FORGE** when you have everything.

<p align="center"><img src="docs/forge-menu.png" alt="Forge menu" width="420"></p>

### Forge ritual
- The materials are taken and the whole server is told **which item** is being forged, but **not by whom** ("Someone").
- A boss bar shows the item, the time left and a location hint. The location is **revealed step by step**:

  | Ritual progress | Location shown |
  |---|---|
  | start | only the dimension ("somewhere in the Overworld") |
  | 33 % | an area of ±500 blocks (randomly shifted, the centre is not the table) |
  | 66 % | an area of ±100 blocks |
  | 87 % | exact coordinates |

- The item floats and spins above the table. The table can't be broken, burned, blown up or moved while the ritual runs.
- When the time is up, lightning strikes and the item **drops on the table**. Whoever picks it up owns it, so defend it.
- The timer only runs while the server is online and survives restarts.

### One of each
Every weapon and relic can exist **only once**. As soon as its ritual starts, nobody else can forge it.
If the item is destroyed as a dropped item (lava, fire, void, despawn), it can be forged again.

---

## Weapons

All abilities use **F** (swap hands) and **Shift + F**. Cooldowns show in the action bar while you hold the weapon.
On-hit effects need a **charged** hit (no spam-clicking).

### Echo Blade (sword)
| | |
|---|---|
| On hit | **Echo**: 1.5 s later the hit repeats for 40 % of its damage |
| F | **Rewind** (30 s): snap back to where you stood 4 s ago and regain the health you had then |
| Shift + F | **Resonance** (40 s): everyone you hurt in the last 10 s takes 50 % of that damage again (max 14) |

### Graviton Hammer (axe)
| | |
|---|---|
| Passive | Resistance I while held |
| On hit | **Crushing Weight**: +1.5 damage per block you are above the target (max +8), airborne targets get slammed down |
| F | **Gravity Anchor** (35 s): a 7-block field for 6 s; enemies inside are slowed and yanked back if they try to leave |
| Shift + F | **Upheaval** (45 s): lift every enemy around you and slam them down; the higher they fall, the more damage |

### Hexbow (bow)
| | |
|---|---|
| Passive | Speed I while held, witch-particle arrow trail |
| On hit | **Ricochet** (40 %): the hex jumps to the nearest other enemy for 60 % damage |
| F | **Mirror Shot** (22 s): your next arrow swaps your place with whatever it hits |
| Shift + F | **Hex Mark** (35 s): the target you look at glows and takes +30 % damage from everything for 10 s |

### Soulbinder (scythe)
| | |
|---|---|
| On hit | Collect a soul per hit, 2 per kill (max 5); heal 15 % of damage dealt |
| F | **Soul Link** (40 s): for 6 s, 50 % of the damage you take is redirected to the linked target |
| Shift + F | **Harvest** (15 s): spend all souls to heal, gain absorption and launch homing soul wisps |

### Hydra Edge (sword)
| | |
|---|---|
| On hit | **Cleave**: 40 % damage to everyone within 3 blocks of the target, heals 10 % of it |
| F | **Crescent** (12 s): spin attack, 8 damage around you, +1 heart per enemy hit |
| Shift + F | **Hydra's Wrath** (45 s): 6 s of Haste II, Strength I and Speed I; Cleave hits twice as hard |

---

## Relics

<p align="center"><img src="docs/relics.gif" alt="Relics"></p>

Relics only work in their own slot. You can combine **one off-hand relic**, **one chestplate** and **one pair of boots**.

| Relic | Slot | Effect |
|---|---|---|
| **Stasis Hourglass** | off-hand | Right-click: golden statue for 2.5 s. Nothing can hurt you, but you can't move or attack (120 s cooldown) |
| **Angel's Wings** | boots | When you would die, you come back with 50 % HP and 3 s of protection (600 s cooldown). Half fall damage |
| **Thorn Plate** | chestplate | Reflects 2 + 25 % of melee damage; attackers get **Grievous Wounds** (no healing for 3 s) |
| **Heartsteel Core** | chestplate | A charged hit on a player or big mob deals bonus damage and **permanently** adds max HP (up to +10 hearts, stored on the item) |

- Cooldowns and Heartsteel stacks are stored on the item itself.
- The armor relics have their own look when worn and small particle effects
  (feather wings at the ankles, a heartbeat on the chest, thorn bursts).
- Relics and weapons can't be used in a smithing table (no trims, no netherite upgrade).

<p align="center"><img src="docs/armor.png" alt="Worn relic armor" width="560"></p>

---

## Legendary materials

<p align="center"><img src="docs/materials.gif" alt="Legendary materials"></p>

Every weapon and relic needs one legendary material. **Nothing is guaranteed**: the best chance is 45 %.

| Material | Where to find it | Used for |
|---|---|---|
| Resonant Shard | Warden 35 % · Ancient City chests 3 % | Echo Blade |
| Graviton Core | Breeze 3 % · Ominous trial vaults 8 % | Graviton Hammer |
| Hexed Thread | Evoker 8 % · Witch 2 % | Hexbow |
| Soul Ember | Wither 40 % · Wither Skeleton in Soul Sand Valley 2 % | Soulbinder |
| Hydra Scale | Elder Guardian 30 % | Hydra Edge |
| Chrono Sand | Desert Pyramid chests 6 % · Ancient City chests 2 % | Stasis Hourglass |
| Seraph Feather | Ender Dragon 45 % · Phantom above Y 200, 2 % | Angel's Wings |
| Thornheart Seed | Jungle Temple chests 8 % · Ravager 4 % | Thorn Plate |
| Crimson Heart | Wither 25 % · Piglin Brute 3 % | Heartsteel Core |

- Mob drops only count when a **player** kills the mob (no farms). Looting helps a little, but the 45 % cap stays.
- The material drops on the ground, glowing, and can't burn, blow up or despawn.
  The **Ender Dragon**'s drop rises out of the dragon and spirals down onto the egg pillar.
- Every find is announced to the server.
- Materials can't be placed, eaten, traded, brewed, smelted or used in vanilla crafting.

---

## Commands and permissions

| Command | Permission | Description |
|---|---|---|
| shift + right-click a crafting table | `customcraft.use` (everyone) | open the forge |
| `/forge` | everyone | tells you how to open the forge |
| `/forge status` | `customcraft.admin` | which weapons and relics exist or are being forged (with real names) |
| `/forge give <player> <weapon:id\|relic:id\|material:id> [amount]` | `customcraft.admin` | give an item (ignores uniqueness) |
| `/forge release <id>` | `customcraft.admin` | make a weapon or relic forgeable again (stops its ritual) |
| `/forge finish <id>` | `customcraft.admin` | finish a running ritual right now |
| `/forge reload` | `customcraft.admin` | reload `config.yml` |
| | `customcraft.nocooldown` | abilities and relics without cooldowns (for testing) |

Aliases: `/recipes`, `/recepty`.

---

## Configuration

Everything lives in `plugins/CustomCraft/config.yml` and is documented inside the file. The most useful parts:

### Switch items on and off
```yaml
craftable:
  weapons:
    echo_blade: true
    hex_bow: false      # gone from the forge; existing Hexbows keep working
  materials:
    soul_ember: false   # stops dropping
```

### Ritual
```yaml
ritual:
  duration-minutes: 15
  bossbar: ALL            # ALL, NEARBY or NONE
  show-player: false      # hide who is forging
  location-reveal:
    stages:
      - { at-percent: 0,  radius: -1 }    # only the dimension
      - { at-percent: 33, radius: 500 }
      - { at-percent: 66, radius: 100 }
      - { at-percent: 87, radius: 0 }     # exact coordinates
```

### Abilities and balance
```yaml
ability-settings:
  damage-players: true
  pvp-multiplier: 1.0     # ability damage against players
  disabled-worlds: []
```
Every ability, relic and drop has its own numbers (damage, radius, cooldown, chance...) in its section.

### Making your own items
Weapons, relics and materials are defined in the config, so you can add your own and combine the existing ability types:

```yaml
weapons:
  my_weapon:
    material: DIAMOND_SWORD
    name: "&cMy Weapon"
    custom-model-data: 2001
    attack-damage: 9
    abilities:
      on-hit:
        - { type: LIFESTEAL, percent: 20, description: "Heals 20% of damage dealt" }
      active:
        anchor:
          type: GRAVITY_ANCHOR
          name: "&5Anchor"
          trigger: SWAP          # SWAP = F, SHIFT_SWAP = Shift + F
          cooldown: 30
          radius: 6
    forge:
      - { item: DIAMOND_SWORD, amount: 1 }
      - { item: "material:hydra_scale", amount: 1 }
```

| | Types |
|---|---|
| Active abilities | `REWIND`, `RESONANCE`, `GRAVITY_ANCHOR`, `UPHEAVAL`, `MIRROR_SHOT`, `HEX_MARK`, `SOUL_LINK`, `HARVEST`, `CRESCENT`, `FRENZY` |
| On-hit effects | `ECHO`, `CRUSH`, `RICOCHET`, `SOUL`, `LIFESTEAL`, `CLEAVE` |
| Relic types | `STASIS`, `REVIVE`, `THORNS`, `HEARTSTEEL` (slots: `OFFHAND`, `HEAD`, `CHEST`, `LEGS`, `FEET`) |
| Material drops | `{ mob: WARDEN, chance: 35 }`, `{ loot-table: chests/ancient_city, chance: 3 }`, optional `biomes`, `min-y`, `max-y` |

---

## Resource pack

The plugin works without it, but the pack is what makes it look legendary:

- animated 32×32 textures for all weapons, relics and materials
- the Hexbow's own draw animation
- worn looks for Thorn Plate, Heartsteel and Angel's Wings
- a forge-themed menu (stone panel, gold result slots, ember arrows, FORGE / CLOSE buttons)

The pack's source is in [`resourcepack/`](resourcepack). GitHub Actions zips it and attaches it to every release.

**Setup on a server**
1. Copy the two lines from the release notes into `server.properties`:
   ```properties
   resource-pack=https://github.com/<owner>/<repo>/releases/download/latest/CustomCraft-pack.zip
   resource-pack-sha1=<sha1 from the release notes>
   ```
   The `latest` link never changes. Only update the SHA1 when the pack itself changed (the release notes always show the current one).
   Optionally add `require-resource-pack=true`.
2. Set `menu.resource-pack: true` in the plugin config and run `/forge reload`.

**Local testing:** drop the zip into `.minecraft/resourcepacks`, enable it in the game, and set `menu.resource-pack: true`.

Custom model data used by the pack: weapons `1001-1005`, relics `1201-1204`, materials `1301-1309`, menu icons `9000-9021`.

---

## Building from source

Requires **Java 21** and **Maven**.

```bash
mvn clean package
```

The plugin ends up in `target/CustomCraft.jar`.

### Automatic releases (GitHub Actions)
[`.github/workflows/build.yml`](.github/workflows/build.yml) builds the plugin and zips the resource pack on every push:

| You do | You get |
|---|---|
| push to `main` / `master` | the **`latest`** release is replaced with the new `CustomCraft.jar` + `CustomCraft-pack.zip` |
| push a tag like `v1.9.0` (`git tag v1.9.0 && git push origin v1.9.0`) | a permanent release **v1.9.0** with both files and a changelog |
| any build | both files are also attached to the workflow run as an artifact |

The release notes include the `server.properties` lines with the current pack SHA1.
If a release fails with a permission error, enable *Settings → Actions → General → Workflow permissions → Read and write*.

### Project layout

| File | What it does |
|---|---|
| `CustomCraft.java` | plugin entry point, messages |
| `ItemManager.java` | loads weapons, relics and materials from the config and builds the items |
| `MenuManager.java` | the forge menu |
| `RitualManager.java` | forge rituals, uniqueness, boss bar, location reveal |
| `ForgeData.java` | `data.yml`: who owns what, running rituals |
| `AbilityManager.java` / `WeaponAbilities.java` | weapon abilities, cooldowns, action bar HUD |
| `RelicManager.java` / `Relic.java` | relic effects |
| `LootManager.java` | legendary material drops, chest and vault loot |
| `RecipesCommand.java` | `/forge` command |
| `resourcepack/` | resource pack source (zipped by GitHub Actions) |

---

## Limitations

- A plugin can't add buttons to the vanilla crafting table window, so the forge is its own menu (shift + right-click).
- Worn armor can only change its texture, not its 3D shape, without client mods.
- A forge recipe has room for 5 different ingredients.
