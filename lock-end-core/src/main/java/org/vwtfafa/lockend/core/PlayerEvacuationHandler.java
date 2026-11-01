package org.vwtfafa.lockend.core;

/**
 * Platform contract for moving players out of the End when the lock becomes
 * active or, as a restart safety net, when the platform boots with a
 * persisted active lock.
 *
 * <p>The core defines the contract only. Each platform decides how to reach
 * players (Paper: entity/region schedulers; mods: their own side); the core
 * never sees platform types.</p>
 */
public interface PlayerEvacuationHandler {

    /**
     * Moves eligible players out of the locked End, obeying the platform
     * rules for eligibility (bypass permissions, whitelist exemptions) and
     * scheduling. Must be idempotent and must run asynchronously-safe on
     * region-threaded platforms.
     */
    void evacuatePlayers();

    /**
     * Cancels any pending or scheduled evacuation work; called on unlock and
     * on platform shutdown. Must be safe to call when no work is pending.
     */
    void cancelEvacuation();
}
