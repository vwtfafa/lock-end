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
            default -> null;
        };
    }
}
