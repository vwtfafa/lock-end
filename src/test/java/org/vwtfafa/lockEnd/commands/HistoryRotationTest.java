package org.vwtfafa.lockEnd.commands;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests history rotation: retention window and maximum size trimming.
 */
class HistoryRotationTest {

    private static HistoryEntry entryAt(LocalDateTime timestamp) {
        return new HistoryEntry(timestamp, "Admin", "LOCK", "COMMAND", true);
    }

    @Test
    void entriesInsideRetentionWindowAreKept() {
        LocalDateTime now = LocalDateTime.now();
        List<HistoryEntry> entries = List.of(
                entryAt(now.minusDays(29)),
                entryAt(now.minusHours(1)),
                entryAt(now));

        List<HistoryEntry> rotated = LockHistoryCommand.applyRotation(entries, 1000, 30);

        assertEquals(3, rotated.size());
    }

    @Test
    void oldEntriesBeyondTheRetentionWindowAreDropped() {
        LocalDateTime now = LocalDateTime.now();
        List<HistoryEntry> entries = List.of(
                entryAt(now.minusDays(40)),
                entryAt(now.minusDays(31)),
                entryAt(now.minusDays(5)));

        List<HistoryEntry> rotated = LockHistoryCommand.applyRotation(entries, 1000, 30);

        assertEquals(1, rotated.size());
        assertTrue(rotated.get(0).timestamp().isAfter(now.minusDays(6)));
    }

    @Test
    void nonPositiveRetentionKeepsAllAges() {
        LocalDateTime now = LocalDateTime.now();
        List<HistoryEntry> entries = List.of(
                entryAt(now.minusYears(2)),
                entryAt(now));

        assertEquals(2, LockHistoryCommand.applyRotation(entries, 1000, 0).size());
        assertEquals(2, LockHistoryCommand.applyRotation(entries, 1000, -1).size());
    }

    @Test
    void oversizedHistoriesKeepOnlyTheNewestEntries() {
        List<HistoryEntry> entries = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            entries.add(entryAt(LocalDateTime.now().minusMinutes(50 - i)));
        }

        List<HistoryEntry> rotated = LockHistoryCommand.applyRotation(entries, 10, 0);

        assertEquals(10, rotated.size());
        // The oldest entries are gone; the newest survives.
        assertEquals(entries.get(49).timestamp(), rotated.get(rotated.size() - 1).timestamp());
        assertEquals(entries.get(40).timestamp(), rotated.get(0).timestamp());
    }
}
