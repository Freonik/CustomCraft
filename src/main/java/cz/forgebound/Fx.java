package cz.forgebound;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weapon effects made of display entities instead of only particles: slashes and rings drawn with textures from the
 * resource pack, the ground cracking open in a wave, floating rubble, a ghost of the weapon and an afterimage of the
 * player. The client animates between the states the server sends (interpolation), so it stays smooth and cheap.
 * Nothing here is saved with the world (setPersistent(false)) and everything removes itself.
 */
final class Fx {

    private static final Quaternionf FLAT = new Quaternionf().rotateX((float) -Math.PI / 2);   // a sprite lying on the ground

    private final Forgebound plugin;
    private final Set<Entity> alive = Collections.newSetFromMap(new WeakHashMap<>());

    Fx(Forgebound plugin) {
        this.plugin = plugin;
    }

    boolean enabled() {
        return plugin.getConfig().getBoolean("ability-settings.display-effects", true);
    }

    /** Server stop / reload: nothing may stay behind. */
    void clear() {
        for (Entity e : new ArrayList<>(alive)) {
            if (e.isValid()) {
                e.remove();
            }
        }
        alive.clear();
    }

    // ================================================================ building blocks

    private ItemStack sprite(String name) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("customcraft", "fx_" + name));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemDisplay item(Location at, ItemStack stack, Transformation start, Color glow) {
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(stack);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(Display.Billboard.FIXED);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(start);
            e.setViewRange(1.5f);
            if (glow != null) {
                e.setGlowing(true);
                e.setGlowColorOverride(glow);
            }
        });
        alive.add(d);
        return d;
    }

    private BlockDisplay block(Location at, BlockData data, Transformation start) {
        BlockDisplay d = at.getWorld().spawn(at, BlockDisplay.class, e -> {
            e.setPersistent(false);
            e.setBlock(data);
            e.setTransformation(start);
            e.setViewRange(1.2f);
        });
        alive.add(d);
        return d;
    }

    /** Moves a display to a new transformation over `ticks` (from the next tick on, so the client sees the start). */
    private void animate(Display d, int delay, int ticks, Transformation to) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (d.isValid()) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(ticks);
                d.setTransformation(to);
            }
        }, Math.max(1, delay));
    }

    private void removeLater(Entity e, int ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.isValid()) {
                e.remove();
            }
            alive.remove(e);
        }, ticks);
    }

    private static Transformation tf(float tx, float ty, float tz, Quaternionf rot, float scale) {
        return new Transformation(new Vector3f(tx, ty, tz), rot, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /** The solid block a column stands on (looking a few blocks up and down), or null. */
    private static Block ground(World world, double x, double y, double z) {
        Block b = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y) + 1, (int) Math.floor(z));
        for (int i = 0; i < 5; i++, b = b.getRelative(0, -1, 0)) {
            if (b.getType().isSolid() && b.getType().isOccluding() && !b.getRelative(0, 1, 0).getType().isOccluding()) {
                return b;
            }
        }
        return null;
    }

    // ================================================================ effects

    /**
     * The ground cracks open in a wave: rings of the real ground blocks jump up, tilt and drop back, from the centre
     * outwards.
     */
    void shockwave(Location center, double radius) {
        if (!enabled()) {
            return;
        }
        World world = center.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int budget = 70;
        for (double ring = 1; ring <= radius && budget > 0; ring += 1) {
            int count = (int) Math.round(2 * Math.PI * ring * 0.9);
            double offset = r.nextDouble() * Math.PI;
            for (int k = 0; k < count && budget > 0; k++) {
                double a = offset + 2 * Math.PI * k / count;
                Block g = ground(world, center.getX() + Math.cos(a) * ring, center.getY(), center.getZ() + Math.sin(a) * ring);
                if (g == null) {
                    continue;
                }
                budget--;
                Location at = g.getLocation();
                BlockDisplay d = block(at, g.getBlockData(),
                        tf(-0.005f, -0.005f, -0.005f, new Quaternionf(), 1.01f));
                int delay = 1 + (int) Math.round(ring * 1.6);
                float lift = (float) (0.35 + 0.45 * (1 - ring / (radius + 1)) + r.nextDouble() * 0.2);
                Quaternionf tilt = new Quaternionf().rotateX((float) (r.nextDouble() - 0.5) * 0.7f)
                        .rotateZ((float) (r.nextDouble() - 0.5) * 0.7f);
                animate(d, delay, 3, tf(0, lift, 0, tilt, 1.0f));
                animate(d, delay + 4, 5, tf(-0.005f, -0.005f, -0.005f, new Quaternionf(), 1.01f));
                removeLater(d, delay + 11);
            }
        }
        world.spawnParticle(Particle.BLOCK, center, 40, radius / 2, 0.2, radius / 2, 0,
                ground(world, center.getX(), center.getY(), center.getZ()) instanceof Block b ? b.getBlockData()
                        : Material.STONE.createBlockData());
    }

    /** A flat textured ring on the ground that grows from `from` to `to` blocks across and disappears. */
    void ring(Location center, String sprite, float from, float to, int ticks, Color glow) {
        if (!enabled()) {
            return;
        }
        ItemDisplay d = item(center.clone().add(0, 0.08, 0), sprite(sprite), tf(0, 0, 0, new Quaternionf(FLAT), from), glow);
        animate(d, 1, ticks, tf(0, 0, 0, new Quaternionf(FLAT), to));
        removeLater(d, ticks + 2);
    }

    /** A slash: a drawn crescent in front of the player that sweeps around and fades out. */
    void slash(Location at, float yaw, float tilt, float size) {
        slash(at, yaw, tilt, size, "echo");
    }

    /** kind: echo, blood, silk, soul, venom, frost. */
    void slash(Location at, float yaw, float tilt, float size, String kind) {
        if (!enabled()) {
            return;
        }
        float face = (float) (Math.PI - Math.toRadians(yaw));
        Quaternionf start = new Quaternionf().rotateY(face - 0.6f).rotateX((float) -Math.PI / 2 + tilt);
        Quaternionf end = new Quaternionf().rotateY(face + 0.6f).rotateX((float) -Math.PI / 2 + tilt);
        ItemDisplay d = item(at, sprite(kind + "_slash"), tf(0, 0, 0, start, size * 0.7f), null);
        animate(d, 1, 3, tf(0, 0, 0, end, size));
        animate(d, 5, 3, tf(0, 0, 0, end, 0.01f));
        removeLater(d, 10);
    }

    /**
     * The weapon's ghost hangs in the air next to the target, then cuts through it after `delay` ticks
     * (the Echo of the Echo Blade).
     */
    void ghostStrike(Player attacker, LivingEntity target, ItemStack weapon, int delay) {
        if (!enabled() || weapon == null) {
            return;
        }
        Location eye = attacker.getEyeLocation();
        org.bukkit.util.Vector right = eye.getDirection().crossProduct(new org.bukkit.util.Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-4) {
            right = new org.bukkit.util.Vector(1, 0, 0);
        }
        right.normalize();
        Location at = target.getLocation().add(0, target.getHeight() + 0.4, 0).add(right.multiply(0.9));
        float face = (float) (Math.PI - Math.toRadians(eye.getYaw()));
        // the model lies diagonally (like a sprite): +45 degrees stands it upright, point up
        Quaternionf raised = new Quaternionf().rotateY(face).rotateZ((float) Math.PI / 4 + 0.35f);
        Quaternionf cut = new Quaternionf().rotateY(face).rotateZ((float) Math.PI / 4 - 2.1f);
        ItemStack ghost = weapon.clone();
        ItemDisplay d = item(at, ghost, tf(0, 0, 0, raised, 0.01f), Color.fromRGB(90, 230, 240));
        animate(d, 1, 5, tf(0, 0, 0, raised, 1.5f));                                  // appears
        animate(d, Math.max(3, delay - 2), 3, tf(-0.6f, -0.9f, 0, cut, 1.5f));       // cuts down through the target
        animate(d, delay + 3, 4, tf(-0.6f, -1.2f, 0, cut, 0.01f));                   // and fades
        removeLater(d, delay + 9);
    }

    /** An afterimage of the player that stays behind for a moment (Rewind). */
    void afterimage(Player player, Location at, int ticks) {
        if (!enabled()) {
            return;
        }
        try {
            Mannequin m = at.getWorld().spawn(at, Mannequin.class, e -> {
                e.setPersistent(false);
                e.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
                e.setDescription(null);
                e.setImmovable(true);
                e.setAI(false);
                e.setGravity(false);
                e.setInvulnerable(true);
                e.setSilent(true);
                e.setCollidable(false);
                e.setGlowing(true);
                for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                        EquipmentSlot.FEET, EquipmentSlot.HAND}) {
                    ItemStack item = player.getInventory().getItem(slot);
                    e.getEquipment().setItem(slot, item == null ? null : item.clone());
                    e.getEquipment().setDropChance(slot, 0f);
                }
            });
            alive.add(m);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (m.isValid()) {
                    m.getWorld().spawnParticle(Particle.SCULK_SOUL, m.getLocation().add(0, 1, 0), 18, 0.3, 0.6, 0.3, 0.03);
                    m.remove();
                }
                alive.remove(m);
            }, ticks);
        } catch (RuntimeException | LinkageError ex) {
            // older server without mannequins: just the particles
        }
    }

    /**
     * Rubble torn out of the ground that circles a point (Gravity Anchor) for `ticks`, then falls back.
     */
    void orbit(Location center, double radius, int pieces, int ticks) {
        if (!enabled()) {
            return;
        }
        World world = center.getWorld();
        Block g = ground(world, center.getX(), center.getY(), center.getZ());
        BlockData data = g != null ? g.getBlockData() : Material.COBBLESTONE.createBlockData();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<BlockDisplay> rocks = new ArrayList<>();
        List<double[]> orbits = new ArrayList<>();
        for (int i = 0; i < pieces; i++) {
            float s = (float) (0.35 + r.nextDouble() * 0.3);
            BlockDisplay d = block(center.clone(), data, tf(-s / 2, -s / 2, -s / 2, new Quaternionf(), s));
            d.setTeleportDuration(2);
            rocks.add(d);
            orbits.add(new double[] {2 * Math.PI * i / pieces, radius * (0.55 + r.nextDouble() * 0.45), 0.8 + r.nextDouble() * 1.6,
                    0.05 + r.nextDouble() * 0.05});
        }
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t += 2;
                for (int i = 0; i < rocks.size(); i++) {
                    BlockDisplay d = rocks.get(i);
                    if (!d.isValid()) {
                        continue;
                    }
                    double[] o = orbits.get(i);
                    double a = o[0] + t * o[3];
                    double rise = Math.min(1, t / 12.0) * o[2] + Math.sin(t * 0.15 + i) * 0.15;
                    if (t >= ticks) {
                        rise = Math.max(0, o[2] - (t - ticks) * 0.35);           // falling back down
                    }
                    Location at = center.clone().add(Math.cos(a) * o[1], rise, Math.sin(a) * o[1]);
                    at.setYaw((float) Math.toDegrees(a));
                    d.teleport(at);
                }
                if (t >= ticks + 8) {
                    for (BlockDisplay d : rocks) {
                        d.getWorld().spawnParticle(Particle.BLOCK, d.getLocation(), 6, 0.2, 0.1, 0.2, 0, data);
                        d.remove();
                        alive.remove(d);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    /** A spinning ring of runes on the ground (Gravity Anchor's field). */
    void fieldRing(Location center, double radius, int ticks, Color glow) {
        if (!enabled()) {
            return;
        }
        float size = (float) (radius * 2 / 0.84);
        ItemDisplay d = item(center.clone().add(0, 0.06, 0), sprite("gravity_ring"), tf(0, 0, 0, new Quaternionf(FLAT), 0.5f), glow);
        animate(d, 1, 6, tf(0, 0, 0, new Quaternionf(FLAT), size));
        new BukkitRunnable() {
            int t = 8;

            @Override
            public void run() {
                if (!d.isValid() || t >= ticks) {
                    if (d.isValid()) {
                        d.setInterpolationDelay(0);
                        d.setInterpolationDuration(5);
                        d.setTransformation(tf(0, 0, 0, new Quaternionf(FLAT), 0.01f));
                        removeLater(d, 7);
                    }
                    cancel();
                    return;
                }
                float angle = (float) (t / 10 * Math.PI / 2);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(10);
                d.setTransformation(tf(0, 0, 0, new Quaternionf().rotateY(angle).mul(FLAT), size));
                t += 10;
            }
        }.runTaskTimer(plugin, 8L, 10L);
    }

    /** Spikes of a block (ice) bursting out of the ground at a spot, then sinking back. */
    void spikes(Location at, BlockData data, int count, double spread, float height) {
        if (!enabled()) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Location p = at.clone().add((r.nextDouble() - 0.5) * spread * 2, 0, (r.nextDouble() - 0.5) * spread * 2);
            Block g = ground(p.getWorld(), p.getX(), p.getY(), p.getZ());
            if (g == null) {
                continue;
            }
            Location base = new Location(p.getWorld(), p.getX(), g.getY() + 1, p.getZ());
            float w = (float) (0.3 + r.nextDouble() * 0.25);
            float h = height * (float) (0.6 + r.nextDouble() * 0.6);
            Quaternionf lean = new Quaternionf().rotateY((float) (r.nextDouble() * Math.PI))
                    .rotateX((float) (r.nextDouble() - 0.5) * 0.6f);
            BlockDisplay d = block(base, data, new Transformation(new Vector3f(-w / 2, -h, -w / 2), lean,
                    new Vector3f(w, h, w), new Quaternionf()));
            animate(d, 1, 2, new Transformation(new Vector3f(-w / 2, 0, -w / 2), lean, new Vector3f(w, h, w), new Quaternionf()));
            animate(d, 22 + r.nextInt(8), 6, new Transformation(new Vector3f(-w / 2, -h, -w / 2), lean,
                    new Vector3f(w, h, w), new Quaternionf()));
            removeLater(d, 38);
        }
    }

    /** The weapon itself sweeps once around the player (a scythe reaping in a full circle). */
    void sweep(Player player, ItemStack weapon, double radius, int ticks) {
        if (!enabled() || weapon == null || weapon.getType().isAir()) {
            return;
        }
        Location center = player.getLocation().add(0, 1.0, 0);
        float start = (float) Math.toRadians(-player.getLocation().getYaw());
        ItemDisplay d = item(center, weapon.clone(), tf((float) radius, 0, 0,
                new Quaternionf().rotateY(start).rotateZ((float) -Math.PI / 4), 1.6f), Color.fromRGB(120, 220, 240));
        d.setTeleportDuration(1);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (!d.isValid() || t > ticks) {
                    if (d.isValid()) {
                        d.remove();
                    }
                    alive.remove(d);
                    cancel();
                    return;
                }
                float a = start + (float) (2 * Math.PI * t / ticks);
                // the blade lies flat and points outward, turning with the sweep
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(1);
                Vector3f off = new Vector3f((float) Math.cos(a) * (float) radius, 0, (float) -Math.sin(a) * (float) radius);
                d.setTransformation(new Transformation(off, new Quaternionf().rotateY(a).rotateX((float) Math.PI / 2)
                        .rotateZ((float) -Math.PI / 4), new Vector3f(1.6f), new Quaternionf()));
                d.teleport(player.getLocation().add(0, 1.0, 0));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Rows of fangs close on a target from above and below (Devour). */
    void jaws(LivingEntity target) {
        if (!enabled()) {
            return;
        }
        Location c = target.getLocation().add(0, target.getHeight() / 2, 0);
        BlockData bone = Material.BONE_BLOCK.createBlockData();
        double h = target.getHeight() / 2 + 0.7;
        for (int row = -1; row <= 1; row += 2) {
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6;
                float s = 0.28f;
                Location at = c.clone().add(Math.cos(a) * 0.7, 0, Math.sin(a) * 0.7);
                Quaternionf point = new Quaternionf().rotateX(row > 0 ? (float) Math.PI : 0);
                BlockDisplay d = block(at, bone, new Transformation(new Vector3f(-s / 2, (float) (row * h), -s / 2), point,
                        new Vector3f(s, 0.7f, s), new Quaternionf()));
                animate(d, 1, 3, new Transformation(new Vector3f(-s / 2, (float) (row * 0.25), -s / 2), point,
                        new Vector3f(s, 0.7f, s), new Quaternionf()));
                animate(d, 7, 4, new Transformation(new Vector3f(-s / 2, (float) (row * 0.25), -s / 2), point,
                        new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
                removeLater(d, 13);
            }
        }
    }

    /** A chunk of the ground that rises under a lifted enemy and stays with them (Upheaval). */
    void liftRubble(LivingEntity target, int ticks) {
        if (!enabled()) {
            return;
        }
        Block g = ground(target.getWorld(), target.getLocation().getX(), target.getLocation().getY(), target.getLocation().getZ());
        if (g == null) {
            return;
        }
        BlockData data = g.getBlockData();
        List<BlockDisplay> parts = new ArrayList<>();
        float[][] shape = {{-0.45f, 0.7f}, {0.05f, 0.55f}, {-0.2f, 0.5f}};
        for (float[] s : shape) {
            BlockDisplay d = block(target.getLocation().add(s[0], -0.6, s[0] * 0.7), data,
                    tf(-s[1] / 2, -s[1] / 2, -s[1] / 2, new Quaternionf().rotateY(s[0] * 3), s[1]));
            d.setTeleportDuration(2);
            parts.add(d);
        }
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t += 2;
                boolean done = t >= ticks || !target.isValid();
                for (int i = 0; i < parts.size(); i++) {
                    BlockDisplay d = parts.get(i);
                    if (done) {
                        d.getWorld().spawnParticle(Particle.BLOCK, d.getLocation(), 8, 0.2, 0.2, 0.2, 0, data);
                        d.remove();
                        alive.remove(d);
                    } else {
                        d.teleport(target.getLocation().add(shape[i][0], -0.55, shape[i][0] * 0.7));
                    }
                }
                if (done) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }
}
