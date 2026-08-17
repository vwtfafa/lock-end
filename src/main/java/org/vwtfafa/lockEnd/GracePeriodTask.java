package org.vwtfafa.lockEnd;

import org.bukkit.Bukkit;

/**
 * Handles grace period after locking - temporarily unlocks to allow safe exit.
 */
public class GracePeriodTask {
    private final LockEnd plugin;
    private volatile boolean active;

    public GracePeriodTask(LockEnd plugin) {
        this.plugin = plugin;
        this.active = false;
    }

    /**
     * Starts the grace period after a lock is set.
     * @param durationSeconds Duration of grace period in seconds
     */
    public void startGracePeriod(int durationSeconds) {
        if (active) {
            return;
        }
        active = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.isLocked()) {
                plugin.setLocked(false);
                plugin.getLogger().info("Grace period ended, End is now unlocked.");
            }
            active = false;
        }, durationSeconds * 20L);
        plugin.getLogger().info("Grace period started for " + durationSeconds + " seconds.");
    }

    /**
     * Checks if grace period is active.
     * @return true if grace period is active
     */
    public boolean isActive() {
        return active;
    }
}