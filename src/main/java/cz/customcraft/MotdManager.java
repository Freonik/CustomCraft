package cz.customcraft;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.Map;

/**
 * Live MOTD in the server list: the first line stays, the second shows what's happening right now
 * (Rift, Blood Moon, meteor, Warden, a ritual, small events), or how many legends are still unclaimed.
 */
public final class MotdManager implements Listener {

    private final CustomCraft plugin;
    private final ItemManager items;

    public MotdManager(CustomCraft plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
    }

    private String line(String key, String def) {
        return plugin.getConfig().getString("motd." + key, def);
    }

    private Component text(String key, String def, Object... pairs) {
        Component result = ItemManager.color(line(key, def));
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            final String placeholder = "{" + pairs[i] + "}";
            final Component value = pairs[i + 1] instanceof Component c ? c : Component.text(String.valueOf(pairs[i + 1]));
            result = result.replaceText(b -> b.matchLiteral(placeholder).replacement(value));
        }
        return result;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(ServerListPingEvent event) {
        if (!plugin.getConfig().getBoolean("motd.enabled", true)) {
            return;
        }
        Component first = text("line1", "&6&l⚒ &e&lCUSTOMCRAFT &6&l⚒ &8| &7Paper 1.21.11");
        event.motd(first.append(Component.newline()).append(second()));
    }

    private Component second() {
        EventManager events = plugin.events();
        if (events != null && events.riftActive()) {
            return events.riftOpening()
                    ? text("rift-opening", "&5✷ A Rift is tearing the sky open...")
                    : text("rift", "&5✷ A Rift is open! &7Wave &d{wave}&7/&d{waves}",
                            "wave", events.riftWave(), "waves", events.riftWaves());
        }
        if (events != null && events.bloodMoon()) {
            return text("blood-moon", "&4☾ The Blood Moon has risen... &7({online} online)",
                    "online", Bukkit.getOnlinePlayers().size());
        }
        if (plugin.meteor() != null && plugin.meteor().active()) {
            return text("meteor", "&6☄ A meteor is falling! &7Its cache is up for grabs.");
        }
        if (plugin.warden() != null && plugin.warden().alive()) {
            return text("warden", "&4⚒ The Forge Warden is awake!");
        }
        RitualManager rituals = plugin.rituals();
        if (rituals != null) {
            for (Map.Entry<String, Long> r : rituals.runningSnapshot().entrySet()) {
                ItemManager.Entry entry = items.get(r.getKey());
                Component name = entry == null ? Component.text(r.getKey()) : ItemManager.nameOf(entry.item);
                return text("ritual", "&d⚒ Someone is forging {item}&d... &7{time} left",
                        "item", name, "time", RitualManager.format(r.getValue()));
            }
        }
        if (events != null && events.happyType() != null) {
            return text("happy-hour", "&a✦ Happy Hour: &f{what}", "what", events.happyWhat());
        }
        if (events != null && events.marketOpen()) {
            return text("forge-market", "&6⚖ The Forge Market is open! &7Special offers in /shop");
        }
        if (events != null && events.shipmentWaiting()) {
            return text("golden-shipment", "&e✉ A Golden Shipment is waiting to be found!");
        }
        int total = 0;
        int free = 0;
        for (ItemManager.Kind kind : new ItemManager.Kind[] {ItemManager.Kind.WEAPON, ItemManager.Kind.RELIC}) {
            for (ItemManager.Entry e : items.all(kind)) {
                if (!items.craftable(e)) {
                    continue;
                }
                total++;
                if (rituals == null || rituals.blocked(e) == null) {
                    free++;
                }
            }
        }
        return free == 0
                ? text("all-claimed", "&7Every legend has an owner. &cTake one from them.")
                : text("idle", "&7Forge a &dlegend&7. &f{free}&7/&f{total} &7are still unclaimed.",
                        "free", free, "total", total);
    }
}
