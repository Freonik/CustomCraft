package cz.customcraft;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Rift: a portal opens near the players and monsters pour out in waves. Close it together;
 * everyone is rewarded by how much they helped.
 */
public final class RiftEvent implements Listener {

    private static final Particle.DustOptions VOID = new Particle.DustOptions(Color.fromRGB(120, 40, 200), 1.5f);
    private static final List<String> DEFAULT_WAVES = List.of(
            "ZOMBIE:5,SKELETON:3",
            "HUSK:4,STRAY:3,SPIDER:3",
            "VINDICATOR:3,PILLAGER:3,WITCH:2",
            "BLAZE:3,WITHER_SKELETON:4",
            "RAVAGER:1,EVOKER:1,VINDICATOR:3");

    private enum Phase { OPENING, FIGHT, PAUSE }

    private final CustomCraft plugin;
    private final EventManager events;
    private final NamespacedKey mobKey;

    private Location portal;
    private Phase phase;
    private long phaseEndsAt;
    private long startedAt;
    private int wave;
    private int waveTotal;
    private int lonely;
    private BossBar bar;
    private BukkitRunnable task;
    private final List<LivingEntity> mobs = new ArrayList<>();
    private final Map<UUID, Double> score = new HashMap<>();

    public RiftEvent(CustomCraft plugin, EventManager events) {
        this.plugin = plugin;
        this.events = events;
        this.mobKey = new NamespacedKey(plugin, "rift_mob");
    }

    public boolean active() {
        return portal != null;
    }

    private List<String> waves() {
        List<String> list = plugin.getConfig().getStringList("events.rift.waves");
        return list.isEmpty() ? DEFAULT_WAVES : list;
    }

    private int nearby(double radius) {
        int n = 0;
        for (Player p : portal.getWorld().getPlayers()) {
            if (fighter(p) && p.getLocation().distanceSquared(portal) <= radius * radius) {
                n++;
            }
        }
        return n;
    }

    private static boolean fighter(Player p) {
        return !p.isDead() && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE;
    }

