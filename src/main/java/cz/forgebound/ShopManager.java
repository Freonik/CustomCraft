package cz.forgebound;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The token economy: /shop with fixed offers and a weekly rotating offer, and /pay to send Bounty Tokens to others.
 * Everything can be switched off with economy.enabled: false.
 */
public final class ShopManager implements Listener, CommandExecutor, TabCompleter {

    private static final int[] PERMANENT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int[] ROTATING_SLOTS = {37, 38, 39, 40, 41, 42, 43};

    private final Forgebound plugin;
    private final File file;
    private final YamlConfiguration data;

    /** One offer from the config. */
    private record Offer(String id, Material material, String special, int amount, int price, int limit, String group) {
        boolean rotating() {
            return group.equals("rotating");
        }

        boolean market() {
            return group.equals("market");
        }
    }

    private static final int[] MARKET_SLOTS = {46, 47, 48, 49, 50, 51, 52};
    /** This market's offers and what each player bought (reset when a new market opens). */
    private List<Offer> market = List.of();
    private final Map<String, Integer> marketBought = new HashMap<>();

    private static final class ShopHolder implements InventoryHolder {
        Inventory inventory;
        final Map<Integer, Offer> offers = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public ShopManager(Forgebound plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        if (!data.isSet("anchor")) {
            data.set("anchor", System.currentTimeMillis());
            changed = true;
        }
        if (!data.isSet("salt")) {
            data.set("salt", ThreadLocalRandom.current().nextLong());
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("economy.enabled", true);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save shop.yml: " + ex.getMessage());
        }
    }

    // ================================================================ rewards from events

    /** Tokens for killing the Forge Warden (0 if the economy is off). */
    public int wardenTokens() {
        return enabled() ? Math.max(0, plugin.getConfig().getInt("economy.rewards.warden-tokens", 10)) : 0;
    }

