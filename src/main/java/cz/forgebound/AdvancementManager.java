package cz.forgebound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The plugin's own advancement tab: real Minecraft advancements (toast, chat message, saved with the player).
 */
public final class AdvancementManager implements Listener {

    private record Def(String id, String parent, String icon, String title, String description,
                       String frame, boolean hidden, int xp) { }

    private static final List<Def> DEFS = List.of(
            new Def("root", null, "weapon:echo_blade", "Forgebound",
                    "Shift + right-click a crafting table to open the forge", "task", false, 0),
            // materials
            new Def("legendary_find", "root", "material:crimson_heart", "Legendary Find",
                    "Find a legendary material", "task", false, 0),
            new Def("hoarder", "legendary_find", "material:chrono_sand", "Hoarder",
                    "Find 5 different legendary materials", "goal", false, 0),
            new Def("collector", "hoarder", "trophy", "Collector",
                    "Find every legendary material", "challenge", true, 150),
            new Def("dragons_gift", "legendary_find", "material:seraph_feather", "Dragon's Gift",
                    "Find a Seraph Feather", "goal", false, 0),
            // forging
            new Def("forgemaster", "root", "weapon:graviton_hammer", "Forgemaster",
                    "Finish a forge ritual", "goal", false, 0),
            new Def("finders_keepers", "forgemaster", "weapon:hex_bow", "Finders Keepers",
                    "Pick up a legendary item someone else forged", "goal", false, 0),
            new Def("arsenal", "forgemaster", "weapon:hydra_edge", "Arsenal",
                    "Carry 3 legendary items at once", "goal", false, 0),
            new Def("the_unique", "arsenal", "weapon:knightfall", "The Unique",
                    "Carry 5 legendary items at once", "challenge", true, 100),
            // stars and the Ancient Forges
            new Def("ascension", "forgemaster", "anvil", "Ascension",
                    "Give a legend weapon a star at an Ancient Forge", "goal", false, 0),
            new Def("five_stars", "ascension", "weapon:the_last_act", "Five Stars",
                    "Ascend a legend weapon to five stars at an Ancient Forge", "challenge", false, 100),
            new Def("pilgrim", "ascension", "compass", "Pilgrim",
                    "Use all three Ancient Forges: the Scorched Lands, the Nether and the End", "challenge", false, 75),
            new Def("out_of_the_void", "ascension", "end_crystal", "Out of the Void",
                    "Be in the End when its Ancient Forge rises", "goal", false, 0),
            new Def("starfall", "ascension", "nether_star", "Starfall",
                    "Kill a player and knock the stars off a five-star legend", "challenge", false, 50),
            new Def("hubris", "five_stars", "feather", "Hubris",
                    "Lose stars from a five-star legend without anyone's help", "task", true, 0),
            // sets
            new Def("full_set", "forgemaster", "relic:heartsteel_core", "Full Set",
                    "Wear all four pieces of a relic set", "goal", false, 0),
            new Def("wardrobe", "full_set", "armor_stand", "Wardrobe",
                    "Wear every relic set complete at least once", "challenge", false, 100),
            // the Scorched Lands
            new Def("scorched_earth", "root", "magma_block", "Scorched Earth",
                    "Set foot in the Scorched Lands", "task", false, 0),
            new Def("ruin_raider", "scorched_earth", "iron_axe", "Ruin Raider",
                    "Defeat a guard of the ruins", "task", false, 0),
            // weapons
            new Def("not_today", "forgemaster", "weapon:echo_blade", "Not Today",
                    "Rewind away from death with less than 2 hearts", "task", false, 0),
            new Def("hammer_time", "forgemaster", "weapon:graviton_hammer", "Hammer Time",
                    "Slam someone for 20 damage or more with the Graviton Hammer", "goal", false, 0),
            new Def("swallowed_whole", "forgemaster", "weapon:abyssal_maw", "Swallowed Whole",
                    "Devour an enemy", "task", false, 0),
            new Def("ice_cold", "forgemaster", "weapon:rimebreaker", "Ice Cold",
                    "Kill something you froze with the Rimebreaker", "goal", false, 0),
            new Def("riptide_anywhere", "forgemaster", "weapon:great_sea_trident", "Riptide Anywhere",
                    "Use Tidal Surge without water or rain", "task", false, 0),
            // relics
            new Def("saved_by_an_angel", "forgemaster", "relic:angels_wings", "Saved by an Angel",
                    "Let Angel's Wings bring you back from death", "goal", false, 0),
            new Def("frozen_in_time", "forgemaster", "relic:stasis_hourglass", "Frozen in Time",
                    "Survive a deadly hit inside the Stasis Hourglass", "goal", false, 0),
            new Def("heart_of_steel", "forgemaster", "relic:heartsteel_core", "Heart of Steel",
                    "Grow the Heartsteel Core to its full size", "challenge", false, 50),
            new Def("perfect_parry", "forgemaster", "relic:stitchguard", "Perfect Parry",
                    "Parry 10 hits with the Stitchguard", "challenge", false, 50));

    private static final int TROPHY_MODEL = 1401;

