package org.vwtfafa.lockEnd;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests schedule time parsing and duration formatting.
 */
class ScheduleManagerLogicTest {

    @Test
    void parsesValidScheduleTimes() {
        assertEquals(LocalDateTime.of(2026, 8, 30, 12, 0),
                ScheduleManager.parseScheduleTime("2026-08-30 12:00"));
    }

    @Test
    void blankAndNullInputsParseToNull() {
        assertNull(ScheduleManager.parseScheduleTime(null));
        assertNull(ScheduleManager.parseScheduleTime(""));
        assertNull(ScheduleManager.parseScheduleTime("   "));
    }

    @Test
    void invalidInputParsesToNull() {
        assertNull(ScheduleManager.parseScheduleTime("30.08.2026 12:00"));
        assertNull(ScheduleManager.parseScheduleTime("2026-13-40 99:99"));
    }

    @Test
    void impossibleDatesParseToNull() {
        // February 30th does not exist in any year.
        assertNull(ScheduleManager.parseScheduleTime("2026-02-30 12:00"));
        // Non-leap year February 29th.
        assertNull(ScheduleManager.parseScheduleTime("2026-02-29 00:00"));
    }

    @Test
    void leapYearFebruaryIsAccepted() {
        assertEquals(LocalDateTime.of(2028, 2, 29, 0, 0),
                ScheduleManager.parseScheduleTime("2028-02-29 00:00"));
    }

    @Test
    void formatDeviationsAreRejected() {
        // Single digit month/day or missing zero padding must not parse.
        assertNull(ScheduleManager.parseScheduleTime("2026-8-3 5:00"));
        // ISO format with a T separator is not accepted either.
        assertNull(ScheduleManager.parseScheduleTime("2026-08-30T12:00"));
    }

    @Test
    void formatsDurationsCompactly() {
        assertEquals("59s", ScheduleManager.formatDuration(59));
        assertEquals("1m 0s", ScheduleManager.formatDuration(60));
        assertEquals("5m 30s", ScheduleManager.formatDuration(330));
        assertEquals("2h 5m", ScheduleManager.formatDuration(7500));
        assertEquals("3d 4h", ScheduleManager.formatDuration(273600));
    }

    @Test
    void computesRemainingSecondsFromACapturedTarget() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 30, 12, 0, 0, 0, ZoneId.of("UTC"));

        assertEquals(120, ScheduleManager.remainingSeconds(
                LocalDateTime.of(2026, 8, 30, 12, 2), now));
        assertEquals(0, ScheduleManager.remainingSeconds(
                LocalDateTime.of(2026, 8, 30, 11, 59), now));
        assertEquals(-1, ScheduleManager.remainingSeconds(null, now));
    }

    @Test
    void onlyExplicitLockAndUnlockActionsAreSupported() {
        assertTrue(ScheduleManager.isSupportedAction("lock"));
        assertTrue(ScheduleManager.isSupportedAction("UNLOCK"));
        assertFalse(ScheduleManager.isSupportedAction("lok"));
        assertFalse(ScheduleManager.isSupportedAction(""));
        assertFalse(ScheduleManager.isSupportedAction(null));
    }

    @Test
    void storesPersistedScheduleStateForAbsoluteTargets() {
        var config = new YamlConfiguration();
        LocalDateTime target = LocalDateTime.of(2026, 9, 18, 12, 30);

        ScheduleManager.persistScheduledAction(config, target, "lock");

        assertEquals(true, config.getBoolean("scheduled-unlock.enabled"));
        assertEquals("lock", config.getString("scheduled-unlock.action"));
        assertEquals("datetime", config.getString("scheduled-unlock.mode"));
        assertEquals("2026-09-18 12:30", config.getString("scheduled-unlock.datetime"));
        assertEquals("2026-09-18 12:30", config.getString("scheduled-unlock.target-datetime"));
    }
}
