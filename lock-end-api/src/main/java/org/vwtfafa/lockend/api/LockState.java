package org.vwtfafa.lockend.api;

/**
 * Read-only runtime state of the EndLock lock.
 *
 * <p>The state is intentionally expressed as an enum instead of a boolean so
 * future state kinds can be added to the shared API without changing every
 * consumer signature. Consumers must handle unknown values toleratedly
 * (default branches, not exhaustive switches over the values).</p>
 */
public enum LockState {

    /**
     * Access to the End dimension is blocked.
     */
    LOCKED,

    /**
     * Access to the End dimension is permitted.
     */
    UNLOCKED;

    /**
     * Converts the boolean representation used by the legacy Paper API.
     *
     * @param locked {@code true} for {@link #LOCKED}, {@code false} otherwise
     * @return the matching state; never {@code null}
     */
    public static LockState fromLocked(boolean locked) {
        return locked ? LOCKED : UNLOCKED;
    }

    /**
     * Converts this state to the boolean representation used by the legacy
     * Paper API.
     *
     * @return {@code true} if the state is {@link #LOCKED}
     */
    public boolean isLocked() {
        return this == LOCKED;
    }
}
