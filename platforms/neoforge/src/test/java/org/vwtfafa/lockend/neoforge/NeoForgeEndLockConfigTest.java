package org.vwtfafa.lockend.neoforge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the settings file behavior: defaults are written on first boot,
 * human edits are picked up and corrupt files fall back to defaults with a
 * backup instead of failing mod init.
 */
class NeoForgeEndLockConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void firstBootWritesDefaults() throws IOException {
        NeoForgeEndLockConfig config = NeoForgeEndLockConfig.load(tempDir);

        assertEquals("Maintenance", config.lockReason());
        assertTrue(config.endScope().configuredEnds().isEmpty());
        assertTrue(config.blockEntities());
        assertTrue(Files.exists(tempDir.resolve("lock-end.json")));
    }

    @Test
    void humanEditsArePickedUpOnReload() throws IOException {
        Files.writeString(tempDir.resolve("lock-end.json"), """
                {
                  "lockReason": "Closed for maintenance",
                  "endWorlds": ["minecraft:the_end", "custom:maintenance_end"],
                  "blockEntities": false,
                  "playerWhitelist": ["Admin"],
                  "uuidWhitelist": ["11111111-1111-1111-1111-111111111111"],
                  "worldWhitelist": ["minecraft:the_end"]
                }
                """);

        NeoForgeEndLockConfig reloaded = NeoForgeEndLockConfig.load(tempDir);

        assertEquals("Closed for maintenance", reloaded.lockReason());
        assertEquals(List.of("minecraft:the_end", "custom:maintenance_end"),
                reloaded.endScope().configuredEnds());
        assertFalse(reloaded.blockEntities());
        assertEquals(List.of("Admin"), reloaded.bypassChecker().playerNames());
        assertEquals(List.of("11111111-1111-1111-1111-111111111111"),
                reloaded.bypassChecker().uuids());
        assertEquals(List.of("minecraft:the_end"), reloaded.bypassChecker().worlds());
    }

    @Test
    void firstBootPersistsEmptyBypassLists() throws IOException {
        NeoForgeEndLockConfig.load(tempDir);

        String json = Files.readString(tempDir.resolve("lock-end.json"));

        assertTrue(json.contains("\"playerWhitelist\": []"));
        assertTrue(json.contains("\"uuidWhitelist\": []"));
        assertTrue(json.contains("\"worldWhitelist\": []"));
        assertTrue(json.contains("\"endWorlds\": []"));
        assertTrue(json.contains("\"blockEntities\": true"));
    }

    @Test
    void blankReasonFallsBackToDefault() throws IOException {
        Files.writeString(tempDir.resolve("lock-end.json"), fileContent("   "));

        NeoForgeEndLockConfig config = NeoForgeEndLockConfig.load(tempDir);

        assertEquals("Maintenance", config.lockReason());
    }

    @Test
    void setLockReasonUpdatesLiveValueAndPersists() throws IOException {
        NeoForgeEndLockConfig config = NeoForgeEndLockConfig.load(tempDir);

        config.setLockReason("  Closed for maintenance  ");
        NeoForgeEndLockConfig reloaded = NeoForgeEndLockConfig.load(tempDir);

        assertEquals("Closed for maintenance", config.lockReason());
        assertEquals("Closed for maintenance", reloaded.lockReason());
    }

    @Test
    void setLockReasonRejectsBlankInput() throws IOException {
        NeoForgeEndLockConfig config = NeoForgeEndLockConfig.load(tempDir);

        assertThrows(IllegalArgumentException.class, () -> config.setLockReason("   "));
        assertThrows(IllegalArgumentException.class, () -> config.setLockReason(null));
    }

    @Test
    void clearReasonRestoresDefaultAndPersists() throws IOException {
        NeoForgeEndLockConfig config = NeoForgeEndLockConfig.load(tempDir);
        config.setLockReason("Event night");

        config.clearReason();
        NeoForgeEndLockConfig reloaded = NeoForgeEndLockConfig.load(tempDir);

        assertEquals("Maintenance", config.lockReason());
        assertEquals("Maintenance", reloaded.lockReason());
    }

    @Test
    void corruptFilesFallBackWithBackup() throws IOException {
        Path file = tempDir.resolve("lock-end.json");
        Files.writeString(file, "],[ not json");

        NeoForgeEndLockConfig config = NeoForgeEndLockConfig.load(tempDir);

        assertEquals("Maintenance", config.lockReason());
        assertTrue(Files.exists(tempDir.resolve("lock-end.json.corrupt")));
        assertNotNull(config.parseFailureMessage());
        assertTrue(config.parseFailureMessage().contains("lock-end.json"));
    }

    @Test
    void reloadPicksUpNewSettingsCleanly() throws IOException {
        NeoForgeEndLockConfig config = NeoForgeEndLockConfig.load(tempDir);
        config.setLockReason("Event night");

        Files.writeString(tempDir.resolve("lock-end.json"), fileContent("Server event"));
        NeoForgeEndLockConfig reloaded = config.reload();

        assertEquals("Server event", reloaded.lockReason());
        assertNull(reloaded.parseFailureMessage());
    }

    private static String fileContent(String reason) {
        return "{\"lockReason\": \"" + reason + "\"}";
    }
}
