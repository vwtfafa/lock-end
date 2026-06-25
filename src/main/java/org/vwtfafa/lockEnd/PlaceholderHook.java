package org.vwtfafa.lockEnd;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public final class PlaceholderHook extends PlaceholderExpansion {
    private final LockEnd plugin;

    public PlaceholderHook(LockEnd plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "lockend";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return switch (params.toLowerCase()) {
            case "status" -> plugin.isLocked() ? plugin.getMessages().raw("placeholder-locked") : plugin.getMessages().raw("placeholder-unlocked");
            case "remaining" -> plugin.getSchedule().formatRemaining(plugin.getMessages());
            case "lock_count" -> String.valueOf(plugin.getStats().getLockCount());
            case "blocked_count" -> String.valueOf(plugin.getStats().getBlockedCount());
            default -> null;
        };
    }
}
