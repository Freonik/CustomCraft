package cz.forgebound;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Totem of the Forge: a totem of undying that works from anywhere in the inventory, brings you back at full
 * health with a few seconds of invulnerability, and throws everyone around you back with a burst of fire.
 * Rewards from big events, weekly quests and the token shop.
 */
public final class TotemManager implements Listener {

    private static final Particle.DustOptions EMBER = new Particle.DustOptions(Color.fromRGB(255, 140, 30), 1.6f);

    private final Forgebound plugin;
    private final NamespacedKey key;

    public TotemManager(Forgebound plugin) {
        this.plugin = plugin;
        this.key = Forgebound.key("forge_totem");
    }

    private double cfg(String path, double def) {
        return plugin.getConfig().getDouble("forge-totem." + path, def);
    }

    public ItemStack item() {
        ItemStack stack = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(ItemManager.color("&6&lTotem of the Forge"));
        List<Component> lore = new ArrayList<>();
        lore.add(ItemManager.color("&7Forged in the heart of the fire."));
        lore.add(Component.empty());
        lore.add(ItemManager.color("&6Works from anywhere in your inventory."));
        lore.add(ItemManager.color("&7Cheat death: back at &ffull health&7,"));
        lore.add(ItemManager.color("&f" + (int) cfg("invulnerable-seconds", 3) + "s &7untouchable, and a burst of fire"));
        lore.add(ItemManager.color("&7throws everyone around you back."));
        meta.lore(lore);
        meta.setCustomModelData(1601);
        meta.setEnchantmentGlintOverride(true);
        meta.setRarity(org.bukkit.inventory.ItemRarity.EPIC);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isTotem(ItemStack stack) {
        return stack != null && stack.getType() == Material.TOTEM_OF_UNDYING && stack.hasItemMeta()
                && stack.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Held in a hand: vanilla uses it up - we only add our part. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHandTotem(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getHand() == null) {
            return;
        }
        ItemStack used = player.getInventory().getItem(event.getHand());
        if (isTotem(used)) {
            Bukkit.getScheduler().runTask(plugin, () -> cheatDeath(player));
        }
    }

    /** Nothing in the hands (and Angel's Wings didn't help): the totem from the inventory. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryTotem(EntityResurrectEvent event) {
        if (!event.isCancelled() || !(event.getEntity() instanceof Player player)
                || !plugin.getConfig().getBoolean("forge-totem.enabled", true)) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (isTotem(stack)) {
                stack.setAmount(stack.getAmount() - 1);
                inv.setItem(slot, stack.getAmount() <= 0 ? null : stack);
                event.setCancelled(false);
                Bukkit.getScheduler().runTask(plugin, () -> cheatDeath(player));
                return;
            }
        }
    }

    private void cheatDeath(Player player) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        double max = player.getAttribute(Attribute.MAX_HEALTH) != null ? player.getAttribute(Attribute.MAX_HEALTH).getValue() : 20;
        player.setHealth(max);
        player.setFireTicks(0);
        int invul = (int) (cfg("invulnerable-seconds", 3) * 20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, invul, 4, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, (int) (cfg("absorption-seconds", 10) * 20),
                (int) cfg("absorption-level", 4) - 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 600, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1, false, true, true));
        // the burst
        double radius = cfg("burst-radius", 5);
        double damage = cfg("burst-damage", 4);
        var at = player.getLocation();
        var world = player.getWorld();
        world.playSound(at, Sound.ITEM_TOTEM_USE, 1f, 0.8f);
        world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
        world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.6f);
        world.spawnParticle(Particle.FLAME, at.clone().add(0, 1, 0), 120, 0.3, 0.6, 0.3, 0.25);
        world.spawnParticle(Particle.LAVA, at.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            world.spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * radius, 0.3, Math.sin(a) * radius), 2, 0.1, 0.1, 0.1, 0, EMBER);
        }
        for (LivingEntity e : world.getNearbyLivingEntities(at, radius)) {
            if (e.equals(player) || (e instanceof Tameable t && player.equals(t.getOwner()))
                    || (e instanceof Player p && ((plugin.teams() != null && plugin.teams().allies(player, p))
                    || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getGameMode() == org.bukkit.GameMode.CREATIVE))) {
                continue;
            }
            Vector away = e.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            e.setVelocity(away.normalize().multiply(cfg("burst-knockback", 1.4)).setY(0.6));
            e.setFireTicks(60);
            if (damage > 0) {
                DamageGuard.run(() -> e.damage(damage, player));
            }
        }
        plugin.award(player, "cheat_death");
        Bukkit.broadcast(plugin.msg("forge-totem-used", "&6✦ {player} &7cheated death with a &6Totem of the Forge&7!",
                "player", player.getName()));
        plugin.getLogger().info("[Totem] " + player.getName() + " used a Totem of the Forge");
    }
}
