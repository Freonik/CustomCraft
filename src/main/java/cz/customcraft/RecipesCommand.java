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
 * /forge                            - opens the forge menu
 * /forge reload                     - reloads the config (admin)
 * /forge give <player> <ref> [amount] - gives a custom item / crate (admin)
 */
public final class RecipesCommand implements CommandExecutor, TabCompleter {

    private final CustomCraft plugin;
    private final ItemManager items;
    private final MenuManager menus;

    public RecipesCommand(CustomCraft plugin, ItemManager items, MenuManager menus) {
        this.plugin = plugin;
        this.items = items;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.msg("player-only", "&cOnly players can use this command."));
                return true;
            }
            if (!player.hasPermission("customcraft.use")) {
                player.sendMessage(plugin.msg("no-permission", "&cYou don't have permission to do that."));
                return true;
            }
            menus.openMain(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!sender.hasPermission("customcraft.admin")) {
                    sender.sendMessage(plugin.msg("no-permission", "&cYou don't have permission to do that."));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(plugin.msg("reloaded", "&aConfig reloaded.",
                        "weapons", items.all(ItemManager.Kind.WEAPON).size(),
                        "boxes", items.all(ItemManager.Kind.BOX).size()));
            }
            case "give" -> give(sender, args);
            default -> sender.sendMessage(plugin.msg("usage", "&7Usage: &e/forge"));
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customcraft.admin")) {
            sender.sendMessage(plugin.msg("no-permission", "&cYou don't have permission to do that."));
            return;
        }
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
        boolean admin = sender.hasPermission("customcraft.admin");
        if (args.length == 1) {
            if (admin) {
                result.add("reload");
                result.add("give");
            }
        } else if (admin && args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                Bukkit.getOnlinePlayers().forEach(p -> result.add(p.getName()));
            } else if (args.length == 3) {
                result.addAll(items.refs());
            } else if (args.length == 4) {
                result.addAll(List.of("1", "8", "16", "64"));
            }
        }
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        result.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(current));
        return result;
    }
}
