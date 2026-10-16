package cz.customcraft;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomCraft extends JavaPlugin implements Listener {

    private ItemManager items;
    private MenuManager menus;
    private RelicManager relics;
    private LootManager loot;
    private ForgeData data;
    private RitualManager rituals;
    private AbilityManager abilities;
    private AdvancementManager advancements;
    private LegendManager legends;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergeNewDefaults();

        items = new ItemManager(this);
        items.load();

        data = new ForgeData(this);
        data.load();
        rituals = new RitualManager(this, items, data);
        menus = new MenuManager(this, items, rituals);
        relics = new RelicManager(this, items);
        loot = new LootManager(this, items);
        loot.load();

        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(relics, this);
        getServer().getPluginManager().registerEvents(loot, this);
        relics.start();
        getServer().getPluginManager().registerEvents(rituals, this);
        rituals.start();
        abilities = new AbilityManager(this, items);
        abilities.setRelics(relics);
        getServer().getPluginManager().registerEvents(abilities, this);
        abilities.start();
        getServer().getPluginManager().registerEvents(this, this);
        legends = new LegendManager(this, items);
        getServer().getPluginManager().registerEvents(legends, this);
        legends.start();
        PluginCommand legendsCommand = getCommand("legends");
        if (legendsCommand != null) {
            legendsCommand.setExecutor(legends);
        }
        advancements = new AdvancementManager(this, items);
        getServer().getPluginManager().registerEvents(advancements, this);
        advancements.register();
        advancements.start();

        RecipesCommand command = new RecipesCommand(this, items, rituals, data);
        PluginCommand pluginCommand = getCommand("forge");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getLogger().info("Loaded " + items.all(ItemManager.Kind.WEAPON).size() + " weapons, "
                + items.all(ItemManager.Kind.RELIC).size() + " relics.");
    }

    @Override
    public void onDisable() {
        if (abilities != null) {
            abilities.shutdown();
        }
        if (legends != null) {
            legends.shutdown();
        }
        if (rituals != null) {
            rituals.shutdown();
        }
    }

    public RitualManager rituals() {
        return rituals;
    }

    /** Completes one of the plugin's advancements for a player (no-op if they're off). */
    public void award(org.bukkit.entity.Player player, String id) {
        if (advancements != null) {
            advancements.award(player, id);
        }
    }

    public LegendManager legends() {
        return legends;
    }

    /** Gives a freshly made legend its serial number (see LegendManager). */
    public void stamp(org.bukkit.inventory.ItemStack stack, String ref) {
        if (legends != null) {
            legends.stamp(stack, ref);
        }
    }

    public AdvancementManager advancements() {
        return advancements;
    }

    /** Reloads config.yml and re-registers recipes. */
    public void reload() {
        reloadConfig();
        mergeNewDefaults();
        items.load();
        loot.load();
        if (getConfig().getBoolean("discover-recipes-on-join", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.discoverRecipes(items.recipeKeys());
            }
        }
    }

    /** Sections where new default entries (new weapons, relics, materials, messages...) are added automatically. */
    private static final java.util.List<String> MERGED_SECTIONS = java.util.List.of("weapons", "relics", "materials",
            "craftable.weapons", "craftable.relics", "craftable.materials", "messages", "ability-settings", "relic-settings");

    /**
     * Copies entries that are new in this plugin version (e.g. a new weapon) into an existing config.yml,
     * so updating doesn't require deleting the config. config.yml is only rewritten when something is added.
     * Entries that were already offered once are remembered in defaults-seen.yml, so ones you delete
     * on purpose are never added back.
     */
    private void mergeNewDefaults() {
        org.bukkit.configuration.Configuration defaults = getConfig().getDefaults();
        if (defaults == null) {
            return;
        }
        java.io.File seenFile = new java.io.File(getDataFolder(), "defaults-seen.yml");
        org.bukkit.configuration.file.YamlConfiguration seenYaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(seenFile);
        java.util.Set<String> seen = new java.util.LinkedHashSet<>(seenYaml.getStringList("seen"));
        boolean firstRun = !seenFile.exists();
        java.util.List<String> added = new java.util.ArrayList<>();
        boolean seenChanged = false;
        for (String section : MERGED_SECTIONS) {
            org.bukkit.configuration.ConfigurationSection def = defaults.getConfigurationSection(section);
            if (def == null) {
                continue;
            }
            for (String key : def.getKeys(false)) {
                String path = section + "." + key;
                if (!getConfig().isSet(path) && !seen.contains(path)) {
                    Object value = plain(def.get(key));
                    if (value instanceof java.util.Map<?, ?> map) {
                        getConfig().createSection(path, map);
                    } else {
                        getConfig().set(path, value);
                    }
                    added.add(path);
                }
                seenChanged |= seen.add(path);
            }
        }
        if (!added.isEmpty()) {
            saveConfig();
            getLogger().info("Added new default config entries: " + String.join(", ", added));
        }
        if (seenChanged || firstRun) {
            seenYaml.options().setHeader(java.util.List.of(
                    "CustomCraft remembers which default config entries it already added to config.yml,",
                    "so entries you delete on purpose are not added back. Don't edit this file."));
            seenYaml.set("seen", new java.util.ArrayList<>(seen));
            try {
                seenYaml.save(seenFile);
            } catch (java.io.IOException ex) {
                getLogger().warning("Could not save defaults-seen.yml: " + ex.getMessage());
            }
        }
    }

    /** Config sections -> plain maps, so they are saved as normal YAML. */
    private static Object plain(Object value) {
        if (value instanceof org.bukkit.configuration.ConfigurationSection section) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                map.put(key, plain(section.get(key)));
            }
            return map;
        }
        return value;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("discover-recipes-on-join", true)) {
            event.getPlayer().discoverRecipes(items.recipeKeys());
        }
    }

    /**
     * Message from the config with the prefix. Placeholders are passed in pairs:
     * msg("reloaded", "default", "weapons", 3, "relics", 2) replaces {weapons} and {relics}.
     */
    public Component msg(String key, String def, Object... pairs) {
        return format(getConfig().getString("messages.prefix", ""), key, def, pairs);
    }

    /** Same as {@link #msg}, but without the prefix (for the action bar). */
    public Component raw(String key, String def, Object... pairs) {
        return format("", key, def, pairs);
    }

    private Component format(String prefix, String key, String def, Object... pairs) {
        String raw = getConfig().getString("messages." + key, def);
        Component result = ItemManager.color(prefix + raw);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            final String placeholder = "{" + pairs[i] + "}";
            final Component value = pairs[i + 1] instanceof Component component
                    ? component
                    : Component.text(String.valueOf(pairs[i + 1]));
            result = result.replaceText(b -> b.matchLiteral(placeholder).replacement(value));
        }
        return result;
    }
}
