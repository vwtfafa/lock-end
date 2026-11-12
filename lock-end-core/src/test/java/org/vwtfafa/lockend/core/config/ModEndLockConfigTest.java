package org.vwtfafa.lockend.core.config;

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
class ModEndLockConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void firstBootWritesDefaults() throws IOException {
        ModEndLockConfig config = ModEndLockConfig.load(tempDir);

        assertEquals("Maintenance", config.lockReason());
        assertTrue(config.endScope().configuredEnds().isEmpty());
        assertTrue(config.blockEntities());
        assertTrue(Files.exists(tempDir.resolve("lock-end.json")));
    }

    @Test
    void playerSectionsBootWithPaperDefaultsAndEvacuationOn() throws IOException {
        ModEndLockConfig config = ModEndLockConfig.load(tempDir);

        assertTrue(config.broadcastEnabled());
        assertTrue(config.broadcastUseActionbar());
        assertTrue(config.broadcastNotifyAll());
        assertFalse(config.soundEffectsEnabled());
        assertEquals("BLOCK_ANVIL_LAND", config.soundEffectSound());
        assertEquals(1.0f, config.soundEffectVolume());
        assertEquals(1.0f, config.soundEffectPitch());
        assertFalse(config.gracePeriodEnabled());
        assertEquals(10, config.gracePeriodDuration());
        // The mods default to evacuating so the previous always-evacuate
        // guard behavior survives the parity change.
        assertTrue(config.evacuationEnabled());
        assertEquals(10L, config.evacuationWarningSeconds());
        assertEquals("", config.evacuationTargetWorld());
        assertTrue(config.evacuationExcludeBypass());
        assertFalse(config.joinNotificationsEnabled());
        String json = Files.readString(tempDir.resolve("lock-end.json"));
        assertTrue(json.contains("\"broadcast\""));
        assertTrue(json.contains("\"soundEffects\""));
        assertTrue(json.contains("\"gracePeriod\""));
        assertTrue(json.contains("\"evacuation\""));
        assertTrue(json.contains("\"joinNotifications\""));
    }

    @Test
    void playerSectionEditsArePickedUp() throws IOException {
        Files.writeString(tempDir.resolve("lock-end.json"), """
                {
                  "lockReason": "Maintenance",
                  "broadcast": {"enabled": false, "useActionbar": false, "notifyAll": false},
                  "soundEffects": {"enabled": true, "sound": "minecraft:block.note_block.pling", "volume": 2.0, "pitch": 0.5},
                  "gracePeriod": {"enabled": true, "duration": 45},
                  "evacuation": {"enabled": false, "warningSeconds": 30, "targetWorld": "", "excludeBypass": true},
                  "joinNotifications": {"enabled": true}
                }
                """);

        ModEndLockConfig config = ModEndLockConfig.load(tempDir);

        assertFalse(config.broadcastEnabled());
        assertFalse(config.broadcastUseActionbar());
        assertFalse(config.broadcastNotifyAll());
        assertTrue(config.soundEffectsEnabled());
        assertEquals("minecraft:block.note_block.pling", config.soundEffectSound());
        assertEquals(2.0f, config.soundEffectVolume());
        assertEquals(0.5f, config.soundEffectPitch());
        assertTrue(config.gracePeriodEnabled());
        assertEquals(45, config.gracePeriodDuration());
        assertFalse(config.evacuationEnabled());
        assertEquals(30L, config.evacuationWarningSeconds());
        assertTrue(config.joinNotificationsEnabled());
    }

    @Test
    void countdownAndPreviewBootWithPaperDefaults() throws IOException {
        ModEndLockConfig config = ModEndLockConfig.load(tempDir);

        assertTrue(config.countdownEnabled());
        assertEquals(10L, config.countdownIntervalSeconds());
        assertEquals(300L, config.countdownStartBeforeSeconds());
        assertFalse(config.previewEnabled());
        assertEquals(30L, config.previewSeconds());
        String json = Files.readString(tempDir.resolve("lock-end.json"));
        assertTrue(json.contains("\"countdown\""));
        assertTrue(json.contains("\"preview\""));
    }

    @Test
    void countdownAndPreviewEditsArePickedUp() throws IOException {
        Files.writeString(tempDir.resolve("lock-end.json"), """
                {
                  "lockReason": "Maintenance",
                  "countdown": {"enabled": false, "interval": 30, "startBefore": 1200},
                  "preview": {"enabled": true, "seconds": 45}
                }
                """);

        ModEndLockConfig config = ModEndLockConfig.load(tempDir);

        assertFalse(config.countdownEnabled());
        assertEquals(30L, config.countdownIntervalSeconds());
        assertEquals(1200L, config.countdownStartBeforeSeconds());
        assertTrue(config.previewEnabled());
        assertEquals(45L, config.previewSeconds());
    }

    @Test
    void countdownAndPreviewInvalidValuesFallBackToDefaults() throws IOException {
        Files.writeString(tempDir.resolve("lock-end.json"), """
                {
                  "lockReason": "Maintenance",
                  "countdown": {"enabled": true, "interval": -3, "startBefore": null},
                  "preview": {"enabled": null, "seconds": 0}
                }
                """);

        ModEndLockConfig config = ModEndLockConfig.load(tempDir);

        assertEquals(10L, config.countdownIntervalSeconds());
        assertEquals(300L, config.countdownStartBeforeSeconds());
        assertFalse(config.previewEnabled());
        assertEquals(30L, config.previewSeconds());
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

        ModEndLockConfig reloaded = ModEndLockConfig.load(tempDir);

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
        ModEndLockConfig.load(tempDir);

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

        ModEndLockConfig config = ModEndLockConfig.load(tempDir);

        assertEquals("Maintenance", config.lockReason());
    }

    @Test
    void setLockReasonUpdatesLiveValueAndPersists() throws IOException {
        ModEndLockConfig config = ModEndLockConfig.load(tempDir);

        config.setLockReason("  Closed for maintenance  ");
        ModEndLockConfig reloaded = ModEndLockConfig.load(tempDir);

        assertEquals("Closed for maintenance", config.lockReason());
        assertEquals("Closed for maintenance", reloaded.lockReason());
    }

    @Test
    void setLockReasonRejectsBlankInput() throws IOException {
        ModEndLockConfig config = ModEndLockConfig.load(tempDir);

        assertThrows(IllegalArgumentException.class, () -> config.setLockReason("   "));
        assertThrows(IllegalArgumentException.class, () -> config.setLockReason(null));
    }

    @Test
    void clearReasonRestoresDefaultAndPersists() throws IOException {
        ModEndLockConfig config = ModEndLockConfig.load(tempDir);
        config.setLockReason("Event night");

        config.clearReason();
        ModEndLockConfig reloaded = ModEndLockConfig.load(tempDir);

        assertEquals("Maintenance", config.lockReason());
        assertEquals("Maintenance", reloaded.lockReason());
    }

    @Test
    void corruptFilesFallBackWithBackup() throws IOException {
        Path file = tempDir.resolve("lock-end.json");
        Files.writeString(file, "],[ not json");

        ModEndLockConfig config = ModEndLockConfig.load(tempDir);

        assertEquals("Maintenance", config.lockReason());
        assertTrue(Files.exists(tempDir.resolve("lock-end.json.corrupt")));
        assertNotNull(config.parseFailureMessage());
        assertTrue(config.parseFailureMessage().contains("lock-end.json"));
    }

    @Test
    void reloadPicksUpNewSettingsCleanly() throws IOException {
        ModEndLockConfig config = ModEndLockConfig.load(tempDir);
        config.setLockReason("Event night");

        Files.writeString(tempDir.resolve("lock-end.json"), fileContent("Server event"));
        ModEndLockConfig reloaded = config.reload();

        assertEquals("Server event", reloaded.lockReason());
        assertNull(reloaded.parseFailureMessage());
    }

    private static String fileContent(String reason) {
        return "{\"lockReason\": \"" + reason + "\"}";
    }
}
