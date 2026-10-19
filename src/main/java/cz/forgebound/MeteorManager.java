package cz.forgebound;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Meteor event: every few hours a meteor falls somewhere in the world. Its location is revealed step by step,
 * and the first one there gets a cache with a guaranteed legendary material.
 */
public final class MeteorManager {

    private static final Set<Material> NATURAL = Set.of(Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.PODZOL, Material.STONE, Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.SAND,
            Material.RED_SAND, Material.GRAVEL, Material.SNOW_BLOCK, Material.TERRACOTTA, Material.MYCELIUM,
            Material.DEEPSLATE, Material.TUFF, Material.MUD, Material.CLAY, Material.SANDSTONE, Material.MOSS_BLOCK);
    private static final Particle.DustOptions EMBER = new Particle.DustOptions(Color.fromRGB(255, 120, 30), 2f);

    private final Forgebound plugin;
    private final ItemManager items;
    private long nextAt;
    private Meteor current;

    private static final class Meteor {
        Location target;
        int ox;
        int oz;
        long startedAt;
        long landsAt;
        int stage = -1;
        BossBar bar;
    }

    public MeteorManager(Forgebound plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("meteor.enabled", true);
    }

    private void scheduleNext() {
        int min = Math.max(5, plugin.getConfig().getInt("meteor.interval-minutes-min", 120));
        int max = Math.max(min, plugin.getConfig().getInt("meteor.interval-minutes-max", 240));
        nextAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(min, max + 1L) * 60_000L;
    }

