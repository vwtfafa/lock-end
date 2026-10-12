package org.vwtfafa.lockEnd.commands;

import org.bukkit.command.CommandSender;
import org.vwtfafa.lockEnd.LockEnd;

import java.util.Map;

/**
 * Command to undo the last lock/unlock action.
 */
public class UndoCommand {
    private final LockEnd plugin;

    public UndoCommand(LockEnd plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes the undo logic.
     * @param sender The command sender
     * @param args The command arguments
     * @return true when handled
     */
    public boolean execute(CommandSender sender, String[] args) {
        return execute(sender, sender.getName());
    }

    public boolean execute(CommandSender sender, String actor) {
        if (!sender.hasPermission("endlock.undo")) {
            plugin.getPaperScheduler().sendMessage(sender, plugin.message("permission", Map.of()));
            return true;
        }

        if (!plugin.undoLastAction(actor)) {
            plugin.getPaperScheduler().sendMessage(sender, plugin.message("undo.empty", Map.of()));
            return true;
        }

        plugin.getPaperScheduler().sendMessage(sender, plugin.message("undo.success", Map.of(
                "%action%", plugin.isLocked() ? plugin.msg("closed") : plugin.msg("open"))));
        return true;
    }
}