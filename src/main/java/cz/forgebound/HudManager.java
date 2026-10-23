package cz.forgebound;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sidebar scoreboard, tab list header/footer and a title on join. Each part can be switched off, and the
 * sidebar and tab list step aside when the TAB plugin is installed (use the placeholders there instead).
 * Players can hide their own sidebar with /hud.
 */
public final class HudManager implements Listener {

    private final Forgebound plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();
    /** What each player was last sent, so unchanged lines aren't sent again. */
    private final Map<UUID, Map<String, String>> sent = new HashMap<>();
    private final File file;

    public HudManager(Forgebound plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "hud.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String s : yaml.getStringList("hidden")) {
            try {
                hidden.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
    }

    private boolean tabPlugin() {
        return Bukkit.getPluginManager().isPluginEnabled("TAB");
    }

    private boolean sidebarOn() {
        return plugin.getConfig().getBoolean("hud.scoreboard.enabled", true)
                && !(tabPlugin() && plugin.getConfig().getBoolean("hud.scoreboard.disable-with-tab-plugin", true));
    }

    private boolean tabOn() {
        return plugin.getConfig().getBoolean("hud.tab.enabled", true)
                && !(tabPlugin() && plugin.getConfig().getBoolean("hud.tab.disable-with-tab-plugin", true));
    }

    public void start() {
        long period = Math.max(10L, plugin.getConfig().getLong("hud.update-ticks", 40));
        new BukkitRunnable() {
            @Override
            public void run() {
                update();
            }
        }.runTaskTimer(plugin, 40L, period);
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            removeSidebar(p);
        }
    }

    private Component line(Player player, String raw) {
        return ItemManager.color(plugin.placeholders().apply(player, raw));
    }

