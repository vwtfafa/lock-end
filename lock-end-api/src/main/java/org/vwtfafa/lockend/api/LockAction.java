package org.vwtfafa.lockend.api;

/**
 * The trigger that caused a lock state change.
 *
 * <p>The documented values mirror the action identifiers of the legacy
 * {@code EndLockStateChangeEvent} (Paper API version 3.1). The set is not
 * closed: future versions may introduce additional actions. Consumers must
 * tolerate unknown values (default branches, not exhaustive switches over
 * the values).</p>
 */
public enum LockAction {

    /**
     * The lock was activated by a direct command.
     */
    LOCK,

    /**
     * The lock was released by a direct command.
     */
    UNLOCK,

    /**
     * The last state change was reverted with the undo command.
     */
    UNDO,

    /**
     * The lock was activated by a configured schedule.
     */
    SCHEDULED_LOCK,

    /**
     * The lock was released by a configured schedule.
     */
    SCHEDULED_UNLOCK
}
