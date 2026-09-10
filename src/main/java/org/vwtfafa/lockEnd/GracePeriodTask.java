package org.vwtfafa.lockEnd;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Handles the grace period after locking: while it is active the lock is not
 * yet enforced and access attempts are allowed. Once it ends, the existing
 * lock becomes fully enforced - the lock state itself is never changed here.
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
     * Any previously running grace period is cancelled first.
     * @param durationSeconds Duration of grace period in seconds
     */
    public void startGracePeriod(int durationSeconds) {
        cancel();
        int safeDuration = Math.max(0, durationSeconds);
        active = true;
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            task = null;
            active = false;
            plugin.getLogger().info("Grace period ended, End lock is now fully enforced.");
        }, (long) safeDuration * 20L);
        plugin.getLogger().info("Grace period started for " + safeDuration + " seconds.");
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
