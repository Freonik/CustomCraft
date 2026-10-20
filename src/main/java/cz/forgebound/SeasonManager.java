package cz.forgebound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Season start ceremony: countdown, "Welcome to ... SMP", effects around the host, a white beam into the sky,
 * then everyone is scattered across the SMP world. /forge season start | preview | cancel
 */
public final class SeasonManager {

    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 210, 90), 1.4f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.6f);

    private final Forgebound plugin;
    private BukkitRunnable running;
    private final List<BlockDisplay> beam = new ArrayList<>();
    private final List<Display> halo = new ArrayList<>();
    private static final Particle.DustOptions EMBER = new Particle.DustOptions(Color.fromRGB(255, 140, 40), 1.2f);
    private static final Particle.DustTransition COOLING =
            new Particle.DustTransition(Color.fromRGB(255, 170, 60), Color.fromRGB(70, 60, 55), 1.0f);
    private org.bukkit.entity.TextDisplay hologram;

    public SeasonManager(Forgebound plugin) {
        this.plugin = plugin;
    }

    private String cfg(String key, String def) {
        return plugin.getConfig().getString("season." + key, def);
    }

    private double num(String key, double def) {
        return plugin.getConfig().getDouble("season." + key, def);
    }

    /** "forge" (default) or "soul". */
    private boolean forge() {
        return !cfg("theme", "forge").equalsIgnoreCase("soul");
    }

    private List<ItemManager.Entry> legends() {
        List<ItemManager.Entry> list = new ArrayList<>();
        for (ItemManager.Kind kind : new ItemManager.Kind[] {ItemManager.Kind.WEAPON, ItemManager.Kind.RELIC}) {
            for (ItemManager.Entry e : plugin.items().all(kind)) {
                if (plugin.items().craftable(e)) {
                    list.add(e);
                }
            }
        }
        return list;
    }

    public boolean running() {
        return running != null;
    }

    public void cancel() {
        if (running != null) {
            running.cancel();
            running = null;
        }
        removeBeam();
        removeHalo();
        removeHologram();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }
    }

    public void shutdown() {
        cancel();
    }

    private static Title title(Component t, Component s, int fadeIn, int stay, int fadeOut) {
        return Title.title(t, s, Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)));
    }

    /** Starts the ceremony around "host" (or the world spawn). teleport=false is the preview. */
    public boolean start(Player host, boolean teleport) {
        if (running != null) {
            return false;
        }
        Location center = host != null ? host.getLocation() : Bukkit.getWorlds().get(0).getSpawnLocation();
        World world = center.getWorld();
        int countdown = Math.max(1, (int) num("countdown-seconds", 5));
        int welcomeAt = countdown * 20;
        int beamAt = welcomeAt + 60;
        int teleportAt = beamAt + (int) (num("beam-seconds", 5) * 20);
        int endAt = teleportAt + 60;
        boolean freeze = plugin.getConfig().getBoolean("season.freeze-players", true);
        if (freeze) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(host)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, teleportAt + 20, 6, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, teleportAt + 20, 200, false, false));
                }
            }
        }
        hologram(center, cfg("hologram-countdown", forge() ? "&6The Forge Awakens..." : "&7Hold still..."));
        running = new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                ambient(center, t);
                // countdown
                if (t < welcomeAt && t % 20 == 0) {
                    int n = countdown - t / 20;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(title(ItemManager.color(cfg("countdown-color", "&6&l") + n),
                                ItemManager.color(cfg("countdown-subtitle", "&7The season begins...")), 0, 18, 4));
                        if (forge()) {
                            // hammer on the anvil, louder every time
                            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f + (countdown - n) * 0.12f,
                                    0.6f + (countdown - n) * 0.08f);
                        } else {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f + (countdown - n) * 0.15f);
                        }
                    }
                    if (forge()) {
                        Location c = center.clone().add(0, 1, 0);
                        world.spawnParticle(Particle.LAVA, c, 10 + (t / 20) * 4, 0.3, 0.2, 0.3, 0, null, true);
                        world.spawnParticle(Particle.DUST, c, 30 + (t / 20) * 10, 0.6, 0.4, 0.6, 0, EMBER, true);
                    }
                }
                if (t < welcomeAt) {
                    ring(center, 1.5 + t * 0.06, t, forge() ? Particle.FLAME : Particle.END_ROD, 24);
                }
                // the moment
                if (t == welcomeAt) {
                    Component big = ItemManager.color(cfg("title", "&6&lWelcome to FORGEBOUND SMP"));
                    Component sub = ItemManager.color(cfg("subtitle", "&eSeason 1 has begun"));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(title(big, sub, 10, 80, 20));
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
                    }
                    Bukkit.broadcast(ItemManager.color(cfg("chat", "&6&l⚒ &eWelcome to &6&lFORGEBOUND SMP&e! Season 1 has begun.")));
                    if (hologram != null && hologram.isValid()) {
                        hologram.text(ItemManager.color(cfg("hologram", forge()
                                ? "&6&lWelcome to FORGEBOUND SMP\n&e{legends} legends. One of each. &7Season 1"
                                : "&6&lThe FORGEBOUND SMP Begins").replace("\\n", "\n")
                                .replace("{legends}", String.valueOf(legends().size()))));
                    }
                    spawnHalo(center);
                    for (int i = 0; i < 4; i++) {
                        double a = i * Math.PI / 2;
                        world.strikeLightningEffect(center.clone().add(Math.cos(a) * 6, 0, Math.sin(a) * 6));
                    }
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
                    firework(center.clone().add(0, 1, 0), Color.ORANGE, Color.YELLOW);
                    if (host != null && plugin.getConfig().getBoolean("season.lift-host", true)) {
                        host.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0, false, false));
                        host.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, endAt - t + 60, 0, false, false));
                    }
                }
                // building up around the host
                if (t >= welcomeAt && t < teleportAt) {
                    Location c = host != null && host.isOnline() ? host.getLocation() : center;
                    double power = Math.min(1, (t - welcomeAt) / 80.0);
                    helix(c, t, power);
                    if (t % 4 == 0) {
                        ring(c, 2.5 + (t % 40) * 0.15, t, Particle.ELECTRIC_SPARK, 32);
                    }
                    if (t % 25 == 0) {
                        double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                        firework(c.clone().add(Math.cos(a) * 8, 0, Math.sin(a) * 8),
                                ThreadLocalRandom.current().nextBoolean() ? Color.WHITE : Color.YELLOW, Color.ORANGE);
                    }
                }
                // the beam
                if (t == beamAt) {
                    spawnBeam(center);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.7f);
                        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, 0.5f);
                    }
                }
                if (t > welcomeAt && t < teleportAt) {
                    // the halo spins, and closes in on the beam towards the end
                    double close = t < beamAt ? 0 : Math.min(1, (double) (t - beamAt) / (teleportAt - beamAt));
                    moveHalo(center, t, close);
                }
                if (t > beamAt && t < teleportAt) {
                    pulseBeam(t - beamAt);
                    if (t % 10 == 0) {
                        world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 2f, 0.8f);
                    }
                }
                // everyone goes
                if (t == teleportAt - 12) {
                    boolean pack;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        pack = plugin.menus() != null && plugin.menus().usesPack(p);
                        if (pack) {
                            // a white flash over the whole screen (a full-screen glyph from the resource pack)
                            p.showTitle(title(flash(net.kyori.adventure.text.format.NamedTextColor.WHITE), Component.empty(),
                                    8, forge() ? 10 : 18, forge() ? 0 : 30));
                            if (forge()) {
                                // ...that turns into the glow of an open forge
                                Bukkit.getScheduler().runTaskLater(plugin, () -> p.showTitle(title(
                                        flash(net.kyori.adventure.text.format.TextColor.color(255, 140, 40)),
                                        Component.empty(), 0, 6, 30)), 18L);
                            }
                        } else {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false));
                        }
                        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.4f, 1.6f);
                        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
                    }
                    center.getWorld().spawnParticle(Particle.FLASH, center.clone().add(0, 4, 0), 3, 1, 2, 1, 0, Color.WHITE, true);
                    burstHalo();
                }
                if (t == teleportAt) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.removePotionEffect(PotionEffectType.SLOWNESS);
                        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    }
                    if (teleport) {
                        scatter();
                    }
                    removeBeam();
                    removeHalo();
                    removeHologram();
                }
                if (t >= endAt) {
                    cancel();
                    running = null;
                    return;
                }
                t++;
            }
        };
        running.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ================================================================ effects

    private void ring(Location c, double r, int t, Particle particle, int points) {
        World w = c.getWorld();
        for (int i = 0; i < points; i++) {
            double a = i * 2 * Math.PI / points + t * 0.05;
            w.spawnParticle(particle, c.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 1, 0, 0, 0, 0, null, true);
        }
    }

    private void helix(Location c, int t, double power) {
        World w = c.getWorld();
        int arms = 3;
        for (int arm = 0; arm < arms; arm++) {
            for (int k = 0; k < 2; k++) {
                double h = ((t * 0.12 + k * 1.5) % 4.5);
                double a = t * 0.25 + arm * 2 * Math.PI / arms + h;
                double r = 1.6 - h * 0.25;
                w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, h, Math.sin(a) * r), 1, 0, 0, 0, 0,
                        arm == 0 ? WHITE : GOLD, true);
            }
        }
        if (power > 0.5 && t % 2 == 0) {
            w.spawnParticle(Particle.END_ROD, c.clone().add(0, 1, 0), (int) (4 * power), 0.6, 0.8, 0.6, 0.05, null, true);
        }
    }

    private void firework(Location at, Color a, Color b) {
        at.getWorld().spawn(at, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(a, b)
                    .withFade(Color.WHITE).trail(true).flicker(true).build());
            meta.setPower(1);
            f.setFireworkMeta(meta);
            f.setTicksToDetonate(20 + ThreadLocalRandom.current().nextInt(15));
        });
    }

    private static Component flash(net.kyori.adventure.text.format.TextColor color) {
        return Component.text("\ue100").font(net.kyori.adventure.key.Key.key("customcraft", "flash")).color(color);
    }

    // ================================================================ hologram, ambient, halo

    private void hologram(Location center, String text) {
        removeHologram();
        Location at = center.clone().add(0, num("hologram-height", 4.5), 0);
        hologram = at.getWorld().spawn(at, org.bukkit.entity.TextDisplay.class, d -> {
            d.text(ItemManager.color(text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setViewRange(4f);
            d.setBrightness(new Display.Brightness(15, 15));
            float sc = (float) num("hologram-scale", 2.5);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(sc, sc, sc), new Quaternionf()));
        });
    }

    private void removeHologram() {
        if (hologram != null && hologram.isValid()) {
            hologram.remove();
        }
        hologram = null;
    }

    /** Forge: embers and sparks over the whole area. Soul: drifting soul flames. */
    private void ambient(Location c, int t) {
        if (forge()) {
            double radius = num("ambient-radius", 20);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            World w = c.getWorld();
            for (int i = 0; i < (int) num("ambient-amount", 12); i++) {
                double a = r.nextDouble(Math.PI * 2);
                double d = Math.sqrt(r.nextDouble()) * radius;
                Location at = c.clone().add(Math.cos(a) * d, r.nextDouble(0.5, 9), Math.sin(a) * d);
                switch (i % 3) {
                    case 0 -> w.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0, 0, 0, 0, COOLING, true);
                    case 1 -> w.spawnParticle(Particle.SMALL_FLAME, at, 0, r.nextDouble(-0.01, 0.01), 0.03,
                            r.nextDouble(-0.01, 0.01), 1, null, true);
                    default -> w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, EMBER, true);
                }
            }
            if (t % 5 == 0) {
                w.spawnParticle(Particle.LAVA, c.clone().add(r.nextDouble(-6, 6), 0.5, r.nextDouble(-6, 6)), 1, 0, 0, 0, 0, null, true);
            }
            return;
        }
        Particle particle;
        try {
            particle = Particle.valueOf(cfg("ambient-particle", "SOUL_FIRE_FLAME"));
        } catch (IllegalArgumentException ex) {
            particle = Particle.SOUL_FIRE_FLAME;
        }
        if (particle.getDataType() != Void.class) {
            particle = Particle.SOUL_FIRE_FLAME;
        }
        double radius = num("ambient-radius", 20);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < (int) num("ambient-amount", 12); i++) {
            double a = r.nextDouble(Math.PI * 2);
            double d = Math.sqrt(r.nextDouble()) * radius;
            c.getWorld().spawnParticle(particle, c.clone().add(Math.cos(a) * d, r.nextDouble(0.5, 9), Math.sin(a) * d),
                    0, r.nextDouble(-0.01, 0.01), -0.04, r.nextDouble(-0.01, 0.01), 1, null, true);
        }
    }

    /** Forge: every legend on the server, spinning above the center. Soul: a ring of blocks. */
    private void spawnHalo(Location center) {
        removeHalo();
        if (forge()) {
            List<ItemManager.Entry> list = legends();
            for (ItemManager.Entry e : list) {
                org.bukkit.inventory.ItemStack shown = e.item.clone();
                halo.add(center.getWorld().spawn(center.clone().add(0, 7, 0), org.bukkit.entity.ItemDisplay.class, d -> {
                    d.setItemStack(shown);
                    d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);
                    d.setPersistent(false);
                    d.setTeleportDuration(2);
                    d.setViewRange(6f);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setGlowing(true);
                    d.setGlowColorOverride(Color.fromRGB(255, 150, 50));
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                            new Vector3f(1.3f, 1.3f, 1.3f), new Quaternionf()));
                }));
            }
            return;
        }
        Material block = Material.matchMaterial(cfg("halo-block", "CRYING_OBSIDIAN"));
        if (block == null || !block.isBlock()) {
            block = Material.CRYING_OBSIDIAN;
        }
        int n = (int) num("halo-blocks", 18);
        Material b = block;
        for (int i = 0; i < n; i++) {
            halo.add(center.getWorld().spawn(center.clone().add(0, 7, 0), BlockDisplay.class, d -> {
                d.setBlock(b.createBlockData());
                d.setPersistent(false);
                d.setTeleportDuration(2);
                d.setViewRange(6f);
                d.setBrightness(new Display.Brightness(12, 12));
                d.setTransformation(new Transformation(new Vector3f(-0.4f, -0.4f, -0.4f), new Quaternionf(),
                        new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
            }));
        }
    }

    private void moveHalo(Location center, int t, double close) {
        int n = halo.size();
        double base = num("halo-radius", 5) * (1 - 0.6 * close);
        double height = 7 + Math.sin(t * 0.05) * 0.4;
        for (int i = 0; i < n; i++) {
            Display d = halo.get(i);
            if (!d.isValid()) {
                continue;
            }
            double radius = base;
            double y = height;
            // near the end the legends dive into the beam one after another
            double start = 0.55 + 0.4 * i / Math.max(1, n);
            double dive = Math.max(0, Math.min(1, (close - start) / 0.08));
            if (dive > 0) {
                radius *= 1 - dive;
                y = height - 3 * dive;
            }
            double a = i * 2 * Math.PI / n + t * (0.04 + 0.12 * close);
            Location at = center.clone().add(Math.cos(a) * radius, y + Math.sin(a) * radius * 0.25, Math.sin(a) * radius);
            at.setYaw((float) Math.toDegrees(-a) + 90f);
            d.teleport(at);
            if (dive >= 1) {
                World w = center.getWorld();
                w.spawnParticle(Particle.LAVA, at, 6, 0.2, 0.2, 0.2, 0, null, true);
                w.spawnParticle(Particle.DUST, at, 15, 0.3, 0.3, 0.3, 0, forge() ? EMBER : WHITE, true);
                w.playSound(at, Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.6f);
                d.remove();
            }
        }
    }

    private void burstHalo() {
        for (Display d : halo) {
            if (!d.isValid()) {
                continue;
            }
            if (d instanceof BlockDisplay bd) {
                d.getWorld().spawnParticle(Particle.BLOCK, d.getLocation(), 20, 0.3, 0.3, 0.3, 0, bd.getBlock(), true);
            }
            d.getWorld().spawnParticle(Particle.END_ROD, d.getLocation(), 6, 0.2, 0.2, 0.2, 0.2, null, true);
        }
        removeHalo();
    }

    private void removeHalo() {
        for (Display d : halo) {
            if (d.isValid()) {
                d.remove();
            }
        }
        halo.clear();
    }

    // ================================================================ the beam (block displays, not particles)

    private BlockDisplay pillar(Location base, Material block, float width, float height, int brightness) {
        return base.getWorld().spawn(base, BlockDisplay.class, d -> {
            d.setBlock(block.createBlockData());
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(brightness, brightness));
            d.setViewRange(16f);                            // visible from far away
            d.setDisplayHeight(height + 2);
            d.setDisplayWidth(width + 2);
            d.setInterpolationDuration(0);
            d.setTransformation(new Transformation(new Vector3f(-width / 2, 0, -width / 2), new Quaternionf(),
                    new Vector3f(width, 0.01f, width), new Quaternionf()));
        });
    }

    private void spawnBeam(Location center) {
        removeBeam();
        float height = (float) Math.max(16, center.getWorld().getMaxHeight() + 64 - center.getY());
        Location base = center.clone();
        base.setYaw(0);
        base.setPitch(0);
        BlockDisplay core = pillar(base, Material.WHITE_CONCRETE, 0.5f, height, 15);
        Material glowBlock = Material.matchMaterial(cfg("beam-glow-block", forge() ? "ORANGE_STAINED_GLASS" : "WHITE_STAINED_GLASS"));
        BlockDisplay glow = pillar(base, glowBlock == null ? Material.WHITE_STAINED_GLASS : glowBlock, 1.1f, height, 15);
        beam.add(core);
        beam.add(glow);
        // grow up into the sky over 1.5 s
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (BlockDisplay d : beam) {
                if (!d.isValid()) {
                    continue;
                }
                float w = d == core ? 0.5f : 1.1f;
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(30);
                d.setTransformation(new Transformation(new Vector3f(-w / 2, 0, -w / 2), new Quaternionf(),
                        new Vector3f(w, height, w), new Quaternionf()));
            }
        }, 2L);
        center.getWorld().spawnParticle(Particle.FLASH, center.clone().add(0, 1, 0), 2, 0, 0, 0, 0, Color.WHITE, true);
    }

    /** The beam slowly gets wider and brighter while it pulses. */
    private void pulseBeam(int age) {
        if (age < 32 || age % 10 != 0 || beam.size() < 2) {
            return;
        }
        BlockDisplay glow = beam.get(1);
        if (!glow.isValid()) {
            return;
        }
        Transformation tr = glow.getTransformation();
        float h = tr.getScale().y();
        float w = 1.1f + Math.min(1.4f, age / 60f) + (age / 10 % 2 == 0 ? 0.25f : 0f);
        glow.setInterpolationDelay(0);
        glow.setInterpolationDuration(10);
        glow.setTransformation(new Transformation(new Vector3f(-w / 2, 0, -w / 2), new Quaternionf(),
                new Vector3f(w, h, w), new Quaternionf()));
    }

    private void removeBeam() {
        for (BlockDisplay d : beam) {
            if (d.isValid()) {
                d.remove();
            }
        }
        beam.clear();
    }

    // ================================================================ scatter

    private void scatter() {
        String name = cfg("world", "");
        World world = name.isEmpty() ? Bukkit.getWorlds().get(0) : Bukkit.getWorld(name);
        if (world == null) {
            plugin.getLogger().warning("Season: world '" + name + "' doesn't exist, nobody was teleported.");
            return;
        }
        int min = (int) num("radius-min", 200);
        int max = Math.max(min + 1, (int) num("radius-max", 1500));
        boolean sky = plugin.getConfig().getBoolean("season.drop-from-sky", true);
        Component title = ItemManager.color(cfg("arrive-title", "&6&lGood luck"));
        Component sub = ItemManager.color(cfg("arrive-subtitle", "&7Forge your legend."));
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        // a few per tick, so the server doesn't load all chunks at once
        new BukkitRunnable() {
            int i;

            @Override
            public void run() {
                for (int n = 0; n < 2 && i < players.size(); n++, i++) {
                    Player p = players.get(i);
                    if (!p.isOnline()) {
                        continue;
                    }
                    Location at = EventManager.surface(world, world.getSpawnLocation(), min, max);
                    if (at == null) {
                        at = world.getSpawnLocation();
                    }
                    if (sky) {
                        Block top = at.getBlock();
                        at = top.getLocation().add(0.5, Math.min(80, world.getMaxHeight() - top.getY() - 5), 0.5);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 20, 0, false, true));
                    }
                    p.teleportAsync(at);
                    if (plugin.codex() != null) {
                        plugin.codex().giveIfMissing(p);
                    }
                    p.showTitle(title(title, sub, 10, 50, 20));
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
                }
                if (i >= players.size()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
