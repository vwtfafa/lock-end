package org.vwtfafa.lockEnd;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.Map;

/**
 * Manages customizable lock reasons for the EndLock plugin.
 */
public class LockReasonManager {
    private final FileConfiguration config;
    private final String defaultReason;

    public LockReasonManager(FileConfiguration config) {
        this.config = config;
        this.defaultReason = config.getString("lock-reason.default", "Maintenance");
    }

    /**
     * Gets the lock reason for a given key.
     * @param key The reason key (e.g., "maintenance", "event")
     * @return The lock reason message
     */
    public String getReason(String key) {
        if (key == null || key.isEmpty()) {
            return getDefaultReason();
        }
        String reason = config.getString("lock-reasons." + key);
        return reason != null ? reason : getDefaultReason();
    }

    /**
     * Gets the default lock reason.
     * @return The default lock reason
     */
    public String getDefaultReason() {
        return defaultReason;
    }

    /**
     * Gets all configured lock reasons.
     * @return Map of reason keys to reason messages
     */
    public Map<String, Object> getReasons() {
        return config.getConfigurationSection("lock-reasons").getValues(false);
    }
}