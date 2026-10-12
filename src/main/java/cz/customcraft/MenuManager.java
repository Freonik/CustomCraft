package cz.customcraft;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Menu "kovárny": seznam receptů (řádek = výsledek + suroviny), výběr řádku
 * a tlačítko Proceed, které spotřebuje suroviny z inventáře a vydá item.
 *
 * Řádek (9 slotů):  [název] [výsledek] [šipka] [surovina x5] [tlačítko boxu]
 * Spodní řádek:     [Cancel x2] [<] [Zbraně] [strana] [Boxy] [>] [Proceed x2]
 */
public final class MenuManager implements Listener {

    private static final int RECIPE_ROWS = 5;

    private static final int CANCEL_A = 45;
    private static final int CANCEL_B = 46;
    private static final int PREV = 47;
    private static final int TAB_WEAPONS = 48;
    private static final int PAGE = 49;
    private static final int TAB_BOXES = 50;
    private static final int NEXT = 51;
    private static final int PROCEED_A = 52;
    private static final int PROCEED_B = 53;

    private final CustomCraft plugin;
    private final ItemManager items;

    public MenuManager(CustomCraft plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
    }

    /** Stav hlavního menu: záložka, strana a vybraný recept. */
    private static final class ForgeHolder implements InventoryHolder {
        ItemManager.Kind kind = ItemManager.Kind.WEAPON;
        int page;
        ItemManager.Entry selected;
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Náhled možné výhry mystery boxu; pamatuje si, kam se vrátit. */
    private static final class LootHolder implements InventoryHolder {
        final ForgeHolder parent;
        Inventory inventory;

        LootHolder(ForgeHolder parent) {
            this.parent = parent;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ---------------------------------------------------------------- otevření

    public void openMain(Player player) {
        openForge(player, ItemManager.Kind.WEAPON, 0, null);
    }

    private void openForge(Player player, ItemManager.Kind kind, int page, ItemManager.Entry selected) {
        ForgeHolder holder = new ForgeHolder();
        holder.kind = kind;
        holder.page = page;
        holder.selected = selected;

        boolean weapons = kind == ItemManager.Kind.WEAPON;
        Component title = title(
                weapons ? "menu.title-weapons" : "menu.title-boxes",
                weapons ? "&8Kovárna • Zbraně" : "&8Kovárna • Mystery boxy",
                weapons ? '' : '');
        holder.inventory = Bukkit.createInventory(holder, 54, title);

        render(holder, player);
        player.openInventory(holder.inventory);
    }

    // ---------------------------------------------------------------- vykreslení

    private void render(ForgeHolder h, Player player) {
        Inventory inv = h.inventory;
        List<ItemManager.Entry> list = items.all(h.kind);
        int pages = Math.max(1, (list.size() + RECIPE_ROWS - 1) / RECIPE_ROWS);
        h.page = Math.max(0, Math.min(h.page, pages - 1));

        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
        }

        for (int row = 0; row < RECIPE_ROWS; row++) {
            int index = h.page * RECIPE_ROWS + row;
            if (index >= list.size()) {
                break;
            }
            ItemManager.Entry entry = list.get(index);
            boolean selected = entry == h.selected;
            boolean affordable = items.canAfford(player.getInventory(), entry);
            int base = row * 9;

            if (selected) {
                for (int c = 0; c < 9; c++) {
                    inv.setItem(base + c, pane(Material.PURPLE_STAINED_GLASS_PANE));
                }
            }

            inv.setItem(base, nameTag(entry, selected, affordable));
            inv.setItem(base + 1, entry.item.clone());
            inv.setItem(base + 2, icon(Material.ARROW, "&7◄"));

            for (int i = 0; i < entry.cost.size(); i++) {
                ItemManager.Cost cost = entry.cost.get(i);
                inv.setItem(base + 3 + i, costIcon(cost, items.count(player.getInventory(), cost)));
            }

            if (entry.kind == ItemManager.Kind.BOX && !entry.loot.isEmpty()) {
                inv.setItem(base + 8, icon(Material.ENDER_CHEST, "&dMožná výhra",
                        "&7Co může v bedně být", "&7a s jakou šancí.", "", "&eKlikni pro zobrazení"));
            }
        }

        // spodní lišta
        ItemStack cancel = icon(Material.RED_STAINED_GLASS_PANE, "&cCancel", "&7Zavřít menu");
        inv.setItem(CANCEL_A, cancel);
        inv.setItem(CANCEL_B, cancel.clone());

        boolean canProceed = h.selected != null && items.canAfford(player.getInventory(), h.selected);
        ItemStack proceed = canProceed
                ? icon(Material.LIME_STAINED_GLASS_PANE, "&aProceed", "&7Vyrobit vybraný item")
                : icon(Material.BLACK_STAINED_GLASS_PANE, "&8Proceed",
                        h.selected == null ? "&7Nejdřív vyber recept." : "&cChybí ti suroviny.");
        inv.setItem(PROCEED_A, proceed);
        inv.setItem(PROCEED_B, proceed.clone());

        boolean weapons = h.kind == ItemManager.Kind.WEAPON;
        inv.setItem(TAB_WEAPONS, glow(icon(Material.NETHERITE_SWORD, weapons ? "&6Zbraně &7(zobrazeno)" : "&7Zbraně",
                "&eKlikni pro zobrazení"), weapons));
        inv.setItem(TAB_BOXES, glow(icon(Material.PURPLE_SHULKER_BOX, !weapons ? "&dMystery boxy &7(zobrazeno)" : "&7Mystery boxy",
                "&eKlikni pro zobrazení"), !weapons));
        inv.setItem(PAGE, icon(Material.PAPER, "&7Strana &f" + (h.page + 1) + "&7/&f" + pages));
        if (h.page > 0) {
            inv.setItem(PREV, icon(Material.ARROW, "&ePředchozí strana"));
        }
        if (h.page < pages - 1) {
            inv.setItem(NEXT, icon(Material.ARROW, "&eDalší strana"));
        }
    }

