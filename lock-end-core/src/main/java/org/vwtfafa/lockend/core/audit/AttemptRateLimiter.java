package org.vwtfafa.lockend.core.audit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player throttle for blocked-attempt audit logging, mirroring the Paper
 * plugin's {@code logging.rate-limit-seconds} behavior: at most one logged
 * attempt per player within the configured window. Nothing is recorded for
 * players who already logged inside the window.
 */
public final class AttemptRateLimiter {

    private final Map<UUID, Long> lastAttemptTimes = new ConcurrentHashMap<>();
    private volatile int rateLimitSeconds;

    /**
     * Creates the limiter.
     *
     * @param rateLimitSeconds minimum seconds between two logged attempts
     *                         per player; values below zero are clamped
     */
    public AttemptRateLimiter(int rateLimitSeconds) {
        this.rateLimitSeconds = Math.max(0, rateLimitSeconds);
    }

    /**
     * Updates the window length (used by config reloads).
     *
     * @param newRateLimitSeconds the new window in seconds
     */
    public void setRateLimitSeconds(int newRateLimitSeconds) {
        this.rateLimitSeconds = Math.max(0, newRateLimitSeconds);
    }

    /**
     * Reserve a logging slot for the given player.
     *
     * @param playerId the attempting player
     * @param nowMillis the current time reference (for example
     *                  {@code System.currentTimeMillis()})
     * @return {@code true} if the attempt should be logged now (and the
     *         window is opened); {@code false} if it must be dropped
     */
    public boolean shouldLog(UUID playerId, long nowMillis) {
        if (playerId == null) {
            return false;
        }
        Long lastAttempt = lastAttemptTimes.get(playerId);
        long windowMillis = rateLimitSeconds * 1000L;
        if (lastAttempt != null && nowMillis - lastAttempt < windowMillis) {
            return false;
        }
        lastAttemptTimes.put(playerId, nowMillis);
        return true;
    }

    /**
     * Drops a player's window state, matching the Paper plugin's cleanup on
     * player quit.
     *
     * @param playerId the leaving player
     */
    public void clear(UUID playerId) {
        lastAttemptTimes.remove(playerId);
    }
}
