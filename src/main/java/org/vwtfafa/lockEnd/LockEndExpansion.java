package org.vwtfafa.lockEnd;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

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
    public String onPlaceholderRequest(Player player, String identifier) {
        if (identifier == null) {
            return null;
        }

        return switch (identifier.toLowerCase()) {
            case "status" -> plugin.isLocked() ? "Locked" : "Unlocked";
            case "remaining" -> plugin.getRemainingText();
            case "remaining_seconds" -> String.valueOf(plugin.getScheduledRemainingSeconds());
            case "unlock_at", "target_time" -> plugin.getScheduledTime() == null
                    ? ""
                    : plugin.getScheduledTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            case "lock_reason", "reason" -> plugin.getLockReason();
            case "blocked_count" -> String.valueOf(plugin.getBlockedCount());
            case "schedule_action" -> plugin.hasScheduledAction() ? plugin.getScheduledAction() : "none";
            case "schedule_active" -> String.valueOf(plugin.hasScheduledAction());
            default -> null;
        };
    }
}
