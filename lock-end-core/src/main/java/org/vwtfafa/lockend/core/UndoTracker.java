package org.vwtfafa.lockend.core;

/**
 * Keeps the lock state from before the last supported state change so the
 * {@code undo} command can re-apply it. Purely in-memory on every platform:
 * like the Paper plugin, an undo target does not survive a restart.
 */
public final class UndoTracker {

    private volatile Boolean lastPreviousState;

    /**
     * Records the previous state of an executed state change.
     *
     * @param previousState the state before the change
     */
    public void recordPreviousState(boolean previousState) {
        lastPreviousState = previousState;
    }

    /**
     * @return the recorded previous state, or {@code null} if no supported
     *         pending undo exists
     */
    public Boolean getLastPreviousState() {
        return lastPreviousState;
    }

    /**
     * Clears the undo target after a successful undo.
     */
    public void clearLastPreviousState() {
        lastPreviousState = null;
    }
}
