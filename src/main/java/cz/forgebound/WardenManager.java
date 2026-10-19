package cz.forgebound;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Forge Warden: a boss summoned at a crafting table (or guarding a meteor). Three phases, slams, chains,
 * thralls and fire. The top damage dealer gets a Warden's Seal: pick any legendary material.
 */
public final class WardenManager implements Listener {

    private static final int SEAL_MODEL = 1403;
    private static final Particle.DustOptions CHAIN = new Particle.DustOptions(Color.fromRGB(110, 110, 120), 1f);
    private static final Particle.DustOptions EMBER = new Particle.DustOptions(Color.fromRGB(255, 110, 30), 1.4f);

    private final Forgebound plugin;
    private final NamespacedKey wardenKey;
    private final NamespacedKey thrallKey;
    private final NamespacedKey sealKey;

    private WitherSkeleton boss;
    private BossBar bar;
    private Location home;
    private final Map<UUID, Double> damage = new HashMap<>();
    private final List<LivingEntity> thralls = new ArrayList<>();
    private int ticks;
    private int lonely;
    private boolean enraged;
    private boolean summoning;

    public WardenManager(Forgebound plugin) {
        this.plugin = plugin;
        this.wardenKey = Forgebound.key("forge_warden");
        this.thrallKey = Forgebound.key("warden_thrall");
        this.sealKey = Forgebound.key("warden_seal");
    }

    private double cfg(String key, double def) {
        return plugin.getConfig().getDouble("warden." + key, def);
    }

