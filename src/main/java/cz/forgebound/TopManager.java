package cz.forgebound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Leaderboards (/top) and the records they need (Wardens slain, Rifts closed, Legend Hunts survived).
 * The lists are rebuilt every minute and are also available as placeholders for holograms.
 */
public final class TopManager implements CommandExecutor, TabCompleter {

    /** category -> display name */
    public static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("bounty", "Most wanted (bounty)");
        CATEGORIES.put("kills", "Player kills with legends");
        CATEGORIES.put("mobkills", "Mob kills with legends");
        CATEGORIES.put("damage", "Damage dealt with legends");
        CATEGORIES.put("wardens", "Forge Wardens slain");
        CATEGORIES.put("rifts", "Rifts closed");
        CATEGORIES.put("hunts", "Legend Hunts survived");
        CATEGORIES.put("ghosts", "Doppelgangers destroyed");
        CATEGORIES.put("assists", "Recon assists");
    }

    public record Entry(UUID uuid, String name, double value) {
    }

    private final Forgebound plugin;
    private final File file;
    private final YamlConfiguration records;
    private final Map<String, List<Entry>> boards = new HashMap<>();

    public TopManager(Forgebound plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "records.yml");
        this.records = YamlConfiguration.loadConfiguration(file);
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                rebuild();
            }
        }.runTaskTimer(plugin, 100L, Math.max(200L, plugin.getConfig().getLong("top.update-seconds", 60) * 20));
    }

    /** +1 to one of the records (wardens, rifts, hunts). */
    public void add(Player player, String record) {
        String path = player.getUniqueId() + "." + record;
        records.set(path, records.getInt(path, 0) + 1);
        records.set(player.getUniqueId() + ".name", player.getName());
        plugin.saveYaml(records, file);
    }

    private Map<UUID, Double> record(String key) {
        Map<UUID, Double> map = new HashMap<>();
        for (String k : records.getKeys(false)) {
            int v = records.getInt(k + "." + key, 0);
            if (v > 0) {
                try {
                    map.put(UUID.fromString(k), (double) v);
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        }
        return map;
    }

    private String nameOf(UUID uuid) {
        String saved = records.getString(uuid + ".name");
        if (saved != null) {
            return saved;
        }
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        return p.getName() == null ? "?" : p.getName();
    }

    public void rebuild() {
        for (String cat : CATEGORIES.keySet()) {
            Map<UUID, Double> values = switch (cat) {
                case "bounty" -> plugin.bounty() == null ? Map.of() : plugin.bounty().allBounties();
                case "kills" -> plugin.legends() == null ? Map.of() : plugin.legends().totals("kills");
                case "mobkills" -> plugin.legends() == null ? Map.of() : plugin.legends().totals("mob-kills");
                case "damage" -> plugin.legends() == null ? Map.of() : plugin.legends().totals("damage");
                default -> record(cat);
            };
            List<Entry> list = new ArrayList<>();
            values.forEach((uuid, v) -> list.add(new Entry(uuid, nameOf(uuid), v)));
            list.sort((a, b) -> Double.compare(b.value(), a.value()));
            boards.put(cat, list.size() > 100 ? new ArrayList<>(list.subList(0, 100)) : list);
        }
    }

    public List<Entry> board(String category) {
        if (boards.isEmpty()) {
            rebuild();
        }
        return boards.getOrDefault(category, List.of());
    }

    /** 1-based rank of a player in a category, or 0. */
    public int rank(String category, UUID uuid) {
        List<Entry> list = board(category);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid().equals(uuid)) {
                return i + 1;
            }
        }
        return 0;
    }

    public static String format(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.0f", value);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("top.enabled", true)) {
            sender.sendMessage(plugin.msg("top-disabled", "&7Leaderboards are turned off on this server."));
            return true;
        }
        String cat = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "bounty";
        if (!CATEGORIES.containsKey(cat)) {
            sender.sendMessage(plugin.msg("top-usage", "&7Usage: &e/top <{categories}>",
                    "categories", String.join("|", CATEGORIES.keySet())));
            return true;
        }
        List<Entry> list = board(cat);
        sender.sendMessage(ItemManager.color("&6&l🏆 " + CATEGORIES.get(cat)));
        if (list.isEmpty()) {
            sender.sendMessage(ItemManager.color("&7Nobody yet."));
        }
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            Entry e = list.get(i);
            String color = i == 0 ? "&6" : i == 1 ? "&f" : i == 2 ? "&c" : "&7";
            sender.sendMessage(ItemManager.color(color + "#" + (i + 1) + " &f" + e.name() + " &8- &e" + format(e.value())));
        }
        if (sender instanceof Player p) {
            int r = rank(cat, p.getUniqueId());
            if (r > 10) {
                sender.sendMessage(ItemManager.color("&8...  &7You: &f#" + r));
            }
        }
        // clickable list of the other categories
        Component other = ItemManager.color("&8» ");
        for (String c : CATEGORIES.keySet()) {
            Component part = ItemManager.color((c.equals(cat) ? "&e&n" : "&7") + c)
                    .clickEvent(ClickEvent.runCommand("/top " + c));
            other = other.append(part).append(Component.text(" "));
        }
        sender.sendMessage(other);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return CATEGORIES.keySet().stream().filter(c -> c.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
