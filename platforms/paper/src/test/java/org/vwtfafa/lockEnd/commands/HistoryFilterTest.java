package org.vwtfafa.lockEnd.commands;

import org.junit.jupiter.api.Test;
import org.vwtfafa.lockend.core.history.HistoryEntry;
import org.vwtfafa.lockend.core.history.HistoryFilter;

import java.time.Instant;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests history filter matching for the player and action filter types.
 */
class HistoryFilterTest {
    private static final HistoryEntry ENTRY = new HistoryEntry(
            Instant.parse("2026-08-23T12:00:00Z"),
            "Admin", "LOCK", "COMMAND", true);

    @Test
    void nullFilterMatchesEverything() {
        assertTrue(new HistoryFilter(null, null).matches(ENTRY));
    }

    @Test
    void playerFilterMatchesCaseInsensitively() {
        assertTrue(new HistoryFilter("player", "ADMIN").matches(ENTRY));
        assertFalse(new HistoryFilter("player", "SomeoneElse").matches(ENTRY));
    }

    @Test
    void actionFilterMatchesCaseInsensitively() {
        assertTrue(new HistoryFilter("action", "lock").matches(ENTRY));
        assertFalse(new HistoryFilter("action", "UNDO").matches(ENTRY));
    }

    @Test
    void unknownFilterTypeMatchesEverything() {
        assertTrue(new HistoryFilter("world", "world").matches(ENTRY));
    }

    @Test
    void commandTokensAreNormalizedIndependentOfSystemLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("action", LockHistoryCommand.lowerCaseToken("ACTION"));
        } finally {
            Locale.setDefault(original);
        }
    }
}
