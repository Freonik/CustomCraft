package cz.forgebound;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent server-wide state in plugins/Forgebound/data.yml:
 * which unique weapons already exist (or are being forged) and running rituals.
 */
public final class ForgeData {

    public enum State { RITUAL, FORGED }

    /** Who owns / is forging a unique weapon. */
    public record Claim(State state, UUID player, String name, long time, String source) { }

    /** A ritual saved across restarts. Remaining time only counts while the server runs. */
    public record SavedRitual(String weapon, String world, int x, int y, int z,
                              long remaining, long total, UUID player, String name) { }

    private final Forgebound plugin;
    private final File file;
    private final Map<String, Claim> claims = new LinkedHashMap<>();
    private final List<SavedRitual> rituals = new ArrayList<>();

    public ForgeData(Forgebound plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        claims.clear();
        rituals.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection c = yml.getConfigurationSection("claims");
        if (c != null) {
            for (String id : c.getKeys(false)) {
                ConfigurationSection s = c.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                try {
                    claims.put(ref(id.replace("__", ":")), new Claim(State.valueOf(s.getString("state", "FORGED")),
                            UUID.fromString(s.getString("player", "")), s.getString("name", "?"),
                            s.getLong("time"), s.getString("source", "forge")));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("data.yml: bad claim for " + id + ": " + ex.getMessage());
                }
            }
        }
        ConfigurationSection r = yml.getConfigurationSection("rituals");
        if (r != null) {
            for (String id : r.getKeys(false)) {
                ConfigurationSection s = r.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                try {
                    rituals.add(new SavedRitual(ref(id.replace("__", ":")), s.getString("world"), s.getInt("x"), s.getInt("y"), s.getInt("z"),
                            s.getLong("remaining"), s.getLong("total"), UUID.fromString(s.getString("player", "")),
                            s.getString("name", "?")));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("data.yml: bad ritual for " + id + ": " + ex.getMessage());
                }
            }
        }
    }

    /** Older data.yml files used plain weapon ids ("echo_blade"); now keys are refs ("weapon:echo_blade"). */
    private static String ref(String key) {
        return key.contains(":") ? key : "weapon:" + key;
    }

    /** Writes claims and the given running rituals to data.yml. */
    public void save(Collection<SavedRitual> running) {
        rituals.clear();
        rituals.addAll(running);
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Claim> e : claims.entrySet()) {
            String p = "claims." + e.getKey().replace(":", "__") + ".";
            Claim claim = e.getValue();
            yml.set(p + "state", claim.state().name());
            yml.set(p + "player", claim.player().toString());
            yml.set(p + "name", claim.name());
            yml.set(p + "time", claim.time());
            yml.set(p + "source", claim.source());
        }
        for (SavedRitual ritual : running) {
            String p = "rituals." + ritual.weapon().replace(":", "__") + ".";
            yml.set(p + "world", ritual.world());
            yml.set(p + "x", ritual.x());
            yml.set(p + "y", ritual.y());
            yml.set(p + "z", ritual.z());
            yml.set(p + "remaining", ritual.remaining());
            yml.set(p + "total", ritual.total());
            yml.set(p + "player", ritual.player().toString());
            yml.set(p + "name", ritual.name());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }

    public Claim claim(String weapon) {
        return claims.get(weapon);
    }

    public void setClaim(String weapon, Claim claim) {
        claims.put(weapon, claim);
    }

    public Claim release(String weapon) {
        return claims.remove(weapon);
    }

    public Map<String, Claim> claims() {
        return claims;
    }

    /** Rituals loaded from disk (only meaningful right after {@link #load()}). */
    public List<SavedRitual> savedRituals() {
        return List.copyOf(rituals);
    }
}
