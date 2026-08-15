package org.vwtfafa.lockEnd.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.vwtfafa.lockEnd.LockEnd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Command to validate configuration file.
 */
public class ConfigValidatorCommand implements CommandExecutor, TabCompleter {
    private final LockEnd plugin;

    public ConfigValidatorCommand(LockEnd plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("endlock.validate")) {
            sender.sendMessage(plugin.msg("permission"));
            return true;
        }

        List<String> issues = new ArrayList<>();

        // Validate basic settings
        if (!plugin.getConfig().getBoolean("logging.enabled") &&
            !plugin.getConfig().getBoolean("broadcast.enabled") &&
            !plugin.getConfig().getBoolean("metrics.enabled")) {
            issues.add("Warning: All major features are disabled. Plugin may not work as expected.");
        }

        // Validate scheduled unlock settings
        if (plugin.getConfig().getBoolean("scheduled-unlock.enabled")) {
            String mode = plugin.getConfig().getString("scheduled-unlock.mode", "days");
            if ("days".equalsIgnoreCase(mode)) {
                int days = plugin.getConfig().getInt("scheduled-unlock.days", 7);
                if (days <= 0) {
                    issues.add("Error: scheduled-unlock.days must be positive.");
                }
            } else if ("datetime".equalsIgnoreCase(mode)) {
                String datetime = plugin.getConfig().getString("scheduled-unlock.datetime", "");
                if (datetime.isEmpty()) {
                    issues.add("Error: scheduled-unlock.datetime is required when mode is 'datetime'.");
                }
            }
        }

        // Validate lock reasons
        if (!plugin.getConfig().contains("lock-reasons")) {
            issues.add("Info: No custom lock reasons configured. Using default.");
        }

        // Validate whitelists
        if (plugin.getConfig().isList("whitelists.players")) {
            List<String> players = plugin.getConfig().getStringList("whitelists.players");
            if (players.isEmpty()) {
                issues.add("Info: Player whitelist is empty.");
            }
        }

        if (plugin.getConfig().isList("whitelists.entities")) {
            List<String> entities = plugin.getConfig().getStringList("whitelists.entities");
            if (entities.isEmpty()) {
                issues.add("Info: Entity whitelist is empty.");
            }
        }

        // Output results
        if (issues.isEmpty()) {
            sender.sendMessage(plugin.msg("config.valid"));
        } else {
            sender.sendMessage(plugin.msg("config.issues"));
            for (String issue : issues) {
                sender.sendMessage("  - " + issue);
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}