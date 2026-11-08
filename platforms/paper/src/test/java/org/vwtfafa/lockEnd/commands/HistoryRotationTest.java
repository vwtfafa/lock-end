package org.vwtfafa.lockEnd.commands;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests history rotation: retention window and maximum size trimming.
 */
class HistoryRotationTest {

    private static HistoryEntry entryAt(Instant timestamp) {
        return new HistoryEntry(timestamp, "Admin", "LOCK", "COMMAND", true);
    }

    @Test
    void entriesInsideRetentionWindowAreKept() {
        Instant now = Instant.now();
        List<HistoryEntry> entries = List.of(
                entryAt(now.minus(29, ChronoUnit.DAYS)),
                entryAt(now.minus(1, ChronoUnit.HOURS)),
                entryAt(now));

        List<HistoryEntry> rotated = LockHistoryCommand.applyRotation(entries, 1000, 30);

        assertEquals(3, rotated.size());
    }

    @Test
    void oldEntriesBeyondTheRetentionWindowAreDropped() {
        Instant now = Instant.now();
        List<HistoryEntry> entries = List.of(
                entryAt(now.minus(40, ChronoUnit.DAYS)),
                entryAt(now.minus(31, ChronoUnit.DAYS)),
                entryAt(now.minus(5, ChronoUnit.DAYS)));

        List<HistoryEntry> rotated = LockHistoryCommand.applyRotation(entries, 1000, 30);

        assertEquals(1, rotated.size());
        assertTrue(rotated.get(0).timestamp().isAfter(now.minus(6, ChronoUnit.DAYS)));
    }

    @Test
    void nonPositiveRetentionKeepsAllAges() {
        Instant now = Instant.now();
        List<HistoryEntry> entries = List.of(
                entryAt(now.minus(730, ChronoUnit.DAYS)),
                entryAt(now));

        assertEquals(2, LockHistoryCommand.applyRotation(entries, 1000, 0).size());
        assertEquals(2, LockHistoryCommand.applyRotation(entries, 1000, -1).size());
    }

    @Test
    void oversizedHistoriesKeepOnlyTheNewestEntries() {
        List<HistoryEntry> entries = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            entries.add(entryAt(Instant.now().minus(50 - i, ChronoUnit.MINUTES)));
        }

        List<HistoryEntry> rotated = LockHistoryCommand.applyRotation(entries, 10, 0);

        assertEquals(10, rotated.size());
        // The oldest entries are gone; the newest survives.
        assertEquals(entries.get(49).timestamp(), rotated.get(rotated.size() - 1).timestamp());
        assertEquals(entries.get(40).timestamp(), rotated.get(0).timestamp());
    }

    @Test
    void paginationHandlesHugeAndOverflowingPageNumbers() {
        assertEquals(0, LockHistoryCommand.pageEndExclusive(1000, 429496730));
        assertEquals(Integer.MAX_VALUE,
                LockHistoryCommand.parsePageNumber("999999999999999999999999999999"));
        assertEquals(0, LockHistoryCommand.pageEndExclusive(1000,
                LockHistoryCommand.parsePageNumber("999999999999999999999999999999")));
    }

    @Test
    void paginationUsesTenEntryPagesWithoutOverflow() {
        assertEquals(25, LockHistoryCommand.pageEndExclusive(25, 1));
        assertEquals(15, LockHistoryCommand.pageEndExclusive(25, 2));
        assertEquals(0, LockHistoryCommand.pageEndExclusive(25, 4));
    }
}
