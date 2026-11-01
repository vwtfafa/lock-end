package org.vwtfafa.lockEnd;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.time.LocalDateTime;
import java.util.Locale;

public class LockEndExpansion extends PlaceholderExpansion {
    private final LockEnd plugin;

    public LockEndExpansion(LockEnd plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "lockend";
    }

    @Override
    public String getAuthor() {
        return plugin.getPluginMeta().getAuthors().isEmpty() ? "Unknown" : plugin.getPluginMeta().getAuthors().get(0);
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (identifier == null) {
            return null;
        }

        return switch (identifier.toLowerCase(Locale.ROOT)) {
            case "status" -> plugin.isLocked() ? "Locked" : "Unlocked";
            case "remaining" -> plugin.getRemainingText();
            case "remaining_seconds" -> String.valueOf(plugin.getScheduledRemainingSeconds());
            case "unlock_at", "target_time" -> formatScheduledTime(plugin.getScheduledTime());
            case "lock_reason", "reason" -> plugin.getLockReason();
            case "blocked_count" -> String.valueOf(plugin.getBlockedCount());
            case "unlock_count", "unlocks" -> String.valueOf(plugin.getUnlockCount());
            case "evacuated" -> String.valueOf(plugin.getEvacuatedCount());
            case "schedule_action" -> plugin.getScheduledTime() == null ? "none" : plugin.getScheduledAction();
            case "schedule_active" -> String.valueOf(plugin.getScheduledTime() != null);
            default -> null;
        };
    }

    private static String formatScheduledTime(LocalDateTime time) {
        return time == null ? "" : time.format(LockEnd.SCHEDULE_FORMAT);
    }
}
