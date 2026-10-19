package cz.customcraft;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
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
public final class HudManager implements Listener, CommandExecutor {

    private final CustomCraft plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();
    private final File file;

    public HudManager(CustomCraft plugin) {
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
                List<String> header = plugin.getConfig().getStringList("hud.tab.header");
                List<String> footer = plugin.getConfig().getStringList("hud.tab.footer");
                p.sendPlayerListHeaderAndFooter(join(p, header), join(p, footer));
            }
        }
    }

    private Component join(Player p, List<String> lines) {
        Component c = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                c = c.append(Component.newline());
            }
            c = c.append(line(p, lines.get(i)));
        }
        return c;
    }

    private void sidebar(Player p) {
        Scoreboard board = boards.get(p.getUniqueId());
        if (board == null || p.getScoreboard() != board) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            boards.put(p.getUniqueId(), board);
            p.setScoreboard(board);
        }
        Objective obj = board.getObjective("cc_sidebar");
        if (obj == null) {
            obj = board.registerNewObjective("cc_sidebar", Criteria.DUMMY, Component.empty());
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
        }
        obj.displayName(line(p, plugin.getConfig().getString("hud.scoreboard.title", "&6&l⚒ CUSTOMCRAFT")));
        List<String> lines = plugin.getConfig().getStringList("hud.scoreboard.lines");
        int n = Math.min(15, lines.size());
        for (int i = 0; i < 15; i++) {
            String entry = "l" + i;
            if (i < n) {
                Score score = obj.getScore(entry);
                score.setScore(n - i);
                score.customName(line(p, lines.get(i)));
            } else if (obj.getScore(entry).isScoreSet()) {
                board.resetScores(entry);
            }
        }
    }

    private void removeSidebar(Player p) {
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
    }

    /** /hud: hide or show your own sidebar. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save hud.yml: " + ex.getMessage());
        }
        return true;
    }
}
