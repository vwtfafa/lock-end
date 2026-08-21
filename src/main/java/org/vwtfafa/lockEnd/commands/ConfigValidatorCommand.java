package org.vwtfafa.lockEnd.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.vwtfafa.lockEnd.LockEnd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Command to validate configuration file.
 */
public class ConfigValidatorCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter SCHEDULE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> SUPPORTED_LANGUAGES = List.of("de", "en", "es", "fr", "it", "ja", "ru", "zh");
    private static final List<String> REQUIRED_MESSAGES = List.of(
            "locked", "locked-reason", "toggle", "status", "permission", "open", "closed",
            "already-locked", "already-unlocked", "broadcast-locked", "broadcast-unlocked",
            "actionbar-locked", "actionbar-unlocked", "test-success", "test-info", "countdown-notification",
            "history.header", "history.empty", "undo.success", "undo.empty", "config.valid", "config.issues",
            "schedule-cancelled", "reason-usage", "reason-set", "join-notification", "reload-success",
            "preview-lock", "preview-unlock", "schedule-paused", "schedule-resumed"
    );

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

        if (!plugin.getConfig().getBoolean("logging.enabled") &&
            !plugin.getConfig().getBoolean("broadcast.enabled") &&
            !plugin.getConfig().getBoolean("metrics.enabled")) {
            issues.add("Warning: All major features are disabled. Plugin may not work as expected.");
        }

        // Validate scheduled unlock settings
        if (plugin.getConfig().getBoolean("scheduled-unlock.enabled")) {
            String mode = plugin.getConfig().getString("scheduled-unlock.mode", "days");
            if (mode == null) {
                mode = "";
            }
            if ("days".equalsIgnoreCase(mode)) {
                int days = plugin.getConfig().getInt("scheduled-unlock.days", 7);
                if (days <= 0) {
                    issues.add("Error: scheduled-unlock.days must be positive.");
                }
            } else if ("datetime".equalsIgnoreCase(mode)) {
                String datetime = plugin.getConfig().getString("scheduled-unlock.datetime", "");
                if (datetime == null || datetime.isBlank()) {
                    issues.add("Error: scheduled-unlock.datetime is required when mode is 'datetime'.");
                } else {
                    try {
                        LocalDateTime.parse(datetime, SCHEDULE_FORMAT);
                    } catch (DateTimeParseException exception) {
                        issues.add("Error: scheduled-unlock.datetime must use yyyy-MM-dd HH:mm.");
                    }
                }
            } else {
                issues.add("Error: scheduled-unlock.mode must be 'days' or 'datetime'.");
            }
        }

        validateNumericSettings(issues);

        String language = plugin.getConfig().getString("language", "en");
        if (language == null || !SUPPORTED_LANGUAGES.contains(language.toLowerCase(Locale.ROOT))) {
            issues.add("Error: language must be one of " + String.join(", ", SUPPORTED_LANGUAGES) + ".");
        }

        for (String key : REQUIRED_MESSAGES) {
            if (!plugin.hasMessage(key)) {
                issues.add("Error: Missing message key '" + key + "'.");
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
                sender.sendMessage(plugin.msg("config-issue").replace("%issue%", issue));
            }
        }

        return true;
    }

    private void validateNumericSettings(List<String> issues) {
        validatePositive(issues, "preview-notifications.seconds");
        validatePositive(issues, "grace-period.duration");
        validatePositive(issues, "logging.rate-limit-seconds");
        validatePositive(issues, "scheduled-unlock.countdown.interval");
        validateNonNegative(issues, "scheduled-unlock.countdown.start-before");

        double volume = plugin.getConfig().getDouble("sound-effects.volume", 1.0);
        double pitch = plugin.getConfig().getDouble("sound-effects.pitch", 1.0);
        if (volume < 0 || volume > 2) {
            issues.add("Error: sound-effects.volume must be between 0 and 2.");
        }
        if (pitch < 0 || pitch > 2) {
            issues.add("Error: sound-effects.pitch must be between 0 and 2.");
        }
    }

    private void validatePositive(List<String> issues, String path) {
        if (plugin.getConfig().getLong(path, 1) <= 0) {
            issues.add("Error: " + path + " must be positive.");
        }
    }

    private void validateNonNegative(List<String> issues, String path) {
        if (plugin.getConfig().getLong(path, 0) < 0) {
            issues.add("Error: " + path + " cannot be negative.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}