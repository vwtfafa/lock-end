package org.vwtfafa.lockend.core;

import java.time.LocalDateTime;

/**
 * Immutable snapshot of the persisted schedule data shared by every
 * platform's schedule repository: the pending action name, its absolute
 * target and the paused flag.
 *
 * <p>An empty instance (no target) means nothing is scheduled; the action
 * name carries no meaning in that case. A present target with an unsupported
 * action name is tolerated here - the runtime {@link LockScheduler} rejects
 * it during restore so a typo in persisted data never coerces to
 * {@code unlock}.</p>
 *
 * @param action the pending action name ({@code lock} or {@code unlock});
 *               may be {@code null} or unknown for corrupt data
 * @param target the absolute execution time; {@code null} when empty
 * @param paused whether the schedule was paused when it was persisted
 */
public record PersistedSchedule(String action, LocalDateTime target, boolean paused) {

    /**
     * @return an instance representing "nothing scheduled"
     */
    public static PersistedSchedule empty() {
        return new PersistedSchedule(null, null, false);
    }

    /**
     * @return {@code true} when no target is present
     */
    public boolean isEmpty() {
        return target == null;
    }
}
