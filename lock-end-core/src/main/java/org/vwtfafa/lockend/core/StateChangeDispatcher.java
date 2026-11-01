package org.vwtfafa.lockend.core;

import org.vwtfafa.lockend.api.LockStateChange;
import org.vwtfafa.lockend.api.LockStateChangeListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches completed lock state changes to registered
 * {@link LockStateChangeListener}s.
 *
 * <p>The dispatcher is intentionally tiny: it only owns the listener list and
 * the iteration. Platform providers call {@link #notifyListeners} from the
 * tick context that performed the state change, so listeners observe the
 * change on the same thread context that the platform documents for the
 * change event.</p>
 *
 * <p>Dispatch contract:
 * <ul>
 *   <li>Listeners run sequentially. The relative order is registration
 *       order; it is stable but unsubstantiated for equality and must not
 *       be relied upon for cross-listener coordination.</li>
 *   <li>A listener failure ({@code RuntimeException} or {@code Error}) is
 *       not swallowed: it propagates to the caller, mirroring the behavior
 *       of the legacy Paper state change event. This can skip the remaining
 *       listeners, so listeners must be fail-safe.</li>
 *   <li>Listeners must be re-entrancy aware: a listener that triggers
 *       another state change synchronously runs a nested dispatch.</li>
 * </ul></p>
 *
 * <p>Thread safety: all methods are safe to call from any thread. The
 * underlying list is a {@link CopyOnWriteArrayList}; adding or removing a
 * listener never affects an in-progress dispatch.</p>
 */
public final class StateChangeDispatcher {

    private final List<LockStateChangeListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Registers a listener for subsequent changes.
     *
     * <p>Registering the same instance twice is rejected silently; one
     * instance receives the change exactly once.</p>
     *
     * @param listener the listener to register; not {@code null}
     * @throws IllegalArgumentException if {@code listener} is {@code null}
     */
    public void addListener(LockStateChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unregisters a listener. Removing a listener that is not registered is
     * a no-op.
     *
     * @param listener the listener to remove; not {@code null}
     * @throws IllegalArgumentException if {@code listener} is {@code null}
     */
    public void removeListener(LockStateChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.remove(listener);
    }

    /**
     * Delivers the change to every registered listener.
     *
     * @param change the completed change; not {@code null}
     * @throws IllegalArgumentException if {@code change} is {@code null}
     */
    public void notifyListeners(LockStateChange change) {
        if (change == null) {
            throw new IllegalArgumentException("change must not be null");
        }
        for (LockStateChangeListener listener : listeners) {
            listener.onStateChange(change);
        }
    }
}
