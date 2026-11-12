package org.vwtfafa.lockend.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the in-memory undo contract shared by the mod adapters and Paper once
 * Paper switches onto {@link UndoTracker}.
 */
class UndoTrackerTest {

    @Test
    void startsWithNoPendingUndo() {
        assertNull(new UndoTracker().getLastPreviousState());
    }

    @Test
    void recordsTrueAndFalse() {
        UndoTracker tracker = new UndoTracker();

        tracker.recordPreviousState(true);
        assertEquals(Boolean.TRUE, tracker.getLastPreviousState());

        tracker.recordPreviousState(false);
        assertEquals(Boolean.FALSE, tracker.getLastPreviousState());
    }

    @Test
    void clearRemovesPendingUndo() {
        UndoTracker tracker = new UndoTracker();
        tracker.recordPreviousState(true);

        tracker.clearLastPreviousState();

        assertNull(tracker.getLastPreviousState());
    }

    @Test
    void overwriteKeepsOnlyTheLatestValue() {
        UndoTracker tracker = new UndoTracker();
        tracker.recordPreviousState(false);
        tracker.recordPreviousState(true);

        assertEquals(Boolean.TRUE, tracker.getLastPreviousState());
    }
}
