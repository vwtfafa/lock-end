package org.vwtfafa.lockend.core.history;

import java.util.ArrayList;
import java.util.List;

/**
 * Platform-independent history owner: bounded in-memory list, rotation on
 * every insertion, and debounced persistence. This corresponds to the Paper
 * plugin's {@code LockHistoryCommand} runtime minus command parsing and
 * export rendering (both stay platform-side because they render through the
 * platform's message styles and file locations).
 *
 * <p>Threading: every mutator is synchronized, mirroring the Paper runtime.
 * Callers (command handlers on the server thread) may also simply call from
 * one thread; the synchronization keeps the tenant of the Paper behavior
 * while nothing else guarantees single-threaded history access.</p>
 */
public final class HistoryBuffer {

    private final List<HistoryEntry> history = new ArrayList<>();
    private final HistoryPersistence persistence;
    private final int maxEntries;
    private final int retentionDays;
    private boolean saveDirty;

    /**
     * Creates the buffer from a fresh (already loaded and rotated) entry
     * list.
     *
     * @param persistence   the platform's persistence callback; not
     *                      {@code null}
     * @param initial       the entries loaded from disk, will be rotated
     * @param maxEntries    maximum entries retained (drop oldest beyond)
     * @param retentionDays maximum entry age in days; non-positive keeps
     *                      forever
     */
    public HistoryBuffer(HistoryPersistence persistence, List<HistoryEntry> initial,
                         int maxEntries, int retentionDays) {
        if (persistence == null) {
            throw new IllegalArgumentException("persistence must not be null");
        }
        this.persistence = persistence;
        this.maxEntries = Math.max(1, maxEntries);
        this.retentionDays = retentionDays;
        history.addAll(HistoryRotation.applyRotation(initial, maxEntries, retentionDays));
    }

    /**
     * Creates an empty buffer (fresh installation).
     */
    public HistoryBuffer(HistoryPersistence persistence, int maxEntries, int retentionDays) {
        this(persistence, List.of(), maxEntries, retentionDays);
    }

    /**
     * Adds an entry, applies rotation, and marks the buffer for the next
     * flush. The actual write is deferred; callers flush on their own cadence
     * (scheduler tick or shutdown) like the Paper plugin's debounced save.
     *
     * @param entry the entry to append
     * @return {@code true} if a flush is still required after the next one
     */
    public synchronized boolean addEntry(HistoryEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        history.add(entry);
        List<HistoryEntry> rotated = HistoryRotation.applyRotation(
                history, maxEntries, retentionDays);
        history.clear();
        history.addAll(rotated);
        saveDirty = true;
        return true;
    }

    /**
     * Convenience adapter matching the Paper plugin's recorded state-change
     * signature.
     *
     * @param actor         display name of the actor
     * @param actorUuid     UUID of the acting entity, or {@code null}
     * @param action        action identifier
     * @param source        action source beyond the action id
     * @param result        outcome identifier (for example {@code SUCCESS})
     * @param subjectUuid   UUID of the affected player when one exists
     * @param previousState previous lock state, or {@code null}
     * @param newState      resulting lock state, or {@code null}
     */
    public synchronized boolean addEntry(String actor, java.util.UUID actorUuid, String action,
                                         String source, String result,
                                         java.util.UUID subjectUuid, Boolean previousState,
                                         Boolean newState) {
        return addEntry(new HistoryEntry(java.time.Instant.now(), actor, actorUuid, action,
                source, subjectUuid, result, previousState, newState));
    }

    /**
     * Writes a defensive copy of the current entries.
     *
     * @return the snapshot, oldest first; never {@code null}
     */
    public synchronized List<HistoryEntry> snapshot() {
        return new ArrayList<>(history);
    }

    /**
     * Persists all pending changes immediately.
     *
     * @return {@code true} when the history file is up to date now
     */
    public synchronized boolean flush() {
        if (!saveDirty) {
            return true;
        }
        boolean saved = persistence.save(new ArrayList<>(history));
        saveDirty = !saved;
        return saved;
    }

    /**
     * Flushes only when at least one entry was added since the last flush.
     *
     * @return {@code true} when no write is pending anymore
     */
    public synchronized boolean flushIfDirty() {
        if (!saveDirty) {
            return true;
        }
        return flush();
    }

    /**
     * @return {@code true} if at least one entry is not persisted yet
     */
    public synchronized boolean isDirty() {
        return saveDirty;
    }
}
