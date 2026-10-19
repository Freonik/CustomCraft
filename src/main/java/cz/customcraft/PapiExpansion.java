package cz.customcraft;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI expansion: %customcraft_<placeholder>%. Only loaded when PlaceholderAPI is installed.
 */
public final class PapiExpansion extends PlaceholderExpansion {

    private final CustomCraft plugin;

    public PapiExpansion(CustomCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "customcraft";
    }

    @Override
    public String getAuthor() {
        return "Freonik";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return plugin.placeholders().value(player, params);
    }

    static String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
