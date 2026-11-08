package org.vwtfafa.lockend.api;

/**
 * Shared, platform-independent status access to the EndLock lock.
 *
 * <p>This is the type-safe multiplatform surface introduced with the 4.x
 * shared API. It carries a state snapshot, a structured change model and
 * listener registration; the read-only boolean/string contract of the legacy
 * Paper {@code EndLockApi} (version 3.1) remains available on Paper and is
 * backed by the same runtime state through a compatibility adapter.</p>
 *
 * <p>Consumers obtain this API from their platform integration layer. On
 * Paper the shared provider is registered in the Bukkit {@code ServicesManager}
 * alongside the legacy service; mods receive it from their own loader hook.
 * As with the legacy API, never cache a retrieved provider across reloads:
 * a disabled or reloaded provider instance is replaced, and consumer code
 * must re-resolve after lifecycle events.</p>
 *
 * <p>Thread safety: {@link #isLocked()}, {@link #getLockReason()} and
 * {@link #getState()} are lock-free reads of volatile/runtime state and are
 * callable from any thread. Consistency between state and reason across two
 * separate calls is deliberately not guaranteed; use {@link #getState()}
 * when a consistent pair is required. Listener registration methods are safe
 * to call from any thread, following the lifetime contract documented in
 * {@link LockStateChangeListener}.</p>
 */
public interface LockEndApi {

    /**
     * Returns the current runtime lock state of the End.
     *
     * <p>The value reflects the active runtime state that also drives the
     * access gate, not a configuration default or a pending future
     * schedule/undo state. Every successful state change writes that state
     * synchronously in the tick context of the triggering action, so a call
     * observes either the state before or after the last completed change -
     * never an in-between value.</p>
     *
     * @return {@code true} if the End is locked, {@code false} otherwise
     * @see #getState() for a consistent snapshot including the reason
     */
    boolean isLocked();

    /**
     * Returns the effective lock reason shown to blocked players, as plain
     * text.
     *
     * <p>Format rule: the configured reason is <strong>stripped</strong>, not
     * rendered - MiniMessage tags ({@code <...>}) and legacy {@code §} color
     * code sequences are removed; Unicode characters are kept. A configured
     * reason that becomes empty after stripping yields the empty string.
     * Reason formatting may therefore be lost on purpose; consumers receive
     * exactly the text a concurrently blocked player would see.</p>
     *
     * <p>Rule without a configured/empty reason: the built-in fallback reason
     * of the platform implementation applies. The return value is never
     * {@code null}.</p>
     *
     * <p>Consistency between state and reason across two calls is
     * deliberately not guaranteed; use {@link #getState()} for a consistent
     * pair.</p>
     *
     * @return the effective lock reason as plain text; never {@code null}
     */
    String getLockReason();

    /**
     * Returns a consistent point-in-time capture of the lock state and the
     * effective lock reason.
     *
     * <p>Unlike two separate {@link #isLocked()}/{@link #getLockReason()}
     * calls, the snapshot pairs values as observed in one read. It does not
     * attempt to weaken the read semantics of the underlying volatile state:
     * the capture either represents the state before or after the last
     * completed change, exactly like the single-value reads.</p>
     *
     * @return the current state snapshot; never {@code null}
     */
    LockStateSnapshot getState();

    /**
     * Registers a listener for completed lock state changes.
     *
     * <p>See {@link LockStateChangeListener} for dispatch context, lifetime
     * and duplicate-registration rules.</p>
     *
     * @param listener the listener to register; not {@code null}
     * @throws IllegalArgumentException if {@code listener} is {@code null}
     */
    void addStateChangeListener(LockStateChangeListener listener);

    /**
     * Unregisters a previously registered listener.
     *
     * <p>Removing a listener that is not registered is a no-op. Removing a
     * listener that is currently being dispatched is allowed and at worst
     * prevents delivery of further changes to that instance.</p>
     *
     * @param listener the listener to remove; not {@code null}
     * @throws IllegalArgumentException if {@code listener} is {@code null}
     */
    void removeStateChangeListener(LockStateChangeListener listener);
}
