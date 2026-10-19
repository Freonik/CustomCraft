package cz.forgebound;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All Forgebound placeholders in one place. Used by the sidebar, tab list and join title as {name},
 * and by PlaceholderAPI as %forgebound_name% (for TAB, holograms, other plugins).
 *
 * tokens, bounty, legends_held, event, free_legends, total_legends, online, max_players, rotation, player,
 * top_<category>_<1-100>_name, top_<category>_<1-100>_value, top_<category>_rank
 */
public final class Placeholders {

    private static final Pattern BRACES = Pattern.compile("\\{([a-z0-9_]+)}");

    private final Forgebound plugin;
    /** Short cache: TAB and holograms ask many times a second. */
    private final java.util.Map<String, String> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Long> cachedAt = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_MS = 1000;
    private static final String NONE = "\u0000";

    public Placeholders(Forgebound plugin) {
        this.plugin = plugin;
    }

    /** Value of one placeholder, or null if it doesn't exist. Colors use & codes. Cached for a second. */
    public String value(OfflinePlayer who, String key) {
        key = key.toLowerCase(Locale.ROOT);
        String id = (who == null ? "-" : who.getUniqueId().toString()) + "|" + key;
        long now = System.currentTimeMillis();
        Long at = cachedAt.get(id);
        if (at != null && now - at < CACHE_MS) {
            String hit = cache.get(id);
            return hit == null || hit.equals(NONE) ? null : hit;
        }
        String v = compute(who, key);
        if (cache.size() > 5000) {
            cache.clear();
            cachedAt.clear();
        }
        cache.put(id, v == null ? NONE : v);
        cachedAt.put(id, now);
        return v;
    }

    private String compute(OfflinePlayer who, String key) {
        Player online = who == null ? null : who.getPlayer();
        switch (key) {
            case "tokens":
                return online == null || plugin.bounty() == null ? "0" : String.valueOf(plugin.bounty().countTokens(online));
            case "bounty":
                return who == null || plugin.bounty() == null ? "0" : String.valueOf(plugin.bounty().bountyOf(who.getUniqueId()));
            case "legends_held": {
                if (online == null) {
                    return "0";
                }
                int n = 0;
                for (ItemStack s : online.getInventory().getContents()) {
                    ItemManager.Entry e = plugin.items().customOf(s);
                    if (e != null && e.kind != ItemManager.Kind.MATERIAL) {
                        n++;
                    }
                }
                return String.valueOf(n);
            }
            case "event":
                return plugin.events() == null ? "&7-" : plugin.events().shortStatus();
            case "free_legends":
                return String.valueOf(legends()[0]);
            case "total_legends":
                return String.valueOf(legends()[1]);
            case "online":
                return String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max_players":
                return String.valueOf(Bukkit.getMaxPlayers());
            case "rotation":
                return plugin.shop() == null ? "-"
                        : RitualManager.format(plugin.shop().nextRotationAt() - System.currentTimeMillis());
            case "player":
                return who == null || who.getName() == null ? "" : who.getName();
            default:
                break;
        }
        if (key.startsWith("top_") && plugin.top() != null) {
            // top_<category>_<n>_name | top_<category>_<n>_value | top_<category>_rank
            String rest = key.substring(4);
            for (String cat : TopManager.CATEGORIES.keySet()) {
                if (!rest.startsWith(cat + "_")) {
                    continue;
                }
                String tail = rest.substring(cat.length() + 1);
                if (tail.equals("rank")) {
                    int r = who == null ? 0 : plugin.top().rank(cat, who.getUniqueId());
                    return r == 0 ? "-" : String.valueOf(r);
                }
                String[] parts = tail.split("_");
                if (parts.length != 2) {
                    return null;
                }
                int n;
                try {
                    n = Integer.parseInt(parts[0]);
                } catch (NumberFormatException ex) {
                    return null;
                }
                List<TopManager.Entry> list = plugin.top().board(cat);
                boolean has = n >= 1 && n <= list.size();
                return switch (parts[1]) {
                    case "name" -> has ? list.get(n - 1).name() : "-";
                    case "value" -> has ? TopManager.format(list.get(n - 1).value()) : "0";
                    default -> null;
                };
            }
        }
        return null;
    }

    /** [free, total] forgeable legends. */
    private int[] legends() {
        int total = 0;
        int free = 0;
        for (ItemManager.Kind kind : new ItemManager.Kind[] {ItemManager.Kind.WEAPON, ItemManager.Kind.RELIC}) {
            for (ItemManager.Entry e : plugin.items().all(kind)) {
                if (!plugin.items().craftable(e)) {
                    continue;
                }
                total++;
                if (plugin.rituals() == null || plugin.rituals().blocked(e) == null) {
                    free++;
                }
            }
        }
        return new int[] {free, total};
    }

    /** Replaces {name} placeholders (and %...% ones through PlaceholderAPI when it's installed). */
    public String apply(Player player, String text) {
        Matcher m = BRACES.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = value(player, m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group(0) : v));
        }
        m.appendTail(sb);
        String out = sb.toString();
        if (out.indexOf('%') >= 0 && plugin.papi()) {
            out = PapiExpansion.apply(player, out);
        }
        return out;
    }
}
