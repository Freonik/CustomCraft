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
 * Loads weapons and mystery crates from config.yml, builds their items
 * and registers their recipes.
 */
public final class ItemManager {

    public enum Kind { WEAPON, BOX }

    /** One possible reward in a mystery crate. */
    public record Loot(ItemStack stack, int weight, boolean broadcast) { }

    /** One material needed to forge an item in the menu. */
    public record Cost(String ref, ItemStack display, int amount) { }

    /** Max materials per recipe in the menu (what fits in a row). */
    public static final int MAX_COST = 5;

    /** Custom item (weapon or crate) including its recipe for the menu. */
    public static final class Entry {
        public final String id;
        public final Kind kind;
        public final ItemStack item;
        /** 3x3 recipe grid for the menu (null = empty slot). */
        public final ItemStack[] grid = new ItemStack[9];
        public NamespacedKey recipeKey;
        public List<Loot> loot = List.of();
        /** Materials for forging in the menu ("forge" section in the config). */
        public List<Cost> cost = List.of();
        /** Special abilities (weapons only). */
        public WeaponAbilities abilities = WeaponAbilities.NONE;

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

    // ---------------------------------------------------------------- loading

    public void load() {
        for (NamespacedKey key : registered) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();
        entries.clear();

        // Phase 1: build all items (ingredients may reference other custom items)
        loadSection("weapons", Kind.WEAPON);
        loadSection("boxes", Kind.BOX);

        // Phase 2: recipes and loot tables
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
                    throw new IllegalArgumentException("id may only contain a-z, 0-9 and _");
                }
                WeaponAbilities abilities = kind == Kind.WEAPON ? abilities(id, section) : WeaponAbilities.NONE;
                ItemStack item = kind == Kind.WEAPON ? buildWeapon(id, section, abilities) : buildBox(id, section);
                Entry entry = new Entry(id, kind, item);
                entry.abilities = abilities;
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

    // ---------------------------------------------------------------- items

    /**
     * Loads a weapon's abilities. If the weapon has no "abilities" section (older config),
     * the default abilities of the same weapon from the bundled config.yml are used.
     */
    private WeaponAbilities abilities(String id, ConfigurationSection s) {
        ConfigurationSection section = s.getKeys(false).contains("abilities") ? s.getConfigurationSection("abilities") : null;
        if (section == null && plugin.getConfig().getDefaults() != null) {
            section = plugin.getConfig().getDefaults().getConfigurationSection("weapons." + id + ".abilities");
        }
        try {
            return WeaponAbilities.parse(section);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("weapon:" + id + " abilities: " + ex.getMessage());
            return WeaponAbilities.NONE;
        }
    }

    private ItemStack buildWeapon(String id, ConfigurationSection s, WeaponAbilities abilities) {
        Material material = Material.matchMaterial(s.getString("material", "IRON_SWORD"));
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("unknown material '" + s.getString("material") + "'");
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        applyNameAndLore(meta, s, id);
        List<String> abilityLore = abilities.loreLines();
        if (!abilityLore.isEmpty()) {
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            abilityLore.forEach(line -> lore.add(color(line)));
            meta.lore(lore);
        }

        if (s.contains("custom-model-data")) {
            meta.setCustomModelData(s.getInt("custom-model-data"));
        }
        meta.setUnbreakable(s.getBoolean("unbreakable", false));

        ConfigurationSection enchants = s.getConfigurationSection("enchants");
        if (enchants != null) {
            for (String name : enchants.getKeys(false)) {
                Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
                if (enchantment == null) {
                    throw new IllegalArgumentException("unknown enchant '" + name + "'");
                }
                meta.addEnchant(enchantment, enchants.getInt(name), true);
            }
        }

        if (s.contains("attack-damage") || s.contains("attack-speed")) {
            // Players have base damage 1 and speed 4; the modifier is added to those.
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
            throw new IllegalArgumentException("unknown color '" + color + "'");
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

    // ---------------------------------------------------------------- recipes

    private void buildRecipe(Entry entry, ConfigurationSection recipeSection) {
        List<String> rows = recipeSection.getStringList("shape");
        if (rows.isEmpty() || rows.size() > 3) {
            throw new IllegalArgumentException("recipe.shape must have 1 to 3 rows");
        }
        int width = rows.stream().mapToInt(String::length).max().orElse(0);
        if (width < 1 || width > 3) {
            throw new IllegalArgumentException("rows in recipe.shape must have 1 to 3 characters");
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
                throw new IllegalArgumentException("missing ingredient for symbol '" + symbol + "'");
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
                throw new IllegalArgumentException("forge entry has no 'item'");
            }
            String ref = String.valueOf(itemValue);
            int amount = map.containsKey("amount") ? Integer.parseInt(String.valueOf(map.get("amount"))) : 1;
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be > 0 (" + ref + ")");
            }
            result.add(new Cost(ref, display(ref), amount));
        }
        if (result.size() > MAX_COST) {
            throw new IllegalArgumentException("forge may have at most " + MAX_COST + " materials");
        }
        return List.copyOf(result);
    }

