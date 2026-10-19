package cz.forgebound;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
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
 * The "forge" menu, opened only from a crafting table: list of recipes (row = result + materials),
 * row selection and a FORGE button. Weapons and relics start a forge ritual
 * on the crafting table (see RitualManager).
 *
 * Row (9 slots):    [name] [result] [arrow] [material x5] [-]
 * Bottom row:       [Cancel x2] [<] [Weapons] [page] [Relics] [>] [Proceed x2]
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

    private final Forgebound plugin;
    private final ItemManager items;
    private final RitualManager rituals;
    /** Players who loaded the server resource pack (for menu.resource-pack: auto). */
    private final java.util.Set<java.util.UUID> packLoaded = new java.util.HashSet<>();
    /** Whether the menu currently being built uses the resource-pack style. */
    private boolean style;

    public MenuManager(Forgebound plugin, ItemManager items, RitualManager rituals) {
        this.plugin = plugin;
        this.items = items;
        this.rituals = rituals;
    }

    /** Main menu state: tab, page and selected recipe. */
    private static final class ForgeHolder implements InventoryHolder {
        ItemManager.Kind kind = ItemManager.Kind.WEAPON;
        int page;
        ItemManager.Entry selected;
        Block table;
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ---------------------------------------------------------------- opening

    public void openMain(Player player, Block table) {
        plugin.award(player, "root");
        openForge(player, ItemManager.Kind.WEAPON, 0, null, table);
    }

    private void openForge(Player player, ItemManager.Kind kind, int page, ItemManager.Entry selected, Block table) {
        ForgeHolder holder = new ForgeHolder();
        holder.table = table;
        holder.kind = kind;
        holder.page = page;
        holder.selected = selected;

        style = usesPack(player);
        boolean weapons = kind == ItemManager.Kind.WEAPON;
        Component title = title(
                weapons ? "menu.title-weapons" : "menu.title-relics",
                weapons ? "&8Forge • Weapons" : "&8Forge • Relics",
                weapons ? '' : '');
        holder.inventory = Bukkit.createInventory(holder, 54, title);

        render(holder, player);
        player.openInventory(holder.inventory);
    }

    // ---------------------------------------------------------------- rendering

    private void render(ForgeHolder h, Player player) {
        style = usesPack(player);
        Inventory inv = h.inventory;
        List<ItemManager.Entry> list = items.forgeable(h.kind);
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
            boolean affordable = items.canAfford(player.getInventory(), entry) && available(entry);
            int base = row * 9;

            if (selected) {
                for (int c = 0; c < 9; c++) {
                    inv.setItem(base + c, pane(Material.PURPLE_STAINED_GLASS_PANE));
                }
            }

            inv.setItem(base, nameTag(entry, selected, affordable));
            inv.setItem(base + 1, entry.item.clone());
            inv.setItem(base + 2, pack() ? pane(Material.GRAY_STAINED_GLASS_PANE) : icon(Material.ARROW, "&7◄"));

            for (int i = 0; i < entry.cost.size(); i++) {
                ItemManager.Cost cost = entry.cost.get(i);
                inv.setItem(base + 3 + i, costIcon(cost, items.count(player.getInventory(), cost)));
            }

        }

        // bottom bar
        ItemStack cancel = icon(Material.RED_STAINED_GLASS_PANE, "&cCancel", "&7Close the menu");
        inv.setItem(CANCEL_A, cancel);
        inv.setItem(CANCEL_B, cancel.clone());

        boolean canProceed = h.selected != null && available(h.selected)
                && items.canAfford(player.getInventory(), h.selected);
        boolean ritual = h.selected != null && usesRitual(h.selected);
        ItemStack proceed = canProceed
                ? icon(Material.LIME_STAINED_GLASS_PANE, "&aForge",
                        ritual ? "&7Start the forge ritual on this table" : "&7Forge the selected item",
                        ritual ? "&7Takes &d" + RitualManager.format(rituals.durationMs(h.selected)) + "&7, the item drops here." : "")
                : icon(Material.BLACK_STAINED_GLASS_PANE, "&8Forge",
                        h.selected == null ? "&7Select a recipe first."
                                : !available(h.selected) ? "&cThis item already exists." : "&cYou are missing materials.");
        inv.setItem(PROCEED_A, proceed);
        inv.setItem(PROCEED_B, proceed.clone());

        boolean weapons = h.kind == ItemManager.Kind.WEAPON;
        inv.setItem(TAB_WEAPONS, glow(model(icon(Material.NETHERITE_SWORD, weapons ? "&6Weapons &7(shown)" : "&7Weapons",
                "&eClick to view"), 1001), weapons));
        inv.setItem(TAB_BOXES, glow(model(icon(Material.CLOCK, !weapons ? "&dRelics &7(shown)" : "&7Relics",
                "&eClick to view"), 1201), !weapons));
        inv.setItem(PAGE, icon(Material.PAPER, "&7Page &f" + (h.page + 1) + "&7/&f" + pages));
        if (h.page > 0) {
            inv.setItem(PREV, model(icon(Material.ARROW, "&ePrevious page"), 9020));
        }
        if (h.page < pages - 1) {
            inv.setItem(NEXT, model(icon(Material.ARROW, "&eNext page"), 9021));
        }
    }

    private ItemStack nameTag(ItemManager.Entry entry, boolean selected, boolean affordable) {
        ItemStack stack = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(ItemManager.nameOf(entry.item));
        List<Component> lore = new ArrayList<>();
        lore.add(ItemManager.color(""));
        ForgeData.Claim claim = rituals.claim(entry);
        if (claim != null && claim.state() == ForgeData.State.RITUAL) {
            long left = rituals.remaining(entry.ref());
            lore.add(ItemManager.color("&5⚒ Being forged by &d" + rituals.publicName(claim.name())
                    + (left >= 0 ? " &8(" + RitualManager.format(left) + ")" : "")));
        } else if (claim != null) {
            lore.add(ItemManager.color("&c✖ Already exists &8- &7claimed by &c" + rituals.publicName(claim.name())));
        } else if (rituals.uniqueEnabled() && rituals.isUnique(entry)) {
            lore.add(ItemManager.color("&a✔ Still available &8- &7only one can exist"));
        }
        if (usesRitual(entry)) {
            lore.add(ItemManager.color("&7Ritual: &d" + RitualManager.format(rituals.durationMs(entry))));
        }
        lore.add(ItemManager.color(""));
        if (entry.cost.isEmpty()) {
            lore.add(ItemManager.color("&cNo materials configured (forge)."));
        } else if (affordable) {
            lore.add(ItemManager.color("&aYou have all materials."));
        } else {
            lore.add(ItemManager.color("&cYou are missing materials."));
        }
        lore.add(ItemManager.color(selected ? "&dSelected &7(click again to deselect)" : "&eClick to select"));
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
        lore.add(ItemManager.color("&7Needed: &f" + cost.amount()));
        lore.add(ItemManager.color("&7You have: " + (have >= cost.amount() ? "&a" : "&c") + have));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    // ---------------------------------------------------------------- clicks

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder top = event.getInventory().getHolder();
        if (!(top instanceof ForgeHolder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getSlot();

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
                    Bukkit.getScheduler().runTask(plugin, () -> openForge(player, ItemManager.Kind.WEAPON, 0, null, h.table));
                }
            }
            case TAB_BOXES -> {
                if (h.kind != ItemManager.Kind.RELIC) {
                    Bukkit.getScheduler().runTask(plugin, () -> openForge(player, ItemManager.Kind.RELIC, 0, null, h.table));
                }
            }
            case PROCEED_A, PROCEED_B -> craft(h, player);
            default -> { }
        }
    }

    private void handleRecipeClick(ForgeHolder h, int slot, Player player) {
        List<ItemManager.Entry> list = items.forgeable(h.kind);
        int index = h.page * RECIPE_ROWS + slot / 9;
        if (index >= list.size()) {
            return;
        }
        ItemManager.Entry entry = list.get(index);
        int column = slot % 9;

        if (column == 8) {
            return;
        }

        h.selected = h.selected == entry ? null : entry;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        render(h, player);
    }

    private void craft(ForgeHolder h, Player player) {
        ItemManager.Entry entry = h.selected;
        if (entry != null && !items.craftable(entry)) {
            h.selected = null;
            render(h, player);
            return;   // switched off in the config since the menu was opened
        }
        if (entry == null) {
            player.sendMessage(plugin.msg("forge-select-first", "&cSelect a recipe first."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        {
            Component blocked = rituals.blocked(entry);
            if (blocked != null) {
                player.sendMessage(blocked);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                render(h, player);
                return;
            }
        }
        if (!items.canAfford(player.getInventory(), entry)) {
            player.sendMessage(plugin.msg("forge-missing", "&cYou don't have enough materials."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            render(h, player);
            return;
        }

        if (usesRitual(entry)) {
            Block table = h.table;
            if (!RitualManager.isCraftingTable(table) || table.getWorld() != player.getWorld()
                    || table.getLocation().add(0.5, 0.5, 0.5).distanceSquared(player.getLocation()) > 64) {
                player.sendMessage(plugin.msg("ritual-no-table", "&cStand next to the crafting table to start the ritual."));
                Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                return;
            }
            if (rituals.isRunning(entry.ref())) {
                player.sendMessage(plugin.msg("ritual-weapon-busy", "&cThis item is already being forged somewhere."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                return;
            }
            if (rituals.isRitualTable(table)) {
                player.sendMessage(plugin.msg("ritual-busy-table", "&cThis crafting table is already performing a ritual."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                return;
            }
            if (!rituals.enoughPlayers()) {
                player.sendMessage(plugin.msg("ritual-not-enough-players",
                        "&cA forge ritual needs at least &e{min} &cplayers online &8({online}/{min})&c.",
                        "min", String.valueOf(rituals.minPlayers()),
                        "online", String.valueOf(Bukkit.getOnlinePlayers().size())));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                return;
            }
            items.take(player.getInventory(), entry);
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            rituals.begin(player, entry, table);
            return;
        }

        if (!rituals.claimInstant(entry, player, "forge")) {
            render(h, player);
            return;
        }
        items.take(player.getInventory(), entry);
        ItemStack made = entry.item.clone();
        plugin.stamp(made, entry.ref());
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(made);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        player.sendMessage(plugin.msg("forge-crafted", "&aYou forged {item}&a.",
                "item", ItemManager.nameOf(entry.item)));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.1f);
        render(h, player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ForgeHolder) {
            event.setCancelled(true);
        }
    }

    /** Shift + right-click on a crafting table with an empty hand opens the menu (the only way to open it). */
    @EventHandler
    public void onCraftingTableClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.CRAFTING_TABLE) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking() || event.getItem() != null || !player.hasPermission("forgebound.use")) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        openMain(player, event.getClickedBlock());
    }

    /** Can this recipe be made right now (unique weapons that already exist can't)? */
    private boolean available(ItemManager.Entry entry) {
        return rituals.blocked(entry) == null;
    }

    private boolean usesRitual(ItemManager.Entry entry) {
        return rituals.ritualsEnabled();
    }

    // ---------------------------------------------------------------- helpers

    /** Does the menu being built use the resource-pack look? */
    private boolean pack() {
        return style;
    }

    /**
     * menu.resource-pack: true  = always use the pack look,
     *                     false = never,
     *                     auto  = only for players who loaded the server resource pack.
     */
    public boolean usesPack(Player player) {
        String mode = String.valueOf(plugin.getConfig().get("menu.resource-pack", "auto")).toLowerCase(java.util.Locale.ROOT);
        return switch (mode) {
            case "true" -> true;
            case "false" -> false;
            // remembered status, or what the server knows about the player's pack (e.g. after a plugin reload)
            default -> packLoaded.contains(player.getUniqueId()) || player.hasResourcePack();
        };
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> packLoaded.add(event.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED ->
                    packLoaded.remove(event.getPlayer().getUniqueId());
            default -> { }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packLoaded.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Menu title. With the resource pack it is just a background glyph (font customcraft:menu,
     * white = no tint), without it plain text from the config.
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
     * With the resource pack, glass panes (filler, selection, Cancel, Proceed) get custom model data,
     * which the pack renders as transparent tiles and buttons.
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

    /** Background-glyph title for the other menus (shop, exchange, picker), see font customcraft:menu. */
    public static Component glyphTitle(char glyph) {
        return Component.text("\uf808" + glyph)
                .font(Key.key("customcraft", "menu"))
                .color(NamedTextColor.WHITE);
    }

    /** An invisible tile: the pack's background shows through (custom model data 9000). */
    public static ItemStack clearTile() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.setCustomModelData(9000);
        stack.setItemMeta(meta);
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

    /** With the resource pack, give an icon a custom model (custom-model-data). */
    private ItemStack model(ItemStack stack, int cmd) {
        if (pack()) {
            ItemMeta meta = stack.getItemMeta();
            meta.setCustomModelData(cmd);
            stack.setItemMeta(meta);
        }
        return stack;
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
