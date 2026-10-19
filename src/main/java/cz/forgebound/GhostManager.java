package cz.forgebound;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Doppelganger: when someone stays top 1 on the bounty board for hours, "they" join the server - a glitched
 * copy of that player in legendary gear, hunting them. If it wins, it takes one of their legends and keeps
 * coming back with it until somebody kills it.
 */
public final class GhostManager implements Listener {

    private static final Particle.DustOptions BLACK = new Particle.DustOptions(Color.fromRGB(10, 10, 12), 1.4f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(235, 235, 240), 1.0f);
    private static final Particle.DustOptions ICE = new Particle.DustOptions(Color.fromRGB(150, 220, 255), 1.3f);
    private static final Particle.DustOptions VOID = new Particle.DustOptions(Color.fromRGB(110, 40, 190), 1.3f);
    private static final List<String> DEFAULT_LINES = List.of(
            "i can see you, {target}",
            "why do you run, {target}",
            "that legend was never yours",
            "do you hear the forge? it remembers you",
            "behind you",
            "{target}. {target}. {target}.",
            "we are the same, {target}. only one of us stays",
            "you made me");

    private final Forgebound plugin;
    private final NamespacedKey ghostKey;
    private final File file;
    private final YamlConfiguration data;

    private Mannequin body;
    private UUID target;
    private String copyName;            // whose copy it is (the original victim)
    private Component glitched;
    private double hp;
    private double maxHp;
    private long endsAt;
    private long nextAbilityAt;
    private long nextBlinkAt;
    private long nextChatAt;
    private long lastHitOnTarget;
    private long offlineSince;
    private int attackCooldown;
    private int healsUsed;
    private Location lastPos;
    private int stuckTicks;
    private int blockedTicks;
    private long nextWindAt;
    private long flyingUntil;
    private BossBar bar;
    private final java.util.Map<UUID, Double> damage = new java.util.HashMap<>();

    public GhostManager(Forgebound plugin) {
        this.plugin = plugin;
        this.ghostKey = Forgebound.key("doppelganger");
        this.file = new File(plugin.getDataFolder(), "ghost.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private double cfg(String key, double def) {
        return plugin.getConfig().getDouble("doppelganger." + key, def);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("doppelganger.enabled", true);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save ghost.yml: " + ex.getMessage());
        }
    }

    public boolean active() {
        return body != null && body.isValid();
    }

