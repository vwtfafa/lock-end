package org.vwtfafa.lockend.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the scheduled lock/unlock runtime against the checklist behaviors:
 * clock shifts, invalid input, already-matching target states, restart
 * recovery, pause/resume, and the countdown/preview notification windows.
 */
class LockSchedulerTest {

    private static final ZoneId TEST_ZONE = ZoneId.of("UTC");

    private final RecordingCallbacks callbacks = new RecordingCallbacks();
    private final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 11, 5, 12, 0));
    private final LockScheduler scheduler = new LockScheduler(callbacks, clock);

    private void scheduleUnlockInMinutes(int minutes) {
        scheduler.schedule("unlock", LocalDateTime.of(2026, 11, 5, 12, 0).plusMinutes(minutes));
    }

    // --- execution ------------------------------------------------------

    @Test
    void executesScheduledUnlockWhenTheClockPassesTheTarget() {
        scheduleUnlockInMinutes(1);

        scheduler.tick(true);
        clock.advance(LocalDateTime.of(2026, 11, 5, 12, 1));
        scheduler.tick(true);

        assertFalse(scheduler.hasAction(), "pending action is consumed after execution");
        assertEquals(1, callbacks.executions.size());
        assertEquals(false, callbacks.executions.get(0));
    }

    @Test
    void executesScheduledLockWhenTheClockPassesTheTarget() {
        scheduler.schedule("lock", LocalDateTime.of(2026, 11, 5, 12, 1));

        scheduler.tick(false);
        clock.advance(LocalDateTime.of(2026, 11, 5, 12, 1));
        scheduler.tick(false);

        assertEquals(List.of(true), callbacks.executions);
    }

    @Test
    void skipsExecutionWhenTheTargetStateIsAlreadyActive() {
        scheduleUnlockInMinutes(1);
        clock.advance(LocalDateTime.of(2026, 11, 5, 12, 1));

        scheduler.tick(false);
        assertFalse(scheduler.hasAction());
        assertEquals(List.of(), callbacks.executions, "nothing to unlock: already unlocked");
    }

    @Test
    void tickingBeforeTheTargetDoesNothing() {
        scheduleUnlockInMinutes(5);

        scheduler.tick(true);

        assertTrue(scheduler.hasAction());
        assertEquals(List.of(), callbacks.executions);
    }

    @Test
    void clearsPendingLockAsSoonAsEndBecomesLocked() {
        scheduler.setCountdownSettings(true, 299, 10);
        scheduler.schedule("lock", LocalDateTime.of(2026, 11, 5, 12, 5));

        scheduler.tick(false);
        scheduler.tick(true);

        assertFalse(scheduler.hasAction());
        assertEquals(1, callbacks.discardedSchedules);
        assertEquals(List.of(), callbacks.countdowns);
        assertEquals(List.of(), callbacks.executions);
    }

    @Test
    void clearsPendingUnlockAsSoonAsEndBecomesUnlocked() {
        scheduler.schedule("unlock", LocalDateTime.of(2026, 11, 5, 12, 5));

        scheduler.tick(true);
        scheduler.tick(false);

        assertFalse(scheduler.hasAction());
        assertEquals(1, callbacks.discardedSchedules);
        assertEquals(List.of(), callbacks.executions);
    }

    // --- clock shifts ----------------------------------------------------

    @Test
    void aClockShiftForwardExecutesImmediately() {
        // Simulates DST forward drift or a system time jump over the target.
        scheduleUnlockInMinutes(30);

        scheduler.tick(true);
        clock.advance(LocalDateTime.of(2026, 11, 5, 12, 29));
        scheduler.tick(true);
        assertEquals(List.of(), callbacks.executions, "still before the target");

        clock.advance(LocalDateTime.of(2026, 11, 5, 12, 31));
        scheduler.tick(true);
        assertEquals(List.of(false), callbacks.executions, "lapsed target executes after the jump");
    }

    @Test
    void aClockShiftBackwardsKeepsTheSchedulePending() {
        scheduleUnlockInMinutes(30);

        scheduler.tick(true);
        clock.rewind(LocalDateTime.of(2026, 11, 5, 11, 0));
        scheduler.tick(true);

        assertTrue(scheduler.hasAction(), "target still in the future after the shift back");
        assertEquals(List.of(), callbacks.executions);
    }

    // --- invalid input ---------------------------------------------------

    @Test
    void invalidScheduleInputIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule("lok", LocalDateTime.of(2026, 11, 5, 12, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule(null, LocalDateTime.of(2026, 11, 5, 12, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule("unlock", null));
    }

    @Test
    void cancelRemovesThePendingAction() {
        scheduleUnlockInMinutes(5);

        scheduler.cancel();

        assertFalse(scheduler.hasAction());
        assertEquals("unlock", scheduler.action());
    }

    // --- pause / resume ---------------------------------------------------

    @Test
    void pauseHoldsExecutionWithoutMovingTheTarget() {
        scheduleUnlockInMinutes(2);
        clock.advance(LocalDateTime.of(2026, 11, 5, 12, 1));
        scheduler.tick(true);
        scheduler.pause();
        scheduler.tick(true);

        assertTrue(scheduler.isPaused());
        assertTrue(scheduler.hasAction(), "target does not move while paused");
        assertEquals(List.of(), callbacks.executions);

        clock.advance(LocalDateTime.of(2026, 11, 5, 13, 0));
        scheduler.tick(true);
        assertEquals(List.of(), callbacks.executions, "paused schedules do not execute");
        assertTrue(scheduler.remainingSeconds() == 0, "time keeps passing while paused");
    }

    @Test
    void resumeExecutesAnAlreadyLapsedTargetImmediately() {
        scheduleUnlockInMinutes(2);
        scheduler.tick(true);
        scheduler.pause();
        clock.advance(LocalDateTime.of(2026, 11, 5, 13, 0));
        scheduler.tick(true);
        assertEquals(List.of(), callbacks.executions, "paused schedules do not execute");

        scheduler.resume();
        scheduler.tick(true);

        assertEquals(List.of(false), callbacks.executions, "resume executes the lapsed target");
        assertFalse(scheduler.hasAction());
        assertFalse(scheduler.isPaused());
    }

    // --- restart recovery ----------------------------------------------

    @Test
    void restoreResumesAFuturePersistedTarget() {
        LocalDateTime persistedTarget = LocalDateTime.of(2026, 11, 5, 13, 0);

        LockScheduler.RestoreResult result = scheduler.restore("unlock", persistedTarget, false);

        assertEquals(LockScheduler.RestoreResult.RESUMED, result);
        assertTrue(scheduler.hasAction());
        assertEquals("unlock", scheduler.action());
        assertFalse(scheduler.isPaused());
        assertEquals(persistedTarget, scheduler.target());

        clock.advance(persistedTarget);
        scheduler.tick(true);
        assertEquals(List.of(false), callbacks.executions);
    }

    @Test
    void restoredPausedScheduleHoldsAndExecutesAfterResume() {
        LocalDateTime persistedTarget = LocalDateTime.of(2026, 11, 5, 13, 0);

        scheduler.restore("lock", persistedTarget, true);
        clock.advance(persistedTarget);
        scheduler.tick(false);
        assertEquals(List.of(), callbacks.executions, "paused restored schedule holds");

        scheduler.resume();
        scheduler.tick(false);
        assertEquals(List.of(true), callbacks.executions);
    }

    @Test
    void aLapsedPersistedTargetIsClearedOnBootWithoutExecuting() {
        LocalDateTime persistedTarget = LocalDateTime.of(2026, 11, 5, 11, 0);

        LockScheduler.RestoreResult result = scheduler.restore("unlock", persistedTarget, false);

        assertEquals(LockScheduler.RestoreResult.LAPSED_CLEARED, result);
        assertFalse(scheduler.hasAction());
        assertEquals(List.of(), callbacks.executions);
    }

    @Test
    void anUnsupportedPersistedActionNeverCoercesToUnlock() {
        LockScheduler.RestoreResult result = scheduler.restore(
                "open", LocalDateTime.of(2026, 11, 5, 13, 0), false);

        assertEquals(LockScheduler.RestoreResult.INVALID_CLEARED, result);
        assertFalse(scheduler.hasAction(), "a typo in persisted data must not open the End");
        assertEquals(List.of(), callbacks.executions);
    }

    @Test
    void restoreWithoutATargetReportsNone() {
        assertEquals(LockScheduler.RestoreResult.NONE, scheduler.restore("unlock", null, false));
        assertFalse(scheduler.hasAction());
    }

    // --- notifications ---------------------------------------------------

    @Test
    void countdownAnnouncesRoughlyEveryIntervalInsideTheWindow() {
        scheduler.setCountdownSettings(true, 600, 60);
        scheduleUnlockInMinutes(10);

        // Ticking at a one-second cadence: announcements happen when the
        // interval bucket changes, not on every tick.
        LocalDateTime baseTime = LocalDateTime.of(2026, 11, 5, 12, 0, 30);
        clock.advance(baseTime);
        scheduler.tick(true);
        long remainingAtStart = scheduler.remainingSeconds();
        assertEquals(570, remainingAtStart);
        assertEquals(List.of(570L), callbacks.countdowns);

        callbacks.countdowns.clear();
        for (int second = 1; second <= 120; second++) {
            clock.advance(baseTime.plusSeconds(second));
            scheduler.tick(true);
        }

        // Announced once per 60s interval: at remaining 539 and then 479.
        assertEquals(List.of(539L, 479L), callbacks.countdowns);
    }

    @Test
    void countdownStaysSilentOutsideTheWindow() {
        scheduler.setCountdownSettings(true, 300, 10);
        scheduleUnlockInMinutes(30);

        scheduler.tick(true);

        assertTrue(callbacks.countdowns.isEmpty());
    }

    @Test
    void countdownStaysSilentWhenDisabled() {
        scheduler.setCountdownSettings(false, 300, 10);
        scheduleUnlockInMinutes(2);

        clock.advance(LocalDateTime.of(2026, 11, 5, 12, 1));
        scheduler.tick(true);
        scheduler.tick(true);

        assertTrue(callbacks.countdowns.isEmpty());
    }

    @Test
    void previewAnnouncesOnceWhileTheTargetStateDiffers() {
        scheduler.setPreviewSettings(true, 30);
        scheduleUnlockInMinutes(1);

        clock.advance(LocalDateTime.of(2026, 11, 5, 12, 0, 30));
        scheduler.tick(true);
        clock.advance(LocalDateTime.of(2026, 11, 5, 12, 0, 40));
        scheduler.tick(true);

        assertEquals(1, callbacks.previews.size());
        assertEquals("unlock", callbacks.previews.get(0));
        assertEquals(30, callbacks.previewWindows.get(0));
    }

    @Test
    void previewStaysSilentWhenTheTargetStateAlreadyMatches() {
        scheduler.setPreviewSettings(true, 30);
        scheduleUnlockInMinutes(1);

        clock.advance(LocalDateTime.of(2026, 11, 5, 12, 0, 30));
        scheduler.tick(false);

        assertTrue(callbacks.previews.isEmpty());
    }

    @Test
    void previewStaysSilentOutsideTheWindow() {
        scheduler.setPreviewSettings(true, 30);
        scheduleUnlockInMinutes(10);

        scheduler.tick(true);

        assertTrue(callbacks.previews.isEmpty());
    }

    /**
     * Mutable clock for testing clock shifts without a live wall clock.
     */
    private static final class MutableClock extends Clock {

        private final ZoneId zone;
        private Instant instant;

        private MutableClock(LocalDateTime initial) {
            this.zone = TEST_ZONE;
            this.instant = initial.atZone(TEST_ZONE).toInstant();
        }

        private void advance(LocalDateTime to) {
            set(to);
        }

        private void rewind(LocalDateTime to) {
            set(to);
        }

        private void set(LocalDateTime local) {
            this.instant = local.atZone(zone).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            throw new UnsupportedOperationException("tests use UTC only");
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    /**
     * Records every callback invocation without any platform dependency.
     */
    private static final class RecordingCallbacks implements LockScheduler.Callbacks {

        private final List<Boolean> executions = new ArrayList<>();
        private final List<Long> countdowns = new ArrayList<>();
        private final List<String> previews = new ArrayList<>();
        private final List<Long> previewWindows = new ArrayList<>();
        private int discardedSchedules;

        @Override
        public void executeScheduled(boolean lock) {
            executions.add(lock);
        }

        @Override
        public void discardScheduled() {
            discardedSchedules++;
        }

        @Override
        public void announceCountdown(String action, long remainingSeconds) {
            countdowns.add(remainingSeconds);
        }

        @Override
        public void announcePreview(String action, long secondsBefore) {
            previews.add(action);
            previewWindows.add(secondsBefore);
        }
    }

    /**
     * Guards trivial API stability assertions to keep the matrix complete.
     */
    @Test
    void freshSchedulerHasNoAction() {
        assertFalse(scheduler.hasAction());
        assertEquals(-1, scheduler.remainingSeconds());
        assertNull(scheduler.target());
    }
}
