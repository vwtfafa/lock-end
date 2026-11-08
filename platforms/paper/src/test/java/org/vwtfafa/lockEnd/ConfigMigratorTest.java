package org.vwtfafa.lockEnd;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the config migration from unversioned legacy files to the current
 * version, including preservation of user values.
 */
class ConfigMigratorTest {

    private static YamlConfiguration legacyConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("locked", true);
        config.set("language", "de");
        config.set("lock-reason", "Server-Event");
        config.set("end.block-return", false);
        config.set("actionbar.use-alt-char", false);
        config.set("join-notifications.show-remaining", true);
        config.set("whitelists.entities", List.of("ZOMBIE"));
        config.set("metrics.enabled", true);
        return config;
    }

    private static YamlConfiguration bundledDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("config-version", ConfigMigrator.CURRENT_VERSION);
        defaults.set("locked", false);
        defaults.set("end.block-end-gateway", true);
        defaults.set("end.block-entities", true);
        defaults.set("grace-period.enabled", false);
        defaults.set("history.max-entries", 1000);
        return defaults;
    }

    @Test
    void migratesLegacyConfigAndKeepsUserValues() {
        YamlConfiguration config = legacyConfig();

        assertTrue(ConfigMigrator.migrate(config, bundledDefaults()));

        // Dead options are gone...
        assertNull(config.get("end.block-return"));
        assertNull(config.get("actionbar"));
        assertNull(config.get("join-notifications.show-remaining"));
        assertNull(config.get("whitelists.entities"));
        assertNull(config.get("metrics"));

        // ...user values survive...
        assertEquals(true, config.get("locked"));
        assertEquals("de", config.get("language"));

        // ...the legacy lock reason moved into lock-reasons.default...
        assertEquals("Server-Event", config.getString("lock-reasons.default"));
        assertNull(config.get("lock-reason"));

        // ...new options are filled from the bundle...
        assertTrue(config.getBoolean("end.block-entities"));
        assertTrue(config.getBoolean("grace-period.enabled", true));
        assertEquals(1000, config.getInt("history.max-entries"));

        // ...and the version is bumped.
        assertEquals(ConfigMigrator.CURRENT_VERSION, config.getInt("config-version"));
    }

    @Test
    void secondRunIsANoOp() {
        YamlConfiguration config = legacyConfig();
        ConfigMigrator.migrate(config, bundledDefaults());

        assertFalse(ConfigMigrator.migrate(config, bundledDefaults()));
        assertEquals(true, config.get("locked"));
    }

    @Test
    void newerConfigsAreLeftUntouched() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("config-version", 99);
        config.set("custom", "value");

        assertFalse(ConfigMigrator.migrate(config, bundledDefaults()));
        assertEquals(99, config.getInt("config-version"));
    }

    @Test
    void missingBundleStillBumpsVersion() {
        YamlConfiguration config = legacyConfig();

        assertTrue(ConfigMigrator.migrate(config, null));
        assertEquals(ConfigMigrator.CURRENT_VERSION, config.getInt("config-version"));
        assertNull(config.get("metrics"));
    }
}
