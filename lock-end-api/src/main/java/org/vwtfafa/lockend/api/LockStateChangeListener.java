package org.vwtfafa.lockend.api;

/**
 * Receives completed lock state changes.
 *
 * <p>Listeners are invoked after the runtime state has changed, in the tick
 * context that performed the change (on Paper the main thread, on Folia the
 * global region thread; other platforms define their own context and must
 * document it). The change cannot be prevented, cancelled, or reverted.</p>
 *
 * <p>Threading rules for follow-up work: listeners run on the calling tick
 * context. Any follow-up world or player mutation must be scheduled
 * according to the platform's scheduling contract (region-scheduled work on
 * Folia/regioned platforms). Make {@code onStateChange} fast and non-blocking;
 * long or risky work belongs on an async path owned by the consumer.</p>
 *
 * <p>Lifetime contract: registration survives until
 * {@link LockEndApi#removeStateChangeListener(LockStateChangeListener)} is
 * called with the same instance. Duplicate registrations are implementation
 * owned; an implementation may accept or reject them, so register each
 * instance once. Listeners are not necessarily called after the provider is
 * disabled or reloaded; do not assume hot-reload safety.</p>
 */
@FunctionalInterface
public interface LockStateChangeListener {

    /**
     * Called once per completed state change.
     *
     * @param change the completed change; not {@code null}
     */
    void onStateChange(LockStateChange change);
}
