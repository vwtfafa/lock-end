package org.vwtfafa.lockEnd;

import org.vwtfafa.lockEnd.api.EndLockApi;

import java.util.function.Supplier;

/**
 * Internal provider for the read-only 3.1 API. Lives outside the API package
 * and carries no API or compatibility promise; it only wires the published
 * {@link EndLockApi} to the plugin's volatile runtime state. Takes suppliers
 * so the delegation contract is unit testable without instantiating
 * {@code LockEnd}.
 */
final class DefaultEndLockApi implements EndLockApi {

    private final Supplier<Boolean> lockSupplier;
    private final Supplier<String> rawReasonSupplier;

    /**
     * Creates the provider around state accessors.
     *
     * @param lockSupplier      reads the current volatile lock state
     * @param rawReasonSupplier reads the effective raw lock reason (including
     *                          the internal "Maintenance" fallback); plain
     *                          text stripping happens here, never in the
     *                          supplier
     */
    DefaultEndLockApi(Supplier<Boolean> lockSupplier, Supplier<String> rawReasonSupplier) {
        this.lockSupplier = lockSupplier;
        this.rawReasonSupplier = rawReasonSupplier;
    }

    @Override
    public boolean isLocked() {
        return lockSupplier.get();
    }

    @Override
    public String getLockReason() {
        return LockReasonSanitizer.sanitize(rawReasonSupplier.get());
    }
}
