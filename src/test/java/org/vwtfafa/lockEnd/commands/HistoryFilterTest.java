package org.vwtfafa.lockEnd.commands;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests history filter matching for the player and action filter types.
 */
class HistoryFilterTest {
    private static final HistoryEntry ENTRY = new HistoryEntry(
            LocalDateTime.of(2026, 8, 23, 12, 0),
            "Admin", "LOCK", "COMMAND", true);

    @Test
    void nullFilterMatchesEverything() {
        assertTrue(new LockHistoryCommand.HistoryFilter(null, null).matches(ENTRY));
    }

    @Test
    void playerFilterMatchesCaseInsensitively() {
        assertTrue(new LockHistoryCommand.HistoryFilter("player", "ADMIN").matches(ENTRY));
        assertFalse(new LockHistoryCommand.HistoryFilter("player", "SomeoneElse").matches(ENTRY));
    }

    @Test
    void actionFilterMatchesCaseInsensitively() {
        assertTrue(new LockHistoryCommand.HistoryFilter("action", "lock").matches(ENTRY));
        assertFalse(new LockHistoryCommand.HistoryFilter("action", "UNDO").matches(ENTRY));
    }

    @Test
    void unknownFilterTypeMatchesEverything() {
        assertTrue(new LockHistoryCommand.HistoryFilter("world", "world").matches(ENTRY));
    }
}