    public String targetName() {
        Player p = target == null ? null : Bukkit.getPlayer(target);
        return p == null ? "?" : p.getName();
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> stolen() {
        List<?> list = data.getList("stolen.items");
        List<ItemStack> out = new ArrayList<>();
        if (list != null) {
            for (Object o : list) {
                if (o instanceof ItemStack s) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    public void start() {
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (active()) {
                    tick();
                } else if (t % 1200 == 0) {
                    minute();
                }
            }
        }.runTaskTimer(plugin, 100L, 1L);
    }

    public void shutdown() {
        if (active()) {
            leave(false);
        }
        save();
    }

    // ================================================================ when does it come

    /** Every minute: who has been top 1 on the bounty board, and for how long. */
    private void minute() {
        if (!enabled() || plugin.bounty() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        // the stolen legends bring it back
        if (!stolen().isEmpty() && now >= data.getLong("stolen.next-return", 0)) {
            data.set("stolen.next-return", now + (long) (cfg("return-minutes", 30) * 60_000L));
            save();
            List<Player> candidates = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (survival(p)) {
                    candidates.add(p);
                }
            }
            if (candidates.size() >= (int) cfg("min-players", 1)) {
                Player victim = Bukkit.getPlayer(UUID.fromString(data.getString("stolen.victim", new UUID(0, 0).toString())));
                Player pick = victim != null && survival(victim) ? victim
                        : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
                spawn(pick, data.getString("stolen.victim-name", pick.getName()));
                return;
            }
        }
        // top 1 on the bounty board (offline time counts too)
        UUID top = null;
        double best = cfg("min-bounty", 5) - 0.5;
        for (Map.Entry<UUID, Double> e : plugin.bounty().allBounties().entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                top = e.getKey();
            }
        }
        String saved = data.getString("top.uuid");
        if (top == null) {
            if (saved != null) {
                data.set("top", null);
                save();
            }
            return;
        }
        if (!top.toString().equals(saved)) {
            data.set("top.uuid", top.toString());
            data.set("top.since", now);
            save();
            return;
        }
        long since = data.getLong("top.since", now);
        if (now - since < (long) (cfg("hours-as-top-bounty", 3) * 3_600_000L)) {
            return;
        }
        Player p = Bukkit.getPlayer(top);
        if (p != null && survival(p) && Bukkit.getOnlinePlayers().size() >= (int) cfg("min-players", 1)) {
            spawn(p, p.getName());
        }
    }

    private static boolean survival(Player p) {
        return !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    // ================================================================ spawning

    /** The name with some letters scrambled (obfuscated). */
    private static Component glitch(String name) {
        Component c = Component.empty();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean any = false;
        for (int i = 0; i < name.length(); i++) {
            boolean scramble = r.nextDouble() < 0.35 || !any && i == name.length() - 1;
            any |= scramble;
            Component ch = Component.text(String.valueOf(name.charAt(i)));
            c = c.append(scramble ? ch.decorate(TextDecoration.OBFUSCATED) : ch);
        }
        return c;
    }

    private Location spawnSpot(Player victim) {
        Location at = victim.getLocation();
        if (victim.getWorld().getEnvironment() == World.Environment.NORMAL) {
            Location s = EventManager.surface(victim.getWorld(), at, 18, 32);
            if (s != null && Math.abs(s.getY() - at.getY()) < 20) {
                return s;
            }
        }
        for (int i = 0; i < 30; i++) {
            Location l = at.clone().add(ThreadLocalRandom.current().nextInt(-14, 15), 0, ThreadLocalRandom.current().nextInt(-14, 15));
            for (int dy = -3; dy <= 3; dy++) {
                Block feet = l.clone().add(0, dy, 0).getBlock();
                if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).getType().isSolid()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return at.clone().add(at.getDirection().multiply(-6).setY(0));
    }

    /** Admin / scheduler: the Doppelganger joins and hunts "victim". */
    public boolean spawn(Player victim, String copyOf) {
        if (active() || victim == null) {
            return false;
        }
        target = victim.getUniqueId();
        copyName = copyOf;
        glitched = glitch(copyOf);
        Player original = Bukkit.getPlayerExact(copyOf);
        ResolvableProfile skin = ResolvableProfile.resolvableProfile((original != null ? original : victim).getPlayerProfile());
        maxHp = cfg("health", 600);
        hp = maxHp;
        healsUsed = 0;
        damage.clear();
        long now = System.currentTimeMillis();
        endsAt = now + (long) (cfg("hunt-minutes", 30) * 60_000L);
        nextAbilityAt = now + 4000;
        nextBlinkAt = now + 15000;
        nextChatAt = now + 20000;
        offlineSince = 0;
        Location at = spawnSpot(victim);
        List<ItemStack> stolen = stolen();
        body = at.getWorld().spawn(at, Mannequin.class, m -> {
            m.setProfile(skin);
            m.setDescription(null);
            m.customName(glitched);
            m.setCustomNameVisible(true);
            m.setPersistent(false);
            m.setInvulnerable(false);
            m.getPersistentDataContainer().set(ghostKey, PersistentDataType.BYTE, (byte) 1);
            AttributeInstance kb = m.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.setBaseValue(1);
            }
            EntityEquipment eq = m.getEquipment();
            eq.setHelmet(trimmed(Material.NETHERITE_HELMET));
            eq.setChestplate(trimmed(Material.NETHERITE_CHESTPLATE));
            eq.setLeggings(trimmed(Material.NETHERITE_LEGGINGS));
            eq.setBoots(trimmed(Material.NETHERITE_BOOTS));
            eq.setItemInMainHand(handItem(stolen));
            if (plugin.getConfig().getBoolean("doppelganger.wind-charges", true)) {
                eq.setItemInOffHand(new ItemStack(Material.WIND_CHARGE, 16));
            }
        });
        bar = BossBar.bossBar(glitched.color(NamedTextColor.WHITE), 1f, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_20);
        // a perfectly normal join message
        Bukkit.broadcast(Component.translatable("multiplayer.player.joined", NamedTextColor.YELLOW, glitched));
        glitchFx(at);
        if (!stolen.isEmpty()) {
            chat("i still have your " + plain(stolen.get(0)) + ", " + copyOf);
        }
        plugin.getLogger().info("Doppelganger of " + copyOf + " is hunting " + victim.getName());
        return true;
    }

    /** Netherite with the armor trim from the config (doppelganger.trim). */
    private ItemStack trimmed(Material piece) {
        ItemStack stack = new ItemStack(piece);
        if (!plugin.getConfig().getBoolean("doppelganger.trim.enabled", true)) {
            return stack;
        }
        try {
            var access = io.papermc.paper.registry.RegistryAccess.registryAccess();
            org.bukkit.inventory.meta.trim.TrimPattern pattern = access
                    .getRegistry(io.papermc.paper.registry.RegistryKey.TRIM_PATTERN)
                    .get(NamespacedKey.minecraft(plugin.getConfig().getString("doppelganger.trim.pattern", "silence")));
            org.bukkit.inventory.meta.trim.TrimMaterial material = access
                    .getRegistry(io.papermc.paper.registry.RegistryKey.TRIM_MATERIAL)
                    .get(NamespacedKey.minecraft(plugin.getConfig().getString("doppelganger.trim.material", "quartz")));
            if (pattern != null && material != null
                    && stack.getItemMeta() instanceof org.bukkit.inventory.meta.ArmorMeta meta) {
                meta.setTrim(new org.bukkit.inventory.meta.trim.ArmorTrim(material, pattern));
                stack.setItemMeta(meta);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Doppelganger: bad armor trim in the config (" + ex.getMessage() + ")");
        }
        return stack;
    }

    private static String plain(ItemStack stack) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(ItemManager.nameOf(stack));
    }

    /** A legendary weapon for its hand: a stolen one if it has one, otherwise a copy that drops nothing. */
    private ItemStack handItem(List<ItemStack> stolen) {
        for (ItemStack s : stolen) {
            ItemManager.Entry e = plugin.items().customOf(s);
            if (e != null && e.kind == ItemManager.Kind.WEAPON) {
                return s.clone();
            }
        }
        List<ItemManager.Entry> weapons = plugin.items().all(ItemManager.Kind.WEAPON);
        return weapons.isEmpty() ? new ItemStack(Material.NETHERITE_SWORD)
                : weapons.get(ThreadLocalRandom.current().nextInt(weapons.size())).item.clone();
    }

    private void chat(String line) {
        String text = line.replace("{target}", copyName == null ? "" : copyName);
        Bukkit.broadcast(Component.text("<").append(glitched.color(NamedTextColor.WHITE)).append(Component.text("> " + text)));
    }

    private void glitchFx(Location at) {
        World w = at.getWorld();
        w.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 40, 0.4, 0.9, 0.4, 0, BLACK);
        w.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 20, 0.4, 0.9, 0.4, 0, WHITE);
        w.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        w.playSound(at, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1f, 0.6f);
    }

