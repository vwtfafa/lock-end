package org.vwtfafa.lockend.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the timed grace window consulted by the platform guards.
 */
class GracePeriodTest {

    private static final Instant START = Instant.parse("2026-11-05T12:00:00Z");

    @Test
    void theWindowIsInactiveBeforeItStarts() {
        GracePeriod grace = new GracePeriod(Clock.fixed(START, ZoneId.of("UTC")));

        assertFalse(grace.isActive());
    }

    @Test
    void theWindowIsOnlyActiveInsideItsDuration() {
        MutableClock clock = new MutableClock(START);
        GracePeriod grace = new GracePeriod(clock);
        grace.start(10);

        assertTrue(grace.isActive());
        clock.advanceSeconds(9);
        assertTrue(grace.isActive());
        clock.advanceSeconds(1);
        assertFalse(grace.isActive(), "the window ends exactly after the duration");
    }

    @Test
    void restartingExtendsTheWindowFromTheNewStart() {
        MutableClock clock = new MutableClock(START);
        GracePeriod grace = new GracePeriod(clock);
        grace.start(10);
        clock.advanceSeconds(8);
        grace.start(10);
        clock.advanceSeconds(8);

        assertTrue(grace.isActive());
    }

    @Test
    void cancelAndDurationZeroDeactivateTheWindow() {
        MutableClock clock = new MutableClock(START);
        GracePeriod grace = new GracePeriod(clock);

        grace.start(0);
        assertFalse(grace.isActive());

        grace.start(10);
        grace.cancel();
        assertFalse(grace.isActive());
    }

    @Test
    void negativeDurationBehavesLikeZero() {
        MutableClock clock = new MutableClock(START);
        GracePeriod grace = new GracePeriod(clock);

        grace.start(-5);
        assertFalse(grace.isActive());
    }

    /** Clock the test drives directly. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
