package org.vwtfafa.lockEnd.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Fired exactly once whenever the runtime lock state changes.
 *
 * <p>Synchronous event ({@link #isAsynchronous()} is {@code false}) on the
 * global tick context: on Paper the main thread; on Folia the global region
 * thread. EndLock routes its state-changing commands through the global
 * scheduler, and scheduled actions already run there. There is no asynchronous
 * internal change path, so the event is never dispatched from an asynchronous
 * thread.</p>
 *
 * <p>The event is published after the runtime state has changed, even if a
 * later side effect throws; that failure still propagates to the caller.
 * It is not cancellable and cannot prevent or revert the change. It is not
 * fired when the target state is already active (the change is rejected before
 * side effects), for the persisted state restored on server start, for pending
 * schedule registrations (lockat/unlockat/lockin/unlockin) that do not change
 * state immediately, for undo without a recorded previous state, and for
 * read-only paths (blocked attempts, test logging).</p>
 *
 * <p>This event is part of the read-only EndLock API (version 3.1);
 * see {@link EndLockApi} for service lookup and lifecycle rules. Event
 * listeners registered by a plugin that is disabled at dispatch time are
 * skipped. Hot-reloading EndLock while consumers remain enabled is not
 * supported; restart the server after replacing or reloading EndLock.</p>
 */
public final class EndLockStateChangeEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final boolean previousLocked;
    private final boolean newLocked;
    private final String action;
    private final @Nullable String actorName;
    private final @Nullable UUID actorUuid;

    /**
     * Creates the event for a runtime lock state change. Callers must fire it
     * via the plugin manager in the global tick context that performed the
     * change.
     *
     * @param previousLocked lock state immediately before the change
     * @param newLocked      lock state after the change (always different from
     *                       previousLocked)
     * @param action         triggering action; the stable documented values
     *                       are {@code "LOCK"}, {@code "UNLOCK"},
     *                       {@code "UNDO"}, {@code "SCHEDULED_LOCK"} and
     *                       {@code "SCHEDULED_UNLOCK"}, but the set is not
     *                       closed (forward compatibility) - consumers must
     *                       parse unknown values tolerantly
     * @param actorName      display name of the acting command sender, or
     *                       {@code "System"} for scheduled actions; may become
     *                       {@code null} only if future callers cannot name an
     *                       actor
     * @param actorUuid      UUID of the acting entity, or {@code null} for
     *                       console and scheduled actors
     */
    public EndLockStateChangeEvent(boolean previousLocked, boolean newLocked,
                                   String action, @Nullable String actorName,
                                   @Nullable UUID actorUuid) {
        super();
        this.previousLocked = previousLocked;
        this.newLocked = newLocked;
        this.action = action;
        this.actorName = actorName;
        this.actorUuid = actorUuid;
    }

    /**
     * @return lock state immediately before the change
     */
    public boolean previousLocked() {
        return previousLocked;
    }

    /**
     * @return lock state after the change; always different from
     * {@link #previousLocked()}
     */
    public boolean newLocked() {
        return newLocked;
    }

    /**
     * @return triggering action; documented values: {@code "LOCK"},
     * {@code "UNLOCK"}, {@code "UNDO"}, {@code "SCHEDULED_LOCK"},
     * {@code "SCHEDULED_UNLOCK"}; unknown values must be tolerated (not a
     * closed set)
     */
    public String action() {
        return action;
    }

    /**
     * @return display name of the acting sender ({@code "System"} for
     * scheduled actions) or {@code null} if the caller could not name one
     */
    public @Nullable String actorName() {
        return actorName;
    }

    /**
     * @return UUID of the acting entity, or {@code null} for console and
     * scheduled actors
     */
    public @Nullable UUID actorUuid() {
        return actorUuid;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    /**
     * @return the single handler list of this event type
     */
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
