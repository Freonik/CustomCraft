package cz.customcraft;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Opening mystery crates (shulkers): right-click, short action bar animation, weighted reward.
 */
public final class BoxManager implements Listener {

    /** Number of animation steps; one step = 3 ticks (about 3 s in total). */
    private static final int ANIMATION_STEPS = 20;

    private final CustomCraft plugin;
    private final ItemManager items;
    private final Random random = new Random();
    private final Map<UUID, Opening> openings = new HashMap<>();

    public BoxManager(CustomCraft plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
    }

    private static final class Opening {
        final ItemManager.Entry box;
        final ItemManager.Loot result;
        BukkitTask task;

        Opening(ItemManager.Entry box, ItemManager.Loot result) {
            this.box = box;
            this.result = result;
        }
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemManager.Entry box = items.boxOf(event.getItem());
        if (box == null) {
            return;
        }

        // A crate is never placed.
        event.setUseItemInHand(Event.Result.DENY);

        // Clicking a chest/furnace/table etc. keeps vanilla behaviour (no crate opening),
        // so chests can still be opened with a crate in hand.
        Block clicked = event.getClickedBlock();
        Player player = event.getPlayer();
        if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
            return;
        }

        event.setCancelled(true);
        if (!player.hasPermission("customcraft.use")) {
            player.sendMessage(plugin.msg("no-permission", "&cYou don't have permission to do that."));
            return;
        }
        open(player, box);
    }

    /** Safety net: a crate must never be placed in any other way either. */
    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (items.boxOf(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    /** If the player leaves mid-animation, they get the reward right away (and don't lose the crate). */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        finish(event.getPlayer());
    }

    // ---------------------------------------------------------------- opening

    private void open(Player player, ItemManager.Entry box) {
        if (openings.containsKey(player.getUniqueId())) {
            player.sendMessage(plugin.msg("box-already-opening", "&cYou are already opening a crate."));
            return;
        }
        if (box.loot.isEmpty()) {
            player.sendMessage(plugin.msg("box-empty", "&cThis crate has no loot configured."));
            return;
        }

        // Consume one crate from the hand.
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            ItemStack rest = hand.clone();
            rest.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(rest);
        }

        Opening opening = new Opening(box, roll(box));
        openings.put(player.getUniqueId(), opening);

        opening.task = new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                if (step > ANIMATION_STEPS) {
                    finish(player);
                    return;
                }
                ItemManager.Loot preview = box.loot.get(random.nextInt(box.loot.size()));
                player.sendActionBar(ItemManager.nameOf(preview.stack()));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.7f + step * 0.04f);
                player.getWorld().spawnParticle(Particle.ENCHANT,
                        player.getLocation().add(0, 1.5, 0), 10, 0.4, 0.4, 0.4, 0.6);
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /** Finishes opening and hands out the reward. Safe to call repeatedly. */
    private void finish(Player player) {
        Opening opening = openings.remove(player.getUniqueId());
        if (opening == null) {
            return;
        }
        if (opening.task != null) {
            opening.task.cancel();
        }

        ItemStack reward = opening.result.stack().clone();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }

        Component itemName = ItemManager.nameOf(reward);
        player.sendActionBar(itemName);
        player.sendMessage(plugin.msg("box-reward", "&aYou got: &f{item} &7x{amount}",
                "item", itemName, "amount", reward.getAmount()));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1.2, 0), 40, 0.5, 0.6, 0.5, 0.1);

        if (opening.result.broadcast()) {
            Bukkit.broadcast(plugin.msg("box-broadcast", "&6{player} &7opened {box} &7and won &f{item}&7!",
                    "player", player.getName(),
                    "box", ItemManager.nameOf(opening.box.item),
                    "item", itemName));
        }
    }

    /** Finishes all running openings (plugin disable), nobody loses a crate. */
    public void finishAll() {
        for (UUID uuid : new ArrayList<>(openings.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                finish(player);
            }
        }
        openings.clear();
    }

    private ItemManager.Loot roll(ItemManager.Entry box) {
        int total = box.loot.stream().mapToInt(ItemManager.Loot::weight).sum();
        int pick = random.nextInt(total);
        for (ItemManager.Loot loot : box.loot) {
            pick -= loot.weight();
            if (pick < 0) {
                return loot;
            }
        }
        return box.loot.get(box.loot.size() - 1);
    }
}
