package cz.forgebound;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What Forgebound's enchantments do (they are registered in {@link ForgeboundBootstrap}):
 * - Forge Touch: mined blocks drop already smelted (raw iron -> iron ingot, sand -> glass ...), with furnace XP
 * - Bounty Hunter I-III: more melee damage against players who have a bounty on their head
 */
public final class EnchantManager implements Listener {

    private final Forgebound plugin;
    private Enchantment forgeTouch;
    private Enchantment bountyHunter;
    /** block drop -> furnace result (+ xp), built from the server's furnace recipes on first use */
    private Map<Material, Smelt> smelting;

    record Smelt(ItemStack result, float xp) { }

    public EnchantManager(Forgebound plugin) {
        this.plugin = plugin;
        Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        forgeTouch = registry.get(ForgeboundBootstrap.FORGE_TOUCH);
        bountyHunter = registry.get(ForgeboundBootstrap.BOUNTY_HUNTER);
        if (forgeTouch == null || bountyHunter == null) {
            plugin.getLogger().warning("Forgebound enchantments are missing (was the plugin loaded without its bootstrap?)");
        }
    }

    public Enchantment forgeTouch() {
        return forgeTouch;
    }

    public Enchantment bountyHunter() {
        return bountyHunter;
    }

    /** The enchantment with this key, or null. */
    public static Enchantment get(Key key) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
    }

    // ================================================================ Forge Touch

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (forgeTouch == null || !plugin.getConfig().getBoolean("enchantments.forge-touch.enabled", true)) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty() || tool.getEnchantmentLevel(forgeTouch) <= 0) {
            return;
        }
        List<String> skip = plugin.getConfig().getStringList("enchantments.forge-touch.skip");
        float xp = 0;
        boolean any = false;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (skipped(stack.getType(), skip)) {
                continue;
            }
            Smelt smelt = smelt(stack.getType());
            if (smelt == null) {
                continue;
            }
            ItemStack result = smelt.result().clone();
            result.setAmount(Math.min(result.getMaxStackSize(), result.getAmount() * stack.getAmount()));
            item.setItemStack(result);
            xp += smelt.xp() * stack.getAmount();
            any = true;
        }
        if (!any) {
            return;
        }
        Location at = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        at.getWorld().spawnParticle(Particle.FLAME, at, 6, 0.25, 0.25, 0.25, 0.01);
        if (plugin.getConfig().getBoolean("enchantments.forge-touch.xp", true)) {
            int orbs = (int) xp;
            if (ThreadLocalRandom.current().nextFloat() < xp - orbs) {
                orbs++;                                       // like a furnace: the fraction is a chance
            }
            if (orbs > 0) {
                int amount = orbs;
                at.getWorld().spawn(at, ExperienceOrb.class, orb -> orb.setExperience(amount));
            }
        }
    }

    /** Config "skip" entries: exact names or simple wildcards like "*_log" / "raw_*". */
    static boolean skipped(Material material, List<String> skip) {
        String name = material.name().toLowerCase(Locale.ROOT);
        for (String raw : skip) {
            String s = raw.toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (s.startsWith("*") ? name.endsWith(s.substring(1))
                    : s.endsWith("*") ? name.startsWith(s.substring(0, s.length() - 1))
                    : name.equals(s)) {
                return true;
            }
        }
        return false;
    }

    Smelt smelt(Material material) {
        if (smelting == null) {
            smelting = new EnumMap<>(Material.class);
            Iterator<Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext()) {
                if (it.next() instanceof FurnaceRecipe recipe
                        && recipe.getInputChoice() instanceof RecipeChoice.MaterialChoice choice) {
                    for (Material input : choice.getChoices()) {
                        smelting.putIfAbsent(input, new Smelt(recipe.getResult(), recipe.getExperience()));
                    }
                }
            }
        }
        return smelting.get(material);
    }

    // ================================================================ Bounty Hunter

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (bountyHunter == null || !(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof Player victim) || plugin.bounty() == null
                || !plugin.getConfig().getBoolean("enchantments.bounty-hunter.enabled", true)) {
            return;
        }
        int level = attacker.getInventory().getItemInMainHand().getEnchantmentLevel(bountyHunter);
        if (level <= 0 || plugin.bounty().bountyOf(victim.getUniqueId()) <= 0
                || plugin.teams() != null && plugin.teams().allies(attacker, victim)) {
            return;
        }
        double perLevel = plugin.getConfig().getDouble("enchantments.bounty-hunter.damage-per-level", 0.08);
        event.setDamage(event.getDamage() * bonus(level, perLevel));
        victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.2, 0), 8, 0.3, 0.4, 0.3, 0.1);
    }

    /** Damage multiplier for a Bounty Hunter level, e.g. level 3 with 0.08 per level = x1.24. */
    static double bonus(int level, double perLevel) {
        return 1.0 + Math.max(0, level) * Math.max(0, perLevel);
    }
}
