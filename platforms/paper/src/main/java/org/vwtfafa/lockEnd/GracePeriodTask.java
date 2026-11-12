package org.vwtfafa.lockEnd;

import org.vwtfafa.lockend.core.GracePeriod;

import java.time.Clock;

/**
 * Paper adapter around the shared {@link GracePeriod}: while it is active the
 * lock is not yet enforced and access attempts are allowed. Once it ends, the
 * existing lock becomes fully enforced - the lock state itself is never
 * changed here.
 *
 * <p>Uses wall-clock time like the Fabric/NeoForge adapters (intentional
 * alignment for 4.1). Restarts clear the window because it is not persisted.</p>
 */
public class GracePeriodTask {
    private final LockEnd plugin;
    private final GracePeriod gracePeriod;

    public GracePeriodTask(LockEnd plugin) {
        this.plugin = plugin;
        this.gracePeriod = new GracePeriod(Clock.systemUTC());
    }

    /**
     * Starts the grace period after a lock is set.
     * Any previously running grace period is cancelled first.
     * @param durationSeconds Duration of grace period in seconds
     */
    public void startGracePeriod(int durationSeconds) {
        int safeDuration = Math.max(0, durationSeconds);
        gracePeriod.start(safeDuration);
        if (safeDuration > 0) {
            plugin.getLogger().info("Grace period started for " + safeDuration + " seconds.");
        }
    }

    /**
     * Checks if grace period is active.
     * @return true if grace period is active
     */
    public boolean isActive() {
        return gracePeriod.isActive();
    }

    public void cancel() {
        gracePeriod.cancel();
    }
}