    // ================================================================ AI

    private void tick() {
        long now = System.currentTimeMillis();
        Player victim = Bukkit.getPlayer(target);
        if (victim == null || !victim.isOnline()) {
            if (offlineSince == 0) {
                offlineSince = now;
            }
            if (now - offlineSince > 120_000) {
                leave(true);
            }
            return;
        }
        offlineSince = 0;
        if (now >= endsAt) {
            chat("not today. soon.");
            leave(true);
            return;
        }
        Location me = body.getLocation();
        if (me.getY() < me.getWorld().getMinHeight() + 2 || victim.getWorld() != me.getWorld()) {
            blink(victim);
            return;
        }
        if (victim.isDead()) {
            return;
        }
        Location there = victim.getLocation();
        double dist = me.distance(there);
        // face the victim
        Vector to = there.toVector().subtract(me.toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        float pitch = (float) -Math.toDegrees(Math.atan2(to.getY(), Math.hypot(to.getX(), to.getZ())));
        body.setRotation(yaw, pitch);
        body.setBodyYaw(yaw);
        // walk / sprint / jump
        if (dist > 2.2 && now >= flyingUntil) {
            Vector flat = to.clone().setY(0);
            if (flat.lengthSquared() > 1.0E-4) {
                flat.normalize();
            }
            double speed = cfg("speed", 0.3);
            // strafe a little when close, so it isn't a straight line
            if (dist < 7) {
                Vector side = new Vector(-flat.getZ(), 0, flat.getX()).multiply(Math.sin(now / 400.0) * 0.5);
                flat.add(side).normalize();
            }
            Vector v = body.getVelocity();
            Vector move = flat.clone().multiply(speed);
            double vy = v.getY();
            // a one-block step right in front of it?
            Block ahead = me.clone().add(flat.clone().multiply(0.8)).getBlock();
            boolean wall = ahead.getType().isSolid() && ahead.getRelative(0, 1, 0).isPassable()
                    && ahead.getRelative(0, 2, 0).isPassable() && me.clone().add(0, 2, 0).getBlock().isPassable();
            boolean grounded = body.isOnGround()
                    || me.clone().add(0, -0.05, 0).getBlock().getType().isSolid() && Math.abs(vy) < 0.1;
            if (wall) {
                blockedTicks++;
                if (grounded) {
                    vy = 0.5;
                }
                if (blockedTicks > 8) {
                    // the jump didn't work: just step up onto the block
                    blockedTicks = 0;
                    Location up = ahead.getLocation().add(0.5, 1, 0.5);
                    up.setYaw(me.getYaw());
                    up.setPitch(me.getPitch());
                    body.teleport(up);
                    return;
                }
            } else {
                blockedTicks = 0;
            }
            if (body.isInWater() || me.getBlock().isLiquid()) {
                vy = 0.12;
            }
            body.setVelocity(new Vector(move.getX(), vy, move.getZ()));
        }
        // stuck -> glitch closer
        if (body.getTicksLived() % 60 == 0) {
            if (lastPos != null && lastPos.getWorld() == me.getWorld() && lastPos.distanceSquared(me) < 2 && dist > 5) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
            lastPos = me.clone();
            if (stuckTicks >= 2 || dist > 60) {
                stuckTicks = 0;
                blink(victim);
                return;
            }
        }
        // melee
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (dist < 3.3 && attackCooldown == 0 && body.hasLineOfSight(victim)) {
            attackCooldown = 12;
            body.swingMainHand();
            hit(victim, cfg("attack-damage", 9));
            victim.setVelocity(victim.getVelocity().add(to.clone().setY(0).normalize().multiply(0.4).setY(0.25)));
        }
        // other players close by get hit too
        if (attackCooldown == 6) {
            for (Player p : me.getWorld().getPlayers()) {
                if (!p.equals(victim) && survival(p) && p.getLocation().distanceSquared(me) < 9) {
                    body.swingMainHand();
                    hit(p, cfg("attack-damage", 9) * 0.7);
                    break;
                }
            }
        }
        // wind charges: jump up to the victim, chase, or blow them away
        if (now >= nextWindAt && plugin.getConfig().getBoolean("doppelganger.wind-charges", true)) {
            wind(victim, dist, now);
        }
        // abilities
        if (now >= nextAbilityAt) {
            nextAbilityAt = now + (long) (cfg("ability-seconds", 5) * 1000);
            ability(victim, dist);
        }
        if (now >= nextBlinkAt && dist > 10) {
            nextBlinkAt = now + 15000;
            blinkBehind(victim);
        }
        if (now >= nextChatAt) {
            nextChatAt = now + ThreadLocalRandom.current().nextLong(40_000, 90_000);
            List<String> lines = plugin.getConfig().getStringList("doppelganger.chat-lines");
            if (lines.isEmpty()) {
                lines = DEFAULT_LINES;
            }
            chat(lines.get(ThreadLocalRandom.current().nextInt(lines.size())));
        }
        if (body.getTicksLived() % 3 == 0) {
            me.getWorld().spawnParticle(Particle.DUST, me.clone().add(0, 1, 0), 2, 0.3, 0.8, 0.3, 0,
                    ThreadLocalRandom.current().nextBoolean() ? BLACK : WHITE);
        }
        // boss bar for everyone near
        if (body.getTicksLived() % 10 == 0) {
            bar.progress((float) Math.max(0, Math.min(1, hp / maxHp)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld() == me.getWorld() && p.getLocation().distanceSquared(me) < 64 * 64) {
                    p.showBossBar(bar);
                } else {
                    p.hideBossBar(bar);
                }
            }
        }
    }

    private void hit(Player p, double amount) {
        if (p.equals(Bukkit.getPlayer(target))) {
            lastHitOnTarget = System.currentTimeMillis();
        }
        p.damage(amount, body);
    }

    private void ability(Player victim, double dist) {
        World w = body.getWorld();
        Location me = body.getLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (dist < 6) {
            // frost wave: everyone close gets slowed and hurt
            for (int i = 0; i < 40; i++) {
                double a = i * Math.PI / 20;
                w.spawnParticle(Particle.DUST, me.clone().add(Math.cos(a) * 4, 0.3, Math.sin(a) * 4), 2, 0.1, 0.1, 0.1, 0, ICE);
            }
            w.spawnParticle(Particle.SNOWFLAKE, me.clone().add(0, 1, 0), 60, 2, 0.5, 2, 0.05);
            w.playSound(me, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
            for (Player p : w.getPlayers()) {
                if (survival(p) && p.getLocation().distanceSquared(me) < 25) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                    p.setFreezeTicks(Math.max(p.getFreezeTicks(), 100));
                    hit(p, cfg("ability-damage", 6));
                }
            }
        } else if (dist < 14 && r.nextBoolean()) {
            // echo dash: a burst straight at the victim
            Vector dir = victim.getLocation().toVector().subtract(me.toVector()).normalize();
            body.setVelocity(dir.multiply(1.8).setY(0.25));
            w.playSound(me, Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 0.7f);
            w.spawnParticle(Particle.SONIC_BOOM, me.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
        } else if (dist < 22 && r.nextBoolean()) {
            // gravity pull: drags the victim in
            Location from = me.clone().add(0, 1.2, 0);
            Vector line = victim.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
            double len = line.length();
            line.normalize();
            for (double d = 0; d < len; d += 0.5) {
                w.spawnParticle(Particle.DUST, from.clone().add(line.clone().multiply(d)), 1, 0, 0, 0, 0, VOID);
            }
            w.playSound(victim.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.5f);
            Vector pull = me.toVector().subtract(victim.getLocation().toVector()).normalize().multiply(Math.min(2.2, 0.6 + len * 0.08));
            victim.setVelocity(pull.setY(0.4));
            hit(victim, cfg("ability-damage", 6) * 0.5);
        } else if (body.hasLineOfSight(victim)) {
            // arrow volley
            Location eye = body.getEyeLocation();
            Vector aim = victim.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
            for (int i = 0; i < 5; i++) {
                Vector spread = aim.clone().add(new Vector(r.nextDouble(-0.08, 0.08), r.nextDouble(-0.04, 0.06), r.nextDouble(-0.08, 0.08)));
                Arrow arrow = w.spawnArrow(eye.clone().add(aim), spread, 2.2f, 0f);
                arrow.setShooter(body);
                arrow.setDamage(cfg("arrow-damage", 3));
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.getPersistentDataContainer().set(ghostKey, PersistentDataType.BYTE, (byte) 1);
            }
            w.playSound(eye, Sound.ITEM_CROSSBOW_SHOOT, 1.5f, 0.7f);
        } else {
            blinkBehind(victim);
        }
    }

    private void wind(Player victim, double dist, long now) {
        Location me = body.getLocation();
        double up = victim.getLocation().getY() - me.getY();
        boolean grounded = body.isOnGround() || me.clone().add(0, -0.05, 0).getBlock().getType().isSolid();
        long cooldown = (long) (cfg("wind-charge-seconds", 4) * 1000);
        Vector toward = victim.getLocation().toVector().subtract(me.toVector()).setY(0);
        if (toward.lengthSquared() > 1.0E-4) {
            toward.normalize();
        }
        if (grounded && (up > 2.5 && dist < 14 || dist > 18 && dist < 40)) {
            // a wind charge at its own feet: launches it up and forward
            burst(me);
            double lift = up > 2.5 ? Math.min(1.6, 0.9 + up * 0.12) : 0.9;
            body.setVelocity(toward.clone().multiply(up > 2.5 ? 0.6 : 1.3).setY(lift));
            flyingUntil = now + 900;
            nextWindAt = now + cooldown;
        } else if (dist > 4 && dist < 20 && body.hasLineOfSight(victim) && ThreadLocalRandom.current().nextInt(3) == 0) {
            // thrown at the victim
            Vector aim = victim.getLocation().add(0, 0.8, 0).toVector().subtract(body.getEyeLocation().toVector()).normalize();
            body.swingOffHand();
            body.launchProjectile(org.bukkit.entity.WindCharge.class, aim.multiply(1.5));
            me.getWorld().playSound(me, Sound.ENTITY_WIND_CHARGE_THROW, 1f, 0.8f);
            nextWindAt = now + cooldown;
        } else {
            nextWindAt = now + 500;
        }
    }

    private void burst(Location at) {
        World w = at.getWorld();
        w.spawnParticle(Particle.GUST_EMITTER_SMALL, at, 1, 0, 0, 0, 0);
        w.playSound(at, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1f);
        body.swingOffHand();
    }

    /** Teleport right behind the victim. */
    private void blinkBehind(Player victim) {
        Location behind = victim.getLocation().clone();
        Vector back = behind.getDirection().setY(0);
        if (back.lengthSquared() < 1.0E-4) {
            back = new Vector(1, 0, 0);
        }
        behind.subtract(back.normalize().multiply(2.5));
        Block feet = behind.getBlock();
        if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()) {
            behind = victim.getLocation();
        }
        glitchFx(body.getLocation());
        body.teleport(behind);
        glitchFx(behind);
        victim.playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 0.5f, 0.6f);
    }

