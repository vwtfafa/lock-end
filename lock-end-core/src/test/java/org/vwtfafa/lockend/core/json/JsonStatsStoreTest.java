package org.vwtfafa.lockend.core.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies stats persistence: missing or corrupt files boot at zeros, and
 * counters round-trip across store instances.
 */
class JsonStatsStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadWithoutFileReturnsEmpty() {
        JsonStatsStore store = new JsonStatsStore(tempDir, NOPLogger.NOP_LOGGER);

        assertEquals(JsonStatsStore.StatsCounts.EMPTY, store.load());
    }

    @Test
    void saveAndLoadRoundtripAcrossInstances() {
        JsonStatsStore first = new JsonStatsStore(tempDir, NOPLogger.NOP_LOGGER);
        JsonStatsStore second = new JsonStatsStore(tempDir, NOPLogger.NOP_LOGGER);
        JsonStatsStore.StatsCounts counts = new JsonStatsStore.StatsCounts(1, 2, 3, 4);

        assertTrue(first.save(counts));
        assertEquals(counts, second.load());
    }

    @Test
    void corruptFileReturnsEmpty() throws IOException {
        Files.writeString(tempDir.resolve("lock-end.stats.json"), "{ not json ]]");

        JsonStatsStore store = new JsonStatsStore(tempDir, NOPLogger.NOP_LOGGER);

        assertEquals(JsonStatsStore.StatsCounts.EMPTY, store.load());
    }

    @Test
    void emptyJsonObjectReturnsZeros() throws IOException {
        Files.writeString(tempDir.resolve("lock-end.stats.json"), "{}");

        JsonStatsStore store = new JsonStatsStore(tempDir, NOPLogger.NOP_LOGGER);

        assertEquals(JsonStatsStore.StatsCounts.EMPTY, store.load());
    }
}
