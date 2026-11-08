package org.vwtfafa.lockend.core;

import org.vwtfafa.lockend.api.LockAction;

import java.util.Locale;

/**
 * Maps the legacy string action identifiers of the Paper 3.1 API onto the
 * shared {@link LockAction} enum. The mapping is strict: any unknown name
 * fails loudly instead of being coerced to a wrong documented value. Since
 * the internal call sites own the strings this set is currently closed and
 * every future action gets its enum entry when introduced.
 */
public final class LockActions {

    private LockActions() {
    }

    /**
     * Maps an action identifier in the documented legacy form
     * ({@code "LOCK"}, {@code "UNLOCK"}, {@code "UNDO"},
     * {@code "SCHEDULED_LOCK"}, {@code "SCHEDULED_UNLOCK"}) onto the enum.
     *
     * @param action the legacy action name; case-insensitive, must already
     *               be a plain identifier without blanks
     * @return the matching enum value; never {@code null}
     * @throws IllegalArgumentException if the name is {@code null} or does
     *                                  not match a documented action
     */
    public static LockAction fromActionName(String action) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        String normalized = action.toUpperCase(Locale.ROOT);
        try {
            return LockAction.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown lock action name (expected LOCK, UNLOCK, UNDO, "
                            + "SCHEDULED_LOCK or SCHEDULED_UNLOCK): " + action,
                    exception);
        }
    }
}
