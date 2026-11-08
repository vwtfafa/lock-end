package org.vwtfafa.lockEnd;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.vwtfafa.lockend.api.LockState;
import org.vwtfafa.lockend.api.LockStateSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the shared repository contract is fulfilled by the config
 * persistence: the boolean lock state is the master record, and missing data
 * never boots the End locked.
 */
class ConfigLockStateRepositoryTest {

    @Test
    void loadWithoutPersistedStateIsUnlocked() {
        var config = new YamlConfiguration();
        ConfigLockStateRepository repository = new ConfigLockStateRepository(() -> config);

        assertFalse(repository.load().isLocked());
    }

    @Test
    void saveAndLoadRoundtripThroughTheLiveConfig() {
        // The in-memory configuration is readable and writable on the same
        // live object; a reload replaces the instance, so the repository is
        // built around a supplier rather than a captured reference.
        var config = new YamlConfiguration();
        ConfigLockStateRepository repository = new ConfigLockStateRepository(() -> config);

        repository.save(new LockStateSnapshot(LockState.LOCKED, "", 0L));
        assertTrue(config.getBoolean("locked"));
        assertTrue(repository.load().isLocked());

        repository.save(new LockStateSnapshot(LockState.UNLOCKED, "", 0L));
        assertFalse(config.getBoolean("locked"));
        assertFalse(repository.load().isLocked());
    }

    @Test
    void missingDataMapsToUnlockedSnapshot() {
        var config = new YamlConfiguration();
        ConfigLockStateRepository repository = new ConfigLockStateRepository(() -> config);

        assertEquals(LockState.UNLOCKED, repository.load().state());
    }
}
