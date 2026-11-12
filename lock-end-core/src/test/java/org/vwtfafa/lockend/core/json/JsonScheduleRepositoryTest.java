package org.vwtfafa.lockend.core.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.vwtfafa.lockend.core.PersistedSchedule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the shared JSON schedule persistence contract: missing or corrupt
 * data never schedules anything on boot, and schedule round-trips survive
 * process boundaries (file-based).
 */
class JsonScheduleRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadWithoutScheduleFileIsEmpty() {
        JsonScheduleRepository repository = new JsonScheduleRepository(tempDir);

        assertTrue(repository.load().isEmpty());
    }

    @Test
    void saveAndLoadRoundtripAcrossRepositoryInstances() throws IOException {
        // Second instance models a server restart: only the file persists.
        JsonScheduleRepository first = new JsonScheduleRepository(tempDir);
        JsonScheduleRepository second = new JsonScheduleRepository(tempDir);
        PersistedSchedule schedule = new PersistedSchedule(
                "unlock", LocalDateTime.of(2026, 11, 5, 13, 0), true);

        first.save(schedule);

        PersistedSchedule reloaded = second.load();
        assertEquals("unlock", reloaded.action());
        assertEquals(LocalDateTime.of(2026, 11, 5, 13, 0), reloaded.target());
        assertTrue(reloaded.paused());
    }

    @Test
    void persistedFileContainsTheWireFormat() throws IOException {
        JsonScheduleRepository repository = new JsonScheduleRepository(tempDir);
        repository.save(new PersistedSchedule("lock", LocalDateTime.of(2026, 11, 5, 13, 0), false));

        String json = Files.readString(tempDir.resolve("lock-end.schedule.json"));
        assertTrue(json.contains("\"action\": \"lock\""));
        assertTrue(json.contains("\"target\": \"2026-11-05 13:00\""));
    }

    @Test
    void corruptScheduleFileYieldsEmpty() throws IOException {
        Files.writeString(tempDir.resolve("lock-end.schedule.json"), "{ not json at all ]]");

        JsonScheduleRepository repository = new JsonScheduleRepository(tempDir);

        assertTrue(repository.load().isEmpty());
    }

    @Test
    void malformedTargetTimeYieldsEmpty() throws IOException {
        Files.writeString(tempDir.resolve("lock-end.schedule.json"), """
                {"action": "unlock", "target": "not a date", "paused": false}
                """);

        JsonScheduleRepository repository = new JsonScheduleRepository(tempDir);

        assertTrue(repository.load().isEmpty());
    }

    @Test
    void clearPersistsAnEmptySchedule() throws IOException {
        JsonScheduleRepository repository = new JsonScheduleRepository(tempDir);
        repository.save(new PersistedSchedule("lock", LocalDateTime.of(2026, 11, 5, 13, 0), false));

        repository.clear();

        assertTrue(new JsonScheduleRepository(tempDir).load().isEmpty());
        String json = Files.readString(tempDir.resolve("lock-end.schedule.json"));
        // Gson omits null fields, so a cleared schedule holds no target.
        assertFalse(json.contains("2026-11-05"));
    }
}
