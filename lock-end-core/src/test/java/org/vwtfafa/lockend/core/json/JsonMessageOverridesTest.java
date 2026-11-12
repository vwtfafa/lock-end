package org.vwtfafa.lockend.core.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import org.vwtfafa.lockend.core.messages.ModMessages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies message-override file seeding and load recovery for the mod
 * adapters.
 */
class JsonMessageOverridesTest {

    @TempDir
    Path tempDir;

    @Test
    void writeDefaultsIfMissingCreatesEmptySeed() throws IOException {
        ModMessages messages = new ModMessages();

        JsonMessageOverrides.writeDefaultsIfMissing(tempDir, messages, NOPLogger.NOP_LOGGER);

        Path file = tempDir.resolve(JsonMessageOverrides.fileName());
        assertTrue(Files.exists(file));
        assertEquals("{}", Files.readString(file).replaceAll("\\s+", ""));
    }

    @Test
    void writeDefaultsIfMissingLeavesExistingFileAlone() throws IOException {
        Path file = tempDir.resolve(JsonMessageOverrides.fileName());
        Files.writeString(file, "{\"status\":\"custom\"}");
        ModMessages messages = new ModMessages();

        JsonMessageOverrides.writeDefaultsIfMissing(tempDir, messages, NOPLogger.NOP_LOGGER);

        assertEquals("{\"status\":\"custom\"}", Files.readString(file));
    }

    @Test
    void loadRoundTripsWrittenOverrides() throws IOException {
        Files.writeString(
                tempDir.resolve(JsonMessageOverrides.fileName()),
                "{\"status\":\"custom status\",\"permission\":\"\"}");

        Map<String, String> loaded = JsonMessageOverrides.load(tempDir, NOPLogger.NOP_LOGGER);

        assertEquals("custom status", loaded.get("status"));
        assertFalse(loaded.containsKey("permission"));
    }

    @Test
    void legacyFullEnglishSeedLoadsAsNoOverrides() throws IOException {
        // Earlier versions wrote the complete English table into this file.
        Files.writeString(
                tempDir.resolve(JsonMessageOverrides.fileName()),
                new com.google.gson.Gson().toJson(new ModMessages().defaults()));

        assertTrue(JsonMessageOverrides.load(tempDir, NOPLogger.NOP_LOGGER).isEmpty());
    }

    @Test
    void customValuesSurviveAroundLegacyEnglishDefaults() throws IOException {
        Map<String, String> table = new java.util.HashMap<>(new ModMessages().defaults());
        table.put("status", "Eigener Status: %status%");
        Files.writeString(
                tempDir.resolve(JsonMessageOverrides.fileName()),
                new com.google.gson.Gson().toJson(table));

        Map<String, String> loaded = JsonMessageOverrides.load(tempDir, NOPLogger.NOP_LOGGER);

        assertEquals(Map.of("status", "Eigener Status: %status%"), loaded);
    }

    @Test
    void missingFileLoadsAsEmptyMap() {
        assertTrue(JsonMessageOverrides.load(tempDir, NOPLogger.NOP_LOGGER).isEmpty());
    }

    @Test
    void corruptFileLoadsAsEmptyMap() throws IOException {
        Files.writeString(tempDir.resolve(JsonMessageOverrides.fileName()), "{ not json ]]");

        assertTrue(JsonMessageOverrides.load(tempDir, NOPLogger.NOP_LOGGER).isEmpty());
    }
}
