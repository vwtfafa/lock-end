package org.vwtfafa.lockEnd.api;

/**
 * Read-only status access to the EndLock lock, offered as a Bukkit service.
 *
 * <p>Lookup (never cache the provider; the service is removed and re-registered
 * on every disable/enable cycle of EndLock):</p>
 *
 * <pre>{@code
 * RegisteredServiceProvider<EndLockApi> registration =
 *         Bukkit.getServicesManager().getRegistration(EndLockApi.class);
 * EndLockApi api = registration == null ? null : registration.getProvider();
 * }</pre>
 *
 * <p>A missing service is a normal state: EndLock may be absent or disabled.
 * Surface (version 3.1): this interface adds {@link EndLockStateChangeEvent};
 * the internal provider class carries no API or compatibility promise.</p>
 */
public interface EndLockApi {

    /**
     * Returns the current runtime lock state of the End.
     *
     * <p>The value reflects the active runtime field that also drives the
     * access gate ({@code volatile boolean locked} in LockEnd), not a
     * configuration default or a planned future schedule/undo state. Every
     * successful state change writes that field synchronously in the tick
     * context of the triggering action, so a call observes either the state
     * before or after the last completed change - never an in-between value.</p>
     *
     * <p>Thread safety: lock-free (volatile read); callable from any thread.
     * Follow-up world or player mutations must still respect the Folia
     * scheduling rules (region/entity/global schedulers), see
     * {@link EndLockStateChangeEvent}.</p>
     *
     * <p>While EndLock is disabled the service does not exist, so this method
     * is simply unreachable in that state.</p>
     *
     * @return {@code true} if the End is locked, {@code false} otherwise
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
     * <p>Rule without a configured/empty reason: the same built-in fallback
     * that the player message uses ("Maintenance" from the internal
     * {@code LockReasonManager}) applies. The return value is never
     * {@code null}.</p>
     *
     * <p>Thread safety: the backing value is stored in an unmodifiable map
     * behind a volatile field; the returned string is immutable, so a call
     * from any thread is well-defined. Consistency between state and reason
     * across two calls is deliberately not guaranteed.</p>
     *
     * @return the effective lock reason as plain text; never {@code null}
     */
    String getLockReason();
}
