package org.vwtfafa.lockend.core;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Platform-independent runtime owner of one scheduled lock/unlock action.
 * Mirrors the Paper {@code ScheduleManager} runtime semantics so every
 * platform behaves the same:
 *
 * <ul>
 *   <li>one pending action (supported action name plus absolute target),</li>
 *   <li>pause holds the execution without moving the target; resuming
 *       executes an already-lapsed target immediately,</li>
 *   <li>a due action is skipped when its target lock state is already
 *       active, and the pending action is cleared either way,</li>
 *   <li>restore rules for persisted data: a future target resumes counting,
 *       a lapsed persisted target is cleared instead of being executed on
 *       boot, and an unsupported action never coerces to unlock,</li>
 *   <li>optional periodic countdown notifications inside a configurable
 *       window before the action, plus a one-shot preview notification,
 *       both delivered through the callback contract.</li>
 * </ul>
 *
 * <p>The scheduler is clock-injected so restart recovery, DST-style clock
 * shifts and runtime execution are testable without a live server. This
 * class contains no platform dependency; the platform adapter owns
 * persistence, message rendering and the tick cadence (poll via
 * {@link #tick(boolean)}).</p>
 */
public final class LockScheduler {

    /**
     * The callback contract the platform adapter implements. All callbacks
     * run in the tick context of the caller.
     */
    public interface Callbacks {

        /**
         * Executes the scheduled state change. Adapters gate on their own
         * state change flow (persist, broadcast, evacuate) with
         * {@code LockAction.SCHEDULED_LOCK}/{@code SCHEDULED_UNLOCK}.
         *
         * @param lock {@code true} to lock the End
         */
        void executeScheduled(boolean lock);

        /**
         * Periodic countdown notification for the pending action.
         *
         * @param action           the pending action name
         * @param remainingSeconds the remaining seconds at announce time
         */
        void announceCountdown(String action, long remainingSeconds);

        /**
         * One-shot preview notification for the pending action.
         *
         * @param action        the pending action name
         * @param secondsBefore the configured preview window in seconds
         */
        void announcePreview(String action, long secondsBefore);
    }

    /**
     * Outcome of a boot-time restore of persisted schedule data.
     */
    public enum RestoreResult {
        /** No usable persisted target was supplied. */
        NONE,
        /** A future target resumed counting (possibly paused). */
        RESUMED,
        /** The persisted target already lapsed and was cleared on boot, not
         * executed. */
        LAPSED_CLEARED,
        /** The persisted action name is unsupported; the schedule cleared. */
        INVALID_CLEARED
    }

    private static final long DEFAULT_COUNTDOWN_START_BEFORE = 300L;
    private static final long DEFAULT_COUNTDOWN_INTERVAL = 10L;
    private static final long DEFAULT_PREVIEW_SECONDS = 30L;

    private final Callbacks callbacks;
    private final Clock clock;

    private volatile String action = "unlock";
    private volatile LocalDateTime target;
    private volatile boolean paused;

    private volatile boolean countdownEnabled;
    private volatile long countdownStartBefore = DEFAULT_COUNTDOWN_START_BEFORE;
    private volatile long countdownInterval = DEFAULT_COUNTDOWN_INTERVAL;
    private volatile boolean previewEnabled;
    private volatile long previewSeconds = DEFAULT_PREVIEW_SECONDS;

    private volatile long lastCountdownBucket = Long.MIN_VALUE;
    private volatile boolean previewAnnounced;

    /**
     * Creates the scheduler.
     *
     * @param callbacks the platform callbacks; not {@code null}
     * @param clock     the wall clock driving time checks; not {@code null}
     */
    public LockScheduler(Callbacks callbacks, Clock clock) {
        this.callbacks = callbacks;
        this.clock = clock;
    }

    /**
     * Applies countdown notification settings.
     *
     * @param enabled     whether countdown announcements run
     * @param startBefore announce only when remaining seconds are at most
     *                    this value
     * @param interval    minimum seconds between announcements
     */
    public void setCountdownSettings(boolean enabled, long startBefore, long interval) {
        this.countdownEnabled = enabled;
        this.countdownStartBefore = startBefore;
        this.countdownInterval = interval;
    }

    /**
     * Applies preview notification settings.
     *
     * @param enabled whether the one-shot preview runs
     * @param seconds announce when the remaining time reaches this window
     */
    public void setPreviewSettings(boolean enabled, long seconds) {
        this.previewEnabled = enabled;
        this.previewSeconds = seconds;
    }

    /**
     * Schedules a pending action at an absolute target. The command layer
     * ensures the target lies in the future; this method validates the
     * action name and non-null inputs.
     *
     * @param action the action name ({@code lock} or {@code unlock})
     * @param target the absolute execution time
     * @throws IllegalArgumentException if the action is unsupported or
     *                                  the target is {@code null}
     */
    public void schedule(String action, LocalDateTime target) {
        if (action == null || !ScheduleSupport.isSupportedAction(action)) {
            throw new IllegalArgumentException("Unsupported schedule action: " + action);
        }
        if (target == null) {
            throw new IllegalArgumentException("Schedule target must not be null");
        }
        this.action = action.toLowerCase(Locale.ROOT);
        this.target = target;
        resetAnnouncements();
    }

    /**
     * Restores the runtime schedule from persisted boot data, applying the
     * Paper safety rules: a lapsed persisted target is cleared (never
     * executed on boot) and an unsupported action never coerces to
     * {@code unlock}.
     *
     * @param persistedAction  the persisted action name
     * @param persistedTarget  the persisted absolute target, or {@code null}
     * @param persistedPaused  whether the schedule was paused at shutdown
     * @return the restore outcome
     */
    public RestoreResult restore(String persistedAction, LocalDateTime persistedTarget, boolean persistedPaused) {
        clearState();
        this.paused = persistedPaused;
        if (persistedTarget == null) {
            return RestoreResult.NONE;
        }
        if (persistedAction == null || !ScheduleSupport.isSupportedAction(persistedAction)) {
            return RestoreResult.INVALID_CLEARED;
        }
        if (ZonedDateTime.now(clock).toInstant().isAfter(persistedTarget.atZone(clock.getZone()).toInstant())) {
            return RestoreResult.LAPSED_CLEARED;
        }
        this.action = persistedAction.toLowerCase(Locale.ROOT);
        this.target = persistedTarget;
        resetAnnouncements();
        return RestoreResult.RESUMED;
    }

    /**
     * Clears the pending action.
     */
    public void cancel() {
        clearState();
    }

    /**
     * Pauses execution; the target does not move. A lapsed target executes
     * on the next tick after {@link #resume()}.
     */
    public void pause() {
        this.paused = true;
    }

    /**
     * Resumes a paused schedule.
     */
    public void resume() {
        this.paused = false;
    }

    /**
     * @return {@code true} if a pending action exists
     */
    public boolean hasAction() {
        return target != null;
    }

    /**
     * @return the pending action name
     */
    public String action() {
        return action;
    }

    /**
     * @return the pending target; {@code null} without a pending action
     */
    public LocalDateTime target() {
        return target;
    }

    /**
     * @return {@code true} while the schedule is paused
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * @return remaining whole seconds until the pending action; {@code -1}
     *         without a pending action, lapsed targets clamp to {@code 0}
     */
    public long remainingSeconds() {
        return ScheduleSupport.remainingSeconds(target, ZonedDateTime.now(clock));
    }

    /**
     * Polls due execution and notifications. Call from the server tick
     * context roughly once per second. The due action is only executed when
     * the current lock state does not already match the scheduled target
     * state; the pending action is cleared either way.
     *
     * @param currentLocked whether the End is currently locked
     */
    public void tick(boolean currentLocked) {
        LocalDateTime capturedTarget = target;
        if (capturedTarget == null) {
            return;
        }
        long remaining = ScheduleSupport.remainingSeconds(
                capturedTarget, ZonedDateTime.now(clock));
        if (paused) {
            return;
        }
        if (remaining <= 0) {
            executeDue(currentLocked);
            return;
        }
        maybeCountdown(action, remaining);
        maybePreview(action, currentLocked, remaining);
    }

    private void executeDue(boolean currentLocked) {
        String capturedAction = action;
        boolean targetLocked = "lock".equalsIgnoreCase(capturedAction);
        if (currentLocked != targetLocked) {
            callbacks.executeScheduled(targetLocked);
        }
        clearState();
    }

    private void maybeCountdown(String pendingAction, long remaining) {
        if (!countdownEnabled || remaining > countdownStartBefore || remaining <= 0) {
            return;
        }
        long interval = Math.max(1L, countdownInterval);
        long bucket = remaining / interval;
        if (bucket != lastCountdownBucket) {
            lastCountdownBucket = bucket;
            callbacks.announceCountdown(pendingAction, remaining);
        }
    }

    private void maybePreview(String pendingAction, boolean currentLocked, long remaining) {
        if (!previewEnabled || previewAnnounced || remaining > previewSeconds) {
            return;
        }
        // Mirror Paper: a lock preview is only useful while unlocked and an
        // unlock preview only while locked.
        boolean targetLocked = "lock".equalsIgnoreCase(pendingAction);
        if (currentLocked == targetLocked) {
            previewAnnounced = true;
            return;
        }
        previewAnnounced = true;
        callbacks.announcePreview(pendingAction, previewSeconds);
    }

    private void resetAnnouncements() {
        lastCountdownBucket = Long.MIN_VALUE;
        previewAnnounced = false;
    }

    private void clearState() {
        target = null;
        action = "unlock";
        resetAnnouncements();
    }
}
