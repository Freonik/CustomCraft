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
    private BoxManager boxes;
    private AbilityManager abilities;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        items = new ItemManager(this);
        items.load();

        menus = new MenuManager(this, items);
        boxes = new BoxManager(this, items);

        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(boxes, this);
        abilities = new AbilityManager(this, items);
        getServer().getPluginManager().registerEvents(abilities, this);
        abilities.start();
        getServer().getPluginManager().registerEvents(this, this);

        RecipesCommand command = new RecipesCommand(this, items, menus);
        PluginCommand pluginCommand = getCommand("forge");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getLogger().info("Loaded " + items.all(ItemManager.Kind.WEAPON).size() + " weapons, "
                + items.all(ItemManager.Kind.BOX).size() + " mystery crates.");
    }

    @Override
    public void onDisable() {
        if (boxes != null) {
            boxes.finishAll();
        }
    }

    /** Reloads config.yml and re-registers recipes. */
    public void reload() {
        reloadConfig();
        items.load();
        if (getConfig().getBoolean("discover-recipes-on-join", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.discoverRecipes(items.recipeKeys());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("discover-recipes-on-join", true)) {
            event.getPlayer().discoverRecipes(items.recipeKeys());
        }
    }

    /**
     * Message from the config with the prefix. Placeholders are passed in pairs:
     * msg("reloaded", "default", "weapons", 3, "boxes", 2) replaces {weapons} and {boxes}.
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
