package org.vwtfafa.lockEnd.commands;

import org.bukkit.command.CommandSender;
import org.vwtfafa.lockEnd.LockEnd;

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
        if (!sender.hasPermission("endlock.undo")) {
            sender.sendMessage(plugin.msg("permission"));
            return true;
        }

        if (!plugin.undoLastAction(sender.getName())) {
            sender.sendMessage(plugin.msg("undo.empty"));
            return true;
        }

        sender.sendMessage(plugin.msg("undo.success")
            .replace("%action%", plugin.isLocked() ? plugin.msg("closed") : plugin.msg("open")));
        return true;
    }
}