    /** Tokens in a Meteor Cache (0 if the economy is off). */
    public int meteorTokens() {
        if (!enabled()) {
            return 0;
        }
        int min = Math.max(0, plugin.getConfig().getInt("economy.rewards.meteor-tokens-min", 2));
        int max = Math.max(min, plugin.getConfig().getInt("economy.rewards.meteor-tokens-max", 5));
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    // ================================================================ rotation

    private long period() {
        return Math.max(1, plugin.getConfig().getLong("economy.shop.rotation-days", 7)) * 86_400_000L;
    }

    private long rotation() {
        return Math.max(0, (System.currentTimeMillis() - data.getLong("anchor")) / period());
    }

    public long nextRotationAt() {
        return data.getLong("anchor") + (rotation() + 1) * period();
    }

    private List<Offer> offers(String path, String group) {
        List<Offer> list = new ArrayList<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
        if (sec == null) {
            return list;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection o = sec.getConfigurationSection(id);
            if (o == null || !o.getBoolean("enabled", true)) {
                continue;
            }
            String name = o.getString("material", id);
            String special = name.equalsIgnoreCase("legendary_material") || name.equalsIgnoreCase("legend_tracker")
                    ? name.toLowerCase(Locale.ROOT) : null;
            Material material = special != null ? null : Material.matchMaterial(name);
            if (special == null && (material == null || !material.isItem() || material.isAir())) {
                plugin.getLogger().warning("Shop: unknown material for " + path + "." + id);
                continue;
            }
            list.add(new Offer(id, material, special, Math.max(1, Math.min(64, o.getInt("amount", 1))),
                    Math.max(1, o.getInt("price", 1)), Math.max(0, o.getInt("limit", 0)), group));
        }
        return list;
    }

    /** This rotation's offers: the same for everyone, changes every rotation-days. */
    private List<Offer> rotatingNow() {
        List<Offer> pool = offers("economy.shop.rotating", "rotating");
        Collections.shuffle(pool, new Random(data.getLong("salt") * 31 + rotation()));
        int count = Math.max(0, Math.min(ROTATING_SLOTS.length, plugin.getConfig().getInt("economy.shop.rotating-count", 5)));
        return pool.subList(0, Math.min(count, pool.size()));
    }

    private String boughtPath(Player player, Offer offer) {
        return "bought." + rotation() + "." + player.getUniqueId() + "." + (offer.rotating() ? "r_" : "p_") + offer.id;
    }

    private int bought(Player player, Offer offer) {
        return offer.market() ? marketBought.getOrDefault(player.getUniqueId() + "|" + offer.id, 0)
                : data.getInt(boughtPath(player, offer), 0);
    }

    /** Called when a Forge Market opens: picks its offers. */
    public void newMarket() {
        List<Offer> pool = offers("events.forge-market.offers", "market");
        Collections.shuffle(pool);
        int count = Math.max(1, Math.min(MARKET_SLOTS.length, plugin.getConfig().getInt("events.forge-market.count", 4)));
        market = new ArrayList<>(pool.subList(0, Math.min(count, pool.size())));
        marketBought.clear();
    }

    private ItemStack display(Offer offer) {
        if ("legend_tracker".equals(offer.special)) {
            ItemStack t = plugin.bounty().tracker();
            t.setAmount(offer.amount);
            return t;
        }
        if ("legendary_material".equals(offer.special)) {
            ItemStack s = named(new ItemStack(Material.ENDER_EYE, offer.amount), "&d&lRandom legendary material");
            return s;
        }
        return new ItemStack(offer.material, offer.amount);
    }

    private void deliver(Player player, Offer offer) {
        for (int i = 0; i < offer.amount; i++) {
            if ("legend_tracker".equals(offer.special)) {
                plugin.bounty().give(player, plugin.bounty().tracker());
            } else if ("legendary_material".equals(offer.special)) {
                List<ItemManager.Entry> list = plugin.bounty().enabledMaterials();
                if (!list.isEmpty()) {
                    plugin.bounty().give(player, list.get(ThreadLocalRandom.current().nextInt(list.size())).item.clone());
                }
            }
        }
        if (offer.special == null) {
            plugin.bounty().give(player, new ItemStack(offer.material, offer.amount));
        }
    }

    private void cleanOldPurchases() {
        ConfigurationSection bought = data.getConfigurationSection("bought");
        if (bought == null) {
            return;
        }
        String now = String.valueOf(rotation());
        boolean changed = false;
        for (String key : bought.getKeys(false)) {
            if (!key.equals(now)) {
                data.set("bought." + key, null);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    // ================================================================ /shop

    public void open(Player player) {
        if (!enabled()) {
            player.sendMessage(plugin.msg("economy-disabled", "&7The token economy is turned off on this server."));
            return;
        }
        cleanOldPurchases();
        BountyManager bounty = plugin.bounty();
        int tokens = bounty.countTokens(player);
        ShopHolder holder = new ShopHolder();
        boolean pack = plugin.menus() != null && plugin.menus().usesPack(player);
        boolean marketOn = plugin.events() != null && plugin.events().marketOpen() && !market.isEmpty();
        holder.inventory = Bukkit.createInventory(holder, 54, pack
                ? MenuManager.glyphTitle(marketOn ? '\ue003' : '\ue002')
                : ItemManager.color(plugin.getConfig().getString("economy.shop.title", "&8Token Shop")));
        Inventory inv = holder.inventory;
        ItemStack filler = pack ? MenuManager.clearTile() : named(new ItemStack(Material.BLACK_STAINED_GLASS_PANE), " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }
        ItemStack info = bounty.token(1);
        ItemMeta im = info.getItemMeta();
        im.displayName(ItemManager.color("&6&lYour tokens: &f" + tokens));
        im.lore(List.of(ItemManager.color("&7Earn them from bounties, the Forge Warden"),
                ItemManager.color("&7and meteor caches. &e/pay <player> <amount>")));
        info.setItemMeta(im);
        inv.setItem(pack ? 0 : 4, info);
        if (plugin.getConfig().getBoolean("bounty.enabled", true)) {
            ItemStack exchange = named(new ItemStack(Material.COMPASS), "&c&lBounty Exchange");
            ItemMeta em = exchange.getItemMeta();
            em.lore(List.of(ItemManager.color("&7Legend Tracker, legendary materials,"),
                    ItemManager.color("&7the most wanted players")));
            exchange.setItemMeta(em);
            inv.setItem(8, exchange);
        }
        if (!pack) {
            inv.setItem(1, named(new ItemStack(Material.CHEST), "&a&lAlways available"));
        }
        List<Offer> permanent = offers("economy.shop.permanent", "permanent");
        for (int i = 0; i < permanent.size() && i < PERMANENT_SLOTS.length; i++) {
            put(holder, PERMANENT_SLOTS[i], permanent.get(i), player, tokens);
        }
        ItemStack clock = named(new ItemStack(Material.CLOCK), "&e&lThis week's offer");
        ItemMeta cm = clock.getItemMeta();
        cm.lore(List.of(ItemManager.color("&7Changes in &f" + RitualManager.format(nextRotationAt() - System.currentTimeMillis()))));
        clock.setItemMeta(cm);
        if (!pack) {
            for (int i = 27; i < 36; i++) {
                inv.setItem(i, named(new ItemStack(Material.ORANGE_STAINED_GLASS_PANE), " "));
            }
        }
        inv.setItem(pack ? 35 : 31, clock);
        List<Offer> rotating = rotatingNow();
        int start = (ROTATING_SLOTS.length - rotating.size()) / 2;
        for (int i = 0; i < rotating.size(); i++) {
            put(holder, ROTATING_SLOTS[start + i], rotating.get(i), player, tokens);
        }
        if (marketOn) {
            inv.setItem(45, named(new ItemStack(Material.BELL), "&6&lForge Market &7(limited time)"));
            int mstart = (MARKET_SLOTS.length - market.size()) / 2;
            for (int i = 0; i < market.size(); i++) {
                put(holder, MARKET_SLOTS[mstart + i], market.get(i), player, tokens);
            }
        }
        player.openInventory(inv);
    }

    private void put(ShopHolder holder, int slot, Offer offer, Player player, int tokens) {
        ItemStack stack = display(offer);
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.add(Component.empty());
        lore.add(ItemManager.color((tokens >= offer.price ? "&a" : "&c") + "Costs " + offer.price + " Bounty Tokens"));
        if (offer.limit > 0) {
            int left = Math.max(0, offer.limit - bought(player, offer));
            lore.add(ItemManager.color((left > 0 ? "&7" : "&c") + (offer.market() ? "Left for you: " : "Left for you this week: ")
                    + left + "/" + offer.limit));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        holder.inventory.setItem(slot, stack);
        holder.offers.put(slot, offer);
    }

    private static ItemStack named(ItemStack stack, String name) {
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(ItemManager.color(name));
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 8 && plugin.getConfig().getBoolean("bounty.enabled", true)) {
            plugin.bounty().openExchange(player);
            return;
        }
        Offer offer = holder.offers.get(slot);
        if (offer == null || !enabled()) {
            return;
        }
        // the offer may have rotated away while the menu was open
        if (offer.rotating() && rotatingNow().stream().noneMatch(o -> o.id.equals(offer.id))
                || offer.market() && (plugin.events() == null || !plugin.events().marketOpen())) {
            open(player);
            return;
        }
        String path = boughtPath(player, offer);
        if (offer.limit > 0 && bought(player, offer) >= offer.limit) {
            player.sendMessage(plugin.msg("shop-limit", "&cYou've reached the limit for this offer ({limit}x).",
                    "limit", String.valueOf(offer.limit)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        if (!plugin.bounty().takeTokens(player, offer.price)) {
            player.sendMessage(plugin.msg("bounty-not-enough", "&cYou need &6{price} Bounty Tokens&c.",
                    "price", String.valueOf(offer.price)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        if (offer.market()) {
            marketBought.merge(player.getUniqueId() + "|" + offer.id, 1, Integer::sum);
        } else if (offer.limit > 0) {
            data.set(path, data.getInt(path, 0) + 1);
            save();
        }
        deliver(player, offer);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        open(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    // ================================================================ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("player-only", "&cOnly players can use this command."));
            return true;
        }
        if (command.getName().equalsIgnoreCase("shop")) {
            open(player);
            return true;
        }
        // /pay <player> <amount>
        if (!enabled() || !plugin.getConfig().getBoolean("economy.pay", true)) {
            player.sendMessage(plugin.msg("economy-disabled", "&7The token economy is turned off on this server."));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.msg("pay-usage", "&7Usage: &e/pay <player> <amount>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            amount = 0;
        }
        if (target == null || target.equals(player)) {
            player.sendMessage(plugin.msg("pay-no-player", "&cThat player isn't online."));
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(plugin.msg("pay-usage", "&7Usage: &e/pay <player> <amount>"));
            return true;
        }
        if (!plugin.bounty().takeTokens(player, amount)) {
            player.sendMessage(plugin.msg("bounty-not-enough", "&cYou need &6{price} Bounty Tokens&c.", "price", String.valueOf(amount)));
            return true;
        }
        int left = amount;
        while (left > 0) {
            int n = Math.min(64, left);
            plugin.bounty().give(target, plugin.bounty().token(n));
            left -= n;
        }
        player.sendMessage(plugin.msg("pay-sent", "&aYou sent &6{amount} Bounty Tokens &ato &f{player}&a.",
                "amount", String.valueOf(amount), "player", target.getName()));
        target.sendMessage(plugin.msg("pay-received", "&aYou received &6{amount} Bounty Tokens &afrom &f{player}&a.",
                "amount", String.valueOf(amount), "player", player.getName()));
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("pay") && args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (command.getName().equalsIgnoreCase("pay") && args.length == 2) {
            return List.of("1", "5", "10");
        }
        return List.of();
    }
}