    public void start() {
        scheduleNext();
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 200L, 20L);
    }

    public void shutdown() {
        if (current != null && current.bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(current.bar);
            }
        }
        current = null;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (current == null) {
            if (!enabled() || now < nextAt) {
                return;
            }
            if (Bukkit.getOnlinePlayers().size() < plugin.getConfig().getInt("meteor.min-players", 3)) {
                nextAt = now + 10 * 60_000L;                         // try again in 10 minutes
                return;
            }
            if (plugin.events() != null && plugin.events().bigActive()) {
                nextAt = now + 5 * 60_000L;                          // a Rift or Blood Moon is on, wait
                return;
            }
            launch();
            return;
        }
        Meteor m = current;
        if (now >= m.landsAt) {
            impact(m);
            return;
        }
        double progress = (double) (now - m.startedAt) / Math.max(1, m.landsAt - m.startedAt);
        int stage = progress < 0.4 ? 0 : progress < 0.8 ? 1 : 2;
        if (stage != m.stage) {
            m.stage = stage;
            Bukkit.broadcast(plugin.msg(stage == 0 ? "meteor-start" : stage == 1 ? "meteor-area" : "meteor-exact",
                    stage == 0 ? "&6☄ A meteor is falling from the sky{where}&6! It lands in &e{time}&6."
                            : stage == 1 ? "&6☄ The meteor's path narrows down{where}&6."
                            : "&6☄ The meteor will hit{where}&6!",
                    "where", where(m), "time", RitualManager.format(m.landsAt - now)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6f, 0.5f);
            }
        }
        m.bar.name(plugin.raw("meteor-bossbar", "&6☄ Meteor &8• &f{time}{where}",
                "time", RitualManager.format(m.landsAt - now), "where", where(m)));
        m.bar.progress((float) Math.max(0, Math.min(1, progress)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(m.bar);
        }
    }

    private String where(Meteor m) {
        Location t = m.target;
        String world = t.getWorld().getName();
        return switch (m.stage) {
            case 0 -> " in " + world;
            case 1 -> " around " + (t.getBlockX() + m.ox) + " " + (t.getBlockZ() + m.oz) + " (±"
                    + plugin.getConfig().getInt("meteor.area-radius", 300) + ")";
            default -> " at " + t.getBlockX() + " " + t.getBlockY() + " " + t.getBlockZ();
        };
    }

    /** Is a meteor falling right now? */
    public boolean active() {
        return current != null;
    }

    /** Admin: start a meteor now. Returns false if one is already falling. */
    public boolean launch() {
        if (current != null) {
            return false;
        }
        Location target = pickLocation();
        if (target == null) {
            plugin.getLogger().warning("Meteor: no good landing spot found.");
            scheduleNext();
            return false;
        }
        Meteor m = new Meteor();
        m.target = target;
        int area = plugin.getConfig().getInt("meteor.area-radius", 300);
        m.ox = ThreadLocalRandom.current().nextInt(-area / 2, area / 2 + 1);
        m.oz = ThreadLocalRandom.current().nextInt(-area / 2, area / 2 + 1);
        m.startedAt = System.currentTimeMillis();
        m.landsAt = m.startedAt + Math.max(30, plugin.getConfig().getInt("meteor.warning-seconds", 300)) * 1000L;
        m.bar = BossBar.bossBar(net.kyori.adventure.text.Component.empty(), 0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        current = m;
        plugin.getLogger().info("Meteor launched toward " + target.getBlockX() + " " + target.getBlockY() + " " + target.getBlockZ());
        return true;
    }

    private Location pickLocation() {
        String name = plugin.getConfig().getString("meteor.world", "");
        World world = name.isEmpty() ? Bukkit.getWorlds().get(0) : Bukkit.getWorld(name);
        if (world == null) {
            return null;
        }
        int radius = Math.max(100, plugin.getConfig().getInt("meteor.radius", 2500));
        Location spawn = world.getSpawnLocation();
        WorldBorder border = world.getWorldBorder();
        for (int attempt = 0; attempt < 20; attempt++) {
            int x = spawn.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int z = spawn.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            Location loc = new Location(world, x + 0.5, 0, z + 0.5);
            if (!border.isInside(loc)) {
                continue;
            }
            Block top = world.getHighestBlockAt(x, z);
            if (top.isLiquid() || top.getType() == Material.ICE || !top.getType().isSolid()) {
                continue;
            }
            return top.getLocation().add(0.5, 1, 0.5);
        }
        return null;
    }

    private void impact(Meteor m) {
        current = null;
        scheduleNext();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(m.bar);
        }
        Location target = m.target;
        World world = target.getWorld();
        world.getChunkAt(target).load();
        // a burning rock falls in over 3 seconds
        Location from = target.clone().add(-25, 60, -12);
        BlockDisplay rock = world.spawn(from, BlockDisplay.class, d -> {
            d.setBlock(Material.MAGMA_BLOCK.createBlockData());
            d.setPersistent(false);
            d.setTeleportDuration(1);
            d.setTransformation(new Transformation(new Vector3f(-1, -1, -1), new Quaternionf(),
                    new Vector3f(2, 2, 2), new Quaternionf()));
        });
        world.playSound(target, Sound.ENTITY_GHAST_SHOOT, 3f, 0.4f);
        int steps = 60;
        new BukkitRunnable() {
            int i;

            @Override
            public void run() {
                i++;
                double t = (double) i / steps;
                Location at = from.clone().add(target.clone().subtract(from).toVector().multiply(t));
                if (rock.isValid()) {
                    rock.teleport(at);
                }
                world.spawnParticle(Particle.FLAME, at, 20, 0.6, 0.6, 0.6, 0.05);
                world.spawnParticle(Particle.LARGE_SMOKE, at, 10, 0.6, 0.6, 0.6, 0.02);
                world.spawnParticle(Particle.DUST, at, 6, 0.8, 0.8, 0.8, 0, EMBER);
                if (i >= steps) {
                    cancel();
                    rock.remove();
                    land(target);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void land(Location target) {
        World world = target.getWorld();
        world.createExplosion(target, 0f, false, false);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, target, 2, 0.5, 0.5, 0.5, 0);
        world.spawnParticle(Particle.LAVA, target, 60, 2, 1, 2, 0);
        world.spawnParticle(Particle.LARGE_SMOKE, target.clone().add(0, 1, 0), 120, 3, 2, 3, 0.03);
        world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.5f);
        world.playSound(target, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4f, 0.6f);
        // a small scorched spot with the cache in the middle (only natural blocks are changed)
        Block center = target.getBlock();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5) {
                    continue;
                }
                Block ground = center.getRelative(dx, -1, dz);
                if (NATURAL.contains(ground.getType())) {
                    ground.setType(ThreadLocalRandom.current().nextInt(3) == 0 ? Material.MAGMA_BLOCK
                            : ThreadLocalRandom.current().nextBoolean() ? Material.BLACKSTONE : Material.BASALT);
                }
            }
        }
        for (int[] o : new int[][] {{1, 1}, {-1, 1}, {1, -1}}) {
            Block rock = center.getRelative(o[0], 0, o[1]);
            if (rock.getType().isAir()) {
                rock.setType(Material.BLACKSTONE);
            }
        }
        center.setType(Material.CHEST);
        if (center.getState() instanceof Chest chest) {
            chest.customName(ItemManager.color("&6&lMeteor Cache"));
            chest.update();
            fill(((Chest) center.getState()).getBlockInventory());
        }
        Bukkit.broadcast(plugin.msg("meteor-landed", "&6☄ The meteor has landed at &e{x} {y} {z}&6! Its cache is waiting...",
                "x", String.valueOf(center.getX()), "y", String.valueOf(center.getY()), "z", String.valueOf(center.getZ())));
        if (plugin.warden() != null
                && ThreadLocalRandom.current().nextDouble(100) < plugin.getConfig().getDouble("meteor.warden-chance", 25)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.warden().spawn(center.getLocation().add(3.5, 0, 0.5), null), 40L);
        }
    }

    private void fill(Inventory inv) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<ItemManager.Entry> materials = plugin.bounty() == null ? List.of() : plugin.bounty().enabledMaterials();
        if (!materials.isEmpty()) {
            inv.setItem(13, materials.get(r.nextInt(materials.size())).item.clone());
        }
        inv.setItem(r.nextInt(9), new ItemStack(Material.DIAMOND, 2 + r.nextInt(4)));
        inv.setItem(18 + r.nextInt(4), new ItemStack(Material.GOLD_INGOT, 4 + r.nextInt(9)));
        inv.setItem(9 + r.nextInt(4), new ItemStack(Material.EXPERIENCE_BOTTLE, 8 + r.nextInt(9)));
        if (r.nextInt(100) < 30) {
            inv.setItem(14 + r.nextInt(4), new ItemStack(Material.NETHERITE_SCRAP, 1 + r.nextInt(2)));
        }
        int tokens = plugin.shop() == null || plugin.bounty() == null ? 0 : plugin.shop().meteorTokens();
        if (tokens > 0) {
            inv.setItem(22, plugin.bounty().token(tokens));
        }
        if (plugin.bounty() != null && r.nextDouble(100) < plugin.getConfig().getDouble("meteor.tracker-chance", 25)) {
            inv.setItem(4, plugin.bounty().tracker());
        }
    }
}
