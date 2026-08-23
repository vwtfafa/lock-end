package org.vwtfafa.lockEnd;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void formatsDurationsCompactly() {
        assertEquals("59s", ScheduleManager.formatDuration(59));
        assertEquals("1m 0s", ScheduleManager.formatDuration(60));
        assertEquals("5m 30s", ScheduleManager.formatDuration(330));
        assertEquals("2h 5m", ScheduleManager.formatDuration(7500));
        assertEquals("3d 4h", ScheduleManager.formatDuration(273600));
    }
}
