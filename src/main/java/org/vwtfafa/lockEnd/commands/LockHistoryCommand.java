package org.vwtfafa.lockEnd.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.vwtfafa.lockEnd.LockEnd;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to view lock history.
 */
public class LockHistoryCommand implements CommandExecutor, TabCompleter {
    private final LockEnd plugin;
    private final List<String> history = new ArrayList<>();
    private Boolean lastPreviousState;

    public LockHistoryCommand(LockEnd plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("endlock.history")) {
            sender.sendMessage(plugin.msg("permission"));
            return true;
        }

        if (history.isEmpty()) {
            sender.sendMessage(plugin.msg("history.empty"));
            return true;
        }

        sender.sendMessage(plugin.msg("history.header"));
        for (int i = history.size() - 1; i >= Math.max(0, history.size() - 10); i--) {
            sender.sendMessage("  " + history.get(i));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }

    /**
     * Adds an entry to the history.
     * @param entry The history entry
     */
    public void addEntry(String entry) {
        history.add(entry);
        // Keep only last 100 entries
        if (history.size() > 100) {
            history.remove(0);
        }
    }

    public void recordPreviousState(boolean previousState) {
        lastPreviousState = previousState;
    }

    public Boolean getLastPreviousState() {
        return lastPreviousState;
    }

    public void clearLastPreviousState() {
        lastPreviousState = null;
    }
}