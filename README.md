# CustomCraft

Paper plugin pro custom zbraně, mystery boxy (shulkery) a menu „kovárny“ s recepty.

- **Server:** Paper 1.21.11 (Java 21); `pom.xml` míří na `paper-api 1.21.11-R0.1-SNAPSHOT`
- **Příkaz:** `/recepty` (aliasy `/recipes`, `/receptar`)
- **Bez resource packu** funguje vše (menu používá vanilla itemy). Zbraním lze přidat `custom-model-data` a vlastní textury přes resource pack.

## Sestavení

```bash
mvn clean package
```

Hotový plugin je `target/CustomCraft.jar`. Zkopíruj ho do `plugins/` na serveru a restartuj.

## Jak výroba funguje

`/recepty` (nebo shift + pravý klik prázdnou rukou na crafting stůl) otevře menu:

```
[název] [výsledek] ◄ [surovina] [surovina] [surovina] [surovina] [surovina] [box: možná výhra]
...  (5 receptů na stranu)
[Cancel x2] [<] [Zbraně] [strana] [Boxy] [>] [Proceed x2]
```

1. Hráč klikne na recept, řádek se zvýrazní (fialově).
2. U surovin vidí „Potřeba / Máš“ (zeleně, pokud mu nic nechybí).
3. **Proceed** se rozsvítí, když má všechno. Klik vezme suroviny z inventáře a dá item.
4. U mystery boxů je tlačítko **Možná výhra** s šancemi v %.

Mystery boxy se otevírají pravým klikem s boxem v ruce (animace v action baru, výhra podle vah, vzácné výhry jdou ohlásit celému serveru).

## Příkazy a oprávnění

| Příkaz | Oprávnění | Popis |
|---|---|---|
| `/recepty` | `customcraft.use` (výchozí všichni) | otevře menu |
| `/recepty reload` | `customcraft.admin` (výchozí OP) | znovu načte `config.yml` |
| `/recepty give <hráč> <weapon:id\|box:id> [počet]` | `customcraft.admin` | dá item nebo box |

## Přidání vlastní zbraně nebo boxu

Vše v `plugins/CustomCraft/config.yml`, poté `/recepty reload`.

```yaml
weapons:
  moje_zbran:
    material: DIAMOND_SWORD
    name: "&cMoje zbraň"
    lore: ["&7Popisek"]
    custom-model-data: 2001      # volitelné (resource pack)
    unbreakable: true            # volitelné
    attack-damage: 9             # volitelné, celková hodnota
    attack-speed: 1.6            # volitelné, celková hodnota
    enchants:
      sharpness: 5
    forge:                       # suroviny (max. 5), amount i přes 64
      - { item: DIAMOND_SWORD, amount: 1 }
      - { item: DIAMOND_BLOCK, amount: 8 }

boxes:
  muj_box:
    color: RED                   # NONE = základní fialový shulker
    name: "&cMůj box"
    forge:
      - { item: "box:common", amount: 3 }
      - { item: GOLD_INGOT, amount: 64 }
    loot:
      - { item: DIAMOND, amount: 4, weight: 50 }
      - { item: "weapon:moje_zbran", weight: 5, broadcast: true }
```

- Surovina může být vanilla materiál, nebo jiný custom item (`weapon:id`, `box:id`). Boxy tak jdou řetězit.
- Vanilla suroviny se počítají jen z obyčejných itemů, custom itemy pluginu se jako např. „diamantový meč“ nepočítají.
- Šance výhry = `weight` ÷ součet všech `weight` v dané bedně.
- Názvy enchantů jsou vanilla klíče malými písmeny (`sharpness`, `fire_aspect`, `unbreaking`...).
- Volitelně lze přidat vanilla tvarovaný recept pro crafting stůl: `recipe: { shape: [...], ingredients: {"X": MATERIAL} }` (klíče v uvozovkách). Zobrazí se i ve vanilla knížce.

## Resource pack (volitelný, pro Minecraft 1.21.11)

Soubor `CustomCraft-pack.zip` (pack format 75.0) dodá:
- textury a modely 3 ukázkových zbraní (`custom-model-data` 1001 až 1003),
- tmavé pozadí menu kovárny, průhledné panely a barevná tlačítka Cancel / Proceed.

**Vyzkoušení u sebe:**
1. Zkopíruj `CustomCraft-pack.zip` (nerozbaluj) do `.minecraft/resourcepacks`.
2. V Minecraftu: Options → Resource Packs a pack zapni.
3. V `plugins/CustomCraft/config.yml` nastav `menu.resource-pack: true` a spusť `/recepty reload`.

**Pro hráče na serveru:** nahraj zip na přímý odkaz (např. GitHub Release) a v `server.properties` nastav
`resource-pack=<URL>`, `resource-pack-sha1=<sha1 souboru>` a případně `require-resource-pack=true`.
Konfiguraci `menu.resource-pack: true` zapínej jen tehdy, když pack hráči opravdu mají, jinak uvidí v titulku divné znaky.

**Vlastní textury:** nahraď PNG v `assets/customcraft/textures/item/`. Další zbraň přidáš tak, že vytvoříš model v
`assets/customcraft/models/item/` a soubor v `assets/minecraft/items/<vanilla_item>.json` s novým `custom-model-data`
(podle `netherite_sword.json`). Pozadí menu jsou obrázky 256×256 v `assets/customcraft/textures/font/`.

## Omezení

- Do **vanilla okna crafting stolu** nejde přidat druhé tlačítko pluginem (je to klientská část), proto se menu otevírá příkazem nebo shift-klikem na stůl.
- Řádek receptu má místo na 5 různých surovin.
- Menu loot tabulky zobrazí max. 45 položek na bednu.
- Pro vzhled přesně jako v ukázkovém obrázku (barevné pozadí, vlastní tlačítka) je potřeba resource pack s texturou menu.
