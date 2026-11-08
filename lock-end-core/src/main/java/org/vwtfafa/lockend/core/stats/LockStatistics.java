package org.vwtfafa.lockend.core.stats;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Platform-independent operational counters: locks, unlocks, blocked access
 * attempts and evacuated players. Mirrors the Paper plugin's four counters;
 * persistence stays platform-side (config section on Paper, JSON file on the
 * mods).
 *
 * <p>The {@code revision} counter lets owners detect changes between
 * persistence passes without holding a lock, mimicking the Paper plugin's
 * debounced config rewrite.</p>
 */
public final class LockStatistics {

    private volatile boolean enabled;
    private final AtomicInteger lockCount = new AtomicInteger();
    private final AtomicInteger unlockCount = new AtomicInteger();
    private final AtomicInteger blockedCount = new AtomicInteger();
    private final AtomicInteger evacuatedCount = new AtomicInteger();
    private final AtomicLong revision = new AtomicLong();

    /**
     * Creates disabled statistics; owners enable them through the platform
     * config on boot and reload.
     *
     * @param initiallyEnabled {@code true} if recording starts immediately
     */
    public LockStatistics(boolean initiallyEnabled) {
        this.enabled = initiallyEnabled;
    }

    /**
     * Enables or disables recording. Disabled counters keep their values;
     * new events are not counted.
     *
     * @param newEnabled {@code true} to count new events
     */
    public void setEnabled(boolean newEnabled) {
        this.enabled = newEnabled;
    }

    /**
     * Records a state change.
     *
     * @param lockedNow the state after the change
     */
    public void recordStateChange(boolean lockedNow) {
        if (!enabled) {
            return;
        }
        if (lockedNow) {
            lockCount.incrementAndGet();
        } else {
            unlockCount.incrementAndGet();
        }
        revision.incrementAndGet();
    }

    /**
     * Records a blocked access attempt into the locked End.
     */
    public void recordBlockedAttempt() {
        if (!enabled) {
            return;
        }
        blockedCount.incrementAndGet();
        revision.incrementAndGet();
    }

    /**
     * Records a player successfully moved out of the End by an evacuation.
     */
    public void recordEvacuatedPlayer() {
        if (!enabled) {
            return;
        }
        evacuatedCount.incrementAndGet();
        revision.incrementAndGet();
    }

    /**
     * Prepares counters for a persistence pass.
     *
     * @param lockCountToSet      absolute lock counter value
     * @param unlockCountToSet    absolute unlock counter value
     * @param blockedCountToSet   absolute blocked counter value
     * @param evacuatedCountToSet absolute evacuated counter value
     */
    public void loadCounts(int lockCountToSet, int unlockCountToSet,
                           int blockedCountToSet, int evacuatedCountToSet) {
        lockCount.set(Math.max(0, lockCountToSet));
        unlockCount.set(Math.max(0, unlockCountToSet));
        blockedCount.set(Math.max(0, blockedCountToSet));
        evacuatedCount.set(Math.max(0, evacuatedCountToSet));
        revision.incrementAndGet();
    }

    /**
     * @return the lock counter value
     */
    public int lockCount() {
        return lockCount.get();
    }

    /**
     * @return the unlock counter value
     */
    public int unlockCount() {
        return unlockCount.get();
    }

    /**
     * @return the blocked-attempt counter value
     */
    public int blockedCount() {
        return blockedCount.get();
    }

    /**
     * @return the evacuated counter value
     */
    public int evacuatedCount() {
        return evacuatedCount.get();
    }

    /**
     * Monotonic change counter: increments on every recorded event so
     * persistence can detect activity cheaply.
     *
     * @return the current revision
     */
    public long revision() {
        return revision.get();
    }

    /**
     * @return {@code true} when new events are counted
     */
    public boolean isEnabled() {
        return enabled;
    }
}
