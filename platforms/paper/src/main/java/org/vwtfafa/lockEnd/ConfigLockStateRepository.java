package org.vwtfafa.lockEnd;

import org.bukkit.configuration.file.FileConfiguration;
import org.vwtfafa.lockend.api.LockState;
import org.vwtfafa.lockend.api.LockStateSnapshot;
import org.vwtfafa.lockend.core.LockStateRepository;

import java.util.function.Supplier;

/**
 * Paper implementation of the shared {@link LockStateRepository} contract,
 * persisting the lock state through the plugin's config.yml.
 *
 * <p>The master record is the {@code locked} boolean; the snapshot's reason
 * and capture time are transport information and are ignored by this
 * implementation. Callers save the config explicitly at the points where the
 * existing persistence behavior requires a disk write (state changes and
 * disable), so this repository performs no I/O of its own.</p>
 *
 * <p>The config reference is fetched through a supplier because a reload
 * replaces the {@link FileConfiguration} instance.</p>
 */
final class ConfigLockStateRepository implements LockStateRepository {

    private final Supplier<FileConfiguration> configSupplier;

    /**
     * Creates the repository around a config accessor.
     *
     * @param configSupplier reads the current live configuration
     */
    ConfigLockStateRepository(Supplier<FileConfiguration> configSupplier) {
        this.configSupplier = configSupplier;
    }

    @Override
    public void save(LockStateSnapshot state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        configSupplier.get().set("locked", state.isLocked());
    }

    @Override
    public LockStateSnapshot load() {
        boolean locked = configSupplier.get().getBoolean("locked", false);
        return new LockStateSnapshot(LockState.fromLocked(locked), "", System.currentTimeMillis());
    }
}
