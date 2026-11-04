package org.vwtfafa.lockend.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        Files.writeString(tempDir.resolve("lock-end.json"), """
                {
                  "lockReason": "Closed for maintenance",
                  "playerWhitelist": ["Admin"],
                  "uuidWhitelist": ["11111111-1111-1111-1111-111111111111"],
                  "worldWhitelist": ["minecraft:the_end"]
                }
                """);

        FabricEndLockConfig reloaded = FabricEndLockConfig.load(tempDir);

        assertEquals("Closed for maintenance", reloaded.lockReason());
        assertEquals(List.of("Admin"), reloaded.bypassChecker().playerNames());
        assertEquals(List.of("11111111-1111-1111-1111-111111111111"),
                reloaded.bypassChecker().uuids());
        assertEquals(List.of("minecraft:the_end"), reloaded.bypassChecker().worlds());
    }

    @Test
    void firstBootPersistsEmptyBypassLists() throws IOException {
        FabricEndLockConfig.load(tempDir);

        String json = Files.readString(tempDir.resolve("lock-end.json"));

        assertTrue(json.contains("\"playerWhitelist\": []"));
        assertTrue(json.contains("\"uuidWhitelist\": []"));
        assertTrue(json.contains("\"worldWhitelist\": []"));
    }

    @Test
    void blankReasonFallsBackToDefault() throws IOException {
        Files.writeString(tempDir.resolve("lock-end.json"), fileContent("   "));

        FabricEndLockConfig config = FabricEndLockConfig.load(tempDir);

        assertEquals("Maintenance", config.lockReason());
    }

    @Test
    void setLockReasonUpdatesLiveValueAndPersists() throws IOException {
        FabricEndLockConfig config = FabricEndLockConfig.load(tempDir);

        config.setLockReason("  Closed for maintenance  ");
        FabricEndLockConfig reloaded = FabricEndLockConfig.load(tempDir);

        assertEquals("Closed for maintenance", config.lockReason());
        assertEquals("Closed for maintenance", reloaded.lockReason());
    }

    @Test
    void setLockReasonRejectsBlankInput() throws IOException {
        FabricEndLockConfig config = FabricEndLockConfig.load(tempDir);

        assertThrows(IllegalArgumentException.class, () -> config.setLockReason("   "));
        assertThrows(IllegalArgumentException.class, () -> config.setLockReason(null));
    }

    @Test
    void clearReasonRestoresDefaultAndPersists() throws IOException {
        FabricEndLockConfig config = FabricEndLockConfig.load(tempDir);
        config.setLockReason("Event night");

        config.clearReason();
        FabricEndLockConfig reloaded = FabricEndLockConfig.load(tempDir);

        assertEquals("Maintenance", config.lockReason());
        assertEquals("Maintenance", reloaded.lockReason());
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