    public boolean alive() {
        return boss != null && boss.isValid() && !boss.isDead();
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    public void shutdown() {
        despawn(false);
    }

    // ================================================================ summoning

    /** Sneak + right-click a crafting table with the summoning item (a Nether Star by default). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSummon(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || block == null || block.getType() != Material.CRAFTING_TABLE || !event.getPlayer().isSneaking()
                || !plugin.getConfig().getBoolean("warden.enabled", true)) {
            return;
        }
        Material summon = Material.matchMaterial(plugin.getConfig().getString("warden.summon-item", "NETHER_STAR"));
        int amount = Math.max(1, plugin.getConfig().getInt("warden.summon-amount", 1));
        ItemStack hand = event.getItem();
        if (summon == null || hand == null || hand.getType() != summon || hand.hasItemMeta() && plugin.items().customOf(hand) != null) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        Player player = event.getPlayer();
        if (alive() || summoning) {
            player.sendMessage(plugin.msg("warden-already", "&cThe Forge Warden is already awake."));
            return;
        }
        if (hand.getAmount() < amount) {
            player.sendMessage(plugin.msg("warden-need", "&cYou need {amount}x {item} to wake the Forge Warden.",
                    "amount", String.valueOf(amount), "item", summon.getKey().getKey().replace('_', ' ')));
            return;
        }
        hand.setAmount(hand.getAmount() - amount);
        summoning = true;
        Location at = block.getLocation().add(0.5, 1, 0.5);
        World world = at.getWorld();
        Bukkit.broadcast(plugin.msg("warden-summoning", "&4⚒ &c{player} &7is waking the &4&lForge Warden&7..."
                , "player", player.getName()));
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                double r = 3 - t * 0.018;
                for (int i = 0; i < 12; i++) {
                    double a = t * 0.2 + i * Math.PI / 6;
                    world.spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * r, 0.1 + t * 0.012, Math.sin(a) * r),
                            1, 0, 0, 0, 0, EMBER);
                }
                if (t % 20 == 0) {
                    world.playSound(at, Sound.BLOCK_ANVIL_LAND, 1f, 0.4f + t / 400f);
                }
                if (t >= 160) {
                    cancel();
                    summoning = false;
                    world.strikeLightningEffect(at);
                    spawn(at.clone().add(0, 0.2, 0), player);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Spawns the boss (summoner may be null, e.g. at a meteor). */
    public void spawn(Location at, Player summoner) {
        if (alive()) {
            return;
        }
        World world = at.getWorld();
        home = at.clone();
        damage.clear();
        enraged = false;
        lonely = 0;
        boss = world.spawn(at, WitherSkeleton.class, w -> {
            w.customName(ItemManager.color("&4&lForge Warden"));
            w.setCustomNameVisible(true);
            w.setPersistent(false);
            w.setRemoveWhenFarAway(false);
            w.setCanPickupItems(false);
            w.getPersistentDataContainer().set(wardenKey, PersistentDataType.BYTE, (byte) 1);
            set(w, Attribute.MAX_HEALTH, cfg("health", 500));
            set(w, Attribute.SCALE, cfg("scale", 1.8));
            set(w, Attribute.ATTACK_DAMAGE, cfg("attack-damage", 14));
            set(w, Attribute.ARMOR, 15);
            set(w, Attribute.KNOCKBACK_RESISTANCE, 1);
            set(w, Attribute.FOLLOW_RANGE, 40);
            set(w, Attribute.MOVEMENT_SPEED, 0.28);
            w.setHealth(cfg("health", 500));
            EntityEquipment eq = w.getEquipment();
            eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            eq.setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                try {
                    eq.setDropChance(slot, 0f);
                } catch (IllegalArgumentException ignored) {
                    // body / saddle slots don't apply
                }
            }
        });
        bar = BossBar.bossBar(ItemManager.color("&4&lForge Warden"), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        world.playSound(at, Sound.ENTITY_WITHER_SPAWN, 2f, 0.6f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0, 0, 0, 0);
        Bukkit.broadcast(plugin.msg("warden-awake", "&4☠ The &lForge Warden &4has awoken at &c{x} {y} {z}&4!",
                "x", String.valueOf(at.getBlockX()), "y", String.valueOf(at.getBlockY()), "z", String.valueOf(at.getBlockZ())));
    }

    private static void set(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    // ================================================================ fight

    private List<Player> fighters(double radius) {
        List<Player> list = new ArrayList<>();
        for (Player p : boss.getWorld().getPlayers()) {
            if (!p.isDead() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR
                    && p.getGameMode() != org.bukkit.GameMode.CREATIVE
                    && p.getLocation().distanceSquared(boss.getLocation()) <= radius * radius) {
                list.add(p);
            }
        }
        return list;
    }

    private void tick() {
        if (boss == null) {
            return;
        }
        if (!alive()) {
            if (boss.isDead()) {
                return;                                            // handled in onDeath
            }
            despawn(true);                                         // unloaded / removed
            return;
        }
        ticks++;
        double max = boss.getAttribute(Attribute.MAX_HEALTH).getValue();
        double hp = boss.getHealth() / max;
        bar.progress((float) Math.max(0, Math.min(1, hp)));
        List<Player> near = fighters(48);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (near.contains(p)) {
                p.showBossBar(bar);
            } else {
                p.hideBossBar(bar);
            }
        }
        // nobody around for a minute: it goes back to sleep
        lonely = near.isEmpty() ? lonely + 1 : 0;
        if (lonely > 120) {
            Bukkit.broadcast(plugin.msg("warden-left", "&7The Forge Warden went back to sleep."));
            despawn(true);
            return;
        }
        // leash
        if (home != null && boss.getLocation().distanceSquared(home) > 40 * 40) {
            boss.teleport(home);
        }
        Location at = boss.getLocation();
        World world = boss.getWorld();
        if (!enraged && hp < 0.25) {
            enraged = true;
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, true));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, true));
            world.playSound(at, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.5f);
            world.spawnParticle(Particle.FLAME, at.clone().add(0, 1.5, 0), 80, 1, 1.5, 1, 0.1);
            for (Player p : near) {
                p.sendMessage(plugin.msg("warden-enraged", "&4☠ The Forge Warden is enraged!"));
            }
        }
        // every 10 ticks = 0.5 s
        if (ticks % 16 == 0) {
            slam(near);
        }
        if (ticks % 20 == 10) {
            chains(near);
        }
        if (ticks % 50 == 25) {
            summonThralls();
        }
        if (hp < 0.5 && ticks % 24 == 12) {
            fireRing(near);
        }
        if (ticks % 4 == 0) {
            world.spawnParticle(Particle.DUST, at.clone().add(0, 1.5, 0), 3, 0.5, 0.8, 0.5, 0, EMBER);
        }
    }

    /** Ground slam: everyone close gets hurt and thrown up and away. */
    private void slam(List<Player> near) {
        Location at = boss.getLocation();
        List<Player> close = new ArrayList<>();
        for (Player p : near) {
            if (p.getLocation().distanceSquared(at) <= 36) {
                close.add(p);
            }
        }
        if (close.isEmpty()) {
            return;
        }
        World world = boss.getWorld();
        world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION, at, 3, 1.5, 0.2, 1.5, 0);
        for (int i = 0; i < 36; i++) {
            double a = i * Math.PI / 18;
            world.spawnParticle(Particle.BLOCK, at.clone().add(Math.cos(a) * 4, 0.2, Math.sin(a) * 4), 3, 0.1, 0.1, 0.1, 0,
                    Material.BLACKSTONE.createBlockData());
        }
        for (Player p : close) {
            p.damage(cfg("slam-damage", 10), boss);
            Vector out = p.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (out.lengthSquared() > 1.0E-4) {
                out.normalize().multiply(1.2);
            }
            p.setVelocity(out.setY(0.8));
        }
    }

    /** Chains: the farthest fighter gets yanked back to the boss. */
    private void chains(List<Player> near) {
        Location at = boss.getLocation();
        Player far = null;
        double best = 36;
        for (Player p : near) {
            double d = p.getLocation().distanceSquared(at);
            if (d > best && d < 25 * 25 && boss.hasLineOfSight(p)) {
                best = d;
                far = p;
            }
        }
        if (far == null) {
            return;
        }
        World world = boss.getWorld();
        Location from = at.clone().add(0, 2, 0);
        Location to = far.getLocation().add(0, 1, 0);
        Vector step = to.toVector().subtract(from.toVector());
        double len = step.length();
        step.multiply(0.4 / Math.max(0.01, len));
        Location cur = from.clone();
        for (double d = 0; d < len; d += 0.4) {
            world.spawnParticle(Particle.DUST, cur, 1, 0, 0, 0, 0, CHAIN);
            cur.add(step);
        }
        world.playSound(to, Sound.BLOCK_CHAIN_PLACE, 1.5f, 0.6f);
        Vector pull = at.toVector().subtract(far.getLocation().toVector()).normalize().multiply(Math.min(2.6, 0.5 + len * 0.1));
        far.setVelocity(pull.setY(0.5));
        far.sendActionBar(plugin.raw("warden-chained", "&7The Warden's chains drag you in!"));
    }

    private void summonThralls() {
        thralls.removeIf(t -> !t.isValid() || t.isDead());
        if (thralls.size() >= 4) {
            return;
        }
        World world = boss.getWorld();
        for (int i = 0; i < 2; i++) {
            Location at = boss.getLocation().add(ThreadLocalRandom.current().nextInt(-3, 4), 0.2,
                    ThreadLocalRandom.current().nextInt(-3, 4));
            if (!at.getBlock().isPassable()) {
                continue;
            }
            WitherSkeleton thrall = world.spawn(at, WitherSkeleton.class, t -> {
                t.customName(ItemManager.color("&8Forge Thrall"));
                t.setPersistent(false);
                t.getPersistentDataContainer().set(thrallKey, PersistentDataType.BYTE, (byte) 1);
                t.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_AXE));
                t.getEquipment().setDropChance(EquipmentSlot.HAND, 0f);
            });
            thralls.add(thrall);
            world.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
        }
        world.playSound(boss.getLocation(), Sound.ENTITY_WITHER_SKELETON_AMBIENT, 2f, 0.5f);
    }

    /** Phase 2: a ring of fire bursts out. */
    private void fireRing(List<Player> near) {
        Location at = boss.getLocation();
        World world = boss.getWorld();
        for (int r = 2; r <= 8; r += 2) {
            for (int i = 0; i < r * 6; i++) {
                double a = i * 2 * Math.PI / (r * 6);
                world.spawnParticle(Particle.FLAME, at.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r), 1, 0, 0.1, 0, 0.01);
            }
        }
        world.playSound(at, Sound.ITEM_FIRECHARGE_USE, 2f, 0.5f);
        for (Player p : near) {
            if (p.getLocation().distanceSquared(at) <= 64) {
                p.setFireTicks(Math.max(p.getFireTicks(), 80));
                p.damage(cfg("fire-damage", 4), boss);
            }
        }
    }

    // ================================================================ damage, death

    private boolean isWarden(Entity e) {
        return e != null && e.getPersistentDataContainer().has(wardenKey, PersistentDataType.BYTE);
    }

    private boolean isThrall(Entity e) {
        return e != null && e.getPersistentDataContainer().has(thrallKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!isWarden(event.getEntity())) {
            return;
        }
        Player attacker = event.getDamager() instanceof Player p ? p
                : event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player s ? s : null;
        if (attacker != null) {
            damage.merge(attacker.getUniqueId(), event.getFinalDamage(), Double::sum);
        }
    }

    /** The Warden and its thralls don't fight each other. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if ((isWarden(event.getEntity()) || isThrall(event.getEntity()))
                && (isWarden(event.getTarget()) || isThrall(event.getTarget()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (isThrall(event.getEntity())) {
            event.getDrops().clear();
            return;
        }
        if (!isWarden(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp((int) cfg("xp", 1000));
        Location at = event.getEntity().getLocation();
        UUID top = damage.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        Player winner = top == null ? event.getEntity().getKiller() : Bukkit.getPlayer(top);
        World world = at.getWorld();
        Item seal = world.dropItem(at.clone().add(0, 0.5, 0), seal(), i -> {
            i.setGlowing(true);
            i.setInvulnerable(true);
            i.setUnlimitedLifetime(true);
            if (winner != null) {
                i.setOwner(winner.getUniqueId());                 // only the top damage dealer can pick it up
            }
        });
        int tokens = plugin.shop() == null || plugin.bounty() == null ? 0 : plugin.shop().wardenTokens();
        if (tokens > 0) {
            world.dropItem(at.clone().add(0, 0.5, 0), plugin.bounty().token(tokens), i -> {
                i.setInvulnerable(true);
                i.setUnlimitedLifetime(true);
                if (winner != null) {
                    i.setOwner(winner.getUniqueId());
                }
            });
        }
        world.playSound(at, Sound.ENTITY_WITHER_DEATH, 2f, 0.7f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 2, 0.5, 0.5, 0.5, 0);
        if (winner != null && plugin.top() != null) {
            plugin.top().add(winner, "wardens");
        }
        Bukkit.broadcast(plugin.msg("warden-defeated", "&4☠ The Forge Warden has fallen! &7Top damage: &f{player}",
                "player", winner == null ? "?" : winner.getName()));
        plugin.getLogger().info("Forge Warden defeated, seal " + seal.getUniqueId() + " for " + (winner == null ? "?" : winner.getName()));
        despawn(false);
    }

    private void despawn(boolean removeBoss) {
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }
        for (LivingEntity t : thralls) {
            if (t.isValid()) {
                t.remove();
            }
        }
        thralls.clear();
        if (removeBoss && boss != null && boss.isValid()) {
            boss.remove();
        }
        boss = null;
        bar = null;
    }

    // ================================================================ Warden's Seal

    public ItemStack seal() {
        ItemStack stack = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(ItemManager.color("&4&lWarden's Seal"));
        meta.lore(List.of(ItemManager.color("&7Taken from the Forge Warden."),
                ItemManager.color("&eRight-click &7to choose any legendary material.")));
        meta.setCustomModelData(SEAL_MODEL);
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(sealKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSealUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.KNOWLEDGE_BOOK || !item.hasItemMeta()
                || !item.getItemMeta().getPersistentDataContainer().has(sealKey, PersistentDataType.BYTE)
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || plugin.bounty() == null) {
            return;
        }
        Player player = event.getPlayer();
        plugin.bounty().openPicker(player, "&4Warden's Seal: choose", chosen -> {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() != Material.KNOWLEDGE_BOOK || !hand.hasItemMeta()
                    || !hand.getItemMeta().getPersistentDataContainer().has(sealKey, PersistentDataType.BYTE)) {
                return;                                             // the seal isn't in hand any more
            }
            hand.setAmount(hand.getAmount() - 1);
            plugin.bounty().give(player, chosen.item.clone());
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 0.6f);
        });
    }

    /** Admin: spawn the boss where the player stands. */
    public void spawnAt(Player player) {
        spawn(player.getLocation(), player);
    }
}