    /** Teleport somewhere near the victim (stuck, fell, other world). */
    private void blink(Player victim) {
        Location at = spawnSpot(victim);
        if (at.distanceSquared(victim.getLocation()) > 30 * 30) {
            at = victim.getLocation();
        }
        glitchFx(body.getLocation());
        body.teleport(at);
        glitchFx(at);
    }

    // ================================================================ damage and death

    private boolean isGhost(Entity e) {
        return e != null && e.getPersistentDataContainer().has(ghostKey, PersistentDataType.BYTE);
    }

    /** It has its own health pool: hits are counted, everything else (fire, fall, lava) is ignored. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!active() || !event.getEntity().equals(body)) {
            return;
        }
        event.setCancelled(true);
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            if (event.getCause() == EntityDamageEvent.DamageCause.LAVA || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Player victim = Bukkit.getPlayer(target);
                if (victim != null) {
                    blink(victim);
                }
            }
            return;
        }
        Player attacker = byEntity.getDamager() instanceof Player p ? p
                : byEntity.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player s ? s : null;
        if (attacker == null) {
            return;
        }
        double amount = Math.max(0, event.getFinalDamage()) * cfg("damage-taken-multiplier", 0.6);
        hp -= amount;
        damage.merge(attacker.getUniqueId(), amount, Double::sum);
        body.playHurtAnimation(0);
        body.getWorld().playSound(body.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.6f);
        if (hp <= 0) {
            die(attacker);
            return;
        }
        // at 50 % and 20 %: glitches away for a moment and heals a bit
        if (healsUsed == 0 && hp < maxHp * 0.5 || healsUsed == 1 && hp < maxHp * 0.2) {
            healsUsed++;
            hp = Math.min(maxHp, hp + maxHp * 0.1);
            body.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false));
            glitchFx(body.getLocation());
            chat(healsUsed == 1 ? "that hurt. do it again." : "you can't kill what you made");
            Player victim = Bukkit.getPlayer(target);
            if (victim != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (active() && victim.isOnline()) {
                        blinkBehind(victim);
                    }
                }, 60L);
            }
        }
    }

    /** Its arrows don't hit itself. */
    @EventHandler(ignoreCancelled = true)
    public void onArrow(EntityDamageByEntityEvent event) {
        if (isGhost(event.getDamager()) && isGhost(event.getEntity())) {
            event.setCancelled(true);
        } else if (isGhost(event.getDamager()) && target != null && event.getEntity().getUniqueId().equals(target)) {
            lastHitOnTarget = System.currentTimeMillis();
        }
    }

