package org.vwtfafa.lockend.core;

import org.vwtfafa.lockend.api.LockAction;
import org.vwtfafa.lockend.api.LockState;
import org.vwtfafa.lockend.api.LockStateChange;
import org.vwtfafa.lockend.api.LockStateChangeListener;
import org.vwtfafa.lockend.api.LockStateSnapshot;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Platform-independent owner of the runtime lock state. Corresponds to the
 * plugin's volatile {@code locked} flag plus the effective lock reason and
 * its observation points.
 *
 * <p>Threading and consistency semantics (mirrors the live Paper behavior
 * which this class will back):</p>
 * <ul>
 *   <li>{@link #isLocked()} is a lock-free volatile read, callable from any
 *       thread. A call observes either the state before or after the last
 *       completed change - never an in-between value.</li>
 *   <li>{@link #updateLocked} must be called from the tick context that
 *       performs the change. It flips the state first and then synchronously
 *       dispatches the change to listeners in the same context.</li>
 *   <li>{@link #getLockReason} resolves the reason through the configured
 *       supplier on every call, so supplier results (e.g. after a config
 *       reload) are always current. Consistency between state and reason
 *       across two separate calls is deliberately not guaranteed; use
 *       {@link #getState()} for a consistent pair.</li>
 * </ul>
 *
 * <p>The manager deliberately does not attempt to replicate platform-specific
 * follow-up work (history, broadcasts, evacuation); the owning platform
 * performs those around the state flip. {@link #updateLocked} only flips the
 * state and notifies listeners - it never persists or reverts, and no
 * exception from a listener reverts the change (see
 * {@link StateChangeDispatcher}).</p>
 */
public final class LockStateManager {

    private final StateChangeDispatcher dispatcher = new StateChangeDispatcher();
    private volatile Supplier<String> reasonSupplier = () -> "";
    private volatile boolean locked;

    /**
     * Creates the manager with the given initial state, e.g. the state
     * restored from platform persistence on boot.
     *
     * @param initiallyLocked {@code true} if the End starts locked
     */
    public LockStateManager(boolean initiallyLocked) {
        this.locked = initiallyLocked;
    }

    /**
     * Loads the current lock state at boot time.
     */
    public LockStateManager loadFromPersistence(LockStateRepository repository) {
        this.locked = repository.load().isLocked();
        return this;
    }

    /**
     * Installs the reason supplier; the supplier result must never be
     * {@code null} (the platform adapter owns the stripping/fallback rules).
     *
     * @param reasonSupplier the reason supplier; not {@code null}
     * @throws IllegalArgumentException if the supplier is {@code null}
     */
    public void setReasonSupplier(Supplier<String> reasonSupplier) {
        if (reasonSupplier == null) {
            throw new IllegalArgumentException("reasonSupplier must not be null");
        }
        this.reasonSupplier = reasonSupplier;
    }

    /**
     * @return {@code true} if the End is currently locked
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * @return the current {@link LockState}; never {@code null}
     */
    public LockState getCurrentState() {
        return LockState.fromLocked(locked);
    }

    /**
     * @return the current effective lock reason provided by the configured
     *         supplier; never {@code null}
     */
    public String getLockReason() {
        String reason = reasonSupplier.get();
        return reason == null ? "" : reason;
    }

    /**
     * Captures a consistent snapshot of state and reason at call time.
     *
     * @return the snapshot; never {@code null}
     */
    public LockStateSnapshot getState() {
        return new LockStateSnapshot(
                LockState.fromLocked(locked), getLockReason(), System.currentTimeMillis());
    }

    /**
     * Applies a completed state change and notifies the listeners. Callers
     * must ensure the new state differs from the current one; a change to
     * the current state is rejected and dispatches nothing.
     *
     * @param newLocked the target lock state
     * @param action    the trigger of the change; not {@code null}
     * @param actorName display name of the acting sender, or {@code null}
     *                  if the caller cannot name an actor
     * @param actorUuid UUID of the acting entity, or {@code null} for
     *                  console and scheduled actors
     * @return {@code true} if the state changed, {@code false} if the target
     *         state was already active (no state write, no dispatch)
     * @throws IllegalArgumentException if {@code action} is {@code null}
     */
    public boolean updateLocked(
            boolean newLocked, LockAction action, String actorName, UUID actorUuid) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (locked == newLocked) {
            return false;
        }
        boolean previousLocked = locked;
        locked = newLocked;
        dispatcher.notifyListeners(new LockStateChange(previousLocked, newLocked, action, actorName, actorUuid));
        return true;
    }

    /**
     * @see StateChangeDispatcher#addListener
     */
    public void addStateChangeListener(LockStateChangeListener listener) {
        dispatcher.addListener(listener);
    }

    /**
     * @see StateChangeDispatcher#removeListener
     */
    public void removeStateChangeListener(LockStateChangeListener listener) {
        dispatcher.removeListener(listener);
    }
}
