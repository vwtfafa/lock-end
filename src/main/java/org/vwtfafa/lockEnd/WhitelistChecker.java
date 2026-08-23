package org.vwtfafa.lockEnd;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.World;

import java.util.List;

/**
 * Checks if a player is whitelisted to bypass the lock.
 */
public class WhitelistChecker {
    private final List<String> playerWhitelist;
    private final List<String> uuidWhitelist;
    private final List<String> worldWhitelist;

    public WhitelistChecker(FileConfiguration config) {
        this.playerWhitelist = config.getStringList("whitelists.players");
        this.uuidWhitelist = config.getStringList("whitelists.uuids");
        this.worldWhitelist = config.getStringList("whitelists.worlds");
    }

    /**
     * Checks if a player can bypass the lock.
     * @param player The player to check
     * @return true if the player can bypass
     */
    public boolean canBypass(Player player) {
        return canBypass(player, null);
    }

    public boolean canBypass(Player player, World targetWorld) {
        // Check if player is in whitelist (case-insensitive)
        for (String name : playerWhitelist) {
            if (name.equalsIgnoreCase(player.getName())) {
                return true;
            }
        }
        String playerUuid = player.getUniqueId().toString();
        if (uuidWhitelist.stream().anyMatch(uuid -> uuid.equalsIgnoreCase(playerUuid))) {
            return true;
        }
        if (targetWorld != null) {
            String worldName = targetWorld.getName();
            if (worldWhitelist.stream().anyMatch(world -> world.equalsIgnoreCase(worldName))
                    || player.hasPermission("endlock.bypass.world." + worldName.toLowerCase())) {
                return true;
            }
        }
        // Check if player has permission to bypass
        return player.hasPermission("endlock.whitelist.bypass");
    }

    /**
     * Gets the configured player whitelist.
     * @return List of whitelisted player names
     */
    public List<String> getPlayerWhitelist() {
        return playerWhitelist;
    }

    public List<String> getUuidWhitelist() {
        return uuidWhitelist;
    }

    public List<String> getWorldWhitelist() {
        return worldWhitelist;
    }
}