    private List<Loot> buildLoot(ConfigurationSection s) {
        List<Loot> result = new ArrayList<>();
        for (Map<?, ?> map : s.getMapList("loot")) {
            Object itemValue = map.get("item");
            if (itemValue == null) {
                throw new IllegalArgumentException("loot entry has no 'item'");
            }
            ItemStack stack = display(String.valueOf(itemValue));
            int amount = map.containsKey("amount") ? Integer.parseInt(String.valueOf(map.get("amount"))) : 1;
            int weight = map.containsKey("weight") ? Integer.parseInt(String.valueOf(map.get("weight"))) : 1;
            boolean broadcast = map.containsKey("broadcast") && Boolean.parseBoolean(String.valueOf(map.get("broadcast")));
            if (weight <= 0) {
                throw new IllegalArgumentException("weight must be > 0 (" + itemValue + ")");
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
            throw new IllegalArgumentException("unknown item '" + name + "'");
        }
        return material;
    }

    // ---------------------------------------------------------------- queries

    public List<Entry> all(Kind kind) {
        return entries.values().stream().filter(e -> e.kind == kind).toList();
    }

    /** Finds a custom item by "weapon:id" / "box:id", or null. */
    public Entry get(String ref) {
        return ref == null ? null : entries.get(ref.toLowerCase(Locale.ROOT));
    }

    public List<String> refs() {
        return new ArrayList<>(entries.keySet());
    }

    public List<NamespacedKey> recipeKeys() {
        return List.copyOf(registered);
    }

    /** Returns "weapon:id" / "box:id" if the ItemStack is a custom item of this plugin, otherwise null. */
    public String idOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    /** Returns the crate if the ItemStack is a mystery crate, otherwise null. */
    public Entry boxOf(ItemStack stack) {
        String ref = idOf(stack);
        if (ref == null || !ref.startsWith("box:")) {
            return null;
        }
        return entries.get(ref);
    }

    // ---------------------------------------------------------------- materials (forge)

    /** Does the ItemStack match the material? Custom ones by id, vanilla by material. */
    public boolean matches(ItemStack stack, Cost cost) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        String id = idOf(stack);
        Entry custom = entries.get(cost.ref().toLowerCase(Locale.ROOT));
        if (custom != null) {
            return custom.ref().equals(id);
        }
        // Vanilla material: this plugin's custom items never count as plain material.
        return id == null && stack.getType() == cost.display().getType();
    }

    /** How much of the material the player carries. */
    public int count(PlayerInventory inventory, Cost cost) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (matches(stack, cost)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Does the player have all materials? A recipe without materials can't be forged. */
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

    /** Removes the materials from the inventory. Only call after canAfford succeeded. */
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

    // ---------------------------------------------------------------- helpers

    /** Converts &-colored text to a Component (without default italics). */
    public static Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Display name of an item (custom name, otherwise the material translation). */
    public static Component nameOf(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.displayName();
        }
        return Component.translatable(stack.getType().translationKey());
    }
}