    private final Forgebound plugin;
    private final ItemManager items;
    private final NamespacedKey foundKey;
    private final NamespacedKey parryKey;
    private final NamespacedKey forgedByKey;
    private final NamespacedKey trophyKey;
    private final File file;
    private final YamlConfiguration data;

    public AdvancementManager(Forgebound plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.foundKey = Forgebound.key("found_materials");
        this.parryKey = Forgebound.key("parries");
        this.forgedByKey = Forgebound.key("forged_by");
        this.trophyKey = Forgebound.key("trophy");
        this.file = new File(plugin.getDataFolder(), "advancements.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("advancements.enabled", true);
    }

    // ---------------------------------------------------------------- registration

    public void register() {
        if (!enabled()) {
            return;
        }
        for (Def def : DEFS) {
            NamespacedKey key = Forgebound.key(def.id());
            if (Bukkit.getAdvancement(key) != null) {
                continue;
            }
            try {
                Bukkit.getUnsafe().loadAdvancement(key, json(def).toString());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Could not register advancement " + def.id() + ": " + ex.getMessage());
            }
        }
    }

    /** /reload of data packs drops plugin advancements: put them back. */
    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        register();
    }

    private JsonObject json(Def def) {
        JsonObject root = new JsonObject();
        if (def.parent() != null) {
            root.addProperty("parent", Forgebound.key(def.parent()).toString());
        }
        JsonObject display = new JsonObject();
        display.add("icon", icon(def.icon()));
        JsonObject title = new JsonObject();
        title.addProperty("text", def.title());
        title.addProperty("color", def.frame().equals("challenge") ? "light_purple" : "gold");
        display.add("title", title);
        JsonObject desc = new JsonObject();
        desc.addProperty("text", def.description());
        desc.addProperty("color", "gray");
        display.add("description", desc);
        display.addProperty("frame", def.frame());
        display.addProperty("show_toast", def.parent() != null);
        display.addProperty("announce_to_chat", def.parent() != null);
        display.addProperty("hidden", def.hidden());
        if (def.parent() == null) {
            display.addProperty("background", "minecraft:gui/advancements/backgrounds/nether");
        }
        root.add("display", display);
        JsonObject criteria = new JsonObject();
        JsonObject done = new JsonObject();
        done.addProperty("trigger", "minecraft:impossible");
        criteria.add("done", done);
        root.add("criteria", criteria);
        if (def.xp() > 0) {
            JsonObject rewards = new JsonObject();
            rewards.addProperty("experience", def.xp());
            root.add("rewards", rewards);
        }
        return root;
    }

