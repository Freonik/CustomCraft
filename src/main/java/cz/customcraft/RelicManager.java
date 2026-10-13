package cz.customcraft;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
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
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Relic effects: Stasis Hourglass, Angel's Wings, Thorn Plate, Heartsteel Core.
 * A relic only works in its own slot (off-hand, chestplate, boots...).
 * Cooldowns and Heartsteel stacks are stored on the item itself, so they survive restarts and trades.
 */
public final class RelicManager implements Listener {

    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 205, 60), 1.3f);
    private static final Particle.DustOptions WING = new Particle.DustOptions(Color.fromRGB(255, 250, 230), 1.1f);
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(160, 10, 20), 1.2f);
    private static final Particle.DustOptions BLOOD_SMALL = new Particle.DustOptions(Color.fromRGB(200, 20, 34), 0.7f);
    private static final Particle.DustOptions WING_SMALL = new Particle.DustOptions(Color.fromRGB(255, 255, 250), 0.55f);
    private static final Particle.DustOptions GOLD_SMALL = new Particle.DustOptions(Color.fromRGB(255, 210, 90), 0.55f);
    private static final Particle.DustOptions THORN_SMALL = new Particle.DustOptions(Color.fromRGB(60, 110, 40), 0.7f);
    private static final Particle.DustOptions SPIKE_SMALL = new Particle.DustOptions(Color.fromRGB(225, 230, 170), 0.8f);

    private final CustomCraft plugin;
    private final ItemManager items;
    private final NamespacedKey readyKey;
    private final NamespacedKey stacksKey;
    private final NamespacedKey heartKey;

    /** player -> stasis end (ms) */
    private final Map<UUID, Long> stasis = new HashMap<>();
    /** entity -> grievous wounds end (ms) */
    private final Map<UUID, Long> wounded = new HashMap<>();
    /** attacker -> (target -> Heartsteel ready again at) */
    private final Map<UUID, Map<UUID, Long>> heartsteel = new HashMap<>();
    /** player -> attack charge right before the hit */
    private final Map<UUID, Float> charge = new HashMap<>();

    public RelicManager(CustomCraft plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.readyKey = new NamespacedKey(plugin, "relic_ready_at");
        this.stacksKey = new NamespacedKey(plugin, "heartsteel_stacks");
        this.heartKey = new NamespacedKey(plugin, "heartsteel_health");
    }

    public void start() {
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                long now = System.currentTimeMillis();
                stasis.entrySet().removeIf(e -> {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p == null) {
                        return true;
                    }
                    if (e.getValue() <= now) {
                        endStasis(p);
                        return true;
                    }
                    stasisVisual(p, ticks);
                    return false;
                });
                if (plugin.getConfig().getBoolean("relic-settings.particles", true)) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        ambient(p, ticks);
                    }
                }
                if (ticks % 20 == 0) {
                    wounded.values().removeIf(t -> t < now);
                    heartsteel.values().forEach(m -> m.values().removeIf(t -> t < now));
                    heartsteel.values().removeIf(Map::isEmpty);
                }
            }
        }.runTaskTimer(plugin, 20L, 2L);
    }

    // ================================================================ helpers

    /** The relic of this type the player has in its proper slot, or null. */
    private ItemManager.Entry equipped(Player player, Relic.Type type) {
        for (Relic.Slot slot : Relic.Slot.values()) {
            ItemStack stack = player.getInventory().getItem(slot.equipment);
            ItemManager.Entry entry = items.relicOf(stack);
            if (entry != null && entry.relic.type() == type && entry.relic.slot() == slot) {
                return entry;
            }
        }
        return null;
    }

    private ItemStack stackOf(Player player, ItemManager.Entry entry) {
        return player.getInventory().getItem(entry.relic.slot().equipment);
    }

    /** Remaining cooldown of the relic item in ms. */
    private long cooldownLeft(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        Long ready = stack.getItemMeta().getPersistentDataContainer().get(readyKey, PersistentDataType.LONG);
        return ready == null ? 0 : Math.max(0, ready - System.currentTimeMillis());
    }

    private void startCooldown(Player player, ItemManager.Entry entry) {
        if (entry.relic.cooldown() <= 0 || player.hasPermission("customcraft.nocooldown")) {
            return;
        }
        EquipmentSlot slot = entry.relic.slot().equipment;
        ItemStack stack = player.getInventory().getItem(slot);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(readyKey, PersistentDataType.LONG,
                System.currentTimeMillis() + entry.relic.cooldown() * 1000L);
        stack.setItemMeta(meta);
        player.getInventory().setItem(slot, stack);
    }

    private boolean valid(Player source, Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity.equals(source) || living.isDead() || !living.isValid()
                || entity instanceof ArmorStand || living.isInvulnerable()) {
            return false;
        }
        if (entity instanceof Player p && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) {
            return false;
        }
        return !(entity instanceof Tameable t && source.getUniqueId().equals(t.getOwnerUniqueId()));
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20 : max.getValue();
    }

    private static String seconds(long ms) {
        return ((ms + 999) / 1000) + "s";
    }

    /** Action bar part for the player's relics (cooldowns, Heartsteel stacks). Empty if none. */
    public String hud(Player player) {
        StringBuilder bar = new StringBuilder();
        for (Relic.Slot slot : Relic.Slot.values()) {
            ItemStack stack = player.getInventory().getItem(slot.equipment);
            ItemManager.Entry entry = items.relicOf(stack);
            if (entry == null || entry.relic.slot() != slot) {
                continue;
            }
            String part = null;
            switch (entry.relic.type()) {
                case STASIS, REVIVE -> {
                    long left = cooldownLeft(stack);
                    String name = entry.relic.type() == Relic.Type.STASIS ? "&6⧗" : "&f✦";
                    part = name + " " + (left <= 0 ? "&a✔" : "&c" + seconds(left));
                }
                case HEARTSTEEL -> part = "&c❤ &f+" + trim(heartBonus(stack));
                default -> { }
            }
            if (part != null) {
                if (!bar.isEmpty()) {
                    bar.append("  &8┃  ");
                }
                bar.append(part);
            }
        }
        return bar.toString();
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    public boolean inStasis(Player player) {
        Long until = stasis.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    /** Healing after Grievous Wounds (Thorn Plate). Used by weapon abilities that heal directly. */
    public double adjustHeal(LivingEntity entity, double amount) {
        Long until = wounded.get(entity.getUniqueId());
        if (until == null || until < System.currentTimeMillis()) {
            return amount;
        }
        double reduction = plugin.getConfig().getDouble("relics.thorn_plate.relic.heal-reduction", 100) / 100.0;
        return amount * Math.max(0, 1 - reduction);
    }

    // ================================================================ ambient relic effects

    /** Small, cheap visual effects for worn relics (called every 2 ticks). */
    private void ambient(Player player, int ticks) {
        if (player.getGameMode() == GameMode.SPECTATOR || player.hasPotionEffect(PotionEffectType.INVISIBILITY)
                || inStasis(player)) {
            return;
        }
        World world = player.getWorld();
        Location loc = player.getLocation();
        double yaw = Math.toRadians(loc.getYaw());
        org.bukkit.util.Vector forward = new org.bukkit.util.Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        org.bukkit.util.Vector right = new org.bukkit.util.Vector(-Math.cos(yaw), 0, -Math.sin(yaw));

        // Angel's Wings: little feather wings flap at the ankles while sprinting or in the air
        if (ticks % 2 == 0 && (player.isSprinting() || !player.isOnGround()) && equipped(player, Relic.Type.REVIVE) != null) {
            double flap = 0.08 * Math.sin(ticks * 0.9);
            for (int side = -1; side <= 1; side += 2) {
                Location ankle = loc.clone().add(right.clone().multiply(0.17 * side)).add(0, 0.2, 0)
                        .add(forward.clone().multiply(-0.05));
                for (int i = 1; i <= 4; i++) {
                    double t = i / 4.0;
                    Location p = ankle.clone()
                            .add(right.clone().multiply(side * (0.08 + 0.22 * t)))
                            .add(forward.clone().multiply(-0.18 * t))
                            .add(0, 0.12 + 0.18 * t - 0.12 * t * t + flap * t, 0);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, i == 4 ? GOLD_SMALL : WING_SMALL);
                }
            }
        }
        // Heartsteel: a heartbeat on the chest every 3 seconds (lub-dub)
        int beat = ticks % 30;
        if ((beat == 0 || beat == 3) && equipped(player, Relic.Type.HEARTSTEEL) != null) {
            Location chest = loc.clone().add(0, 1.25, 0).add(forward.clone().multiply(0.28));
            world.spawnParticle(Particle.DUST, chest, beat == 0 ? 6 : 3, 0.08, 0.08, 0.08, 0, BLOOD_SMALL);
        }
        // Thorn Plate: now and then a few green spores drift from the shoulders
        if (ticks % 20 == 7 && equipped(player, Relic.Type.THORNS) != null) {
            for (int side = -1; side <= 1; side += 2) {
                world.spawnParticle(Particle.DUST, loc.clone().add(right.clone().multiply(0.35 * side)).add(0, 1.45, 0),
                        1, 0.05, 0.05, 0.05, 0, THORN_SMALL);
            }
        }
    }

    /** Thorn Plate retaliation: thorns burst out of the wearer. */
    private void thornBurst(Player wearer) {
        Location c = wearer.getLocation().add(0, 1.1, 0);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            for (double r = 0.4; r <= 0.9; r += 0.25) {
                wearer.getWorld().spawnParticle(Particle.DUST,
                        c.clone().add(Math.cos(a) * r, (i % 2 == 0 ? 0.2 : -0.2) * r, Math.sin(a) * r),
                        1, 0, 0, 0, 0, r > 0.8 ? SPIKE_SMALL : THORN_SMALL);
            }
        }
    }

    // ================================================================ STASIS HOURGLASS

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemManager.Entry entry = items.relicOf(event.getItem());
        if (entry == null || entry.relic.type() != Relic.Type.STASIS) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.OFF_HAND) {
            player.sendActionBar(plugin.raw("relic-wrong-slot", "&cThis relic only works in your {slot}.",
                    "slot", entry.relic.slot().label.toLowerCase()));
            return;
        }
        if (inStasis(player)) {
            return;
        }
        long left = cooldownLeft(event.getItem());
        if (left > 0) {
            player.sendActionBar(plugin.raw("relic-cooldown", "&c{relic} &7is recharging: &c{time}",
                    "relic", ItemManager.nameOf(entry.item), "time", seconds(left)));
            return;
        }
        double secs = entry.relic.params().d("duration", 2.5);
        stasis.put(player.getUniqueId(), System.currentTimeMillis() + (long) (secs * 1000));
        startCooldown(player, entry);
        player.setFallDistance(0);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, (int) (secs * 20), 0, false, false, false));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.2f, 1.4f);
        world.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 1.2f, 0.6f);
        world.spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
    }

    private void stasisVisual(Player player, int ticks) {
        Location base = player.getLocation();
        World world = player.getWorld();
        for (int i = 0; i < 8; i++) {
            double a = ticks * 0.3 + i * Math.PI / 4;
            double y = (ticks % 20) / 10.0;
            world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 0.7, y, Math.sin(a) * 0.7), 1, 0, 0, 0, 0, GOLD);
        }
    }

    private void endStasis(Player player) {
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.4f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0, GOLD);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!inStasis(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location stay = from.clone();
            stay.setYaw(to.getYaw());
            stay.setPitch(to.getPitch());
            event.setTo(stay);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStasisDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && inStasis(player)
                && event.getCause() != EntityDamageEvent.DamageCause.VOID
                && event.getCause() != EntityDamageEvent.DamageCause.KILL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStasisAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof Player player && inStasis(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStasisShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && inStasis(player)) {
            event.setCancelled(true);
        }
    }

    // ================================================================ ANGEL'S WINGS

    @EventHandler(priority = EventPriority.HIGH)
    public void onResurrect(EntityResurrectEvent event) {
        if (!event.isCancelled() || !(event.getEntity() instanceof Player player)) {
            return; // a totem already saves them
        }
        ItemManager.Entry entry = equipped(player, Relic.Type.REVIVE);
        if (entry == null || cooldownLeft(stackOf(player, entry)) > 0) {
            return;
        }
        event.setCancelled(false);
        startCooldown(player, entry);
        double heal = entry.relic.params().d("heal-percent", 50) / 100.0;
        int protect = entry.relic.params().i("protection-seconds", 3);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            player.setHealth(Math.max(1, Math.min(maxHealth(player), maxHealth(player) * heal)));
            player.setFireTicks(0);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, protect * 20, 4, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true, true));
            wings(player);
        });
        Bukkit.broadcast(plugin.msg("relic-revived", "&f✦ {player} &7was saved by {relic}&7!",
                "player", player.getName(), "relic", ItemManager.nameOf(entry.item)));
    }

    /** Angel wings drawn behind the player for a moment. */
    private void wings(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.3f);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!player.isOnline() || t++ > 30) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                double yaw = Math.toRadians(loc.getYaw());
                org.bukkit.util.Vector back = new org.bukkit.util.Vector(Math.sin(yaw), 0, -Math.cos(yaw)).multiply(0.35);
                org.bukkit.util.Vector side = new org.bukkit.util.Vector(Math.cos(yaw), 0, Math.sin(yaw));
                double flap = 0.25 * Math.sin(t * 0.5);
                for (int s = -1; s <= 1; s += 2) {
                    for (double u = 0.2; u <= 1.4; u += 0.15) {
                        double h = 1.5 + 0.6 * Math.sin(u * 2.2) - 0.25 * u;
                        for (double d = 0; d <= 0.5 * (1.4 - u) + 0.15; d += 0.15) {
                            Location p = loc.clone().add(back).add(side.clone().multiply(s * u))
                                    .add(back.clone().multiply(flap * u * 2)).add(0, h - d, 0);
                            player.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, WING);
                        }
                    }
                }
                player.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Angel's Wings: lighter falls. */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemManager.Entry entry = equipped(player, Relic.Type.REVIVE);
        if (entry != null) {
            event.setDamage(event.getDamage() * (1 - entry.relic.params().d("fall-reduction", 50) / 100.0));
        }
    }

    // ================================================================ attacks: THORNS, HEARTSTEEL

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        charge.put(event.getPlayer().getUniqueId(), event.getPlayer().getAttackCooldown());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (DamageGuard.active() || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        // ---- Thorn Plate (victim side): reflect damage + Grievous Wounds
        if (victim instanceof Player wearer) {
            LivingEntity attacker = null;
            double share = 1.0;
            if (event.getDamager() instanceof LivingEntity living
                    && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
                attacker = living;
            } else if (event.getDamager() instanceof Projectile projectile
                    && projectile.getShooter() instanceof LivingEntity shooter) {
                attacker = shooter;
                share = 0.5;
            }
            ItemManager.Entry thorns = attacker == null ? null : equipped(wearer, Relic.Type.THORNS);
            if (thorns != null && !attacker.equals(wearer)) {
                WeaponAbilities.Params p = thorns.relic.params();
                double reflect = (p.d("reflect-flat", 2) + event.getDamage() * p.d("reflect-percent", 25) / 100.0) * share;
                LivingEntity target = attacker;
                DamageGuard.run(() -> target.damage(reflect, wearer));
                wounded.put(attacker.getUniqueId(), System.currentTimeMillis() + (long) (p.d("wound-seconds", 3) * 1000));
                Location at = attacker.getLocation().add(0, attacker.getHeight() / 2, 0);
                attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, at, 4, 0.3, 0.4, 0.3, 0.1);
                attacker.getWorld().spawnParticle(Particle.DUST, at, 10, 0.3, 0.4, 0.3, 0, BLOOD);
                attacker.getWorld().playSound(at, Sound.ENCHANT_THORNS_HIT, 1f, 0.8f);
                thornBurst(wearer);
                if (attacker instanceof Player hit) {
                    hit.sendActionBar(plugin.raw("relic-wounded", "&4✖ Grievous Wounds &7- you can't heal for a moment"));
                }
            }
        }

        // ---- attacker side: a charged melee hit by a player
        if (!(event.getDamager() instanceof Player player)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        Float strength = charge.remove(player.getUniqueId());
        if (strength != null && strength < 0.85f) {
            return;
        }

        // Heartsteel Core: big hit on a worthy target, grows your max HP for good
        ItemManager.Entry heart = equipped(player, Relic.Type.HEARTSTEEL);
        if (heart != null && (victim instanceof Player || maxHealth(victim) >= 20)) {
            long now = System.currentTimeMillis();
            Map<UUID, Long> ready = heartsteel.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
            Long until = ready.get(victim.getUniqueId());
            if (until == null || until <= now) {
                WeaponAbilities.Params p = heart.relic.params();
                ready.put(victim.getUniqueId(), now + (long) (p.d("target-cooldown", 30) * 1000));
                event.setDamage(event.getDamage() + p.d("bonus-damage", 3) + maxHealth(player) * p.d("bonus-percent-max-hp", 5) / 100.0);
                growHeartsteel(player, heart);
                Location at = victim.getLocation().add(0, victim.getHeight() / 2, 0);
                victim.getWorld().spawnParticle(Particle.DUST, at, 25, 0.4, 0.5, 0.4, 0, BLOOD);
                victim.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2.1, 0), 3, 0.3, 0.1, 0.3, 0);
                victim.getWorld().playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.2f);
            }
        }

    }

    private double heartBonus(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        Double bonus = stack.getItemMeta().getPersistentDataContainer().get(heartKey, PersistentDataType.DOUBLE);
        return bonus == null ? 0 : bonus;
    }

    /** +max HP stored on the Heartsteel item itself (as an off-hand attribute modifier). */
    private void growHeartsteel(Player player, ItemManager.Entry entry) {
        WeaponAbilities.Params p = entry.relic.params();
        EquipmentSlot slot = entry.relic.slot().equipment;
        ItemStack stack = player.getInventory().getItem(slot);
        double bonus = Math.min(p.d("max-bonus-health", 20), heartBonus(stack) + p.d("health-per-stack", 1));
        ItemMeta meta = stack.getItemMeta();
        int stacks = meta.getPersistentDataContainer().getOrDefault(stacksKey, PersistentDataType.INTEGER, 0) + 1;
        meta.getPersistentDataContainer().set(stacksKey, PersistentDataType.INTEGER, stacks);
        meta.getPersistentDataContainer().set(heartKey, PersistentDataType.DOUBLE, bonus);
        meta.removeAttributeModifier(Attribute.MAX_HEALTH);
        meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(new NamespacedKey(plugin, "heartsteel_bonus"),
                bonus, AttributeModifier.Operation.ADD_NUMBER, entry.relic.slot().group));
        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>(
                entry.item.getItemMeta().lore() == null ? java.util.List.of() : entry.item.getItemMeta().lore());
        lore.add(ItemManager.color("&c❤ Stacks: &f" + stacks + " &8• &c+" + trim(bonus) + " max HP"));
        meta.lore(lore);
        stack.setItemMeta(meta);
        player.getInventory().setItem(slot, stack);
    }

    /** Grievous Wounds also reduce natural and potion healing. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        double amount = adjustHeal((LivingEntity) event.getEntity(), event.getAmount());
        if (amount <= 0) {
            event.setCancelled(true);
        } else {
            event.setAmount(amount);
        }
    }

    /** Relics and custom weapons can't be trimmed or upgraded at a smithing table. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(org.bukkit.event.inventory.PrepareSmithingEvent event) {
        if (items.customOf(event.getInventory().getInputEquipment()) != null) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stasis.remove(id);
        charge.remove(id);
        heartsteel.remove(id);
    }
}