    private void die(Player killer) {
        Location at = body.getLocation();
        World w = at.getWorld();
        // a perfectly normal death message, then it leaves
        Bukkit.broadcast(Component.translatable("death.attack.player", glitched, Component.text(killer.getName())));
        w.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 120, 0.6, 1, 0.6, 0, BLACK);
        w.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 1, 0), 3, 0.4, 0.6, 0.4, 0);
        w.playSound(at, Sound.ENTITY_WARDEN_DEATH, 1.5f, 1.2f);
        // the stolen legends come back
        for (ItemStack s : stolen()) {
            dropSafe(at, s);
        }
        data.set("stolen", null);
        // reward for the top damage dealer (fallback: the killer)
        UUID best = damage.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(killer.getUniqueId());
        Player winner = Bukkit.getPlayer(best) != null ? Bukkit.getPlayer(best) : killer;
        int tokens = plugin.shop() != null && plugin.shop().enabled() ? (int) cfg("reward-tokens", 25) : 0;
        if (tokens > 0 && plugin.bounty() != null) {
            dropSafe(at, plugin.bounty().token(tokens));
        }
        if (plugin.getConfig().getBoolean("doppelganger.reward-material", true) && plugin.bounty() != null) {
            List<ItemManager.Entry> mats = plugin.bounty().enabledMaterials();
            if (!mats.isEmpty()) {
                dropSafe(at, mats.get(ThreadLocalRandom.current().nextInt(mats.size())).item.clone());
            }
        }
        w.spawn(at, org.bukkit.entity.ExperienceOrb.class, o -> o.setExperience((int) cfg("xp", 500)));
        if (plugin.top() != null) {
            plugin.top().add(winner, "ghosts");
        }
        Bukkit.broadcast(plugin.msg("ghost-killed", "&8☠ &7The copy of &f{copy} &7is gone. &f{player} &7ended it.",
                "copy", copyName, "player", winner.getName()));
        resetTop();
        leave(true);
    }

    private void dropSafe(Location at, ItemStack stack) {
        at.getWorld().dropItem(at.clone().add(0, 0.5, 0), stack, i -> {
            i.setInvulnerable(true);
            i.setUnlimitedLifetime(true);
            i.setGlowing(true);
        });
    }

    /** Its target died: if the Doppelganger did it, it takes one of their legends. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVictimDeath(PlayerDeathEvent event) {
        if (!active() || !event.getPlayer().getUniqueId().equals(target)) {
            return;
        }
        Player victim = event.getPlayer();
        boolean byGhost = System.currentTimeMillis() - lastHitOnTarget < 5000
                || event.getDamageSource().getCausingEntity() != null && isGhost(event.getDamageSource().getCausingEntity());
        if (!byGhost) {
            return;
        }
        event.deathMessage(Component.translatable("death.attack.player", victim.displayName(), glitched));
        if (plugin.getConfig().getBoolean("doppelganger.steal-legend", true)
                && stolen().size() < (int) cfg("max-stolen", 3)) {
            ItemStack taken = takeLegend(event);
            if (taken != null) {
                List<ItemStack> list = stolen();
                list.add(taken);
                data.set("stolen.items", list);
                data.set("stolen.victim", victim.getUniqueId().toString());
                data.set("stolen.victim-name", victim.getName());
                data.set("stolen.next-return", System.currentTimeMillis() + (long) (cfg("return-minutes", 30) * 60_000L));
                save();
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(plugin.msg("ghost-stole",
                        "&8☠ &7The copy of &f{copy} &7took &r{item}&7. It will be back. Kill it to get it back.",
                        "copy", copyName, "item", ItemManager.nameOf(taken))));
            }
        }
        // a bounty claimed by nobody: it just resets
        if (plugin.bounty() != null) {
            plugin.bounty().addBounty(victim, -plugin.bounty().bountyOf(victim.getUniqueId()));
        }
        resetTop();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active()) {
                chat("thank you, " + victim.getName());
                leave(true);
            }
        }, 40L);
    }

    /** Removes one legend from the drops (or the inventory with keepInventory). */
    private ItemStack takeLegend(PlayerDeathEvent event) {
        for (Iterator<ItemStack> it = event.getDrops().iterator(); it.hasNext(); ) {
            ItemStack s = it.next();
            ItemManager.Entry e = plugin.items().customOf(s);
            if (e != null && e.kind != ItemManager.Kind.MATERIAL) {
                it.remove();
                return s;
            }
        }
        if (event.getKeepInventory()) {
            ItemStack[] contents = event.getPlayer().getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemManager.Entry e = plugin.items().customOf(contents[i]);
                if (e != null && e.kind != ItemManager.Kind.MATERIAL) {
                    ItemStack s = contents[i];
                    event.getPlayer().getInventory().setItem(i, null);
                    return s;
                }
            }
        }
        return null;
    }

    private void resetTop() {
        data.set("top.since", System.currentTimeMillis());
        save();
    }

    /** It "leaves the game". */
    public void leave(boolean message) {
        if (body != null && body.isValid()) {
            glitchFx(body.getLocation());
            body.remove();
        }
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }
        if (message && glitched != null) {
            Bukkit.broadcast(Component.translatable("multiplayer.player.left", NamedTextColor.YELLOW, glitched));
        }
        body = null;
        bar = null;
        target = null;
    }

    /** Admin: /forge ghost [player] */
    public boolean forceSpawn(Player victim) {
        return spawn(victim, victim.getName());
    }
}
