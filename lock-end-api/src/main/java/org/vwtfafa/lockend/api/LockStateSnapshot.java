package org.vwtfafa.lockend.api;

/**
 * Immutable point-in-time capture of the runtime lock state.
 *
 * <p>A snapshot freezes state and reason together, so consumers that need a
 * consistent pair do not have to race two separate calls. The values mirror
 * the guarantees of {@link LockEndApi#isLocked()} and
 * {@link LockEndApi#getLockReason()} at the moment the platform created the
 * snapshot.</p>
 *
 * <p>The lock reason is plain text: formatting (MiniMessage tags and legacy
 * {@code §} color sequences) is stripped exactly as the effective player
 * message shows it, and a configured reason that becomes empty after
 * stripping yields the empty string. Without a configured or non-empty
 * reason, the built-in fallback reason is used; the value is never
 * {@code null}.</p>
 *
 * @param state                 lock state at capture time; not {@code null}
 * @param lockReason            effective lock reason as plain text; not
 *                              {@code null}
 * @param capturedAtEpochMillis platform wall-clock time of the capture
 *                              ({@code System.currentTimeMillis()}); not
 *                              reinterpreted by the API. Use for ordering
 *                              and diagnostics only.
 */
public record LockStateSnapshot(
        LockState state,
        String lockReason,
        long capturedAtEpochMillis
) {

    /**
     * Creates a snapshot.
     *
     * @throws IllegalArgumentException if {@code state} or {@code lockReason}
     *                                  is {@code null}
     */
    public LockStateSnapshot {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (lockReason == null) {
            throw new IllegalArgumentException("lockReason must not be null");
        }
    }

    /**
     * @return {@code true} if the captured state is {@link LockState#LOCKED}
     */
    public boolean isLocked() {
        return state.isLocked();
    }
}
