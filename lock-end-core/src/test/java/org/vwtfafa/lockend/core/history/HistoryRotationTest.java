package org.vwtfafa.lockend.core.history;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the shared rotation, filtering and pagination rules against the
 * Paper plugin's behavior.
 */
class HistoryRotationTest {

    private static Instant at(int daysAgo) {
        return Instant.parse("2026-11-05T12:00:00Z").minusSeconds(daysAgo * 86400L);
    }

    private static HistoryEntry entry(int daysAgo, String actor) {
        return new HistoryEntry(at(daysAgo), actor, "lock", "lock", true);
    }

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

    @Test
    void dropsEntriesOlderThanTheRetentionWindow() {
        List<HistoryEntry> rotated = HistoryRotation.applyRotation(
                List.of(entry(3, "a"), entry(1, "b")), 10, 2, NOW);

        assertEquals(List.of("b"), rotated.stream().map(HistoryEntry::actor).toList());
    }

    @Test
    void dropsOldestEntriesBeyondTheMaximum() {
        List<HistoryEntry> rotated = HistoryRotation.applyRotation(
                List.of(entry(5, "a"), entry(4, "b"), entry(1, "c")), 2, 0, NOW);

        assertEquals(List.of("b", "c"), rotated.stream().map(HistoryEntry::actor).toList());
    }

    @Test
    void nonPositiveRetentionKeepsForever() {
        List<HistoryEntry> rotated = HistoryRotation.applyRotation(
                List.of(entry(90, "a")), 10, 0, NOW);

        assertEquals(1, rotated.size());
    }

    @Test
    void rotationKeepsRetentionAndMaximumTogether() {
        List<HistoryEntry> rotated = HistoryRotation.applyRotation(
                List.of(entry(10, "old"), entry(3, "newer"), entry(1, "new")), 1, 7, NOW);

        assertEquals(List.of("new"), rotated.stream().map(HistoryEntry::actor).toList());
    }

    @Test
    void worstCasePageNumberResolvesToTheNewestPage() {
        assertEquals(1, HistoryRotation.parsePageNumber("1"));
        assertEquals(Integer.MAX_VALUE, HistoryRotation.parsePageNumber("not-a-number"));
    }

    @Test
    void pageOneEndsAtTheNewestEntry() {
        assertEquals(9, HistoryRotation.pageEndExclusive(9, 1));
        // Uncapped like the Paper plugin: the caller clamps start to (end - PAGE_SIZE).
        assertEquals(25, HistoryRotation.pageEndExclusive(25, 1));
        assertEquals(15, HistoryRotation.pageEndExclusive(25, 2));
        assertEquals(5, HistoryRotation.pageEndExclusive(25, 3));
        assertEquals(0, HistoryRotation.pageEndExclusive(25, 4));
        assertEquals(0, HistoryRotation.pageEndExclusive(5, 99));
    }

    @Test
    void paginationStartIsNewestFirst() {
        List<Integer> entries = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        int end = Math.min(entries.size(), HistoryRotation.pageEndExclusive(entries.size(), 1));
        int start = Math.max(0, end - HistoryRotation.PAGE_SIZE);
        // Page 1 shows indexes 2..11 (the newest ten entries).
        assertEquals(2, start);
        assertEquals(12, end);
    }

    @Test
    void filterMatchesPlayerAndActionCaseInsensitively() {
        HistoryEntry locked = new HistoryEntry(Instant.now(), "Alice",
                UUID.randomUUID(), "LOCK", "LOCK", null, "SUCCESS", true, null);
        assertTrue(new HistoryFilter("player", "alice").matches(locked));
        assertTrue(new HistoryFilter("action", "lock").matches(locked));
        assertFalse(new HistoryFilter("player", "bob").matches(locked));
        assertTrue(new HistoryFilter(null, null).matches(locked));
        assertTrue(new HistoryFilter("unknown", "value").matches(locked));
    }

    @Test
    void emptyFilterValueMatchesNothing() {
        HistoryEntry locked = new HistoryEntry(Instant.now(), "Alice", "LOCK", "LOCK", true);
        assertFalse(new HistoryFilter("player", "").matches(locked));
    }
}