    /** Opens a Rift near a random player (or near "near" for admins). */
    public boolean begin(Location near) {
        if (active()) {
            return false;
        }
        World world;
        Location center;
        if (near != null) {
            world = near.getWorld();
            center = near;
        } else {
            world = events.world();
            List<Player> players = new ArrayList<>(world.getPlayers());
            center = players.isEmpty() ? world.getSpawnLocation()
                    : players.get(ThreadLocalRandom.current().nextInt(players.size())).getLocation();
        }
        int min = near != null ? 25 : plugin.getConfig().getInt("events.rift.distance-min", 150);
        int max = near != null ? 45 : plugin.getConfig().getInt("events.rift.distance-max", 350);
        Location at = EventManager.surface(world, center, min, max);
        if (at == null) {
            return false;
        }
        portal = at.add(0, 0, 0);
        phase = Phase.OPENING;
        startedAt = System.currentTimeMillis();
        phaseEndsAt = startedAt + Math.max(10, plugin.getConfig().getInt("events.rift.warning-seconds", 180)) * 1000L;
        wave = 0;
        waveTotal = waves().size();
        lonely = 0;
        score.clear();
        mobs.clear();
        bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_6);
        Bukkit.broadcast(plugin.msg("rift-start",
                "&5✷ A &d&lRift &5is tearing open at &f{x} {y} {z} &5in {world}! &7It opens in {time}. Close it together!",
                "x", String.valueOf(at.getBlockX()), "y", String.valueOf(at.getBlockY()), "z", String.valueOf(at.getBlockZ()),
                "world", world.getName(), "time", RitualManager.format(phaseEndsAt - startedAt)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 0.6f);
        }
        task = new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                visuals(t);
                if (t % 10 == 0) {
                    tick();
                }
            }
        };
        task.runTaskTimer(plugin, 1L, 2L);
        return true;
    }

    private void visuals(int t) {
        if (portal == null) {
            return;
        }
        World w = portal.getWorld();
        double open = phase == Phase.OPENING
                ? 1 - Math.max(0, (double) (phaseEndsAt - System.currentTimeMillis())
                        / (plugin.getConfig().getInt("events.rift.warning-seconds", 180) * 1000.0))
                : 1;
        double r = 0.6 + 1.9 * open;
        Location c = portal.clone().add(0, 2.6, 0);
        for (int i = 0; i < 16; i++) {
            double a = t * 0.15 + i * Math.PI / 8;
            Location p = c.clone().add(Math.cos(a) * r * 0.55, Math.sin(a) * r, 0);
            w.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, VOID, true);
        }
        w.spawnParticle(Particle.REVERSE_PORTAL, c, 6, 0.3, r * 0.6, 0.3, 0.02, null, true);
        if (t % 10 == 0) {
            for (int y = 0; y < 30; y += 2) {
                w.spawnParticle(Particle.WITCH, portal.clone().add(0, 4 + y, 0), 1, 0.1, 0.5, 0.1, 0, null, true);
            }
        }
    }

    private void tick() {
        if (portal == null) {
            return;
        }
        long now = System.currentTimeMillis();
        mobs.removeIf(m -> !m.isValid() || m.isDead());
        // leash and keep them hunting
        for (LivingEntity m : mobs) {
            if (m.getLocation().getWorld() != portal.getWorld() || m.getLocation().distanceSquared(portal) > 40 * 40) {
                m.teleport(portal);
            }
        }
        // abandoned: nobody close for 2 minutes once it's open
        lonely = phase != Phase.OPENING && nearby(64) == 0 ? lonely + 1 : 0;
        if (lonely >= 120 || now - startedAt > plugin.getConfig().getInt("events.rift.max-minutes", 25) * 60_000L) {
            collapse(false);
            return;
        }
        switch (phase) {
            case OPENING -> {
                bar.name(plugin.raw("rift-bossbar-opening", "&5✷ Rift &8• &7opens in &f{time} &8• &f{x} {y} {z}",
                        "time", RitualManager.format(phaseEndsAt - now), "x", String.valueOf(portal.getBlockX()),
                        "y", String.valueOf(portal.getBlockY()), "z", String.valueOf(portal.getBlockZ())));
                bar.progress(0f);
                if (now >= phaseEndsAt) {
                    portal.getWorld().playSound(portal, Sound.BLOCK_END_PORTAL_SPAWN, 3f, 0.6f);
                    portal.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, portal.clone().add(0, 2, 0), 1, 0, 0, 0, 0);
                    Bukkit.broadcast(plugin.msg("rift-open", "&5✷ The Rift is open! Monsters are pouring out at &f{x} {y} {z}&5!",
                            "x", String.valueOf(portal.getBlockX()), "y", String.valueOf(portal.getBlockY()),
                            "z", String.valueOf(portal.getBlockZ())));
                    nextWave();
                }
            }
            case FIGHT -> {
                bar.name(plugin.raw("rift-bossbar-wave", "&5✷ Rift &8• &dwave {wave}/{waves} &8• &f{left} monsters left",
                        "wave", String.valueOf(wave), "waves", String.valueOf(waveTotal), "left", String.valueOf(mobs.size())));
                bar.progress((float) Math.max(0, Math.min(1, (wave - 1 + 0.0) / waveTotal)));
                if (mobs.isEmpty()) {
                    if (wave >= waveTotal) {
                        close();
                        return;
                    }
                    phase = Phase.PAUSE;
                    phaseEndsAt = now + 10_000;
                    for (Player p : portal.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(portal) < 80 * 80) {
                            p.sendActionBar(plugin.raw("rift-wave-cleared", "&dWave {wave} cleared! &7Next one in 10 seconds...",
                                    "wave", String.valueOf(wave)));
                        }
                    }
                }
            }
            case PAUSE -> {
                bar.name(plugin.raw("rift-bossbar-pause", "&5✷ Rift &8• &7next wave in &f{time}",
                        "time", RitualManager.format(phaseEndsAt - now)));
                bar.progress((float) Math.max(0, Math.min(1, (double) wave / waveTotal)));
                if (now >= phaseEndsAt) {
                    nextWave();
                }
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld() == portal.getWorld() && p.getLocation().distanceSquared(portal) < 150 * 150
                    || phase == Phase.OPENING) {
                p.showBossBar(bar);
            } else {
                p.hideBossBar(bar);
            }
        }
    }

    private void nextWave() {
        if (nearby(64) == 0 && wave > 0) {
            phaseEndsAt = System.currentTimeMillis() + 5000;      // wait for someone to come back
            return;
        }
        wave++;
        phase = Phase.FIGHT;
        int players = Math.max(1, nearby(64));
        double scale = 1 + plugin.getConfig().getDouble("events.rift.per-extra-player", 0.5) * (players - 1);
        String def = waves().get(Math.min(wave - 1, waves().size() - 1));
        World w = portal.getWorld();
        boolean last = wave == waveTotal;
        for (String part : def.split(",")) {
            String[] kv = part.trim().split(":");
            EntityType type;
            try {
                type = EntityType.valueOf(kv[0].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Rift: unknown mob " + kv[0]);
                continue;
            }
            if (!type.isAlive() || !type.isSpawnable()) {
                continue;
            }
            int count = (int) Math.round((kv.length > 1 ? Integer.parseInt(kv[1].trim()) : 1)
                    * (type == EntityType.RAVAGER || type == EntityType.EVOKER ? 1 : scale));
            for (int i = 0; i < count; i++) {
                Location at = portal.clone().add(ThreadLocalRandom.current().nextDouble(-2.5, 2.5), 0.2,
                        ThreadLocalRandom.current().nextDouble(-2.5, 2.5));
                if (!at.getBlock().isPassable()) {
                    at = portal.clone().add(0, 0.2, 0);
                }
                Entity e = w.spawnEntity(at, type, CreatureSpawnEvent.SpawnReason.CUSTOM, ent -> {
                    ent.getPersistentDataContainer().set(mobKey, PersistentDataType.BYTE, (byte) 1);
                    ent.setPersistent(false);
                    ent.setGlowing(true);
                    if (ent instanceof LivingEntity le) {
                        le.setRemoveWhenFarAway(false);
                    }
                });
                if (e instanceof LivingEntity le) {
                    if (last && (type == EntityType.RAVAGER || type == EntityType.EVOKER)) {
                        le.customName(ItemManager.color("&5&lRift " + EventManager.pretty(type.name())));
                        AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
                        if (hp != null) {
                            hp.setBaseValue(hp.getBaseValue() * 2.5);
                            le.setHealth(hp.getValue());
                        }
                    }
                    mobs.add(le);
                    if (le instanceof Mob mob) {
                        Player target = closest(le.getLocation());
                        if (target != null) {
                            mob.setTarget(target);
                        }
                    }
                }
                w.spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.05);
            }
        }
        w.playSound(portal, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2f, 0.6f);
        if (last) {
            Bukkit.broadcast(plugin.msg("rift-final-wave", "&5✷ The final wave is coming out of the Rift!"));
        }
    }

    private Player closest(Location loc) {
        Player best = null;
        double d = 48 * 48;
        for (Player p : loc.getWorld().getPlayers()) {
            if (fighter(p) && p.getLocation().distanceSquared(loc) < d) {
                d = p.getLocation().distanceSquared(loc);
                best = p;
            }
        }
        return best;
    }

    private boolean isRiftMob(Entity e) {
        return e != null && e.getPersistentDataContainer().has(mobKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!isRiftMob(event.getEntity())) {
            return;
        }
        Player p = event.getDamager() instanceof Player pl ? pl
                : event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player s ? s : null;
        if (p != null) {
            score.merge(p.getUniqueId(), Math.min(event.getFinalDamage(), 40), Double::sum);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!isRiftMob(event.getEntity())) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            score.merge(killer.getUniqueId(), 10.0, Double::sum);
        }
    }

    /** Rift monsters don't burn in daylight and don't fight each other. */
    @EventHandler(ignoreCancelled = true)
    public void onBurn(EntityCombustEvent event) {
        if (!(event instanceof EntityCombustByEntityEvent) && !(event instanceof EntityCombustByBlockEvent)
                && isRiftMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (isRiftMob(event.getEntity()) && isRiftMob(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    /** Success: rewards by contribution. */
    private void close() {
        Location at = portal.clone();
        World w = at.getWorld();
        w.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 3f, 0.8f);
        w.spawnParticle(Particle.FLASH, at.clone().add(0, 2.5, 0), 1, 0, 0, 0, 0, Color.PURPLE);
        w.spawnParticle(Particle.END_ROD, at.clone().add(0, 2.5, 0), 120, 1, 1.5, 1, 0.15);
        double total = score.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<UUID, Double> ranked = new LinkedHashMap<>();
        score.entrySet().stream().sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .forEach(e -> ranked.put(e.getKey(), e.getValue()));
        int pool = plugin.shop() != null && plugin.shop().enabled()
                ? Math.max(0, plugin.getConfig().getInt("events.rift.token-pool", 15)) : 0;
        int xp = Math.max(0, plugin.getConfig().getInt("events.rift.xp", 300));
        double minShare = plugin.getConfig().getDouble("events.rift.min-share-percent", 5) / 100.0;
        List<String> top = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<UUID, Double> e : ranked.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            double share = total <= 0 ? 0 : e.getValue() / total;
            if (p == null || share < minShare) {
                continue;
            }
            if (top.size() < 3) {
                top.add(p.getName() + " (" + Math.round(share * 100) + "%)");
            }
            p.getWorld().spawn(p.getLocation(), ExperienceOrb.class, o -> o.setExperience((int) Math.round(xp * (0.5 + share))));
            int tokens = pool <= 0 ? 0 : Math.max(1, (int) Math.round(pool * share));
            if (tokens > 0 && plugin.bounty() != null) {
                plugin.bounty().give(p, plugin.bounty().token(tokens));
            }
            if (first && plugin.bounty() != null
                    && ThreadLocalRandom.current().nextDouble(100) < plugin.getConfig().getDouble("events.rift.top-material-chance", 100)) {
                List<ItemManager.Entry> materials = plugin.bounty().enabledMaterials();
                if (!materials.isEmpty()) {
                    plugin.bounty().give(p, materials.get(ThreadLocalRandom.current().nextInt(materials.size())).item.clone());
                }
            }
            first = false;
            if (plugin.top() != null) {
                plugin.top().add(p, "rifts");
            }
            p.sendMessage(plugin.msg("rift-reward", "&5✷ &7Your share: &d{share}% &8• &6{tokens} tokens",
                    "share", String.valueOf(Math.round(share * 100)), "tokens", String.valueOf(tokens)));
        }
        Bukkit.broadcast(plugin.msg("rift-closed", "&5✷ The Rift has been closed! &7Heroes: &f{top}",
                "top", top.isEmpty() ? "-" : String.join(", ", top)));
        end();
    }

    /** Failure or admin stop: the monsters vanish with the portal. */
    public void collapse(boolean silent) {
        if (!active()) {
            return;
        }
        if (!silent) {
            Bukkit.broadcast(plugin.msg("rift-collapsed", "&7✷ The Rift collapsed on its own. The monsters went with it."));
        }
        for (LivingEntity m : mobs) {
            if (m.isValid()) {
                m.getWorld().spawnParticle(Particle.REVERSE_PORTAL, m.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
                m.remove();
            }
        }
        end();
    }

    private void end() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }
        bar = null;
        mobs.clear();
        portal = null;
    }

    public void shutdown() {
        collapse(true);
    }

    public boolean opening() {
        return phase == Phase.OPENING;
    }

    public int wave() {
        return wave;
    }

    public int waveTotal() {
        return waveTotal;
    }

    public Component statusLine() {
        if (portal == null) {
            return Component.empty();
        }
        String where = portal.getBlockX() + " " + portal.getBlockY() + " " + portal.getBlockZ();
        return ItemManager.color(phase == Phase.OPENING ? "&5✷ A Rift is opening at &f" + where
                : "&5✷ Rift at &f" + where + " &7- wave " + wave + "/" + waveTotal);
    }
}
