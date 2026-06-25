package org.vwtfafa.lockEnd;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public final class StatsService {
    private final LockEnd plugin;
    private final File dataFile;
    private FileConfiguration data;

    private int lockCount;
    private int unlockCount;
    private int blockedCount;

    public StatsService(LockEnd plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    public void load() {
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        lockCount = data.getInt("statistics.lock-count", 0);
        unlockCount = data.getInt("statistics.unlock-count", 0);
        blockedCount = data.getInt("statistics.blocked-count", 0);
    }

    public void save() {
        data.set("statistics.lock-count", lockCount);
        data.set("statistics.unlock-count", unlockCount);
        data.set("statistics.blocked-count", blockedCount);
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save statistics: " + e.getMessage());
        }
    }

    public void incrementLocks() {
        lockCount++;
        save();
    }

    public void incrementUnlocks() {
        unlockCount++;
        save();
    }

    public void incrementBlocked() {
        blockedCount++;
        save();
    }

    public int getLockCount() {
        return lockCount;
    }

    public int getUnlockCount() {
        return unlockCount;
    }

    public int getBlockedCount() {
        return blockedCount;
    }
}
