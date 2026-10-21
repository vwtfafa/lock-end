package org.vwtfafa.lockEnd;

import org.junit.jupiter.api.Test;
import org.vwtfafa.lockEnd.commands.HistoryEntry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that history entries are composed from the language templates instead
 * of hard-coded English text.
 */
class HistoryEntryDisplayTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final MessageService MESSAGES = new MessageService(null) {
        private final Map<String, String> templates = Map.of(
                "history-entry", "[%timestamp%] %action% by %actor% (source: %source%, result: %result%%details%)",
                "history-details-actor-uuid", ", actor UUID: %uuid%",
                "history-details-subject-uuid", ", player UUID: %uuid%",
                "history-details-state", ", state: %state%",
                "history-details-state-transition", " -> %state%",
                "history-state-locked", "locked",
                "history-state-unlocked", "unlocked");

        @Override
        public String msg(String key) {
            return templates.getOrDefault(key, key);
        }
    };

    @Test
    void rendersOptionalUuidAndStateDetails() {
        HistoryEntry entry = new HistoryEntry(
                Instant.parse("2026-10-21T12:34:56Z"),
                "Alice", ACTOR, "LOCK", "COMMAND", SUBJECT, "SUCCESS",
                false, true);

        assertEquals("[2026-10-21 12:34:56 UTC] LOCK by Alice (source: COMMAND, result: SUCCESS, "
                        + "actor UUID: 00000000-0000-0000-0000-000000000001, "
                        + "player UUID: 00000000-0000-0000-0000-000000000002, "
                        + "state: unlocked -> locked)",
                entry.display(MESSAGES));
    }

    @Test
    void omitsDetailsThatAreNotAvailable() {
        HistoryEntry entry = new HistoryEntry(
                Instant.parse("2026-10-21T12:34:56Z"),
                "Bob", null, "UNLOCK", "CONSOLE", null, "RECORDED", null, null);

        assertEquals("[2026-10-21 12:34:56 UTC] UNLOCK by Bob (source: CONSOLE, result: RECORDED)",
                entry.display(MESSAGES));
    }
}
