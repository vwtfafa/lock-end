package org.vwtfafa.lockEnd;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Handles grace period after locking - temporarily unlocks to allow safe exit.
 */
public class GracePeriodTask {
    private final LockEnd plugin;
    private volatile boolean active;
    private BukkitTask task;

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
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.isLocked()) {
                plugin.changeLockState(false, "System", "GRACE_PERIOD_END", false);
                plugin.getLogger().info("Grace period ended, End is now unlocked.");
            }
            active = false;
            task = null;
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

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        active = false;
    }
}