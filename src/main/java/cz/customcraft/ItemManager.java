package cz.customcraft;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Načítá zbraně a mystery boxy z config.yml, vyrábí z nich itemy
 * a registruje jejich recepty.
 */
public final class ItemManager {

    public enum Kind { WEAPON, BOX }

    /** Jedna možná výhra v mystery boxu. */
    public record Loot(ItemStack stack, int weight, boolean broadcast) { }

    /** Jedna surovina potřebná k výrobě v menu (kovárně). */
    public record Cost(String ref, ItemStack display, int amount) { }

    /** Nejvíc surovin na jeden recept v menu (tolik se jich vejde na řádek). */
    public static final int MAX_COST = 5;

    /** Custom item (zbraň nebo box) včetně receptu pro zobrazení v menu. */
    public static final class Entry {
        public final String id;
        public final Kind kind;
        public final ItemStack item;
        /** 3x3 mřížka receptu pro menu (null = prázdné políčko). */
        public final ItemStack[] grid = new ItemStack[9];
        public NamespacedKey recipeKey;
        public List<Loot> loot = List.of();
        /** Suroviny pro výrobu v menu (sekce "forge" v configu). */
        public List<Cost> cost = List.of();

        Entry(String id, Kind kind, ItemStack item) {
            this.id = id;
            this.kind = kind;
            this.item = item;
        }

