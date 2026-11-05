package org.vwtfafa.lockend.core.history;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the shared history runtime: bounded buffering, rotation on insert,
 * dirty tracking and debounced flush semantics.
 */
class HistoryBufferTest {

    private static HistoryEntry entry(int index) {
        return new HistoryEntry(Instant.parse("2026-01-01T00:00:0" + (index % 10) + "Z"),
                "actor" + index, "lock", "lock", true);
    }

    @Test
    void rotationDropsOldestEntriesBeyondTheMaximum() {
        RecordingPersistence persistence = new RecordingPersistence();
        HistoryBuffer buffer = new HistoryBuffer(persistence, List.of(
                entry(1), entry(2), entry(3)), 2, 0);

        assertEquals(2, buffer.snapshot().size());
        assertEquals(List.of("actor2", "actor3"),
                buffer.snapshot().stream().map(HistoryEntry::actor).toList());
    }

    @Test
    void addEntryRotatesImmediatelyAndMarksDirty() {
        HistoryBuffer buffer = new HistoryBuffer(new RecordingPersistence(), 2, 0);
        buffer.addEntry(entry(1));
        buffer.addEntry(entry(2));
        buffer.addEntry(entry(3));

        assertEquals(List.of("actor2", "actor3"),
                buffer.snapshot().stream().map(HistoryEntry::actor).toList());
        assertTrue(buffer.isDirty());
    }

    @Test
    void flushWritesAndClearsTheDirtyFlag() {
        RecordingPersistence persistence = new RecordingPersistence();
        HistoryBuffer buffer = new HistoryBuffer(persistence, 10, 0);
        buffer.addEntry(entry(1));

        assertTrue(buffer.flushIfDirty());
        assertEquals(1, persistence.saved.size());
        assertFalse(buffer.isDirty());
        assertTrue(buffer.flushIfDirty(), "second flush is a no-op");
        assertEquals(1, persistence.saved.size());
    }

    @Test
    void failedWriteKeepsTheDirtyFlag() {
        HistoryBuffer buffer = new HistoryBuffer(
                new RecordingPersistence(false), 10, 0);
        buffer.addEntry(entry(1));

        assertFalse(buffer.flush());
        assertTrue(buffer.isDirty(), "failed writes stay pending");
    }

    @Test
    void nullPersistenceIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new HistoryBuffer(null, 10, 30));
    }

    @Test
    void nullEntryIsRejected() {
        HistoryBuffer buffer = new HistoryBuffer(new RecordingPersistence(), 10, 0);
        assertThrows(IllegalArgumentException.class, () -> buffer.addEntry((HistoryEntry) null));
    }

    @Test
    void nonPositiveMaxEntriesIsClampedToOne() {
        HistoryBuffer buffer = new HistoryBuffer(new RecordingPersistence(), 0, 0);
        buffer.addEntry(entry(1));
        buffer.addEntry(entry(2));

        assertEquals(1, buffer.snapshot().size());
    }

    private static final class RecordingPersistence implements HistoryPersistence {
        private final boolean succeed;
        private final List<List<HistoryEntry>> saved = new java.util.ArrayList<>();

        private RecordingPersistence() {
            this(true);
        }

        private RecordingPersistence(boolean succeed) {
            this.succeed = succeed;
        }

        @Override
        public boolean save(List<HistoryEntry> entries) {
            if (!succeed) {
                return false;
            }
            saved.add(List.copyOf(entries));
            return true;
        }
    }
}