    /** Advancement icon: a custom item (with its model from the pack) or a vanilla item. */
    private JsonObject icon(String ref) {
        JsonObject icon = new JsonObject();
        ItemStack stack = ref.equals("trophy") ? trophy(null) : null;
        if (stack == null) {
            ItemManager.Entry entry = items.get(ref);
            Material vanilla = entry == null ? Material.matchMaterial(ref) : null;
            stack = entry != null ? entry.item : new ItemStack(vanilla != null && vanilla.isItem() ? vanilla : Material.NETHER_STAR);
        }
        icon.addProperty("id", stack.getType().getKey().toString());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasCustomModelData()) {
            JsonObject components = new JsonObject();
            JsonObject cmd = new JsonObject();
            JsonArray floats = new JsonArray();
            floats.add((float) meta.getCustomModelData());
            cmd.add("floats", floats);
            components.add("minecraft:custom_model_data", cmd);
            icon.add("components", components);
        }
        return icon;
    }

    // ---------------------------------------------------------------- awarding

    /** Completes an advancement for the player. Returns true if it was new. */
    public boolean award(Player player, String id) {
        if (!enabled() || player == null || !player.isOnline()) {
            return false;
        }
        Advancement adv = Bukkit.getAdvancement(Forgebound.key(id));
        if (adv == null) {
            return false;
        }
        if (!id.equals("root")) {
            award(player, "root");
        }
        AdvancementProgress progress = player.getAdvancementProgress(adv);
        if (progress.isDone()) {
            return false;
        }
        for (String criterion : new ArrayList<>(progress.getRemainingCriteria())) {
            progress.awardCriteria(criterion);
        }
        reward(player, id);
        return true;
    }

    /**
     * Counts distinct things towards an advancement (forges used, sets completed) and awards it at `need`.
     * Works for offline players too (awarded when they join).
     */
    public void progress(UUID player, String id, String part, int need) {
        String path = "progress." + player + "." + id;
        List<String> done = new ArrayList<>(data.getStringList(path));
        if (!done.contains(part)) {
            done.add(part);
            data.set(path, done);
            save();
        }
        if (done.size() >= need) {
            awardLater(player, id);
        }
    }

    /** Awards now if the player is online, otherwise the next time they join. */
    public void awardLater(UUID player, String id) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            award(online, id);
            return;
        }
        List<String> pending = new ArrayList<>(data.getStringList("pending." + player));
        if (!pending.contains(id)) {
            pending.add(id);
        }
        data.set("pending." + player, pending);
        save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String path = "pending." + event.getPlayer().getUniqueId();
        List<String> pending = data.getStringList(path);
        if (pending.isEmpty()) {
            return;
        }
        data.set(path, null);
        save();
        Bukkit.getScheduler().runTaskLater(plugin, () -> pending.forEach(id -> award(event.getPlayer(), id)), 40L);
    }

    private void save() {
        plugin.saveYaml(data, file);
    }

    /** Item rewards on top of the advancement's own XP. */
    private void reward(Player player, String id) {
        switch (id) {
            case "collector" -> give(player, trophy(player));
            case "the_unique" -> {
                ItemManager.Entry knightfall = items.get("weapon:knightfall");
                boolean taken = data.getBoolean("knightfall-awarded", false)
                        || knightfall == null || plugin.rituals().claim(knightfall) != null;
                if (!taken && plugin.rituals().claimInstant(knightfall, player, "advancement")) {
                    data.set("knightfall-awarded", true);
                    data.set("knightfall-player", player.getName());
                    save();
                    ItemStack legend = knightfall.item.clone();
                    plugin.stamp(legend, knightfall.ref());
                    give(player, legend);
                    Bukkit.broadcast(ItemManager.color("&6⚔ &e" + player.getName()
                            + " &7was the first to carry five legends and claimed " ) .append(ItemManager.nameOf(knightfall.item))
                            .append(ItemManager.color("&7!")));
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
                    }
                } else {
                    give(player, new ItemStack(Material.NETHERITE_INGOT, 5));
                }
            }
            default -> { }
        }
    }

    private void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    /** The Collector's Trophy: purely cosmetic, with the owner's name. */
    private ItemStack trophy(Player owner) {
        ItemStack stack = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(ItemManager.color("&6&lCollector's Trophy"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(ItemManager.color("&7Every legendary material, found."));
        if (owner != null) {
            lore.add(ItemManager.color("&7Awarded to &f" + owner.getName()));
            lore.add(ItemManager.color("&8" + LocalDate.now()));
        }
        meta.lore(lore);
        meta.setCustomModelData(TROPHY_MODEL);
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(trophyKey, PersistentDataType.STRING,
                owner == null ? "" : owner.getUniqueId().toString());
        stack.setItemMeta(meta);
        return stack;
    }

    /** The trophy is a knowledge book underneath: it must not be "read". */
    @EventHandler(priority = EventPriority.LOW)
    public void onTrophyUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.KNOWLEDGE_BOOK && item.hasItemMeta()
                && item.getPersistentDataContainer().has(trophyKey, PersistentDataType.STRING)
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- tracking

    /** Every 2 seconds: what legendary things does each player carry? */
    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled()) {
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    scan(player);
                }
            }
        }.runTaskTimer(plugin, 100L, 40L);
    }

    private void scan(Player player) {
        Set<String> found = found(player);
        int before = found.size();
        Set<String> legends = new HashSet<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            ItemManager.Entry entry = items.customOf(stack);
            if (entry == null) {
                continue;
            }
            if (entry.kind == ItemManager.Kind.MATERIAL) {
                found.add(entry.id);
            } else {
                legends.add(entry.ref());
            }
        }
        if (found.size() != before) {
            player.getPersistentDataContainer().set(foundKey, PersistentDataType.STRING, String.join(",", found));
        }
        if (!found.isEmpty()) {
            award(player, "legendary_find");
        }
        if (found.size() >= 5) {
            award(player, "hoarder");
        }
        if (found.contains("seraph_feather")) {
            award(player, "dragons_gift");
        }
        // collector: every material that can currently be found
        boolean all = !found.isEmpty();
        for (ItemManager.Entry material : items.all(ItemManager.Kind.MATERIAL)) {
            if (items.craftable(material) && !found.contains(material.id)) {
                all = false;
                break;
            }
        }
        if (all) {
            award(player, "collector");
        }
        if (legends.size() >= 3) {
            award(player, "arsenal");
        }
        if (legends.size() >= 5) {
            award(player, "the_unique");
        }
    }

    private Set<String> found(Player player) {
        String raw = player.getPersistentDataContainer().get(foundKey, PersistentDataType.STRING);
        Set<String> set = new LinkedHashSet<>();
        if (raw != null && !raw.isEmpty()) {
            set.addAll(Arrays.asList(raw.split(",")));
        }
        return set;
    }

    /** Marks a freshly forged item with who forged it (for Finders Keepers). */
    public void markForged(ItemStack stack, UUID forger) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(forgedByKey, PersistentDataType.STRING, forger.toString());
        stack.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (!stack.hasItemMeta()) {
            return;
        }
        String forger = stack.getPersistentDataContainer().get(forgedByKey, PersistentDataType.STRING);
        if (forger != null && !forger.equals(player.getUniqueId().toString())) {
            award(player, "finders_keepers");
        }
    }

    /** Counts parries for Perfect Parry. */
    public void parried(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int count = pdc.getOrDefault(parryKey, PersistentDataType.INTEGER, 0) + 1;
        pdc.set(parryKey, PersistentDataType.INTEGER, count);
        if (count >= 10) {
            award(player, "perfect_parry");
        }
    }
}
