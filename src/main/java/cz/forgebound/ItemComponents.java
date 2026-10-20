package cz.forgebound;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DamageResistant;
import io.papermc.paper.registry.keys.tags.DamageTypeTagKeys;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;

/**
 * Vanilla item components on Forgebound items (Paper's Data Component API):
 * - legendary weapons and relics don't burn in fire or lava (they float like netherite)
 * - rarity "epic"
 * New items get them when they're made; older items are updated when they're dropped or when the
 * player joins.
 */
public final class ItemComponents implements Listener {

    private final Forgebound plugin;

    public ItemComponents(Forgebound plugin) {
        this.plugin = plugin;
    }

    /** Adds the components to a Forgebound item. Returns true if anything changed. */
    public static boolean apply(Forgebound plugin, ItemStack stack, ItemManager.Kind kind) {
        // materials are left alone: recipes compare them exactly, older ones would stop matching
        if (stack == null || stack.isEmpty() || kind == null || kind == ItemManager.Kind.MATERIAL) {
            return false;
        }
        boolean changed = false;
        boolean fireproof = plugin.getConfig().getBoolean("items.fireproof-legends", true);
        if (fireproof && !stack.hasData(DataComponentTypes.DAMAGE_RESISTANT)) {
            stack.setData(DataComponentTypes.DAMAGE_RESISTANT, DamageResistant.damageResistant(DamageTypeTagKeys.IS_FIRE));
            changed = true;
        }
        if (stack.getData(DataComponentTypes.RARITY) != ItemRarity.EPIC) {
            stack.setData(DataComponentTypes.RARITY, ItemRarity.EPIC);
            changed = true;
        }
        return changed;
    }

    private boolean update(ItemStack stack) {
        ItemManager.Entry entry = plugin.items().customOf(stack);
        return entry != null && apply(plugin, stack, entry.kind);
    }

    private void update(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (ItemStack stack : contents) {
            changed |= update(stack);
        }
        if (changed) {
            inventory.setContents(contents);
        }
    }

    /** Dropped items: checked right when they appear, so lava can't get them first. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack stack = item.getItemStack();
        if (update(stack)) {
            item.setItemStack(stack);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        update(player.getInventory());
        update(player.getEnderChest());
    }
}
