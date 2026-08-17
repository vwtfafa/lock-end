package org.vwtfafa.lockEnd;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Checks if a player or entity is whitelisted to bypass the lock.
 */
public class WhitelistChecker {
    private final FileConfiguration config;
    private final List<String> playerWhitelist;
    private final List<String> entityWhitelist;

    public WhitelistChecker(FileConfiguration config) {
        this.config = config;
        this.playerWhitelist = config.getStringList("whitelists.players");
        this.entityWhitelist = config.getStringList("whitelists.entities");
    }

    /**
     * Checks if a player can bypass the lock.
     * @param player The player to check
     * @return true if the player can bypass
     */
    public boolean canBypass(Player player) {
        // Check if player is in whitelist (case-insensitive)
        for (String name : playerWhitelist) {
            if (name.equalsIgnoreCase(player.getName())) {
                return true;
            }
        }
        // Check if player has permission to bypass
        if (player.hasPermission("endlock.whitelist.bypass")) {
            return true;
        }
        return false;
    }

    /**
     * Checks if an entity is whitelisted to bypass the lock.
     * @param entity The entity to check
     * @return true if the entity can bypass
     */
    public boolean canBypass(Entity entity) {
        // Only players can bypass via whitelist
        if (!(entity instanceof Player)) {
            return false;
        }
        Player player = (Player) entity;
        return canBypass(player);
    }

    /**
     * Gets the configured player whitelist.
     * @return List of whitelisted player names
     */
    public List<String> getPlayerWhitelist() {
        return playerWhitelist;
    }

    /**
     * Gets the configured entity whitelist.
     * @return List of whitelisted entity type names
     */
    public List<String> getEntityWhitelist() {
        return entityWhitelist;
    }
}