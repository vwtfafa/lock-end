package org.vwtfafa.lockEnd.commands;

import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.vwtfafa.lockEnd.LockEnd;
import org.vwtfafa.lockEnd.ScheduleManager;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

/**
 * Command to validate configuration file.
 */
public class ConfigValidatorCommand {
    private static final List<String> SUPPORTED_LANGUAGES = List.of("de", "en", "es", "fr", "it", "ja", "ru", "zh");
    private static final List<String> REQUIRED_MESSAGES = List.of(
            "locked", "locked-reason", "toggle", "status", "permission", "open", "closed",
            "already-locked", "already-unlocked", "broadcast-locked", "broadcast-unlocked",
            "actionbar-locked", "actionbar-unlocked", "test-success", "test-info", "countdown-notification",
            "history.empty", "undo.success", "undo.empty", "config.valid", "config.issues",
            "scheduled-lock-set", "scheduled-lock-invalid", "countdown-lock-notification",
            "schedule-cancelled", "schedule-status-usage", "schedule-status", "schedule-none", "schedule-cleared",
            "remaining-unlocked", "remaining-permanent",
            "reason-usage", "reason-set", "join-notification", "evacuation-warning", "evacuation-complete",
            "reload-success", "preview-lock", "preview-unlock", "schedule-paused", "schedule-resumed",
            "history-header-page", "history-page-empty", "history-usage", "history-exported",
            "history-export-failed", "history-storage-status",
            "usage", "test-disabled", "scheduled-unlock-set-days", "scheduled-unlock-set-at",
            "grace-period-active", "stats-line-unlocks", "stats-line-evacuated",
            "evacuation-disabled", "evacuation-not-locked", "evacuation-target-unavailable",
            "evacuation-preview", "evacuation-preview-required", "evacuation-preview-expired",
            "evacuation-preview-changed", "evacuation-execution-started"
    );

    private final LockEnd plugin;

    public ConfigValidatorCommand(LockEnd plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes the config validation.
     * @param sender The command sender
     * @param args The command arguments
     * @return true when handled
     */
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("endlock.validate")) {
            plugin.getPaperScheduler().sendMessage(sender, plugin.message("permission", Map.of()));
            return true;
        }

        List<String> issues = new ArrayList<>();

        if (!plugin.getConfig().getBoolean("logging.enabled", true)
                && !plugin.getConfig().getBoolean("broadcast.enabled", true)) {
            issues.add("Warning: All major features are disabled. Plugin may not work as expected.");
        }

        // Validate scheduled unlock settings
        if (plugin.getConfig().getBoolean("scheduled-unlock.enabled")) {
            String action = plugin.getConfig().getString("scheduled-unlock.action", "unlock");
            if (action == null || !ScheduleManager.isSupportedAction(action)) {
                issues.add("Error: scheduled-unlock.action must be 'lock' or 'unlock'.");
            }
            String mode = plugin.getConfig().getString("scheduled-unlock.mode", "days");
            if (mode == null) {
                mode = "";
            }
            if ("days".equalsIgnoreCase(mode)) {
                Long days = integerValue(issues, "scheduled-unlock.days");
                if (days != null && days <= 0) {
                    issues.add("Error: scheduled-unlock.days must be positive.");
                }
            } else if ("datetime".equalsIgnoreCase(mode)) {
                String datetime = plugin.getConfig().getString("scheduled-unlock.datetime", "");
                if (datetime == null || datetime.isBlank()) {
                    issues.add("Error: scheduled-unlock.datetime is required when mode is 'datetime'.");
                } else {
                    try {
                        LocalDateTime.parse(datetime, LockEnd.SCHEDULE_FORMAT);
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

        String evacuationTarget = plugin.getConfig().getString("evacuation.target-world", "world");
        if (plugin.getConfig().getBoolean("evacuation.enabled", false)
                && (evacuationTarget == null || evacuationTarget.isBlank())) {
            issues.add("Error: evacuation.target-world must not be empty when evacuation is enabled.");
        }
        if (evacuationTarget != null && evacuationTarget.contains(":")) {
            try {
                if (NamespacedKey.fromString(evacuationTarget) == null) {
                    issues.add("Error: evacuation.target-world must be a valid namespaced key.");
                }
            } catch (IllegalArgumentException exception) {
                issues.add("Error: evacuation.target-world must be a valid namespaced key.");
            }
        }

        // Output results
        if (issues.isEmpty()) {
            plugin.getPaperScheduler().sendMessage(sender, plugin.message("config.valid", Map.of()));
        } else {
            plugin.getPaperScheduler().sendMessage(sender, plugin.message("config.issues", Map.of()));
            for (String issue : issues) {
                plugin.getPaperScheduler().sendMessage(sender,
                        plugin.message("config-issue", Map.of("%issue%", issue)));
            }
        }

        return true;
    }

    private void validateNumericSettings(List<String> issues) {
        validatePositive(issues, "preview-notifications.seconds");
        validatePositive(issues, "grace-period.duration");
        validatePositive(issues, "logging.rate-limit-seconds");
        validatePositive(issues, "scheduled-unlock.countdown.interval");
        validateInteger(issues, "scheduled-unlock.countdown.start-before", 0);
        validatePositive(issues, "history.max-entries");
        // Non-positive retention disables age filtering, so only the type matters.
        validateInteger(issues, "history.retention-days", null);
        validateRange(issues, "sound-effects.volume", 0.0, 2.0);
        validateRange(issues, "sound-effects.pitch", 0.0, 2.0);
    }

    private void validatePositive(List<String> issues, String path) {
        validateInteger(issues, path, 1);
    }

    private void validateInteger(List<String> issues, String path, Integer minimum) {
        Long value = integerValue(issues, path);
        if (value != null && minimum != null && value < minimum) {
            issues.add("Error: " + path + (minimum == 1
                    ? " must be positive."
                    : " cannot be negative."));
        }
    }

    private Long integerValue(List<String> issues, String path) {
        Object raw = plugin.getConfig().get(path);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Number number)) {
            issues.add("Error: " + path + " must be an integer.");
            return null;
        }
        long value = number.longValue();
        if (number.doubleValue() != (double) value) {
            issues.add("Error: " + path + " must be an integer.");
            return null;
        }
        return value;
    }

    private void validateRange(List<String> issues, String path, double minimum, double maximum) {
        Object raw = plugin.getConfig().get(path);
        if (raw == null) {
            return;
        }
        if (!(raw instanceof Number number)) {
            issues.add("Error: " + path + " must be a number.");
            return;
        }
        double value = number.doubleValue();
        if (value < minimum || value > maximum) {
            issues.add("Error: " + path + " must be between " + minimum + " and " + maximum + ".");
        }
    }
}