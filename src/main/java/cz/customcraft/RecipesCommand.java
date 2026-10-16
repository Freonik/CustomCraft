package cz.customcraft;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /forge                               - tells players to use a crafting table (the menu only opens there)
 * /forge reload                        - reloads the config (admin)
 * /forge give <player> <ref> [amount]  - gives a weapon / relic, ignores uniqueness (admin)
 * /forge status                        - which weapons exist / are being forged (admin)
 * /forge release <id>                  - makes a weapon/relic forgeable again, stops its ritual (admin)
 * /forge finish <weapon>               - finishes a running ritual right now (admin)
 */
public final class RecipesCommand implements CommandExecutor, TabCompleter {

    private final CustomCraft plugin;
    private final ItemManager items;
    private final RitualManager rituals;
    private final ForgeData data;

    public RecipesCommand(CustomCraft plugin, ItemManager items, RitualManager rituals, ForgeData data) {
        this.plugin = plugin;
        this.items = items;
        this.rituals = rituals;
        this.data = data;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.msg("forge-open-hint", "&7Shift + right-click a &fcrafting table &7with an empty hand to open the forge."));
            if (sender.hasPermission("customcraft.admin")) {
                sender.sendMessage(plugin.msg("usage", "&7Usage: &e/forge"));
            }
            return true;
        }
        if (!sender.hasPermission("customcraft.admin")) {
            sender.sendMessage(plugin.msg("no-permission", "&cYou don't have permission to do that."));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.msg("reloaded", "&aConfig reloaded.",
                        "weapons", items.all(ItemManager.Kind.WEAPON).size(),
                        "relics", items.all(ItemManager.Kind.RELIC).size()));
            }
            case "give" -> give(sender, args);
            case "status" -> status(sender);
            case "release" -> {
                String id = weaponId(sender, args);
                if (id != null) {
                    boolean had = rituals.release(id);
                    if (had && plugin.legends() != null) {
                        plugin.legends().revoke(id);
                    }
                    sender.sendMessage(plugin.msg(had ? "weapon-released" : "weapon-not-claimed",
                            had ? "&a{weapon} can be forged again." : "&7{weapon} wasn't claimed.", "weapon", id));
                }
            }
            case "finish" -> {
                String id = weaponId(sender, args);
                if (id != null) {
                    boolean ok = rituals.finishNow(id);
                    sender.sendMessage(plugin.msg(ok ? "ritual-finishing" : "ritual-none",
                            ok ? "&aFinishing the ritual for {weapon}..." : "&7No ritual is running for {weapon}.",
                            "weapon", id));
                }
            }
            case "event" -> {
                if (plugin.events() == null || args.length < 2) {
                    sender.sendMessage(ItemManager.color("&7Usage: &e/forge event <" + String.join("|", EventManager.names()) + ">"));
                } else if (args[1].equalsIgnoreCase("stop")) {
                    plugin.events().stopAll();
                    sender.sendMessage(ItemManager.color("&7All events stopped."));
                } else {
                    boolean ok = plugin.events().start(args[1], sender instanceof org.bukkit.entity.Player p ? p : null);
                    sender.sendMessage(ItemManager.color(ok ? "&aStarted " + args[1] + "."
                            : "&7Couldn't start " + args[1] + " (already running, turned off, or no good spot)."));
                }
            }
            case "meteor" -> {
                boolean ok = plugin.meteor() != null && plugin.meteor().launch();
                sender.sendMessage(ok ? ItemManager.color("&6☄ Meteor launched.")
                        : ItemManager.color("&7A meteor is already falling (or no landing spot was found)."));
            }
            case "warden" -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage(ItemManager.color("&cOnly players can spawn the Warden."));
                } else if (plugin.warden() == null || plugin.warden().alive()) {
                    sender.sendMessage(ItemManager.color("&7The Forge Warden is already awake."));
                } else {
                    plugin.warden().spawnAt(player);
                }
            }
            case "token", "seal", "tracker" -> {
                if (!(sender instanceof org.bukkit.entity.Player player) || plugin.bounty() == null) {
                    sender.sendMessage(ItemManager.color("&cOnly players can do that."));
                } else {
                    String what = args[0].toLowerCase(Locale.ROOT);
                    int n = args.length > 1 ? Math.max(1, Math.min(64, parseInt(args[1]))) : 1;
                    plugin.bounty().give(player, what.equals("token") ? plugin.bounty().token(n)
                            : what.equals("seal") ? plugin.warden().seal() : plugin.bounty().tracker());
                }
            }
            default -> sender.sendMessage(plugin.msg("usage", "&7Usage: &e/forge"));
        }
        return true;
    }

    private String weaponId(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.msg("usage", "&7Usage: &e/forge"));
            return null;
        }
        String raw = args[1].toLowerCase(Locale.ROOT);
        for (String ref : List.of(raw, "weapon:" + raw, "relic:" + raw)) {
            if (items.get(ref) != null || data.claim(ref) != null) {
                return ref;
            }
        }
        sender.sendMessage(plugin.msg("unknown-item", "&cUnknown item '{item}'.", "item", args[1]));
        return null;
    }

    private void status(CommandSender sender) {
        Map<String, Long> running = rituals.runningSnapshot();
        List<ItemManager.Entry> all = new ArrayList<>(items.all(ItemManager.Kind.WEAPON));
        all.addAll(items.all(ItemManager.Kind.RELIC));
        for (ItemManager.Entry weapon : all) {
            ForgeData.Claim claim = data.claim(weapon.ref());
            String state;
            if (running.containsKey(weapon.ref())) {
                state = "&5forging by " + (claim == null ? "?" : claim.name()) + " &8(" + RitualManager.format(running.get(weapon.ref())) + " left)";
            } else if (claim != null) {
                state = "&cexists &8- &7" + claim.name() + " &8(" + claim.source() + ")";
            } else {
                state = "&aavailable";
            }
            sender.sendMessage(ItemManager.color("&8- &f" + weapon.ref() + " &8: " + state));
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("usage", "&7Usage: &e/forge"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.msg("unknown-player", "&cPlayer '{player}' is not online.", "player", args[1]));
            return;
        }
        ItemManager.Entry entry = items.get(args[2]);
        if (entry == null) {
            sender.sendMessage(plugin.msg("unknown-item", "&cUnknown item '{item}'.", "item", args[2]));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {
                // keep 1
            }
        }

        ItemStack stack = entry.item.clone();
        stack.setAmount(amount);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), rest);
        }
        sender.sendMessage(plugin.msg("given", "&aGave &e{amount}x &a{item} &ato &e{player}&a.",
                "amount", amount, "item", ItemManager.nameOf(entry.item), "player", target.getName()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!sender.hasPermission("customcraft.admin")) {
            return result;
        }
        if (args.length == 1) {
            result.addAll(List.of("reload", "give", "status", "release", "finish", "meteor", "warden", "token", "seal", "tracker", "event"));
        } else if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                Bukkit.getOnlinePlayers().forEach(p -> result.add(p.getName()));
            } else if (args.length == 3) {
                result.addAll(items.refs());
            } else if (args.length == 4) {
                result.addAll(List.of("1", "8", "16", "64"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("event")) {
            result.addAll(EventManager.names());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("release") || args[0].equalsIgnoreCase("finish"))) {
            items.all(ItemManager.Kind.WEAPON).forEach(w -> result.add(w.id));
            items.all(ItemManager.Kind.RELIC).forEach(w -> result.add(w.id));
        }
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        result.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(current));
        return result;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }
}
