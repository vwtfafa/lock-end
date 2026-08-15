package org.vwtfafa.lockEnd.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.vwtfafa.lockEnd.LockEnd;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to undo the last lock/unlock action.
 */
public class UndoCommand implements CommandExecutor, TabCompleter {
    private final LockEnd plugin;
    private boolean lastActionLocked;

    public UndoCommand(LockEnd plugin) {
        this.plugin = plugin;
        this.lastActionLocked = false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("endlock.undo")) {
            sender.sendMessage(plugin.msg("permission"));
            return true;
        }

        boolean currentState = plugin.isLocked();
        plugin.setLocked(!currentState);
        plugin.getConfig().set("locked", !currentState);
        plugin.saveConfig();

        sender.sendMessage(plugin.msg("undo.success")
            .replace("%action%", plugin.isLocked() ? plugin.msg("closed") : plugin.msg("open")));

        plugin.getLogger().info(sender.getName() + " undid the last lock/unlock action.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}