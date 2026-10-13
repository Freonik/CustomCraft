package cz.customcraft;

import cz.customcraft.WeaponAbilities.Active;
import cz.customcraft.WeaponAbilities.ActiveType;
import cz.customcraft.WeaponAbilities.HitType;
import cz.customcraft.WeaponAbilities.OnHit;
import cz.customcraft.WeaponAbilities.Params;
import cz.customcraft.WeaponAbilities.Passive;
import cz.customcraft.WeaponAbilities.Trigger;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * Weapon abilities: actives with cooldowns, on-hit effects and passive effects while held.
 * Cooldowns are shown in the action bar while the weapon is held.
 */
public final class AbilityManager implements Listener {

    private static final long HISTORY_MS = 10_000;
    private static final long DAMAGE_MEMORY_MS = 15_000;
    private static final Set<String> UNLINKABLE_CAUSES = Set.of("VOID", "KILL", "SUICIDE", "WORLD_BORDER", "STARVATION");

    private static final Particle.DustOptions ECHO_DUST = new Particle.DustOptions(Color.fromRGB(60, 230, 230), 1.2f);
    private static final Particle.DustOptions GRAVITY_DUST = new Particle.DustOptions(Color.fromRGB(120, 80, 230), 1.4f);
    private static final Particle.DustOptions GRAVITY_CORE = new Particle.DustOptions(Color.fromRGB(20, 10, 50), 2.0f);
    private static final Particle.DustOptions HEX_DUST = new Particle.DustOptions(Color.fromRGB(80, 255, 140), 1.1f);
    private static final Particle.DustOptions HYDRA_DUST = new Particle.DustOptions(Color.fromRGB(70, 210, 120), 1.2f);
    private static final Particle.DustOptions SOUL_DUST = new Particle.DustOptions(Color.fromRGB(120, 240, 255), 1.0f);
    private static final Particle.DustOptions BLOOD_DUST = new Particle.DustOptions(Color.fromRGB(150, 12, 26), 1.3f);
    private static final Particle.DustOptions BLOOD_SMALL = new Particle.DustOptions(Color.fromRGB(190, 20, 34), 0.8f);
    private static final Particle.DustOptions PLAGUE_DUST = new Particle.DustOptions(Color.fromRGB(120, 170, 40), 0.9f);
    private static final Particle.DustOptions TONGUE_DUST = new Particle.DustOptions(Color.fromRGB(214, 84, 116), 1.5f);
    private static final Particle.DustOptions TONGUE_TIP = new Particle.DustOptions(Color.fromRGB(255, 150, 178), 2.0f);
    private static final Particle.DustOptions ABYSS_DUST = new Particle.DustOptions(Color.fromRGB(40, 210, 190), 1.0f);
    private static final Particle.DustOptions ABYSS_FULL = new Particle.DustOptions(Color.fromRGB(255, 190, 60), 1.1f);
    /** Effects Bloodrush shrugs off. */
    private static final Set<PotionEffectType> DEBUFFS = Set.of(PotionEffectType.POISON, PotionEffectType.WITHER,
            PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA,
            PotionEffectType.MINING_FATIGUE, PotionEffectType.HUNGER, PotionEffectType.DARKNESS);

    private final CustomCraft plugin;
    private final ItemManager items;
    private final NamespacedKey arrowKey;
    private final NamespacedKey mirrorKey;

    /** player -> (weapon:ability -> cooldown end in ms) */
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    /** player -> last activation attempt (one click can send two packets) */
    private final Map<UUID, Long> lastTrigger = new HashMap<>();
    /** player -> attack charge right before the hit (already reset inside the damage event) */
    private final Map<UUID, Float> charge = new HashMap<>();
    /** player -> recent positions and health (Rewind) */
    private final Map<UUID, Deque<Snapshot>> history = new HashMap<>();
    /** attacker -> (victim -> damage dealt recently) (Resonance) */
    private final Map<UUID, Map<UUID, Dealt>> dealt = new HashMap<>();
    /** player -> Mirror Shot armed until */
    private final Map<UUID, Long> mirrorArmed = new HashMap<>();
    /** entity -> Hex Mark */
    private final Map<UUID, Hex> hexed = new HashMap<>();
    /** player -> Soul Link */
    private final Map<UUID, Link> links = new HashMap<>();
    /** player -> stored souls */
    private final Map<UUID, Integer> souls = new HashMap<>();
    /** player -> Hydra's Wrath active until (ms) */
    private final Map<UUID, Long> frenzy = new HashMap<>();
    /** player -> Bloodrush active until (ms) */
    private final Map<UUID, Long> bloodrush = new HashMap<>();
    /** entity -> Taste stacks (Abyssal Maw) */
    private final Map<UUID, Taste> taste = new HashMap<>();
    /** swallowed entity -> Devour state */
    private final Map<UUID, Swallow> swallowed = new HashMap<>();
    /** devourer -> swallowed entity */
    private final Map<UUID, UUID> belly = new HashMap<>();
    private RelicManager relics;
    private int ticks;

    private record Snapshot(Location location, double health, long time) { }

    private record Dealt(double amount, long last) { }

    private record Hex(double multiplier, long expires) { }

    private record Link(UUID target, double percent, double maxDistance, long expires) { }

    private record Taste(int stacks, int max, long expires) { }

    private static final class Swallow {
        final UUID devourer;
        final UUID target;
        final boolean ally;
        final long ends;
        final double spitDamage;
        final double launch;
        BukkitTask task;

        Swallow(UUID devourer, UUID target, boolean ally, long ends, double spitDamage, double launch) {
            this.devourer = devourer;
            this.target = target;
            this.ally = ally;
            this.ends = ends;
            this.spitDamage = spitDamage;
            this.launch = launch;
        }
    }

    public void setRelics(RelicManager relics) {
        this.relics = relics;
    }

    private double heal(LivingEntity entity, double amount) {
        return relics == null ? amount : relics.adjustHeal(entity, amount);
    }

    public AbilityManager(CustomCraft plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.arrowKey = new NamespacedKey(plugin, "weapon");
        this.mirrorKey = new NamespacedKey(plugin, "mirror_shot");
    }

