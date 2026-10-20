package cz.forgebound;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Legend Hunt: a random online legend holder is marked for an hour. They glow, their area is revealed every few
 * minutes and their bounty counts double, but their abilities recharge faster. Survive = tokens; get killed =
 * the killer gets a bonus.
 */
public final class LegendHunt implements Listener {

    private final Forgebound plugin;
    private UUID target;
    private String targetName;
    private long endsAt;
    private long nextRevealAt;
    private int withoutLegend;
    private BossBar bar;
    private String area = "";

    public LegendHunt(Forgebound plugin) {
        this.plugin = plugin;
    }

    private double cfg(String key, double def) {
        return plugin.getConfig().getDouble("events.legend-hunt." + key, def);
    }

    public boolean active() {
        return target != null;
    }

    public boolean isTarget(Player player) {
        return target != null && target.equals(player.getUniqueId());
    }

    public String targetName() {
        return targetName;
    }

    public long remaining() {
        return Math.max(0, endsAt - System.currentTimeMillis());
    }

    private static boolean survival(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    /** Starts a hunt on a random online legend holder (or on "forced"). */
    public boolean begin(Player forced) {
        if (active() || plugin.bounty() == null) {
            return false;
        }
        Player chosen = forced;
        if (chosen == null) {
            List<Player> holders = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (survival(p) && plugin.bounty().carriesLegend(p)) {
                    holders.add(p);
                }
            }
            if (holders.isEmpty()) {
                return false;
            }
            chosen = holders.get(ThreadLocalRandom.current().nextInt(holders.size()));
        }
        target = chosen.getUniqueId();
        targetName = chosen.getName();
        endsAt = System.currentTimeMillis() + (long) (cfg("duration-minutes", 60) * 60_000L);
        nextRevealAt = 0;
        withoutLegend = 0;
        chosen.setGlowing(true);
        bar = BossBar.bossBar(ItemManager.color("&c☠ Legend Hunt"), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        Bukkit.broadcast(plugin.msg("hunt-start",
                "&c☠ &4&lLegend Hunt! &f{player} &cis marked for &f{minutes} minutes&c. Their bounty counts &6double&c. "
                        + "Hunt them down!",
                "player", targetName, "minutes", String.valueOf((int) cfg("duration-minutes", 60))));
        chosen.sendMessage(plugin.msg("hunt-you",
                "&c☠ You are being hunted! &7Survive for {minutes} minutes and you get &6{tokens} Bounty Tokens&7. "
                        + "Your abilities recharge faster until then.",
                "minutes", String.valueOf((int) cfg("duration-minutes", 60)),
                "tokens", String.valueOf((int) cfg("survive-tokens", 8))));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1f);
        }
        return true;
    }

    /** Every second, from EventManager. */
    public void tick() {
        if (!active()) {
            return;
        }
        Player p = Bukkit.getPlayer(target);
        if (p == null) {
            return;                                               // handled in onQuit
        }
        long now = System.currentTimeMillis();
        if (now >= endsAt) {
            survived(p);
            return;
        }
        // hiding the legend in a chest doesn't count
        withoutLegend = plugin.bounty().carriesLegend(p) ? 0 : withoutLegend + 1;
        if (withoutLegend > 30) {
            Bukkit.broadcast(plugin.msg("hunt-hid", "&7☠ {player} hid their legend. The hunt is off.", "player", targetName));
            end();
            return;
        }
        if (!p.isGlowing()) {
            p.setGlowing(true);
        }
        if (now >= nextRevealAt) {
            nextRevealAt = now + (long) (cfg("reveal-minutes", 5) * 60_000L);
            int radius = (int) cfg("reveal-radius", 100);
            int x = p.getLocation().getBlockX() + ThreadLocalRandom.current().nextInt(-radius / 2, radius / 2 + 1);
            int z = p.getLocation().getBlockZ() + ThreadLocalRandom.current().nextInt(-radius / 2, radius / 2 + 1);
            area = x + " " + z + " (±" + radius + ") " + p.getWorld().getName();
            Bukkit.broadcast(plugin.msg("hunt-reveal", "&c☠ {player} &7was last seen around &f{area}&7.",
                    "player", targetName, "area", area));
        }
        bar.name(plugin.raw("hunt-bossbar", "&c☠ Hunt: &f{player} &8• &f{time} &8• &7around &f{area}",
                "player", targetName, "time", RitualManager.format(endsAt - now), "area", area));
        bar.progress((float) Math.max(0, Math.min(1, (endsAt - now) / (cfg("duration-minutes", 60) * 60_000.0))));
        for (Player o : Bukkit.getOnlinePlayers()) {
            o.showBossBar(bar);
        }
    }

    private void survived(Player p) {
        int tokens = plugin.shop() != null && plugin.shop().enabled() ? (int) cfg("survive-tokens", 8) : 0;
        if (tokens > 0) {
            plugin.bounty().give(p, plugin.bounty().token(tokens));
        }
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        if (plugin.top() != null) {
            plugin.top().add(p, "hunts");
        }
        Bukkit.broadcast(plugin.msg("hunt-survived", "&6☠ {player} survived the Legend Hunt! &7Reward: &6{tokens} Bounty Tokens",
                "player", targetName, "tokens", String.valueOf(tokens)));
        end();
    }

    /** Runs before the bounty is paid out (MONITOR), so the bounty can still be read here. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (!isTarget(victim)) {
            return;
        }
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)
                || plugin.teams() != null && plugin.teams().allies(killer, victim)) {
            return;                                               // died to a creeper: the hunt goes on
        }
        int bounty = plugin.bounty().bountyOf(victim.getUniqueId());
        int extra = (int) Math.round(bounty * (cfg("bounty-multiplier", 2) - 1));
        int bonus = plugin.shop() != null && plugin.shop().enabled() ? (int) cfg("kill-bonus-tokens", 5) : 0;
        int total = Math.max(0, extra) + bonus;
        if (total > 0) {
            int left = total;
            while (left > 0) {
                int n = Math.min(64, left);
                plugin.bounty().give(killer, plugin.bounty().token(n));
                left -= n;
            }
        }
        Bukkit.broadcast(plugin.msg("hunt-killed", "&c☠ &f{killer} &cended the Legend Hunt on &f{player}&c! &7Bonus: &6{tokens} Bounty Tokens",
                "killer", killer.getName(), "player", targetName, "tokens", String.valueOf(total)));
        end();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!isTarget(event.getPlayer())) {
            return;
        }
        int penalty = (int) cfg("flee-penalty-bounty", 5);
        if (penalty > 0 && plugin.bounty() != null) {
            plugin.bounty().addBounty(event.getPlayer(), penalty);
        }
        Bukkit.broadcast(plugin.msg("hunt-fled", "&7☠ {player} fled the Legend Hunt. &c+{penalty} bounty for cowardice.",
                "player", targetName, "penalty", String.valueOf(penalty)));
        end();
    }

    /** Ends the hunt without rewards. */
    public void end() {
        Player p = target == null ? null : Bukkit.getPlayer(target);
        if (p != null) {
            p.setGlowing(false);
        }
        if (bar != null) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                o.hideBossBar(bar);
            }
        }
        bar = null;
        target = null;
        targetName = null;
        area = "";
    }
}
