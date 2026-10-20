package cz.forgebound;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recon Spyglass: look at a player through it for a moment and you see their glowing outline through walls
 * (and a marker with their name and distance) for a few seconds. Only the spotter sees it.
 */
public final class ReconManager implements Listener {

    private final Forgebound plugin;
    private final NamespacedKey reconKey;
    private final NamespacedKey chargesKey;

    /** spotter -> (target, ticks aimed at them) */
    private final Map<UUID, UUID> aimTarget = new HashMap<>();
    private final Map<UUID, Integer> aimTicks = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final List<Spot> spots = new ArrayList<>();
    /** target -> (spotter -> assist possible until) */
    private final Map<UUID, Map<UUID, Long>> assists = new HashMap<>();

    private static final class Spot {
        UUID spotter;
        UUID target;
        /** who sees it: the spotter and (with teams.share-spots) their teammates */
        List<UUID> viewers = new ArrayList<>();
        long endsAt;
        Mannequin outline;
        TextDisplay marker;
    }

    public ReconManager(Forgebound plugin) {
        this.plugin = plugin;
        this.reconKey = Forgebound.key("recon_spyglass");
        this.chargesKey = Forgebound.key("recon_charges");
    }

    private double cfg(String key, double def) {
        return plugin.getConfig().getDouble("recon." + key, def);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("recon.enabled", true);
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 40L, 1L);
    }

    public void shutdown() {
        for (Spot s : spots) {
            remove(s);
        }
        spots.clear();
    }

    // ================================================================ the item

    public ItemStack item() {
        ItemStack stack = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = stack.getItemMeta();
        int charges = (int) cfg("charges", 20);
        meta.displayName(ItemManager.color("&b&lRecon Spyglass"));
        meta.setCustomModelData(1501);
        meta.setEnchantmentGlintOverride(false);
        meta.getPersistentDataContainer().set(reconKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(chargesKey, PersistentDataType.INTEGER, charges);
        meta.lore(lore(charges));
        stack.setItemMeta(meta);
        return stack;
    }

    private List<Component> lore(int charges) {
        return List.of(ItemManager.color("&7Hold a player in sight to &bspot &7them:"),
                ItemManager.color("&7you see them through walls for a while."),
                ItemManager.color("&8Uses left: &f" + charges));
    }

    private boolean isRecon(ItemStack stack) {
        return stack != null && stack.getType() == Material.SPYGLASS
                && stack.getPersistentDataContainer().has(reconKey, PersistentDataType.BYTE);
    }

    /** Uses one charge; the spyglass breaks after the last one. */
    private void useCharge(Player p, ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        int left = meta.getPersistentDataContainer().getOrDefault(chargesKey, PersistentDataType.INTEGER, 1) - 1;
        if (left <= 0) {
            stack.setAmount(stack.getAmount() - 1);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return;
        }
        meta.getPersistentDataContainer().set(chargesKey, PersistentDataType.INTEGER, left);
        meta.lore(lore(left));
        stack.setItemMeta(meta);
    }

    // ================================================================ spotting

    private void tick() {
        long now = System.currentTimeMillis();
        if (enabled()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                aim(p, now);
            }
        }
        for (Iterator<Spot> it = spots.iterator(); it.hasNext(); ) {
            Spot s = it.next();
            Player spotter = Bukkit.getPlayer(s.spotter);
            Player target = Bukkit.getPlayer(s.target);
            if (spotter == null || target == null || now > s.endsAt || target.isDead()
                    || spotter.getWorld() != target.getWorld()) {
                remove(s);
                it.remove();
                continue;
            }
            follow(s, spotter, target);
        }
    }

    private void aim(Player p, long now) {
        ItemStack active = p.getActiveItem();
        if (!p.isHandRaised() || !isRecon(active)) {
            aimTicks.remove(p.getUniqueId());
            return;
        }
        Long ready = cooldown.get(p.getUniqueId());
        if (ready != null && now < ready) {
            if (Bukkit.getCurrentTick() % 10 == 0) {
                p.sendActionBar(ItemManager.color("&8Recon recharging: &7" + ((ready - now) / 1000 + 1) + "s"));
            }
            return;
        }
        Location eye = p.getEyeLocation();
        RayTraceResult hit = p.getWorld().rayTrace(eye, eye.getDirection(), cfg("range", 150),
                FluidCollisionMode.NEVER, true, 0.4, e -> e instanceof Player o && !o.equals(p)
                        && (plugin.teams() == null || !plugin.teams().allies(p, o))
                        && o.getGameMode() != org.bukkit.GameMode.SPECTATOR);
        Entity e = hit == null ? null : hit.getHitEntity();
        if (!(e instanceof Player target)) {
            aimTicks.remove(p.getUniqueId());
            return;
        }
        if (!target.getUniqueId().equals(aimTarget.get(p.getUniqueId()))) {
            aimTarget.put(p.getUniqueId(), target.getUniqueId());
            aimTicks.put(p.getUniqueId(), 0);
        }
        int ticks = aimTicks.merge(p.getUniqueId(), 1, Integer::sum);
        int needed = (int) (cfg("aim-seconds", 0.75) * 20);
        int bars = Math.min(8, ticks * 8 / Math.max(1, needed));
        p.sendActionBar(ItemManager.color("&bSpotting &f" + target.getName() + " &8[&b" + "▮".repeat(bars)
                + "&8" + "▯".repeat(8 - bars) + "&8]"));
        if (ticks >= needed) {
            aimTicks.remove(p.getUniqueId());
            spot(p, target, active, now);
        }
    }

    private int seconds(Player target) {
        if (plugin.events() != null && plugin.events().hunt() != null && plugin.events().hunt().isTarget(target)) {
            return (int) cfg("seconds-hunted", 20);
        }
        if (plugin.bounty() != null && plugin.bounty().bountyOf(target.getUniqueId()) > 0) {
            return (int) cfg("seconds-bounty", 15);
        }
        return (int) cfg("seconds", 10);
    }

    private void spot(Player spotter, Player target, ItemStack glass, long now) {
        cooldown.put(spotter.getUniqueId(), now + (long) (cfg("cooldown-seconds", 30) * 1000));
        useCharge(spotter, glass);
        // replace an older spot on the same player
        for (Iterator<Spot> it = spots.iterator(); it.hasNext(); ) {
            Spot s = it.next();
            if (s.spotter.equals(spotter.getUniqueId()) && s.target.equals(target.getUniqueId())) {
                remove(s);
                it.remove();
            }
        }
        Spot s = new Spot();
        s.spotter = spotter.getUniqueId();
        s.target = target.getUniqueId();
        int secs = seconds(target);
        s.endsAt = now + secs * 1000L;
        Location at = target.getLocation();
        s.outline = at.getWorld().spawn(at, Mannequin.class, m -> {
            m.setVisibleByDefault(false);
            m.setPersistent(false);
            m.setInvisible(true);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setGravity(false);
            m.setCollidable(false);
            m.setImmovable(true);
            m.setDescription(null);
            m.setGlowing(true);
            m.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile(target.getPlayerProfile()));
        });
        s.marker = at.getWorld().spawn(at.clone().add(0, 2.4, 0), TextDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.setPersistent(false);
            d.setSeeThrough(true);
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(2);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.2f, 1.2f, 1.2f),
                    new Quaternionf()));
        });
        s.viewers.add(spotter.getUniqueId());
        if (plugin.teams() != null && plugin.getConfig().getBoolean("teams.share-spots", true)) {
            for (Player mate : plugin.teams().teammates(spotter)) {
                s.viewers.add(mate.getUniqueId());
                mate.sendActionBar(ItemManager.color("&b◆ &f" + spotter.getName() + " &bspotted &f" + target.getName()));
                mate.playSound(mate.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.8f);
            }
        }
        for (UUID v : s.viewers) {
            Player viewer = Bukkit.getPlayer(v);
            if (viewer != null) {
                viewer.showEntity(plugin, s.marker);
                if (!viewer.equals(spotter)) {
                    redOutline(viewer, s.outline);
                }
            }
        }
        assists.computeIfAbsent(target.getUniqueId(), k -> new HashMap<>())
                .put(spotter.getUniqueId(), s.endsAt + (long) (cfg("assist-window-seconds", 10) * 1000));
        redOutline(spotter, s.outline);
        spots.add(s);
        spotter.playSound(spotter.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.8f);
        spotter.sendActionBar(ItemManager.color("&b◆ &f" + target.getName() + " &bspotted &8(" + secs + "s)"));
        // the target only gets a hint: a quiet click and an eye, no name
        if (plugin.getConfig().getBoolean("recon.hint-target", true)) {
            target.playSound(target.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.35f, 1.4f);
            target.sendActionBar(ItemManager.color("&8👁"));
        }
    }

    /** The outline is red for the spotter (a team on the spotter's own scoreboard). */
    private void redOutline(Player spotter, Mannequin outline) {
        try {
            Scoreboard board = spotter.getScoreboard();
            Team team = board.getTeam("fb_spotted");
            if (team == null) {
                team = board.registerNewTeam("fb_spotted");
                team.color(net.kyori.adventure.text.format.NamedTextColor.RED);
            }
            team.addEntity(outline);
        } catch (RuntimeException ignored) {
            // white outline then
        }
    }

    /** Keeps the outline on the target. It hides while the spotter can see the target directly (so it doesn't get in the way). */
    private void follow(Spot s, Player spotter, Player target) {
        Location at = target.getLocation();
        if (s.outline.isValid()) {
            s.outline.teleport(at);
            s.outline.setPose(target.getPose(), true);
            for (UUID v : s.viewers) {
                Player viewer = Bukkit.getPlayer(v);
                if (viewer == null || viewer.getWorld() != at.getWorld()) {
                    continue;
                }
                boolean direct = viewer.hasLineOfSight(target) && viewer.getLocation().distanceSquared(at) < 48 * 48;
                if (direct) {
                    viewer.hideEntity(plugin, s.outline);
                } else {
                    viewer.showEntity(plugin, s.outline);
                }
            }
        }
        if (s.marker.isValid()) {
            s.marker.teleport(at.clone().add(0, target.getHeight() + 0.6, 0));
            if (Bukkit.getCurrentTick() % 5 == 0) {
                int dist = (int) Math.round(spotter.getLocation().distance(at));
                long left = Math.max(0, (s.endsAt - System.currentTimeMillis()) / 1000 + 1);
                s.marker.text(ItemManager.color("&c◆ &f" + target.getName() + " &7" + dist + "m &8" + left + "s"));
            }
        }
    }

    private void remove(Spot s) {
        for (UUID v : s.viewers) {
            Player viewer = Bukkit.getPlayer(v);
            if (viewer != null && s.outline != null) {
                Team team = viewer.getScoreboard().getTeam("fb_spotted");
                if (team != null) {
                    team.removeEntity(s.outline);
                }
            }
        }
        if (s.outline != null && s.outline.isValid()) {
            s.outline.remove();
        }
        if (s.marker != null && s.marker.isValid()) {
            s.marker.remove();
        }
    }

    /** The outline is only a picture: it can't be hurt. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        for (Spot s : spots) {
            if (event.getEntity().equals(s.outline)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Somebody else killed a player you spotted: an assist. Runs before the bounty is paid out. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKill(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Map<UUID, Long> mine = assists.remove(victim.getUniqueId());
        Player killer = victim.getKiller();
        if (mine == null || killer == null || killer.equals(victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        int bounty = plugin.bounty() == null ? 0 : plugin.bounty().bountyOf(victim.getUniqueId());
        boolean economy = plugin.shop() != null && plugin.shop().enabled() && plugin.bounty() != null;
        for (Map.Entry<UUID, Long> e : mine.entrySet()) {
            Player spotter = Bukkit.getPlayer(e.getKey());
            if (spotter == null || e.getValue() < now || spotter.equals(killer)) {
                continue;
            }
            int tokens = economy ? (int) cfg("assist-tokens", 1)
                    + (int) Math.round(bounty * cfg("assist-bounty-percent", 25) / 100.0) : 0;
            if (tokens > 0) {
                plugin.bounty().give(spotter, plugin.bounty().token(tokens));
            }
            if (plugin.top() != null) {
                plugin.top().add(spotter, "assists");
            }
            spotter.sendMessage(plugin.msg("recon-assist",
                    "&b◆ Assist! &f{killer} &7took down &f{victim}&7, who you spotted. &6+{tokens} Bounty Tokens",
                    "killer", killer.getName(), "victim", victim.getName(), "tokens", String.valueOf(tokens)));
            spotter.playSound(spotter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        aimTicks.remove(id);
        aimTarget.remove(id);
        for (Iterator<Spot> it = spots.iterator(); it.hasNext(); ) {
            Spot s = it.next();
            if (s.spotter.equals(id) || s.target.equals(id)) {
                remove(s);
                it.remove();
            }
        }
    }
}
