package org.vwtfafa.lockend.core;

import java.time.Clock;
import java.time.Instant;

/**
 * Timed non-enforcement window after a lock becomes active: while the grace
 * period runs, a new lock is not enforced yet so players inside the End can
 * finish and leave safely. The lock state itself never changes; platforms
 * consult {@link #isActive()} inside their guards, exactly like the Paper
 * plugin's {@code GracePeriodTask} before its access gate.
 *
 * <p>The clock is injected so platforms (and tests) control the time
 * reference; restarts clear the window because the runtime never persists
 * it.</p>
 */
public final class GracePeriod {

    private final Clock clock;
    private volatile Instant startsAt;
    private volatile int durationSeconds;

    /**
     * Creates the window around the given clock.
     */
    public GracePeriod(Clock clock) {
        this.clock = clock;
    }

    /**
     * Starts (or restarts) the window.
     *
     * @param durationSeconds length in seconds; zero disables enforcement
     *                        blocking only for this instant, negative values
     *                        behave like zero
     */
    public void start(long durationSeconds) {
        this.startsAt = clock.instant();
        this.durationSeconds = (int) Math.max(0, durationSeconds);
    }

    /**
     * @return {@code true} while the window is running
     */
    public boolean isActive() {
        Instant started = startsAt;
        if (started == null || durationSeconds <= 0) {
            return false;
        }
        long elapsed = clock.instant().getEpochSecond() - started.getEpochSecond();
        return elapsed < durationSeconds;
    }

    /**
     * Cancels the window (used when the End unlocks again).
     */
    public void cancel() {
        startsAt = null;
        durationSeconds = 0;
    }
}