        public String ref() {
            return (kind == Kind.WEAPON ? "weapon:" : "box:") + id;
        }
    }

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_]+");

    private final CustomCraft plugin;
    private final NamespacedKey idKey;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<NamespacedKey> registered = new ArrayList<>();

    public ItemManager(CustomCraft plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "item_id");
    }

    // ---------------------------------------------------------------- načítání

    public void load() {
        for (NamespacedKey key : registered) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();
        entries.clear();

        // Fáze 1: vyrobit všechny itemy (ingredience mohou odkazovat na jiné custom itemy)
        loadSection("weapons", Kind.WEAPON);
        loadSection("boxes", Kind.BOX);

        // Fáze 2: recepty a loot tabulky
        for (Entry entry : entries.values()) {
            ConfigurationSection section = section(entry);
            if (section == null) {
                continue;
            }
            try {
                entry.cost = buildCost(section);
                ConfigurationSection recipe = section.getConfigurationSection("recipe");
                if (recipe != null) {
                    buildRecipe(entry, recipe);
                }
                if (entry.kind == Kind.BOX) {
                    entry.loot = buildLoot(section);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning(entry.ref() + ": " + ex.getMessage());
            }
        }
    }

    private void loadSection(String path, Kind kind) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection(path);
        if (root == null) {
            return;
        }
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }
            try {
                String id = rawId.toLowerCase(Locale.ROOT);
                if (!VALID_ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("id smí obsahovat jen a-z, 0-9 a _");
                }
                ItemStack item = kind == Kind.WEAPON ? buildWeapon(id, section) : buildBox(id, section);
                Entry entry = new Entry(id, kind, item);
                entries.put(entry.ref(), entry);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning((kind == Kind.WEAPON ? "weapon:" : "box:") + rawId + ": " + ex.getMessage());
            }
        }
    }

    private ConfigurationSection section(Entry entry) {
        String root = entry.kind == Kind.WEAPON ? "weapons" : "boxes";
        ConfigurationSection rootSection = plugin.getConfig().getConfigurationSection(root);
        if (rootSection == null) {
            return null;
        }
        for (String key : rootSection.getKeys(false)) {
            if (key.equalsIgnoreCase(entry.id)) {
                return rootSection.getConfigurationSection(key);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- itemy

    private ItemStack buildWeapon(String id, ConfigurationSection s) {
        Material material = Material.matchMaterial(s.getString("material", "IRON_SWORD"));
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("neznámý materiál '" + s.getString("material") + "'");
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        applyNameAndLore(meta, s, id);

        if (s.contains("custom-model-data")) {
            meta.setCustomModelData(s.getInt("custom-model-data"));
        }
        meta.setUnbreakable(s.getBoolean("unbreakable", false));

        ConfigurationSection enchants = s.getConfigurationSection("enchants");
        if (enchants != null) {
            for (String name : enchants.getKeys(false)) {
                Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
                if (enchantment == null) {
                    throw new IllegalArgumentException("neznámý enchant '" + name + "'");
                }
                meta.addEnchant(enchantment, enchants.getInt(name), true);
            }
        }

        if (s.contains("attack-damage") || s.contains("attack-speed")) {
            // Hráč má základní damage 1 a speed 4, modifikátor se přičítá k nim.
            double damage = s.getDouble("attack-damage", 7.0);
            double speed = s.getDouble("attack-speed", 1.6);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey(plugin, id + "_damage"), damage - 1.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey(plugin, id + "_speed"), speed - 4.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }

        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, "weapon:" + id);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildBox(String id, ConfigurationSection s) {
        String color = s.getString("color", "NONE").toUpperCase(Locale.ROOT);
        Material material = color.equals("NONE") || color.isEmpty()
                ? Material.SHULKER_BOX
                : Material.matchMaterial(color + "_SHULKER_BOX");
        if (material == null) {
            throw new IllegalArgumentException("neznámá barva '" + color + "'");
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        applyNameAndLore(meta, s, id);
        if (s.contains("custom-model-data")) {
            meta.setCustomModelData(s.getInt("custom-model-data"));
        }

        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, "box:" + id);
        stack.setItemMeta(meta);
        return stack;
    }

    private void applyNameAndLore(ItemMeta meta, ConfigurationSection s, String fallbackName) {
        meta.displayName(color(s.getString("name", fallbackName)));
        List<String> lore = s.getStringList("lore");
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(ItemManager::color).toList());
        }
    }

    // ---------------------------------------------------------------- recepty

    private void buildRecipe(Entry entry, ConfigurationSection recipeSection) {
        List<String> rows = recipeSection.getStringList("shape");
        if (rows.isEmpty() || rows.size() > 3) {
            throw new IllegalArgumentException("recipe.shape musí mít 1 až 3 řádky");
        }
        int width = rows.stream().mapToInt(String::length).max().orElse(0);
        if (width < 1 || width > 3) {
            throw new IllegalArgumentException("řádky v recipe.shape musí mít 1 až 3 znaky");
        }
        String[] shape = rows.stream().map(r -> String.format("%-" + width + "s", r)).toArray(String[]::new);

        ConfigurationSection ingredients = recipeSection.getConfigurationSection("ingredients");
        NamespacedKey key = new NamespacedKey(plugin, entry.kind.name().toLowerCase(Locale.ROOT) + "_" + entry.id);
        ShapedRecipe recipe = new ShapedRecipe(key, entry.item.clone());
        recipe.shape(shape);

        Set<Character> symbols = new LinkedHashSet<>();
        for (String row : shape) {
            for (char c : row.toCharArray()) {
                if (c != ' ') {
                    symbols.add(c);
                }
            }
        }
        for (char symbol : symbols) {
            String ref = ingredients == null ? null : ingredients.getString(String.valueOf(symbol));
            if (ref == null) {
                throw new IllegalArgumentException("chybí ingredience pro znak '" + symbol + "'");
            }
            recipe.setIngredient(symbol, choice(ref));
        }

        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < width; c++) {
                char symbol = shape[r].charAt(c);
                if (symbol != ' ') {
                    entry.grid[r * 3 + c] = display(ingredients.getString(String.valueOf(symbol)));
                }
            }
        }

        Bukkit.removeRecipe(key);
        Bukkit.addRecipe(recipe);
        registered.add(key);
        entry.recipeKey = key;
    }

    private List<Cost> buildCost(ConfigurationSection s) {
        List<Cost> result = new ArrayList<>();
        for (Map<?, ?> map : s.getMapList("forge")) {
            Object itemValue = map.get("item");
            if (itemValue == null) {
                throw new IllegalArgumentException("forge položka nemá 'item'");
            }
            String ref = String.valueOf(itemValue);
            int amount = map.containsKey("amount") ? Integer.parseInt(String.valueOf(map.get("amount"))) : 1;
            if (amount <= 0) {
                throw new IllegalArgumentException("amount musí být > 0 (" + ref + ")");
            }
            result.add(new Cost(ref, display(ref), amount));
        }
        if (result.size() > MAX_COST) {
            throw new IllegalArgumentException("forge smí mít nejvýš " + MAX_COST + " surovin");
        }
        return List.copyOf(result);
    }

    private List<Loot> buildLoot(ConfigurationSection s) {
        List<Loot> result = new ArrayList<>();
        for (Map<?, ?> map : s.getMapList("loot")) {
            Object itemValue = map.get("item");
            if (itemValue == null) {
                throw new IllegalArgumentException("loot položka nemá 'item'");
            }
            ItemStack stack = display(String.valueOf(itemValue));
            int amount = map.containsKey("amount") ? Integer.parseInt(String.valueOf(map.get("amount"))) : 1;
            int weight = map.containsKey("weight") ? Integer.parseInt(String.valueOf(map.get("weight"))) : 1;
            boolean broadcast = map.containsKey("broadcast") && Boolean.parseBoolean(String.valueOf(map.get("broadcast")));
            if (weight <= 0) {
                throw new IllegalArgumentException("weight musí být > 0 (" + itemValue + ")");
            }
            stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
            result.add(new Loot(stack, weight, broadcast));
        }
        return result;
    }

    private RecipeChoice choice(String ref) {
        Entry custom = entries.get(ref.toLowerCase(Locale.ROOT));
        if (custom != null) {
            return new RecipeChoice.ExactChoice(custom.item.clone());
        }
        return new RecipeChoice.MaterialChoice(material(ref));
    }

    private ItemStack display(String ref) {
        Entry custom = entries.get(ref.toLowerCase(Locale.ROOT));
        if (custom != null) {
            return custom.item.clone();
        }
        return new ItemStack(material(ref));
    }

    private Material material(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("neznámý item '" + name + "'");
        }
        return material;
    }

    // ---------------------------------------------------------------- dotazy

    public List<Entry> all(Kind kind) {
        return entries.values().stream().filter(e -> e.kind == kind).toList();
    }

    /** Najde custom item podle "weapon:id" / "box:id", případně null. */
    public Entry get(String ref) {
        return ref == null ? null : entries.get(ref.toLowerCase(Locale.ROOT));
    }

    public List<String> refs() {
        return new ArrayList<>(entries.keySet());
    }

    public List<NamespacedKey> recipeKeys() {
        return List.copyOf(registered);
    }

    /** Vrátí "weapon:id" / "box:id", pokud je ItemStack custom item pluginu, jinak null. */
    public String idOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    /** Vrátí box, pokud je daný ItemStack mystery box, jinak null. */
    public Entry boxOf(ItemStack stack) {
        String ref = idOf(stack);
        if (ref == null || !ref.startsWith("box:")) {
            return null;
        }
        return entries.get(ref);
    }

    // ---------------------------------------------------------------- suroviny (kovárna)

    /** Sedí daný ItemStack na surovinu? Custom suroviny podle id, vanilla podle materiálu. */
    public boolean matches(ItemStack stack, Cost cost) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        String id = idOf(stack);
        Entry custom = entries.get(cost.ref().toLowerCase(Locale.ROOT));
        if (custom != null) {
            return custom.ref().equals(id);
        }
        // Vanilla surovina: custom itemy pluginu se jako obyčejný materiál nepočítají.
        return id == null && stack.getType() == cost.display().getType();
    }

    /** Kolik dané suroviny hráč nese. */
    public int count(PlayerInventory inventory, Cost cost) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (matches(stack, cost)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Má hráč všechny suroviny? Recept bez surovin se vyrobit nedá. */
    public boolean canAfford(PlayerInventory inventory, Entry entry) {
        if (entry.cost.isEmpty()) {
            return false;
        }
        for (Cost cost : entry.cost) {
            if (count(inventory, cost) < cost.amount()) {
                return false;
            }
        }
        return true;
    }

    /** Odebere suroviny z inventáře. Volat jen po úspěšném canAfford. */
    public void take(PlayerInventory inventory, Entry entry) {
        ItemStack[] contents = inventory.getStorageContents();
        for (Cost cost : entry.cost) {
            int need = cost.amount();
            for (int i = 0; i < contents.length && need > 0; i++) {
                ItemStack stack = contents[i];
                if (!matches(stack, cost)) {
                    continue;
                }
                int remove = Math.min(need, stack.getAmount());
                need -= remove;
                if (remove == stack.getAmount()) {
                    contents[i] = null;
                } else {
                    stack.setAmount(stack.getAmount() - remove);
                }
            }
        }
        inventory.setStorageContents(contents);
    }

    // ---------------------------------------------------------------- pomocné

    /** Převod textu s &-barvami na Component (bez výchozí kurzívy). */
    public static Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Zobrazované jméno itemu (custom jméno, jinak překlad materiálu). */
    public static Component nameOf(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.displayName();
        }
        return Component.translatable(stack.getType().translationKey());
    }
}
