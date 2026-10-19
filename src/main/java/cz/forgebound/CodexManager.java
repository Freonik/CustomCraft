package cz.forgebound;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Forge Codex: a book that explains the server's systems. New players get it once; /codex opens it any time.
 * The pages live in config.yml (codex.pages), so they can be translated or changed.
 */
public final class CodexManager implements Listener, CommandExecutor {

    private static final List<String> DEFAULT_PAGES = List.of(
            "&6&l   Forge Codex\n\n&0Every weapon and relic here is a &5legend&0: it exists only &lonce&r&0 on the whole server.\n\n&8Turn the page to learn how to get one.",
            "&6&lThe Forge\n\n&0Sneak + right-click a &lcrafting table&r&0 with an empty hand.\n\nPick a weapon or relic, bring the materials and start the &5ritual&0.",
            "&6&lThe Ritual\n\n&0Forging takes &l15 minutes&r&0. Everyone is told what is being forged, and the place is revealed step by step.\n\n&4Defend the table!&0 The legend drops on it at the end.",
            "&6&lMaterials\n\n&0Legendary materials drop from bosses and rare mobs, or hide in structure chests.\n\nMeteors, Rifts and the Forge Warden give them too.",
            "&6&lLegends\n\n&0F and Shift+F use a weapon's abilities.\n\n&e/legends&0 shows every legend and your stats.\n\nLeave the server for too long and your legend returns to the forge.",
            "&6&lBounties\n\n&0Carrying a legend puts a price on your head. Whoever kills you gets &6Bounty Tokens&0.\n\n&e/bounty&0 shows the most wanted.\n&0The &lLegend Tracker&r&0 finds them.",
            "&6&lTokens\n\n&0Tokens are the server's money.\n\n&e/shop&0 always has something, and a new offer every week.\n&e/pay&0 sends tokens to a friend.",
            "&6&lEvents\n\n&4Blood Moon&0: tough nights.\n&5Rift&0: waves of monsters.\n&cLegend Hunt&0: a holder is hunted.\n&6Meteor&0, &eGolden Shipment&0, &aHappy Hour&0, &6Forge Market&0.\n\n&e/events&0 shows what's on.",
            "&6&lForge Warden\n\n&0Sneak + right-click a crafting table holding a &lNether Star&r&0 to wake the Warden.\n\nThe best fighter gets a &4Warden's Seal&0: any legendary material.",
            "&6&lCommands\n\n&e/legends /bounty\n/shop /pay /events\n/top /hud /codex\n\n&0Good luck, and may your legend outlive you.");

    private final Forgebound plugin;
    private final NamespacedKey codexKey;
    private final File file;
    private final Set<String> received = new HashSet<>();

    public CodexManager(Forgebound plugin) {
        this.plugin = plugin;
        this.codexKey = Forgebound.key("forge_codex");
        this.file = new File(plugin.getDataFolder(), "codex.yml");
        received.addAll(YamlConfiguration.loadConfiguration(file).getStringList("received"));
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("codex.enabled", true);
    }

    public ItemStack book() {
        ItemStack stack = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) stack.getItemMeta();
        meta.title(ItemManager.color(plugin.getConfig().getString("codex.title", "Forge Codex")));
        meta.author(ItemManager.color(plugin.getConfig().getString("codex.author", "The Forge")));
        List<String> raw = plugin.getConfig().getStringList("codex.pages");
        List<Component> pages = new ArrayList<>();
        for (String page : raw.isEmpty() ? DEFAULT_PAGES : raw) {
            pages.add(ItemManager.color(page.replace("\\n", "\n")));
        }
        meta.addPages(pages.toArray(new Component[0]));
        meta.displayName(ItemManager.color(plugin.getConfig().getString("codex.item-name", "&6&lForge Codex")));
        meta.getPersistentDataContainer().set(codexKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!enabled() || !plugin.getConfig().getBoolean("codex.give-on-first-join", true)
                || !received.add(p.getUniqueId().toString())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.getInventory().addItem(book()).values()
                        .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                p.sendMessage(plugin.msg("codex-given", "&6📖 You got the &lForge Codex&6. Read it, or open it any time with &e/codex&6."));
            }
        }, 60L);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("received", new ArrayList<>(received));
        plugin.saveYaml(yaml, file);
    }

    /** /codex: opens the book (no item needed). */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("player-only", "&cOnly players can use this command."));
            return true;
        }
        if (!enabled()) {
            p.sendMessage(plugin.msg("codex-off", "&7The Forge Codex is turned off on this server."));
            return true;
        }
        p.openBook(book());
        return true;
    }
}
