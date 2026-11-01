package org.vwtfafa.lockEnd;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages customizable lock reasons for the EndLock plugin.
 */
public class LockReasonManager {
    private static final String DEFAULT_REASON = "Maintenance";
    private final Map<String, Object> reasons;
    private final String defaultReason;

    public LockReasonManager(FileConfiguration config) {
        Map<String, Object> configuredReasons = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("lock-reasons");
        if (section != null) {
            for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
                if (entry.getValue() instanceof String reason) {
                    configuredReasons.put(entry.getKey(), reason);
                }
            }
        }
        this.reasons = Map.copyOf(configuredReasons);
        Object configuredDefault = reasons.get("default");
        this.defaultReason = configuredDefault instanceof String reason ? reason : DEFAULT_REASON;
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
        Object reason = reasons.get(key);
        return reason instanceof String configured ? configured : getDefaultReason();
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
        return reasons;
    }
}