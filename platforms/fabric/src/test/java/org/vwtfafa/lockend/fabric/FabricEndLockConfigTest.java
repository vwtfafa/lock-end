package org.vwtfafa.lockend.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the settings file behavior: defaults are written on first boot,
 * human edits are picked up and corrupt files fall back to defaults with a
 * backup instead of failing mod init.
 */
class FabricEndLockConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void firstBootWritesDefaults() throws IOException {
        FabricEndLockConfig config = FabricEndLockConfig.load(tempDir);

        assertEquals("Maintenance", config.lockReason());
        assertTrue(Files.exists(tempDir.resolve("lock-end.json")));
    }

    @Test
    void humanEditsArePickedUpOnReload() throws IOException {
        Files.writeString(tempDir.resolve("lock-end.json"), fileContent("Closed for maintenance"));

        FabricEndLockConfig reloaded = FabricEndLockConfig.load(tempDir);

        assertEquals("Closed for maintenance", reloaded.lockReason());
    }

    @Test
    void blankReasonFallsBackToDefault() throws IOException {
        Files.writeString(tempDir.resolve("lock-end.json"), fileContent("   "));

        FabricEndLockConfig config = FabricEndLockConfig.load(tempDir);

        assertEquals("Maintenance", config.lockReason());
    }

    @Test
    void corruptFilesFallBackWithBackup() throws IOException {
        Path file = tempDir.resolve("lock-end.json");
        Files.writeString(file, "],[ not json");

        FabricEndLockConfig config = FabricEndLockConfig.load(tempDir);

        assertEquals("Maintenance", config.lockReason());
        assertTrue(Files.exists(tempDir.resolve("lock-end.json.corrupt")));
    }

    private static String fileContent(String reason) {
        return "{\"lockReason\": \"" + reason + "\"}";
    }
}
