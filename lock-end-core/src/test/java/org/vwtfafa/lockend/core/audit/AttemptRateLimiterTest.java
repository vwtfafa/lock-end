package org.vwtfafa.lockend.core.audit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the per-player blocked-attempt window.
 */
class AttemptRateLimiterTest {

    @Test
    void firstAttemptIsLoggedAndOpensTheWindow() {
        AttemptRateLimiter limiter = new AttemptRateLimiter(5);
        UUID player = UUID.randomUUID();

        assertTrue(limiter.shouldLog(player, 0L));
    }

    @Test
    void attemptsInsideTheWindowAreDropped() {
        AttemptRateLimiter limiter = new AttemptRateLimiter(5);
        UUID player = UUID.randomUUID();

        assertTrue(limiter.shouldLog(player, 0L));
        assertFalse(limiter.shouldLog(player, 3000L));
        assertTrue(limiter.shouldLog(player, 5000L), "exactly at the window edge logs again");
    }

    @Test
    void playersDoNotLimitEachOther() {
        AttemptRateLimiter limiter = new AttemptRateLimiter(5);

        assertTrue(limiter.shouldLog(UUID.randomUUID(), 0L));
        assertTrue(limiter.shouldLog(UUID.randomUUID(), 0L));
    }

    @Test
    void shrinkingTheWindowReenablesLogging() {
        UUID player = UUID.randomUUID();
        AttemptRateLimiter limiter = new AttemptRateLimiter(60);

        assertTrue(limiter.shouldLog(player, 0L));
        assertFalse(limiter.shouldLog(player, 1000L));

        limiter.setRateLimitSeconds(0);
        assertTrue(limiter.shouldLog(player, 1000L));
    }

    @Test
    void clearingAPlayerOpensANewWindow() {
        UUID player = UUID.randomUUID();
        AttemptRateLimiter limiter = new AttemptRateLimiter(5);

        assertTrue(limiter.shouldLog(player, 0L));
        limiter.clear(player);
        assertTrue(limiter.shouldLog(player, 1000L));
    }
}
