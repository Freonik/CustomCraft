package cz.forgebound;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Small helpers around Paper's dialog windows (1.21.6+ clients): confirmations and simple forms.
 * Everything here can be switched off in the config (dialogs.enabled / dialogs.&lt;name&gt;) - the plugin
 * then does what it did before (no confirmation, chat usage hints).
 */
final class Dialogs {

    private Dialogs() {
    }

    /** Is this dialog switched on? */
    static boolean on(Forgebound plugin, String name) {
        return plugin.getConfig().getBoolean("dialogs.enabled", true)
                && plugin.getConfig().getBoolean("dialogs." + name, true);
    }

    /** One-time button callback, valid for a few minutes. Runs on the main thread. */
    static DialogAction action(Forgebound plugin, BiConsumer<Player, DialogResponseView> then) {
        ClickCallback.Options options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build();
        return DialogAction.customClick((view, audience) -> {
            if (audience instanceof Player player) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        then.accept(player, view);
                    }
                });
            }
        }, options);
    }

    static ActionButton button(Component label, Component tooltip, DialogAction action) {
        return ActionButton.builder(label).tooltip(tooltip).width(150).action(action).build();
    }

    /** A plain closing button. */
    static ActionButton close(Component label) {
        return ActionButton.builder(label).width(150).build();
    }

    static DialogBody text(String legacy) {
        return DialogBody.plainMessage(ItemManager.color(legacy), 260);
    }

    static DialogBody item(ItemStack stack) {
        return DialogBody.item(stack).showTooltip(true).showDecorations(true).build();
    }

    /** Yes / no window. "yes" runs on the main thread when the player confirms. */
    static void confirm(Forgebound plugin, Player player, String title, List<DialogBody> body,
                        String yes, String no, Runnable onYes) {
        ActionButton yesButton = button(ItemManager.color(yes), null, action(plugin, (p, view) -> onYes.run()));
        Dialog dialog = Dialog.create(f -> f.empty()
                .base(DialogBase.builder(ItemManager.color(title)).canCloseWithEscape(true).body(body).build())
                .type(DialogType.confirmation(yesButton, close(ItemManager.color(no)))));
        player.showDialog(dialog);
    }

    /** A form: body text, inputs and a list of buttons (+ a closing button). */
    static void form(Player player, String title, List<DialogBody> body, List<? extends DialogInput> inputs,
                     List<ActionButton> buttons, String closeLabel, int columns) {
        List<ActionButton> actions = new ArrayList<>(buttons);
        Dialog dialog = Dialog.create(f -> f.empty()
                .base(DialogBase.builder(ItemManager.color(title)).canCloseWithEscape(true)
                        .body(body).inputs(inputs).build())
                .type(DialogType.multiAction(actions, close(ItemManager.color(closeLabel)), columns)));
        player.showDialog(dialog);
    }
}
