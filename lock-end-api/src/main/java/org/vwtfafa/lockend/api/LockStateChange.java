package org.vwtfafa.lockend.api;

import java.util.UUID;

/**
 * Immutable description of one completed lock state change.
 *
 * <p>The record is published after the runtime state has changed, and only
 * when the state actually changed: rejected changes (target state already
 * active), restored persisted states on server start, pending schedule
 * registrations that do not alter the state immediately, undo calls without
 * a recorded previous state, and read-only paths produce no change instance.</p>
 *
 * <p>Instances are effectively immutable and safe to share across threads.</p>
 *
 * @param previousLocked lock state immediately before the change
 * @param newLocked      lock state after the change; always different from
 *                       {@code previousLocked}
 * @param action         trigger of the change; not {@code null}. See
 *                       {@link LockAction} for the
 *                       documented values and the tolerance rule
 * @param actorName      display name of the acting sender ({@code "System"}
 *                       for scheduled actions), or {@code null} if the
 *                       caller could not name an actor
 * @param actorUuid      UUID of the acting entity, or {@code null} for
 *                       console and scheduled actors
 */
public record LockStateChange(
        boolean previousLocked,
        boolean newLocked,
        LockAction action,
        String actorName,
        UUID actorUuid
) {

    /**
     * Creates a change record.
     *
     * @throws IllegalArgumentException if {@code newLocked} equals
     *                                  {@code previousLocked} or
     *                                  {@code action} is {@code null}
     */
    public LockStateChange {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (previousLocked == newLocked) {
            throw new IllegalArgumentException(
                    "newLocked must differ from previousLocked; a failed or rejected change must not be published");
        }
    }
}