    private ItemStack nameTag(ItemManager.Entry entry, boolean selected, boolean affordable) {
        ItemStack stack = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(ItemManager.nameOf(entry.item));
        List<Component> lore = new ArrayList<>();
        lore.add(ItemManager.color(""));
        if (entry.cost.isEmpty()) {
            lore.add(ItemManager.color("&cNemá nastavené suroviny (forge)."));
        } else if (affordable) {
            lore.add(ItemManager.color("&aMáš všechny suroviny."));
        } else {
            lore.add(ItemManager.color("&cChybí ti suroviny."));
        }
        lore.add(ItemManager.color(selected ? "&dVybráno &7(klikni znovu pro zrušení)" : "&eKlikni pro výběr"));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(selected);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack costIcon(ItemManager.Cost cost, int have) {
        ItemStack stack = cost.display().clone();
        stack.setAmount(Math.max(1, Math.min(cost.amount(), 64)));
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(ItemManager.color(""));
        lore.add(ItemManager.color("&7Potřeba: &f" + cost.amount()));
        lore.add(ItemManager.color("&7Máš: " + (have >= cost.amount() ? "&a" : "&c") + have));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    // ---------------------------------------------------------------- loot náhled

    private void openLoot(Player player, ItemManager.Entry entry, ForgeHolder parent) {
        LootHolder holder = new LootHolder(parent);
        Inventory inv = Bukkit.createInventory(holder, 54,
                title("menu.title-loot", "&8Možná výhra", ''));
        holder.inventory = inv;

        int total = entry.loot.stream().mapToInt(ItemManager.Loot::weight).sum();
        int slot = 0;
        for (ItemManager.Loot loot : entry.loot) {
            if (slot >= 45) {
                break;
            }
            double chance = 100.0 * loot.weight() / total;
            ItemStack stack = loot.stack().clone();
            ItemMeta meta = stack.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(ItemManager.color(""));
            lore.add(ItemManager.color("&7Šance: &e" + String.format("%.1f", chance) + " %"));
            if (loot.broadcast()) {
                lore.add(ItemManager.color("&6Vzácná výhra"));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
            inv.setItem(slot++, stack);
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
        }
        inv.setItem(49, icon(Material.ARROW, "&cZpět"));

        player.openInventory(inv);
    }

    // ---------------------------------------------------------------- kliknutí

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder top = event.getInventory().getHolder();
        if (!(top instanceof ForgeHolder) && !(top instanceof LootHolder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getSlot();

        if (top instanceof LootHolder loot) {
            if (slot == 49) {
                ForgeHolder p = loot.parent;
                Bukkit.getScheduler().runTask(plugin, () -> openForge(player, p.kind, p.page, p.selected));
            }
            return;
        }
        handleForge((ForgeHolder) top, slot, player);
    }

    private void handleForge(ForgeHolder h, int slot, Player player) {
        if (slot < RECIPE_ROWS * 9) {
            handleRecipeClick(h, slot, player);
            return;
        }
        switch (slot) {
            case CANCEL_A, CANCEL_B -> Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            case PREV -> {
                if (h.page > 0) {
                    h.page--;
                    render(h, player);
                }
            }
            case NEXT -> {
                h.page++;
                render(h, player);
            }
            case TAB_WEAPONS -> {
                if (h.kind != ItemManager.Kind.WEAPON) {
                    Bukkit.getScheduler().runTask(plugin, () -> openForge(player, ItemManager.Kind.WEAPON, 0, null));
                }
            }
            case TAB_BOXES -> {
                if (h.kind != ItemManager.Kind.BOX) {
                    Bukkit.getScheduler().runTask(plugin, () -> openForge(player, ItemManager.Kind.BOX, 0, null));
                }
            }
            case PROCEED_A, PROCEED_B -> craft(h, player);
            default -> { }
        }
    }

    private void handleRecipeClick(ForgeHolder h, int slot, Player player) {
        List<ItemManager.Entry> list = items.all(h.kind);
        int index = h.page * RECIPE_ROWS + slot / 9;
        if (index >= list.size()) {
            return;
        }
        ItemManager.Entry entry = list.get(index);
        int column = slot % 9;

        if (column == 8) {
            if (entry.kind == ItemManager.Kind.BOX && !entry.loot.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> openLoot(player, entry, h));
            }
            return;
        }

        h.selected = h.selected == entry ? null : entry;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        render(h, player);
    }

    private void craft(ForgeHolder h, Player player) {
        ItemManager.Entry entry = h.selected;
        if (entry == null) {
            player.sendMessage(plugin.msg("forge-select-first", "&cNejdřív vyber recept."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        if (!items.canAfford(player.getInventory(), entry)) {
            player.sendMessage(plugin.msg("forge-missing", "&cNemáš dostatek surovin."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            render(h, player);
            return;
        }

        items.take(player.getInventory(), entry);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(entry.item.clone());
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        player.sendMessage(plugin.msg("forge-crafted", "&aVyrobil jsi {item}&a.",
                "item", ItemManager.nameOf(entry.item)));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.1f);
        render(h, player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ForgeHolder || holder instanceof LootHolder) {
            event.setCancelled(true);
        }
    }

    /** Shift + pravý klik prázdnou rukou na crafting stůl otevře menu. */
    @EventHandler
    public void onCraftingTableClick(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("menu.sneak-click-crafting-table", true)) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.CRAFTING_TABLE) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking() || event.getItem() != null || !player.hasPermission("customcraft.use")) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        openMain(player);
    }

    // ---------------------------------------------------------------- pomocné

    /** Je zapnutý resource pack (menu.resource-pack v configu)? */
    private boolean pack() {
        return plugin.getConfig().getBoolean("menu.resource-pack", false);
    }

    /**
     * Titulek menu. S resource packem je to jen glyf s pozadím (font customcraft:menu,
     * bílá barva = bez obarvení), bez něj obyčejný text z configu.
     */
    private Component title(String configPath, String fallback, char glyph) {
        if (pack()) {
            return Component.text("" + glyph)
                    .font(Key.key("customcraft", "menu"))
                    .color(NamedTextColor.WHITE);
        }
        return ItemManager.color(plugin.getConfig().getString(configPath, fallback));
    }

    /**
     * S resource packem dostanou skleněné panely (výplň, výběr, Cancel, Proceed) custom model data,
     * podle kterých je pack vykreslí jako průhledné dlaždice a tlačítka.
     */
    private ItemStack styled(ItemStack stack) {
        if (!pack()) {
            return stack;
        }
        int cmd = switch (stack.getType()) {
            case GRAY_STAINED_GLASS_PANE -> 9000;
            case PURPLE_STAINED_GLASS_PANE -> 9001;
            case RED_STAINED_GLASS_PANE -> 9010;
            case LIME_STAINED_GLASS_PANE -> 9011;
            case BLACK_STAINED_GLASS_PANE -> 9012;
            default -> -1;
        };
        if (cmd > 0) {
            ItemMeta meta = stack.getItemMeta();
            meta.setCustomModelData(cmd);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack pane(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        stack.setItemMeta(meta);
        return styled(stack);
    }

    private ItemStack icon(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(ItemManager.color(name));
        if (lore.length > 0) {
            meta.lore(Arrays.stream(lore).map(ItemManager::color).toList());
        }
        stack.setItemMeta(meta);
        return styled(stack);
    }

    private ItemStack glow(ItemStack stack, boolean on) {
        if (on) {
            ItemMeta meta = stack.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
