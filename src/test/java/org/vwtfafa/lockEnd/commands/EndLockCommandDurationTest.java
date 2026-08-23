package org.vwtfafa.lockEnd.commands;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests natural duration parsing for the lockin/unlockin aliases.
 */
class EndLockCommandDurationTest {

    private static boolean targetsIn(LocalDateTime target, long minutes) {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);
        assertFalse(target.isBefore(before.plusMinutes(minutes)));
        return !target.isAfter(after.plusMinutes(minutes));
    }

    @Test
    void parsesMinutesHoursAndDays() {
        assertNotNull(EndLockCommand.parseDurationTarget("5m"));
        assertTrue(targetsIn(EndLockCommand.parseDurationTarget("90M"), 90));
        assertTrue(targetsIn(EndLockCommand.parseDurationTarget("2h"), 120));
        assertTrue(targetsIn(EndLockCommand.parseDurationTarget("7d"), 7 * 24 * 60));
        assertTrue(targetsIn(EndLockCommand.parseDurationTarget(" 15 m "), 15));
    }

    @Test
    void rejectsInvalidInput() {
        assertNull(EndLockCommand.parseDurationTarget(null));
        assertNull(EndLockCommand.parseDurationTarget(""));
        assertNull(EndLockCommand.parseDurationTarget("5"));
        assertNull(EndLockCommand.parseDurationTarget("5x"));
        assertNull(EndLockCommand.parseDurationTarget("m"));
        assertNull(EndLockCommand.parseDurationTarget("-5m"));
    }
}
