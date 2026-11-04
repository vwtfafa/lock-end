package org.vwtfafa.lockend.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the shared schedule helper logic: strict time parsing, remaining
 * seconds, compact duration formatting and the supported action names.
 */
class ScheduleSupportTest {

    @Test
    void parsesValidScheduleTimes() {
        assertEquals(LocalDateTime.of(2026, 8, 30, 12, 0),
                ScheduleSupport.parseScheduleTime("2026-08-30 12:00"));
    }

    @Test
    void blankAndNullInputsParseToNull() {
        assertNull(ScheduleSupport.parseScheduleTime(null));
        assertNull(ScheduleSupport.parseScheduleTime(""));
        assertNull(ScheduleSupport.parseScheduleTime("   "));
    }

    @Test
    void invalidInputParsesToNull() {
        assertNull(ScheduleSupport.parseScheduleTime("30.08.2026 12:00"));
        assertNull(ScheduleSupport.parseScheduleTime("2026-13-40 99:99"));
    }

    @Test
    void impossibleDatesParseToNull() {
        // February 30th does not exist in any year.
        assertNull(ScheduleSupport.parseScheduleTime("2026-02-30 12:00"));
        // Non-leap year February 29th.
        assertNull(ScheduleSupport.parseScheduleTime("2026-02-29 00:00"));
    }

    @Test
    void leapYearFebruaryIsAccepted() {
        assertEquals(LocalDateTime.of(2028, 2, 29, 0, 0),
                ScheduleSupport.parseScheduleTime("2028-02-29 00:00"));
    }

    @Test
    void formatDeviationsAreRejected() {
        // Single digit month/day or missing zero padding must not parse.
        assertNull(ScheduleSupport.parseScheduleTime("2026-8-3 5:00"));
        // ISO format with a T separator is not accepted either.
        assertNull(ScheduleSupport.parseScheduleTime("2026-08-30T12:00"));
    }

    @Test
    void computesRemainingSecondsFromACapturedTarget() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 30, 12, 0, 0, 0, ZoneId.of("UTC"));

        assertEquals(120, ScheduleSupport.remainingSeconds(
                LocalDateTime.of(2026, 8, 30, 12, 2), now));
        assertEquals(0, ScheduleSupport.remainingSeconds(
                LocalDateTime.of(2026, 8, 30, 11, 59), now));
        assertEquals(-1, ScheduleSupport.remainingSeconds(null, now));
    }

    @Test
    void formatsDurationsCompactly() {
        assertEquals("59s", ScheduleSupport.formatDuration(59));
        assertEquals("1m 0s", ScheduleSupport.formatDuration(60));
        assertEquals("5m 30s", ScheduleSupport.formatDuration(330));
        assertEquals("2h 5m", ScheduleSupport.formatDuration(7500));
        assertEquals("3d 4h", ScheduleSupport.formatDuration(273600));
    }

    @Test
    void onlyExplicitLockAndUnlockActionsAreSupported() {
        assertTrue(ScheduleSupport.isSupportedAction("lock"));
        assertTrue(ScheduleSupport.isSupportedAction("UNLOCK"));
        assertFalse(ScheduleSupport.isSupportedAction("lok"));
        assertFalse(ScheduleSupport.isSupportedAction(""));
        assertFalse(ScheduleSupport.isSupportedAction(null));
    }

    @Test
    void parsesPositiveIntegersOnly() {
        assertEquals(7, ScheduleSupport.tryParsePositiveInt("7"));
        assertEquals(7, ScheduleSupport.tryParsePositiveInt(" 7 "));
        assertNull(ScheduleSupport.tryParsePositiveInt("0"));
        assertNull(ScheduleSupport.tryParsePositiveInt("-2"));
        assertNull(ScheduleSupport.tryParsePositiveInt("2h"));
        assertNull(ScheduleSupport.tryParsePositiveInt(null));
    }

    @Test
    void parsesDurationTargetsFromTheCallerSReference() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0);

        assertEquals(LocalDateTime.of(2026, 11, 5, 13, 0),
                ScheduleSupport.parseDurationTarget("60m", now));
        assertEquals(LocalDateTime.of(2026, 11, 5, 14, 0),
                ScheduleSupport.parseDurationTarget("2H", now));
        assertEquals(LocalDateTime.of(2026, 11, 12, 12, 0),
                ScheduleSupport.parseDurationTarget("7d", now));
        assertEquals(LocalDateTime.of(2026, 11, 5, 13, 30),
                ScheduleSupport.parseDurationTarget("90m", now));
    }

    @Test
    void invalidDurationTargetsParseToNull() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0);

        assertNull(ScheduleSupport.parseDurationTarget("7", now));
        assertNull(ScheduleSupport.parseDurationTarget("0h", now));
        assertNull(ScheduleSupport.parseDurationTarget("2x", now));
        assertNull(ScheduleSupport.parseDurationTarget("h", now));
        assertNull(ScheduleSupport.parseDurationTarget("", now));
        assertNull(ScheduleSupport.parseDurationTarget(null, now));
        assertNull(ScheduleSupport.parseDurationTarget("60m", null));
    }
}
