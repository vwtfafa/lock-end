package org.vwtfafa.lockEnd;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the Bukkit-bound schedule persistence helper; the pure schedule
 * logic (parsing, durations, supported actions) moved to the shared core and
 * is covered by core tests.
 */
class ScheduleManagerLogicTest {

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
