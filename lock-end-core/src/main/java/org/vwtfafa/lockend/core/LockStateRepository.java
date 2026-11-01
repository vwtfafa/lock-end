package org.vwtfafa.lockend.core;

import org.vwtfafa.lockend.api.LockStateSnapshot;

/**
 * Persistence contract for the lock state.
 *
 * <p>The core defines the contract; each platform provides its own
 * implementation against its native persistence (Paper: plugin config, mods:
 * their own stores). The master record is the boolean lock state; the
 * snapshot's lock reason and capture time are transport information and a
 * platform implementation may ignore them (documented per implementation).</p>
 *
 * <p>Threading: implementations run on the tick context of the caller
 * (typically the state change context), so implementations must never
 * perform blocking or slow I/O synchronously on hot paths. Follow the
 * platform's persistence rules, e.g. Paper's existing batched config save
 * behavior.</p>
 */
public interface LockStateRepository {

    /**
     * Persists the given state snapshot. Implementations may ignore the
     * reason and capture time; the lock state is authoritative.
     *
     * @param state the snapshot to persist; not {@code null}
     * @throws IllegalArgumentException if {@code state} is {@code null}
     */
    void save(LockStateSnapshot state);

    /**
     * Restores the persisted snapshot for boot. The returned snapshot's lock
     * state drives the initial runtime state.
     *
     * <p>When nothing valid is stored, implementations must return
     * {@link org.vwtfafa.lockend.api.LockState#UNLOCKED} of the returned
     * snapshot; missing data never locks the End on boot.</p>
     *
     * @return the persisted snapshot; never {@code null}
     */
    LockStateSnapshot load();
}