    private void update() {
        boolean side = sidebarOn();
        boolean tab = tabOn();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (side && !hidden.contains(p.getUniqueId())) {
                sidebar(p);
            } else {
                removeSidebar(p);
            }
            if (tab) {
                String header = joinRaw(p, plugin.getConfig().getStringList("hud.tab.header"));
                String footer = joinRaw(p, plugin.getConfig().getStringList("hud.tab.footer"));
                if (changed(p, "tab", header + "\u0000" + footer)) {
                    p.sendPlayerListHeaderAndFooter(ItemManager.color(header), ItemManager.color(footer));
                }
            }
        }
    }

    private String joinRaw(Player p, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(plugin.placeholders().apply(p, lines.get(i)));
        }
        return sb.toString();
    }

    /** True (and remembered) if this part of the player's HUD differs from what was sent last time. */
    private boolean changed(Player p, String part, String text) {
        Map<String, String> mine = sent.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        return !text.equals(mine.put(part, text));
    }

    private void sidebar(Player p) {
        Scoreboard board = boards.get(p.getUniqueId());
        if (board == null || p.getScoreboard() != board) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            boards.put(p.getUniqueId(), board);
            p.setScoreboard(board);
            Map<String, String> mine = sent.get(p.getUniqueId());
            if (mine != null) {
                mine.keySet().removeIf(k -> k.startsWith("side"));   // a new board needs everything again
            }
        }
        mirrorTeams(board);
        Objective obj = board.getObjective("cc_sidebar");
        if (obj == null) {
            obj = board.registerNewObjective("cc_sidebar", Criteria.DUMMY, Component.empty());
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
        }
        String title = plugin.placeholders().apply(p, plugin.getConfig().getString("hud.scoreboard.title", "&6&l⚒ FORGEBOUND"));
        if (changed(p, "side-title", title)) {
            obj.displayName(ItemManager.color(title));
        }
        List<String> lines = plugin.getConfig().getStringList("hud.scoreboard.lines");
        int n = Math.min(15, lines.size());
        for (int i = 0; i < 15; i++) {
            String entry = "l" + i;
            if (i < n) {
                String text = plugin.placeholders().apply(p, lines.get(i));
                if (changed(p, "side" + i, (n - i) + "|" + text)) {
                    Score score = obj.getScore(entry);
                    score.setScore(n - i);
                    score.customName(ItemManager.color(text));
                }
            } else if (obj.getScore(entry).isScoreSet()) {
                board.resetScores(entry);
                Map<String, String> mine = sent.get(p.getUniqueId());
                if (mine != null) {
                    mine.remove("side" + i);
                }
            }
        }
    }

    /**
     * The sidebar needs its own scoreboard, which would hide the teams of the main one (tags and colors in the tab
     * list, above heads and in chat - e.g. from Forgebound Teams or /team). So they're copied over.
     */
    private static void mirrorTeams(Scoreboard board) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (org.bukkit.scoreboard.Team own : new java.util.ArrayList<>(board.getTeams())) {
            if (main.getTeam(own.getName()) == null) {
                own.unregister();
            }
        }
        for (org.bukkit.scoreboard.Team t : main.getTeams()) {
            org.bukkit.scoreboard.Team copy = board.getTeam(t.getName());
            if (copy == null) {
                copy = board.registerNewTeam(t.getName());
            }
            if (!t.prefix().equals(copy.prefix())) {
                copy.prefix(t.prefix());
            }
            if (!t.suffix().equals(copy.suffix())) {
                copy.suffix(t.suffix());
            }
            if (!t.displayName().equals(copy.displayName())) {
                copy.displayName(t.displayName());
            }
            if (t.hasColor() && (!copy.hasColor() || copy.color() != t.color())) {
                copy.color(net.kyori.adventure.text.format.NamedTextColor.nearestTo(t.color()));
            }
            if (copy.allowFriendlyFire() != t.allowFriendlyFire()) {
                copy.setAllowFriendlyFire(t.allowFriendlyFire());
            }
            if (copy.canSeeFriendlyInvisibles() != t.canSeeFriendlyInvisibles()) {
                copy.setCanSeeFriendlyInvisibles(t.canSeeFriendlyInvisibles());
            }
            for (org.bukkit.scoreboard.Team.Option o : org.bukkit.scoreboard.Team.Option.values()) {
                if (copy.getOption(o) != t.getOption(o)) {
                    copy.setOption(o, t.getOption(o));
                }
            }
            java.util.Set<String> want = t.getEntries();
            for (String e : copy.getEntries()) {
                if (!want.contains(e)) {
                    copy.removeEntry(e);
                }
            }
            for (String e : want) {
                if (!copy.hasEntry(e)) {
                    copy.addEntry(e);
                }
            }
        }
    }

    private void removeSidebar(Player p) {
        Map<String, String> mine = sent.get(p.getUniqueId());
        if (mine != null) {
            mine.keySet().removeIf(k -> k.startsWith("side"));
        }
        Scoreboard board = boards.remove(p.getUniqueId());
        if (board != null && p.getScoreboard() == board) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getConfig().getBoolean("hud.join-title.enabled", true)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            boolean first = !p.hasPlayedBefore();
            String t = plugin.getConfig().getString(first ? "hud.join-title.first-title" : "hud.join-title.title", "");
            String s = plugin.getConfig().getString(first ? "hud.join-title.first-subtitle" : "hud.join-title.subtitle", "");
            p.showTitle(Title.title(line(p, t), line(p, s),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000))));
        }, Math.max(1L, plugin.getConfig().getLong("hud.join-title.delay-ticks", 30)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
        sent.remove(event.getPlayer().getUniqueId());
    }

    /** /hud: hide or show your own sidebar. */
    public boolean onCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("player-only", "&cOnly players can use this command."));
            return true;
        }
        if (!sidebarOn()) {
            p.sendMessage(plugin.msg("hud-off", "&7The sidebar is turned off on this server."));
            return true;
        }
        if (!hidden.remove(p.getUniqueId())) {
            hidden.add(p.getUniqueId());
            removeSidebar(p);
            p.sendMessage(plugin.msg("hud-hidden", "&7Sidebar hidden. &e/hud &7shows it again."));
        } else {
            p.sendMessage(plugin.msg("hud-shown", "&aSidebar shown."));
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("hidden", hidden.stream().map(UUID::toString).toList());
        plugin.saveYaml(yaml, file);
        return true;
    }
}