    /** Starts the repeating task: history, HUD, passive effects, marks. */
    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 5L);
    }

    // ---------------------------------------------------------------- settings

    private boolean enabled() {
        return plugin.getConfig().getBoolean("ability-settings.enabled", true);
    }

    private boolean enabledIn(World world) {
        return enabled() && !plugin.getConfig().getStringList("ability-settings.disabled-worlds").contains(world.getName());
    }

    // ---------------------------------------------------------------- repeating task

    private void tick() {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean slow = (ticks++ & 1) == 0; // every 10 ticks
        boolean hud = plugin.getConfig().getBoolean("ability-settings.hud", true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            Deque<Snapshot> past = history.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
            if (!player.isDead()) {
                past.addLast(new Snapshot(player.getLocation(), player.getHealth(), now));
            }
            while (!past.isEmpty() && past.peekFirst().time() < now - HISTORY_MS) {
                past.removeFirst();
            }
            if (!slow) {
                continue;
            }
            ItemManager.Entry weapon = weaponOf(player.getInventory().getItemInMainHand());
            boolean hasWeapon = weapon != null && !weapon.abilities.isEmpty() && enabledIn(player.getWorld());
            if (hasWeapon) {
                for (Passive passive : weapon.abilities.passives) {
                    player.addPotionEffect(new PotionEffect(passive.type(), 50, passive.amplifier(), true, false, true));
                }
                // low-health regeneration (every 10 ticks = half of the per-second amount)
                double max = maxHealth(player);
                if (weapon.abilities.regenPercent > 0 && !player.isDead()
                        && player.getHealth() < max * weapon.abilities.regenBelow / 100.0) {
                    double add = heal(player, max * weapon.abilities.regenPercent / 200.0);
                    if (add > 0) {
                        player.setHealth(Math.min(max, player.getHealth() + add));
                        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1.1, 0), 2, 0.25, 0.4, 0.25, 0, PLAGUE_DUST);
                    }
                }
            }
            if (hud) {
                String bar = hasWeapon ? hud(player, weapon, now) : "";
                String relicBar = relics == null ? "" : relics.hud(player);
                if (!relicBar.isEmpty()) {
                    bar = bar.isEmpty() ? relicBar : bar + "  &8┃  " + relicBar;
                }
                if (!bar.isEmpty()) {
                    player.sendActionBar(ItemManager.color(bar));
                }
            }
        }

        if (slow) {
            // Hex Mark: floating rune ring above marked targets
            for (Iterator<Map.Entry<UUID, Hex>> it = hexed.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Hex> e = it.next();
                Entity entity = Bukkit.getEntity(e.getKey());
                if (e.getValue().expires() < now || entity == null || !entity.isValid()) {
                    it.remove();
                    continue;
                }
                Location top = entity.getLocation().add(0, entity.getHeight() + 0.4, 0);
                ring(top, 0.45, 8, l -> l.getWorld().spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, HEX_DUST));
            }
            // Taste: little stack orbs above tasted targets (gold ring when ready to devour)
            for (Iterator<Map.Entry<UUID, Taste>> it = taste.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Taste> e = it.next();
                Entity entity = Bukkit.getEntity(e.getKey());
                if (e.getValue().expires() < now || entity == null || !entity.isValid()) {
                    it.remove();
                    continue;
                }
                Taste t = e.getValue();
                Location top = entity.getLocation().add(0, entity.getHeight() + 0.35, 0);
                for (int i = 0; i < t.stacks(); i++) {
                    double off = (i - (t.stacks() - 1) / 2.0) * 0.3;
                    entity.getWorld().spawnParticle(Particle.DUST, top.clone().add(off, 0, 0), 1, 0, 0, 0, 0,
                            t.stacks() >= t.max() ? ABYSS_FULL : ABYSS_DUST);
                }
                if (t.stacks() >= t.max()) {
                    ring(top.clone().add(0, 0.2, 0), 0.5, 10,
                            l -> l.getWorld().spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, ABYSS_FULL));
                }
            }
            bloodrush.values().removeIf(t -> t < now);
            mirrorArmed.values().removeIf(t -> t < now);
            dealt.values().forEach(m -> m.values().removeIf(d -> d.last() < now - DAMAGE_MEMORY_MS));
            dealt.values().removeIf(Map::isEmpty);
        }
    }

    private String hud(Player player, ItemManager.Entry weapon, long now) {
        StringBuilder bar = new StringBuilder();
        for (Active active : weapon.abilities.actives) {
            if (!bar.isEmpty()) {
                bar.append("  &8┃  ");
            }
            bar.append(active.name()).append(' ');
            long left = remaining(player, weapon, active, now);
            if (active.type() == ActiveType.MIRROR_SHOT && mirrorArmed.containsKey(player.getUniqueId())) {
                bar.append("&dARMED");
            } else {
                bar.append(left <= 0 ? "&a✔" : "&c" + seconds(left) + "s");
            }
        }
        OnHit soul = weapon.abilities.onHit(HitType.SOUL);
        if (soul != null) {
            if (!bar.isEmpty()) {
                bar.append("  &8┃  ");
            }
            int max = Math.max(1, soul.params().i("max", 5));
            int have = Math.min(max, souls.getOrDefault(player.getUniqueId(), 0));
            bar.append("&3Souls &b").append("◆".repeat(have)).append("&8").append("◇".repeat(max - have));
        }
        return bar.toString();
    }

    // ---------------------------------------------------------------- activation

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        // Clicking a chest, door etc. keeps vanilla behaviour.
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
            return;
        }
        Trigger trigger = player.isSneaking() ? Trigger.SHIFT_RIGHT_CLICK : Trigger.RIGHT_CLICK;
        if (activate(player, trigger)) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemManager.Entry weapon = weaponOf(player.getInventory().getItemInMainHand());
        if (weapon == null) {
            return;
        }
        Trigger trigger = player.isSneaking() ? Trigger.SHIFT_SWAP : Trigger.SWAP;
        if (weapon.abilities.active(trigger) != null) {
            // A weapon with an F ability is never swapped into the off hand.
            event.setCancelled(true);
            activate(player, trigger);
        }
    }

    /** Tries to fire an ability. Returns true if the weapon has an ability on this trigger. */
    private boolean activate(Player player, Trigger trigger) {
        ItemManager.Entry weapon = weaponOf(player.getInventory().getItemInMainHand());
        if (weapon == null) {
            return false;
        }
        Active active = weapon.abilities.active(trigger);
        if (active == null) {
            return false;
        }
        if (!enabledIn(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR
                || (relics != null && relics.inStasis(player)) || swallowed.containsKey(player.getUniqueId())) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = lastTrigger.put(player.getUniqueId(), now);
        if (last != null && now - last < 150) {
            return true;
        }
        long left = remaining(player, weapon, active, now);
        if (left > 0) {
            player.sendActionBar(plugin.raw("ability-cooldown", "&c{ability} &7is recharging: &c{time}s",
                    "ability", ItemManager.color(active.name()), "time", seconds(left)));
            return true;
        }
        boolean used;
        try {
            used = execute(player, weapon, active);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Ability " + weapon.ref() + "/" + active.key() + " failed: " + ex);
            used = false;
        }
        if (used) {
            if (!player.hasPermission("customcraft.nocooldown")) {
                cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                        .put(weapon.ref() + ":" + active.key(), now + active.cooldown() * 1000L);
            }
            player.sendActionBar(plugin.raw("ability-used", "&a» {ability}&a!",
                    "ability", ItemManager.color(active.name())));
        }
        return true;
    }

    /** Gives back part of an ability's remaining cooldown (e.g. when Cleaver Toss hits). */
    private void refund(Player player, ItemManager.Entry weapon, Active active, double fraction) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null || fraction <= 0) {
            return;
        }
        String key = weapon.ref() + ":" + active.key();
        Long until = map.get(key);
        long now = System.currentTimeMillis();
        if (until != null && until > now) {
            map.put(key, now + (long) ((until - now) * (1 - Math.min(1, fraction))));
        }
    }

    private long remaining(Player player, ItemManager.Entry weapon, Active active, long now) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) {
            return 0;
        }
        Long until = map.get(weapon.ref() + ":" + active.key());
        return until == null ? 0 : until - now;
    }

    private static long seconds(long millis) {
        return (millis + 999) / 1000;
    }

    private boolean execute(Player player, ItemManager.Entry weapon, Active active) {
        Params p = active.params();
        return switch (active.type()) {
            case REWIND -> rewind(player, p);
            case RESONANCE -> resonance(player, p);
            case GRAVITY_ANCHOR -> gravityAnchor(player, p);
            case UPHEAVAL -> upheaval(player, p);
            case MIRROR_SHOT -> mirrorShot(player, p);
            case HEX_MARK -> hexMark(player, p);
            case SOUL_LINK -> soulLink(player, p);
            case HARVEST -> harvest(player, p);
            case CRESCENT -> crescent(player, p);
            case FRENZY -> frenzy(player, p);
            case CLEAVER_TOSS -> cleaverToss(player, weapon, active);
            case BLOODRUSH -> bloodrush(player, p);
            case TONGUE_LASH -> tongueLash(player, weapon, p);
            case DEVOUR -> devour(player, p);
        };
    }

    private void fail(Player player, String key, String def) {
        player.sendActionBar(plugin.raw(key, def));
    }

    // ================================================================ ECHO BLADE

    /** Rewind: snap back to where you were a few seconds ago and get back the health you had then. */
    private boolean rewind(Player player, Params p) {
        long target = System.currentTimeMillis() - (long) (p.d("seconds", 4) * 1000);
        Deque<Snapshot> past = history.get(player.getUniqueId());
        Snapshot found = null;
        if (past != null) {
            for (Snapshot snapshot : past) {
                if (snapshot.time() > target) {
                    break;
                }
                found = snapshot;
            }
        }
        if (found == null || found.location().getWorld() != player.getWorld()) {
            fail(player, "ability-rewind-none", "&cNo echo to return to yet.");
            return false;
        }
        Block feet = found.location().getBlock();
        if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()) {
            fail(player, "ability-rewind-blocked", "&cYour past position is blocked.");
            return false;
        }
        Location from = player.getLocation();
        Location to = found.location().clone();
        to.setYaw(from.getYaw());
        to.setPitch(from.getPitch());
        World world = player.getWorld();
        line(from.clone().add(0, 1, 0), to.clone().add(0, 1, 0), 0.4,
                l -> world.spawnParticle(Particle.DUST, l, 1, 0.05, 0.05, 0.05, 0, ECHO_DUST));
        world.spawnParticle(Particle.SCULK_SOUL, from.clone().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.02);
        world.playSound(from, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 1.6f);

        player.teleport(to);
        player.setFallDistance(0);
        player.setFireTicks(0);
        double restore = Math.min(maxHealth(player), found.health());
        if (restore > player.getHealth()) {
            player.setHealth(player.getHealth() + heal(player, restore - player.getHealth()));
        }
        history.remove(player.getUniqueId()); // no chaining through older echoes

        world.spawnParticle(Particle.SCULK_SOUL, to.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.04);
        world.playSound(to, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.8f);
        return true;
    }

    /** Resonance: everyone you recently hurt takes a share of all the damage you dealt them, again. */
    private boolean resonance(Player player, Params p) {
        double radius = p.d("radius", 12);
        double percent = p.d("percent", 50) / 100.0;
        double cap = p.d("max-damage", 14);
        long window = (long) (p.d("window", 10) * 1000);
        long now = System.currentTimeMillis();
        Map<UUID, Dealt> mine = dealt.get(player.getUniqueId());
        List<LivingEntity> hit = new ArrayList<>();
        List<Double> amounts = new ArrayList<>();
        if (mine != null) {
            for (Map.Entry<UUID, Dealt> e : mine.entrySet()) {
                if (e.getValue().last() < now - window) {
                    continue;
                }
                if (Bukkit.getEntity(e.getKey()) instanceof LivingEntity target && valid(player, target)
                        && target.getWorld() == player.getWorld()
                        && target.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                    hit.add(target);
                    amounts.add(Math.min(cap, e.getValue().amount() * percent));
                }
            }
        }
        if (hit.isEmpty()) {
            fail(player, "ability-resonance-none", "&cNothing is resonating. Hit something first.");
            return false;
        }
        dealt.remove(player.getUniqueId());
        World world = player.getWorld();
        Location center = player.getLocation();
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.4f);
        repeat(1, 6, step -> {
            ring(center.clone().add(0, 1, 0), radius * (step + 1) / 6.0, 40,
                    l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0.1, 0, 0, ECHO_DUST));
            return true;
        });
        for (int i = 0; i < hit.size(); i++) {
            LivingEntity target = hit.get(i);
            double amount = amounts.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!target.isValid() || !player.isOnline()) {
                    return;
                }
                Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
                target.getWorld().spawnParticle(Particle.SONIC_BOOM, at, 1, 0, 0, 0, 0);
                target.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, at, 20, 0.3, 0.5, 0.3, 0.05);
                hurt(player, target, amount);
            }, 4L + i * 2L);
        }
        return true;
    }

    // ================================================================ GRAVITON HAMMER

    /** Gravity Anchor: a field that yanks back anyone trying to leave it and weighs them down. */
    private boolean gravityAnchor(Player player, Params p) {
        double radius = p.d("radius", 7);
        int seconds = p.i("duration", 6);
        double pull = p.d("pull", 0.5);
        int slowLevel = Math.max(0, p.i("slow-level", 2) - 1);
        Location center = player.getLocation();
        World world = player.getWorld();
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 0.5f);
        world.playSound(center, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
        int steps = seconds * 10;
        repeat(2, steps, step -> {
            double spin = step * 0.15;
            ring(center.clone().add(0, 0.15, 0), radius, Math.max(24, (int) (radius * 7)),
                    l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, GRAVITY_DUST));
            for (int k = 0; k < 4; k++) {
                double a = spin + k * Math.PI / 2;
                double r = radius * (1 - (step % 10) / 10.0);
                world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * r, 0.4, Math.sin(a) * r),
                        1, 0, 0, 0, 0, GRAVITY_DUST);
            }
            world.spawnParticle(Particle.DUST, center.clone().add(0, 0.6 + (step % 10) * 0.25, 0), 2, 0.1, 0.1, 0.1, 0, GRAVITY_CORE);
            if (step % 10 == 0) {
                world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 1f, 0.5f);
            }
            for (LivingEntity target : targets(player, center, radius + 3)) {
                Vector to = center.toVector().subtract(target.getLocation().toVector()).setY(0);
                double distance = to.length();
                if (distance > radius * 0.8 && distance > 0.01) {
                    target.setVelocity(to.normalize().multiply(pull).setY(-0.1));
                    world.spawnParticle(Particle.REVERSE_PORTAL, target.getLocation().add(0, 1, 0), 6, 0.2, 0.4, 0.2, 0.05);
                }
                if (step % 10 == 0 && distance <= radius) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, slowLevel));
                }
            }
            return true;
        });
        return true;
    }

    /** Upheaval: lift everyone around you into the air, then slam them down. Higher = more damage. */
    private boolean upheaval(Player player, Params p) {
        double radius = p.d("radius", 6);
        int lift = Math.max(2, p.i("lift-ticks", 25));
        double base = p.d("base-damage", 4);
        double perBlock = p.d("damage-per-block", 1);
        double cap = p.d("max-damage", 12);
        Location center = player.getLocation();
        List<LivingEntity> victims = targets(player, center, radius);
        if (victims.isEmpty()) {
            fail(player, "ability-no-target", "&cNo target in range.");
            return false;
        }
        World world = player.getWorld();
        world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.6f);
        world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 0.5, 0), 80, radius / 2, 0.3, radius / 2, 0.1);
        for (LivingEntity target : victims) {
            target.setVelocity(new Vector(0, 0.55, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, lift, 2, false, true, true));
        }
        repeat(2, lift / 2, step -> {
            for (LivingEntity target : victims) {
                if (target.isValid()) {
                    world.spawnParticle(Particle.DUST, target.getLocation(), 3, 0.3, 0.1, 0.3, 0, GRAVITY_DUST);
                }
            }
            return true;
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (LivingEntity target : victims) {
                if (!target.isValid() || target.isDead() || !player.isOnline()) {
                    continue;
                }
                target.removePotionEffect(PotionEffectType.LEVITATION);
                double height = heightAboveGround(target.getLocation());
                target.setVelocity(new Vector(0, -2.8, 0));
                hurt(player, target, Math.min(cap, base + perBlock * height));
                target.getWorld().spawnParticle(Particle.DUST, target.getLocation(), 20, 0.3, 0.6, 0.3, 0, GRAVITY_CORE);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.5f);
            }
        }, lift);
        return true;
    }

    // ================================================================ HEXBOW

    /** Mirror Shot: your next arrow (within a few seconds) swaps your place with whatever it hits. */
    private boolean mirrorShot(Player player, Params p) {
        mirrorArmed.put(player.getUniqueId(), System.currentTimeMillis() + (long) (p.d("window", 10) * 1000));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.2f);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
        return true;
    }

    /** Hex Mark: the entity you look at glows and takes extra damage from every source. */
    private boolean hexMark(Player player, Params p) {
        LivingEntity target = lookTarget(player, p.d("range", 32));
        if (target == null) {
            fail(player, "ability-no-target", "&cNo target in range.");
            return false;
        }
        int seconds = p.i("duration", 10);
        hexed.put(target.getUniqueId(), new Hex(1 + p.d("amplify", 30) / 100.0,
                System.currentTimeMillis() + seconds * 1000L));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, seconds * 20, 0, false, false, false));
        World world = player.getWorld();
        line(player.getEyeLocation(), target.getLocation().add(0, target.getHeight() / 2, 0), 0.5,
                l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, HEX_DUST));
        world.spawnParticle(Particle.WITCH, target.getLocation().add(0, 1, 0), 40, 0.4, 0.7, 0.4, 0.1);
        world.playSound(target.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.6f);
        if (target instanceof Player victim) {
            victim.sendActionBar(plugin.raw("ability-hexed", "&2You have been hexed!"));
        }
        return true;
    }

    // ================================================================ SOULBINDER

    /** Soul Link: chain yourself to a target; part of all damage you take is sent to it instead. */
    private boolean soulLink(Player player, Params p) {
        LivingEntity target = lookTarget(player, p.d("range", 20));
        if (target == null) {
            fail(player, "ability-no-target", "&cNo target in range.");
            return false;
        }
        int seconds = p.i("duration", 6);
        Link link = new Link(target.getUniqueId(), p.d("percent", 50) / 100.0, p.d("break-distance", 25),
                System.currentTimeMillis() + seconds * 1000L);
        links.put(player.getUniqueId(), link);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 2f, 0.6f);
        world.playSound(target.getLocation(), Sound.ENTITY_VEX_CHARGE, 1f, 0.5f);
        repeat(3, seconds * 7, step -> {
            if (links.get(player.getUniqueId()) != link) {
                return false;
            }
            if (!linkHolds(player, link, target)) {
                links.remove(player.getUniqueId());
                if (player.isOnline() && System.currentTimeMillis() < link.expires()) {
                    fail(player, "ability-link-broken", "&3Soul link broken.");
                }
                return false;
            }
            line(player.getLocation().add(0, 1, 0), target.getLocation().add(0, target.getHeight() / 2, 0), 0.6,
                    l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, SOUL_DUST));
            if (step % 4 == 0) {
                world.spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 2, 0.3, 0.4, 0.3, 0.02);
            }
            return true;
        });
        return true;
    }

    private boolean linkHolds(Player player, Link link, LivingEntity target) {
        return player.isOnline() && !player.isDead() && target.isValid() && !target.isDead()
                && System.currentTimeMillis() < link.expires() && target.getWorld() == player.getWorld()
                && target.getLocation().distanceSquared(player.getLocation()) <= link.maxDistance() * link.maxDistance();
    }

    /** Harvest: spend all souls to heal, gain absorption and launch homing soul wisps. */
    private boolean harvest(Player player, Params p) {
        int count = souls.getOrDefault(player.getUniqueId(), 0);
        if (count <= 0) {
            fail(player, "ability-no-souls", "&3You have no souls to harvest.");
            return false;
        }
        souls.remove(player.getUniqueId());
        double heal = count * p.d("heal-per-soul", 2);
        player.setHealth(Math.min(maxHealth(player), player.getHealth() + heal(player, heal)));
        if (count >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, p.i("absorption-seconds", 15) * 20,
                    (count - 1) / 2, false, true, true));
        }
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.5f, 0.8f);
        world.playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 2f, 1f);
        world.spawnParticle(Particle.SOUL, player.getLocation().add(0, 1, 0), 15 * count, 0.5, 0.8, 0.5, 0.05);

        double range = p.d("range", 16);
        double damage = p.d("damage-per-soul", 3);
        Location origin = player.getLocation();
        List<LivingEntity> enemies = new ArrayList<>(targets(player, origin, range));
        enemies.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(origin)));
        for (int i = 0; i < count && !enemies.isEmpty(); i++) {
            LivingEntity target = enemies.get(i % enemies.size());
            Bukkit.getScheduler().runTaskLater(plugin, () -> wisp(player, target, damage), i * 4L);
        }
        return true;
    }

    /** A homing soul wisp flying from the player to the target. */
    private void wisp(Player player, LivingEntity target, double damage) {
        if (!player.isOnline() || !target.isValid()) {
            return;
        }
        Location pos = player.getEyeLocation().add(ThreadLocalRandom.current().nextDouble(-0.6, 0.6), 0.4,
                ThreadLocalRandom.current().nextDouble(-0.6, 0.6));
        World world = pos.getWorld();
        world.playSound(pos, Sound.ENTITY_ALLAY_DEATH, 0.6f, 1.6f);
        repeat(1, 80, step -> {
            if (!target.isValid() || target.isDead() || target.getWorld() != world) {
                return false;
            }
            Location goal = target.getLocation().add(0, target.getHeight() / 2, 0);
            Vector to = goal.toVector().subtract(pos.toVector());
            if (to.lengthSquared() < 1.0) {
                hurt(player, target, damage);
                world.spawnParticle(Particle.SCULK_SOUL, goal, 12, 0.2, 0.3, 0.2, 0.05);
                world.playSound(goal, Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 1.4f);
                return false;
            }
            pos.add(to.normalize().multiply(0.75));
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, pos, 2, 0.05, 0.05, 0.05, 0);
            world.spawnParticle(Particle.DUST, pos, 1, 0, 0, 0, 0, SOUL_DUST);
            return true;
        });
    }

    // ================================================================ HYDRA EDGE

    /** Crescent: a full 360° swing that hits everyone around you and heals per enemy hit. */
    private boolean crescent(Player player, Params p) {
        double radius = p.d("radius", 4.5);
        double damage = p.d("damage", 8);
        double healPer = p.d("heal-per-target", 2);
        Location center = player.getLocation();
        List<LivingEntity> hit = targets(player, center, radius);
        World world = player.getWorld();
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
        world.playSound(center, Sound.ITEM_TRIDENT_RETURN, 1.2f, 0.6f);
        double yaw0 = Math.toRadians(center.getYaw());
        repeat(1, 6, step -> {
            for (int k = 0; k < 10; k++) {
                double a = yaw0 + (step * 10 + k) * (2 * Math.PI / 60);
                for (double r = 1.2; r <= radius; r += 0.9) {
                    world.spawnParticle(Particle.DUST, center.clone().add(-Math.sin(a) * r, 1.0, Math.cos(a) * r),
                            1, 0, 0, 0, 0, HYDRA_DUST);
                }
            }
            world.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0, 1, 0), 2, radius / 2, 0.1, radius / 2, 0);
            return true;
        });
        for (LivingEntity target : hit) {
            hurt(player, target, damage);
            Vector out = target.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (out.lengthSquared() > 1.0E-4) {
                out.normalize().multiply(0.6);
            }
            target.setVelocity(target.getVelocity().add(out.setY(0.25)));
        }
        if (!hit.isEmpty()) {
            player.setHealth(Math.min(maxHealth(player), player.getHealth() + heal(player, healPer * hit.size())));
        }
        return true;
    }

    /** Hydra's Wrath: a few seconds of frenzy - faster, stronger, and every hit cleaves twice as hard. */
    private boolean frenzy(Player player, Params p) {
        int seconds = p.i("duration", 6);
        frenzy.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, seconds * 20, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, seconds * 20, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 0, false, true, true));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1.3f);
        repeat(4, seconds * 5, step -> {
            if (!player.isOnline() || player.isDead()) {
                return false;
            }
            Location l = player.getLocation().add(0, 1, 0);
            for (int head = 0; head < 3; head++) {
                double a = step * 0.5 + head * 2 * Math.PI / 3;
                world.spawnParticle(Particle.DUST, l.clone().add(Math.cos(a) * 0.8, 0.6 + 0.2 * Math.sin(step + head), Math.sin(a) * 0.8),
                        2, 0.05, 0.05, 0.05, 0, HYDRA_DUST);
            }
            return true;
        });
        return true;
    }

    // ================================================================ BUTCHER'S CLEAVER

    /**
     * Cleaver Toss: throw the cleaver (costs a bit of your own health). It hits the first enemy for
     * a share of its CURRENT health and slows it; a hit heals you and gives back part of the cooldown.
     */
    private boolean cleaverToss(Player player, ItemManager.Entry weapon, Active active) {
        Params p = active.params();
        double cost = p.d("health-cost", 2);
        if (player.getHealth() <= cost + 1) {
            fail(player, "ability-too-hurt", "&cYou're too hurt for that.");
            return false;
        }
        double range = p.d("range", 18);
        double speed = Math.max(0.3, p.d("speed", 1.4));
        double base = p.d("damage", 4);
        double percent = p.d("current-health-percent", 15) / 100.0;
        double cap = p.d("max-damage", 16);
        int slowTicks = p.i("slow-ticks", 40);
        int slowLevel = Math.max(0, p.i("slow-level", 2) - 1);
        double healPercent = p.d("heal-percent-max-hp", 5) / 100.0;
        double refundShare = p.d("refund-percent", 50) / 100.0;

        player.setHealth(Math.max(1, player.getHealth() - cost));
        World world = player.getWorld();
        Location pos = player.getEyeLocation().subtract(0, 0.35, 0);
        Vector dir = pos.getDirection().normalize();
        float yaw = pos.getYaw() + 90f;
        ItemStack look = weapon.item.clone();
        ItemDisplay display = world.spawn(pos.clone(), ItemDisplay.class, d -> {
            d.setItemStack(look);
            d.setPersistent(false);
            d.setTeleportDuration(1);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setRotation(yaw, 0);
        });
        world.playSound(pos, Sound.ENTITY_WITCH_THROW, 1.2f, 0.6f);
        world.playSound(pos, Sound.ITEM_TRIDENT_THROW, 1f, 0.7f);
        world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0, BLOOD_SMALL);
        int steps = (int) Math.ceil(range / speed);
        repeat(1, steps + 1, step -> {
            if (!display.isValid()) {
                return false;
            }
            if (step >= steps || !player.isOnline()) {
                world.spawnParticle(Particle.DUST, pos, 10, 0.2, 0.2, 0.2, 0, BLOOD_SMALL);
                display.remove();
                return false;
            }
            RayTraceResult hit = world.rayTrace(pos, dir, speed, FluidCollisionMode.NEVER, true, 0.6, e -> valid(player, e));
            if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
                hurt(player, target, Math.min(cap, base + target.getHealth() * percent));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowLevel));
                double max = maxHealth(player);
                if (!player.isDead()) {
                    player.setHealth(Math.min(max, player.getHealth() + heal(player, max * healPercent)));
                }
                refund(player, weapon, active, refundShare);
                world.spawnParticle(Particle.DUST, at, 30, 0.35, 0.45, 0.35, 0, BLOOD_DUST);
                world.spawnParticle(Particle.DUST, at, 10, 0.3, 0.4, 0.3, 0, PLAGUE_DUST);
                world.spawnParticle(Particle.CRIT, at, 15, 0.3, 0.4, 0.3, 0.3);
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.6f);
                world.playSound(at, Sound.BLOCK_HONEY_BLOCK_BREAK, 1.2f, 0.6f);
                display.remove();
                return false;
            }
            if (hit != null && hit.getHitBlock() != null) {
                Location at = hit.getHitPosition().toLocation(world);
                world.spawnParticle(Particle.BLOCK, at, 15, 0.2, 0.2, 0.2, 0, hit.getHitBlock().getBlockData());
                world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.7f);
                display.remove();
                return false;
            }
            pos.add(dir.clone().multiply(speed));
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTransformation(new Transformation(new Vector3f(),
                    new Quaternionf().rotateZ((float) (-step * 0.9)), new Vector3f(1.3f, 1.3f, 1.3f), new Quaternionf()));
            Location to = pos.clone();
            to.setYaw(yaw);
            to.setPitch(0);
            display.teleport(to);
            world.spawnParticle(Particle.DUST, pos, 1, 0.05, 0.05, 0.05, 0, BLOOD_SMALL);
            if (step % 3 == 0) {
                world.playSound(pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.4f, 1.6f);
            }
            return true;
        });
        return true;
    }

    /**
     * Bloodrush: pay a slice of your current health, then regenerate a big share of your MAX health
     * over a few seconds, run faster and shrug off debuffs. Scales with max HP (Heartsteel!).
     */
    private boolean bloodrush(Player player, Params p) {
        int seconds = Math.max(1, p.i("duration", 10));
        double total = p.d("heal-percent-max-hp", 40) / 100.0;
        double cost = p.d("health-cost-percent", 10) / 100.0;
        int speedLevel = Math.max(0, p.i("speed-level", 2) - 1);
        player.setHealth(Math.max(1, player.getHealth() * (1 - cost)));
        bloodrush.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        for (PotionEffectType type : DEBUFFS) {
            player.removePotionEffect(type);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, speedLevel, false, true, true));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, 0.7f);
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.2f);
        world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 40, 0.4, 0.7, 0.4, 0, BLOOD_DUST);
        int pulses = seconds * 4;
        repeat(5, pulses, step -> {
            if (!player.isOnline() || player.isDead()) {
                bloodrush.remove(player.getUniqueId());
                return false;
            }
            double max = maxHealth(player);
            double add = heal(player, max * total / pulses);
            player.setHealth(Math.min(max, player.getHealth() + add));
            Location l = player.getLocation();
            for (int k = 0; k < 2; k++) {
                double a = step * 0.8 + k * Math.PI;
                world.spawnParticle(Particle.DUST, l.clone().add(Math.cos(a) * 0.7, 0.2 + (step % 8) * 0.22, Math.sin(a) * 0.7),
                        2, 0.03, 0.03, 0.03, 0, BLOOD_SMALL);
            }
            if (step % 4 == 0) {
                world.playSound(l, Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f, 1.4f);
            }
            if (step == pulses - 1) {
                bloodrush.remove(player.getUniqueId());
            }
            return true;
        });
        return true;
    }

    /** During Bloodrush, new debuffs simply don't stick. */
    @EventHandler(ignoreCancelled = true)
    public void onDebuff(EntityPotionEffectEvent event) {
        if (event.getNewEffect() == null || !DEBUFFS.contains(event.getNewEffect().getType())) {
            return;
        }
        Long until = bloodrush.get(event.getEntity().getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    // ================================================================ ABYSSAL MAW

    private int tasteOf(Entity entity) {
        Taste t = taste.get(entity.getUniqueId());
        return t == null || t.expires() < System.currentTimeMillis() ? 0 : t.stacks();
    }

    /** Adds Taste stacks to a target (refreshes the timer). */
    private void addTaste(LivingEntity target, int amount, int max, double seconds) {
        int now = Math.min(max, tasteOf(target) + amount);
        taste.put(target.getUniqueId(), new Taste(now, max, System.currentTimeMillis() + (long) (seconds * 1000)));
        World world = target.getWorld();
        Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
        world.spawnParticle(Particle.DUST, at, 6, 0.3, 0.4, 0.3, 0, ABYSS_DUST);
        if (now >= max) {
            world.playSound(at, Sound.ENTITY_FROG_AMBIENT, 1.5f, 0.6f);
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.6f);
        }
    }

    /** Taste settings of the weapon (on-hit TASTE entry), used by the tongue too. */
    private static Params tasteParams(ItemManager.Entry weapon) {
        OnHit hit = weapon.abilities.onHit(HitType.TASTE);
        return hit == null ? new Params(Map.of()) : hit.params();
    }

    /**
     * Tongue Lash: a long tongue that grabs the first enemy, damages and slows it and yanks it to you.
     * Its reach and damage grow with your MAX health.
     */
    private boolean tongueLash(Player player, ItemManager.Entry weapon, Params p) {
        double max = maxHealth(player);
        double range = Math.min(p.d("max-range", 20),
                p.d("range", 8) + Math.max(0, max - 20) * p.d("range-per-health", 0.25));
        double damage = p.d("damage", 3) + max * p.d("damage-percent-max-hp", 4) / 100.0;
        double pull = p.d("pull", 1.1);
        int slowTicks = p.i("slow-ticks", 40);
        int slowLevel = Math.max(0, p.i("slow-level", 2) - 1);
        double speed = 2.5;
        Params tp = tasteParams(weapon);
        World world = player.getWorld();
        Vector dir = player.getEyeLocation().getDirection().normalize();
        world.playSound(player.getLocation(), Sound.ENTITY_FROG_TONGUE, 1.5f, 0.6f);
        double[] reach = {0};
        repeat(1, (int) Math.ceil(range / speed) + 2, step -> {
            if (!player.isOnline() || player.isDead() || player.getWorld() != world) {
                return false;
            }
            Location mouth = mouth(player);
            double seg = Math.min(speed, range - reach[0]);
            Location tip = mouth.clone().add(dir.clone().multiply(reach[0]));
            RayTraceResult hit = seg <= 0 ? null
                    : world.rayTrace(tip, dir, seg, FluidCollisionMode.NEVER, true, 0.5, e -> valid(player, e));
            if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                Location grab = hit.getHitPosition().toLocation(world);
                tongue(mouth, grab);
                hurt(player, target, damage);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowLevel));
                addTaste(target, tp.i("tongue-stacks", 1), Math.max(1, tp.i("max", 3)), tp.d("duration", 6));
                Vector toward = player.getLocation().toVector().subtract(target.getLocation().toVector());
                double distance = toward.length();
                if (distance > 1.5) {
                    Vector v = toward.normalize().multiply(Math.min(2.6, pull * (0.45 + distance * 0.09))).setY(0.38);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (target.isValid()) {
                            target.setVelocity(v);
                        }
                    });
                }
                world.playSound(grab, Sound.ENTITY_SLIME_ATTACK, 1.2f, 0.7f);
                world.playSound(grab, Sound.ENTITY_FROG_EAT, 1.2f, 0.8f);
                world.spawnParticle(Particle.ITEM_SLIME, grab, 12, 0.2, 0.3, 0.2, 0.05);
                retract(player, grab);
                return false;
            }
            if ((hit != null && hit.getHitBlock() != null) || reach[0] + seg >= range) {
                Location end = hit != null ? hit.getHitPosition().toLocation(world) : tip.clone().add(dir.clone().multiply(seg));
                tongue(mouth, end);
                if (hit != null) {
                    world.playSound(end, Sound.BLOCK_SLIME_BLOCK_HIT, 1f, 0.8f);
                }
                retract(player, end);
                return false;
            }
            reach[0] += seg;
            tongue(mouth, mouth.clone().add(dir.clone().multiply(reach[0])));
            return true;
        });
        return true;
    }

    private static Location mouth(Player player) {
        return player.getEyeLocation().subtract(0, 0.3, 0).add(player.getEyeLocation().getDirection().multiply(0.3));
    }

    private void tongue(Location from, Location to) {
        World world = from.getWorld();
        line(from, to, 0.22, l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, TONGUE_DUST));
        world.spawnParticle(Particle.DUST, to, 3, 0.06, 0.06, 0.06, 0, TONGUE_TIP);
    }

    /** The tongue snaps back into the mouth over a few ticks. */
    private void retract(Player player, Location end) {
        repeat(1, 3, step -> {
            if (!player.isOnline()) {
                return false;
            }
            Location mouth = mouth(player);
            if (mouth.getWorld() != end.getWorld()) {
                return false;
            }
            double t = 1 - (step + 1) / 4.0;
            tongue(mouth, mouth.clone().add(end.toVector().subtract(mouth.toVector()).multiply(t)));
            return true;
        });
    }

    /**
     * Devour: swallow the entity you look at.
     * Ally (any player without full Taste): hidden and untouchable inside you, sneak to hop out.
     * Enemy (full Taste stacks): trapped and blinded for a moment, then spat out far and hurt.
     */
    private boolean devour(Player player, Params p) {
        if (belly.containsKey(player.getUniqueId())) {
            fail(player, "ability-devour-full", "&cYour belly is already full.");
            return false;
        }
        Location eye = player.getEyeLocation();
        RayTraceResult ray = player.getWorld().rayTrace(eye, eye.getDirection(), p.d("range", 4.5),
                FluidCollisionMode.NEVER, true, 0.6, e -> e instanceof LivingEntity living && !e.equals(player)
                        && living.isValid() && !living.isDead() && !(e instanceof ArmorStand)
                        && !swallowed.containsKey(e.getUniqueId()) && !belly.containsKey(e.getUniqueId()));
        if (ray == null || !(ray.getHitEntity() instanceof LivingEntity target)) {
            fail(player, "ability-no-target", "&cNo target in range.");
            return false;
        }
        List<String> immune = plugin.getConfig().getStringList("ability-settings.devour-immune");
        if (target instanceof Boss || immune.contains(target.getType().name())
                || (target instanceof Player tp && tp.getGameMode() == GameMode.SPECTATOR)) {
            fail(player, "ability-devour-too-big", "&cThat's way too big to swallow.");
            return false;
        }
        int need = Math.max(1, p.i("stacks-needed", 3));
        boolean ally;
        if (tasteOf(target) >= need && valid(player, target)) {
            ally = false;
        } else if (target instanceof Player) {
            ally = true;
        } else {
            player.sendActionBar(plugin.raw("ability-devour-taste", "&cIt needs &6{stacks} Taste &cstacks first.",
                    "stacks", String.valueOf(need)));
            return false;
        }
        double seconds = ally ? p.d("ally-duration", 4) : p.d("enemy-duration", 2);
        int ticks = (int) Math.round(seconds * 20);
        target.leaveVehicle();
        target.eject();
        Swallow s = new Swallow(player.getUniqueId(), target.getUniqueId(), ally,
                System.currentTimeMillis() + (long) (seconds * 1000),
                p.d("spit-damage", 6) + maxHealth(player) * p.d("spit-damage-percent-max-hp", 5) / 100.0,
                p.d("spit-launch", 1.8));
        taste.remove(target.getUniqueId());
        if (!player.addPassenger(target)) {
            fail(player, "ability-no-target", "&cNo target in range.");
            return false;
        }
        swallowed.put(target.getUniqueId(), s);
        belly.put(player.getUniqueId(), target.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target)) {
                viewer.hideEntity(plugin, target);
            }
        }
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, ally ? 0 : 1, false, true, true));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_FROG_EAT, 2f, 0.5f);
        world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.5f, 0.6f);
        world.spawnParticle(Particle.ITEM_SLIME, target.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.1);
        world.spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0, ABYSS_DUST);
        if (target instanceof Player victim) {
            if (ally) {
                victim.sendActionBar(plugin.raw("ability-devoured-ally",
                        "&3You were swallowed for safety. &7Sneak to hop out."));
            } else {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks + 20, 0, false, false, false));
                victim.sendActionBar(plugin.raw("ability-devoured-enemy", "&4You've been devoured!"));
            }
        }
        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> devourTick(s), 1L, 1L);
        return true;
    }

    private void devourTick(Swallow s) {
        Player devourer = Bukkit.getPlayer(s.devourer);
        Entity target = Bukkit.getEntity(s.target);
        if (devourer == null || target == null || !target.isValid() || devourer.isDead()
                || !devourer.getPassengers().contains(target)) {
            release(s, false);
            return;
        }
        if (System.currentTimeMillis() >= s.ends) {
            release(s, true);
            return;
        }
        target.setFireTicks(0);
        target.setFallDistance(0);
        if (target instanceof LivingEntity living) {
            living.setRemainingAir(living.getMaximumAir());
        }
        if (devourer.getTicksLived() % 5 == 0) {
            Location belly = devourer.getLocation().add(0, 0.9, 0);
            devourer.getWorld().spawnParticle(Particle.DUST, belly, 2, 0.3, 0.3, 0.3, 0, ABYSS_DUST);
            devourer.getWorld().spawnParticle(Particle.BUBBLE_POP, belly, 2, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** Lets a swallowed entity out. launch = time is up (enemies get spat out and hurt). */
    private void release(Swallow s, boolean launch) {
        if (swallowed.get(s.target) != s) {
            return;
        }
        swallowed.remove(s.target);
        belly.remove(s.devourer);
        if (s.task != null) {
            s.task.cancel();
        }
        Player devourer = Bukkit.getPlayer(s.devourer);
        Entity target = Bukkit.getEntity(s.target);
        if (target != null) {
            target.leaveVehicle();
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                viewer.showEntity(plugin, target);
            }
        }
        if (devourer != null) {
            devourer.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        if (!(target instanceof LivingEntity living) || !living.isValid() || devourer == null
                || !devourer.isOnline() || devourer.getWorld() != living.getWorld()) {
            return;
        }
        if (living instanceof Player victim && !s.ally) {
            victim.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        Location base = devourer.getLocation();
        Vector facing = base.getDirection().setY(0);
        if (facing.lengthSquared() < 1.0E-4) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        Location out = base.clone().add(facing.clone().multiply(1.1));
        if (!out.getBlock().isPassable() || !out.clone().add(0, 1, 0).getBlock().isPassable()) {
            out = base.clone();
        }
        out.setYaw(living.getLocation().getYaw());
        out.setPitch(living.getLocation().getPitch());
        living.teleport(out);
        living.setFallDistance(0);
        World world = living.getWorld();
        world.playSound(out, Sound.ENTITY_PLAYER_BURP, 1.5f, 0.6f);
        world.playSound(out, Sound.ENTITY_LLAMA_SPIT, 1.2f, 0.5f);
        world.spawnParticle(Particle.ITEM_SLIME, out.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.15);
        world.spawnParticle(Particle.SPLASH, out.clone().add(0, 1, 0), 40, 0.3, 0.5, 0.3, 0.2);
        if (!s.ally && launch) {
            Vector v = facing.multiply(s.launch).setY(0.55);
            hurt(devourer, living, s.spitDamage);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (living.isValid()) {
                    living.setVelocity(v);
                }
            });
        }
    }

    /** Lets everybody out (plugin shutdown). */
    public void shutdown() {
        for (Swallow s : new ArrayList<>(swallowed.values())) {
            release(s, false);
        }
    }

    public boolean isSwallowed(Entity entity) {
        return swallowed.containsKey(entity.getUniqueId());
    }

    /** Allies may hop out by sneaking; enemies stay in until they're spat out. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDismount(EntityDismountEvent event) {
        Swallow s = swallowed.get(event.getEntity().getUniqueId());
        if (s == null || !event.getDismounted().getUniqueId().equals(s.devourer)) {
            return;
        }
        Player devourer = Bukkit.getPlayer(s.devourer);
        if (!s.ally && devourer != null && devourer.isOnline() && !devourer.isDead()) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> release(s, false));
    }

    /** Nothing can hurt a swallowed entity (except the void / kill). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwallowedDamage(EntityDamageEvent event) {
        if (swallowed.containsKey(event.getEntity().getUniqueId())
                && event.getCause() != EntityDamageEvent.DamageCause.VOID
                && event.getCause() != EntityDamageEvent.DamageCause.KILL) {
            event.setCancelled(true);
        }
    }

    /** ... and a swallowed entity can't hurt anyone either. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwallowedAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (swallowed.containsKey(damager.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwallowedShoot(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Entity shooter && swallowed.containsKey(shooter.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Teleporting the devourer or the swallowed one ends Devour first. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Swallow s = swallowed.get(id);
        if (s == null) {
            UUID inside = belly.get(id);
            s = inside == null ? null : swallowed.get(inside);
        }
        if (s != null) {
            release(s, false);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (UUID id : swallowed.keySet()) {
            Entity hidden = Bukkit.getEntity(id);
            if (hidden != null && !hidden.equals(event.getPlayer())) {
                event.getPlayer().hideEntity(plugin, hidden);
            }
        }
    }

    /** Ends Devour when the devourer or the swallowed entity dies or leaves. */
    private void releaseFor(UUID id) {
        Swallow s = swallowed.get(id);
        if (s == null) {
            UUID inside = belly.get(id);
            s = inside == null ? null : swallowed.get(inside);
        }
        if (s != null) {
            release(s, false);
        }
    }

    // ================================================================ bows

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !enabledIn(player.getWorld())) {
            return;
        }
        ItemManager.Entry weapon = weaponOf(event.getBow());
        if (weapon == null || weapon.abilities.isEmpty()) {
            return;
        }
        Entity projectile = event.getProjectile();
        projectile.getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING, weapon.ref());
        boolean mirror = mirrorArmed.remove(player.getUniqueId()) != null;
        if (mirror) {
            projectile.getPersistentDataContainer().set(mirrorKey, PersistentDataType.BYTE, (byte) 1);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.5f);
        }
        Particle trail = weapon.abilities.arrowTrail;
        if (trail != null || mirror) {
            repeat(1, 100, step -> {
                if (!projectile.isValid() || (projectile instanceof AbstractArrow arrow && arrow.isInBlock())) {
                    return false;
                }
                World world = projectile.getWorld();
                if (trail != null) {
                    world.spawnParticle(trail, projectile.getLocation(), 2, 0.05, 0.05, 0.05, 0);
                }
                if (mirror) {
                    world.spawnParticle(Particle.END_ROD, projectile.getLocation(), 1, 0, 0, 0, 0);
                }
                return true;
            });
        }
    }

    // ================================================================ damage

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        charge.put(event.getPlayer().getUniqueId(), event.getPlayer().getAttackCooldown());
    }

    /** Hex Mark amplifies all damage the marked entity takes. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHexedDamage(EntityDamageEvent event) {
        Hex hex = hexed.get(event.getEntity().getUniqueId());
        if (hex == null) {
            return;
        }
        if (hex.expires() < System.currentTimeMillis()) {
            hexed.remove(event.getEntity().getUniqueId());
            return;
        }
        event.setDamage(event.getDamage() * hex.multiplier());
        Entity entity = event.getEntity();
        entity.getWorld().spawnParticle(Particle.WITCH, entity.getLocation().add(0, entity.getHeight() / 2, 0), 8, 0.3, 0.4, 0.3, 0.05);
    }

    /** Soul Link sends part of the damage the player takes to the linked target. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLinkedDamage(EntityDamageEvent event) {
        if (DamageGuard.active() || !(event.getEntity() instanceof Player player)
                || UNLINKABLE_CAUSES.contains(event.getCause().name())) {
            return;
        }
        Link link = links.get(player.getUniqueId());
        if (link == null) {
            return;
        }
        if (!(Bukkit.getEntity(link.target()) instanceof LivingEntity target) || !linkHolds(player, link, target)) {
            links.remove(player.getUniqueId());
            return;
        }
        double redirected = event.getDamage() * link.percent();
        event.setDamage(event.getDamage() - redirected);
        hurt(player, target, redirected);
        target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.03);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (DamageGuard.active() || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Player attacker = null;
        ItemManager.Entry weapon = null;
        boolean melee = false;
        if (event.getDamager() instanceof Player player
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            attacker = player;
            weapon = weaponOf(player.getInventory().getItemInMainHand());
            melee = true;
            if (weapon != null && isRanged(weapon)) {
                weapon = null;
            }
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            String ref = projectile.getPersistentDataContainer().get(arrowKey, PersistentDataType.STRING);
            if (ref != null) {
                attacker = shooter;
                weapon = items.get(ref);
            }
            if (projectile.getPersistentDataContainer().has(mirrorKey, PersistentDataType.BYTE)
                    && !shooter.equals(victim) && valid(shooter, victim)) {
                swapPlaces(shooter, victim);
            }
        }
        if (attacker == null || attacker.equals(victim) || weapon == null
                || weapon.abilities.onHit.isEmpty() || !enabledIn(attacker.getWorld())) {
            return;
        }
        // Spam-clicking without a charged attack doesn't trigger on-hit effects.
        Float strength = charge.remove(attacker.getUniqueId());
        if (melee && strength != null && strength < 0.85f) {
            return;
        }
        for (OnHit hit : weapon.abilities.onHit) {
            if (ThreadLocalRandom.current().nextDouble(100) < hit.chance()) {
                applyOnHit(attacker, victim, hit, event);
            }
        }
    }

    /** Remembers damage dealt by players (for Resonance). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player s) {
            attacker = s;
        }
        if (attacker == null || !(event.getEntity() instanceof LivingEntity) || attacker.equals(event.getEntity())) {
            return;
        }
        long now = System.currentTimeMillis();
        dealt.computeIfAbsent(attacker.getUniqueId(), k -> new HashMap<>())
                .merge(event.getEntity().getUniqueId(), new Dealt(event.getFinalDamage(), now),
                        (old, add) -> old.last() < now - DAMAGE_MEMORY_MS ? add
                                : new Dealt(old.amount() + add.amount(), now));
    }

    private void applyOnHit(Player attacker, LivingEntity victim, OnHit hit, EntityDamageByEntityEvent event) {
        Params p = hit.params();
        World world = victim.getWorld();
        Location at = victim.getLocation().add(0, victim.getHeight() / 2, 0);
        switch (hit.type()) {
            case ECHO -> {
                double amount = event.getDamage() * p.d("percent", 40) / 100.0;
                long delay = Math.max(1, p.i("delay-ticks", 30));
                world.spawnParticle(Particle.DUST, at, 6, 0.3, 0.4, 0.3, 0, ECHO_DUST);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!victim.isValid() || victim.isDead() || !attacker.isOnline()) {
                        return;
                    }
                    Location now = victim.getLocation().add(0, victim.getHeight() / 2, 0);
                    victim.getWorld().spawnParticle(Particle.SCULK_SOUL, now, 10, 0.3, 0.4, 0.3, 0.03);
                    victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, now, 1, 0, 0, 0, 0);
                    victim.getWorld().playSound(now, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.2f, 1.4f);
                    hurt(attacker, victim, amount);
                }, delay);
            }
            case CRUSH -> {
                double height = attacker.getLocation().getY() - victim.getLocation().getY();
                if (height >= 1) {
                    double bonus = Math.min(p.d("max-bonus", 8), height * p.d("damage-per-block", 1.5));
                    event.setDamage(event.getDamage() + scaled(victim, bonus));
                    world.spawnParticle(Particle.DUST, at, 20, 0.3, 0.5, 0.3, 0, GRAVITY_CORE);
                    world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.7f, 0.6f);
                }
                if (!victim.isOnGround()) {
                    double slam = p.d("slam", 1.6);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (victim.isValid()) {
                            victim.setVelocity(victim.getVelocity().setY(-slam));
                        }
                    });
                }
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, p.i("slow-ticks", 30), 0));
            }
            case RICOCHET -> {
                LivingEntity next = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity other : targets(attacker, victim.getLocation(), p.d("range", 8))) {
                    double d = other.getLocation().distanceSquared(victim.getLocation());
                    if (!other.equals(victim) && d < best) {
                        best = d;
                        next = other;
                    }
                }
                if (next != null) {
                    LivingEntity bounce = next;
                    line(at, bounce.getLocation().add(0, bounce.getHeight() / 2, 0), 0.35,
                            l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, HEX_DUST));
                    world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.5f);
                    hurt(attacker, bounce, event.getDamage() * p.d("percent", 60) / 100.0);
                }
            }
            case SOUL -> {
                int max = Math.max(1, p.i("max", 5));
                souls.put(attacker.getUniqueId(), Math.min(max, souls.getOrDefault(attacker.getUniqueId(), 0) + 1));
                world.spawnParticle(Particle.SOUL, at, 4, 0.2, 0.3, 0.2, 0.02);
            }
            case CLEAVE -> {
                boolean wrath = frenzy.getOrDefault(attacker.getUniqueId(), 0L) > System.currentTimeMillis();
                double splash = event.getDamage() * p.d("percent", 40) / 100.0 * (wrath ? p.d("wrath-multiplier", 2) : 1);
                double radius = p.d("radius", 3) + (wrath ? 1.5 : 0);
                double healed = 0;
                for (LivingEntity near : targets(attacker, victim.getLocation(), radius)) {
                    if (near.equals(victim)) {
                        continue;
                    }
                    hurt(attacker, near, splash);
                    healed += splash * p.d("lifesteal", 10) / 100.0;
                    near.getWorld().spawnParticle(Particle.DUST, near.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0, HYDRA_DUST);
                }
                ring(victim.getLocation().add(0, 0.9, 0), radius, 18,
                        l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, HYDRA_DUST));
                world.spawnParticle(Particle.SWEEP_ATTACK, at, wrath ? 3 : 1, 0.6, 0.1, 0.6, 0);
                if (healed > 0) {
                    attacker.setHealth(Math.min(maxHealth(attacker), attacker.getHealth() + heal(attacker, healed)));
                }
            }
            case BLUNT_FORCE -> {
                // more damage the more health YOU are missing
                double missing = Math.max(0, maxHealth(attacker) - attacker.getHealth());
                double bonus = Math.min(p.d("max-bonus", 6), missing / 2 * p.d("damage-per-missing-heart", 0.6));
                if (bonus > 0.05) {
                    event.setDamage(event.getDamage() + scaled(victim, bonus));
                    world.spawnParticle(Particle.DUST, at, 12, 0.3, 0.4, 0.3, 0, BLOOD_DUST);
                    world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.6f);
                }
            }
            case TASTE -> addTaste(victim, 1, Math.max(1, p.i("max", 3)), p.d("duration", 6));
            case LIFESTEAL -> {
                double heal = event.getFinalDamage() * p.d("percent", 20) / 100.0;
                attacker.setHealth(Math.min(maxHealth(attacker), attacker.getHealth() + heal(attacker, heal)));
                world.spawnParticle(Particle.DUST, at, 10, 0.3, 0.5, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 0, 20), 1.1f));
            }
        }
    }

    /** Mirror Shot: swap shooter and victim (next tick, outside the damage event). */
    private void swapPlaces(Player shooter, LivingEntity victim) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!shooter.isOnline() || !victim.isValid() || victim.isDead() || shooter.getWorld() != victim.getWorld()) {
                return;
            }
            Location a = shooter.getLocation();
            Location b = victim.getLocation();
            Location shooterTo = b.clone();
            shooterTo.setYaw(a.getYaw());
            shooterTo.setPitch(a.getPitch());
            Location victimTo = a.clone();
            victimTo.setYaw(b.getYaw());
            victimTo.setPitch(b.getPitch());
            World world = shooter.getWorld();
            line(a.clone().add(0, 1, 0), b.clone().add(0, 1, 0), 0.5,
                    l -> world.spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0));
            shooter.teleport(shooterTo);
            victim.teleport(victimTo);
            shooter.setFallDistance(0);
            world.spawnParticle(Particle.WITCH, shooterTo.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.1);
            world.spawnParticle(Particle.WITCH, victimTo.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.1);
            world.playSound(shooterTo, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
            world.playSound(victimTo, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
        });
    }

    /** Killing with a soul weapon grants extra souls. */
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        releaseFor(event.getEntity().getUniqueId());
        taste.remove(event.getEntity().getUniqueId());
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        ItemManager.Entry weapon = weaponOf(killer.getInventory().getItemInMainHand());
        OnHit soul = weapon == null ? null : weapon.abilities.onHit(HitType.SOUL);
        if (soul == null) {
            return;
        }
        int max = Math.max(1, soul.params().i("max", 5));
        souls.merge(killer.getUniqueId(), soul.params().i("on-kill", 2), (a, b) -> Math.min(max, a + b));
        killer.getWorld().spawnParticle(Particle.SOUL, event.getEntity().getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        releaseFor(id);
        taste.remove(id);
        bloodrush.remove(id);
        charge.remove(id);
        lastTrigger.remove(id);
        history.remove(id);
        mirrorArmed.remove(id);
        frenzy.remove(id);
        links.remove(id);
        dealt.remove(id);
        Map<String, Long> map = cooldowns.get(id);
        if (map != null) {
            long now = System.currentTimeMillis();
            map.values().removeIf(t -> t < now);
            if (map.isEmpty()) {
                cooldowns.remove(id);
            }
        }
    }

    // ================================================================ helpers

    private ItemManager.Entry weaponOf(ItemStack stack) {
        String ref = items.idOf(stack);
        if (ref == null || !ref.startsWith("weapon:")) {
            return null;
        }
        return items.get(ref);
    }

    private static boolean isRanged(ItemManager.Entry weapon) {
        Material type = weapon.item.getType();
        return type == Material.BOW || type == Material.CROSSBOW;
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20 : max.getValue();
    }

    /** Is the entity a valid ability target for this player? */
    private boolean valid(Player source, Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity.equals(source) || living.isDead() || !living.isValid()) {
            return false;
        }
        if (entity instanceof ArmorStand || living.isInvulnerable()) {
            return false;
        }
        if (entity instanceof Player target) {
            GameMode mode = target.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                return false;
            }
            if (!plugin.getConfig().getBoolean("ability-settings.damage-players", true)) {
                return false;
            }
        }
        return !(entity instanceof Tameable tameable && source.getUniqueId().equals(tameable.getOwnerUniqueId()));
    }

    private List<LivingEntity> targets(Player source, Location center, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        double r2 = radius * radius;
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (valid(source, entity) && entity.getLocation().distanceSquared(center) <= r2) {
                result.add((LivingEntity) entity);
            }
        }
        return result;
    }

    /** The living entity the player is looking at (blocks stop the ray). */
    private LivingEntity lookTarget(Player player, double range) {
        Location eye = player.getEyeLocation();
        RayTraceResult ray = player.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER,
                true, 0.8, e -> valid(player, e));
        return ray != null && ray.getHitEntity() instanceof LivingEntity target ? target : null;
    }

    /** Ability damage goes through the normal damage event (region protection, armor etc. apply). */
    private void hurt(Player source, LivingEntity target, double amount) {
        if (amount <= 0 || !valid(source, target)) {
            return;
        }
        DamageGuard.run(() -> {
            target.setNoDamageTicks(0);
            target.damage(scaled(target, amount), source);
        });
    }

    private double scaled(LivingEntity target, double amount) {
        if (target instanceof Player) {
            return amount * plugin.getConfig().getDouble("ability-settings.pvp-multiplier", 1.0);
        }
        return amount;
    }

    private static double heightAboveGround(Location location) {
        Block block = location.getBlock();
        int min = location.getWorld().getMinHeight();
        for (int i = 1; i <= 40 && block.getY() - i >= min; i++) {
            Block below = block.getRelative(0, -i, 0);
            if (below.getType().isSolid()) {
                return Math.max(0, location.getY() - (below.getY() + 1));
            }
        }
        return 0;
    }

    private static void ring(Location center, double radius, int points, Consumer<Location> spawn) {
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            spawn.accept(center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        }
    }

    private static void line(Location from, Location to, double step, Consumer<Location> spawn) {
        if (from.getWorld() != to.getWorld()) {
            return;
        }
        Vector dir = to.toVector().subtract(from.toVector());
        double length = dir.length();
        if (length < 0.01) {
            return;
        }
        dir.multiply(1 / length);
        for (double d = 0; d <= length; d += step) {
            spawn.accept(from.clone().add(dir.clone().multiply(d)));
        }
    }

    /**
     * Repeats a step every {@code period} ticks, at most {@code times} times.
     * The step returns false to stop early.
     */
    private void repeat(long period, int times, IntPredicate step) {
        new BukkitRunnable() {
            int i;

            @Override
            public void run() {
                boolean keep;
                try {
                    keep = step.test(i);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Ability animation error: " + ex);
                    keep = false;
                }
                if (!keep || ++i >= times) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, period);
    }
}
