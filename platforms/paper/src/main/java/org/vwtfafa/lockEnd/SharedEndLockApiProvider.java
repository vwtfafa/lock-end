package org.vwtfafa.lockEnd;

import org.vwtfafa.lockend.api.LockEndApi;
import org.vwtfafa.lockend.api.LockStateChangeListener;
import org.vwtfafa.lockend.api.LockStateSnapshot;
import org.vwtfafa.lockend.core.LockStateManager;

import java.util.function.Supplier;

/**
 * Paper implementation of the shared, platform-independent
 * {@link LockEndApi} (4.x surface). Delegates the runtime state to the
 * shared {@link LockStateManager} and applies the same plain text stripping
 * rule as the legacy 3.1 provider, so both APIs expose identical texts.
 */
final class SharedEndLockApiProvider implements LockEndApi {

    private final LockStateManager state;
    private final Supplier<String> rawReasonSupplier;

    /**
     * Creates the provider around the shared state manager and the raw
     * reason accessor.
     *
     * @param state             the shared runtime state owner
     * @param rawReasonSupplier reads the effective raw lock reason (including
     *                          the "Maintenance" fallback); plain text
     *                          stripping happens here, never in the supplier
     */
    SharedEndLockApiProvider(LockStateManager state, Supplier<String> rawReasonSupplier) {
        this.state = state;
        this.rawReasonSupplier = rawReasonSupplier;
    }

    @Override
    public boolean isLocked() {
        return state.isLocked();
    }

    @Override
    public String getLockReason() {
        return LockReasonSanitizer.sanitize(rawReasonSupplier.get());
    }

    @Override
    public LockStateSnapshot getState() {
        return new LockStateSnapshot(
                state.getCurrentState(),
                LockReasonSanitizer.sanitize(rawReasonSupplier.get()),
                System.currentTimeMillis());
    }

    @Override
    public void addStateChangeListener(LockStateChangeListener listener) {
        state.addStateChangeListener(listener);
    }

    @Override
    public void removeStateChangeListener(LockStateChangeListener listener) {
        state.removeStateChangeListener(listener);
    }
}
