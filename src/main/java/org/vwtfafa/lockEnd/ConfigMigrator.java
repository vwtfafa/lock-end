package org.vwtfafa.lockEnd;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Migrates legacy configuration files to the current config version. The
 * steps operate on plain FileConfigurations so they are unit testable; the
 * plugin wrapper supplies the live config and the bundled defaults.
 */
public final class ConfigMigrator {

    /** Version of the bundled config.yml. */
    public static final int CURRENT_VERSION = 2;

    private static final List<String> DEAD_KEYS_SINCE_V2 = List.of(
            "end.block-return",
            "actionbar",
            "join-notifications.show-remaining",
            "whitelists.entities",
            "metrics");

    private ConfigMigrator() {}

    /**
     * Brings the given configuration up to {@link #CURRENT_VERSION} by removing
     * obsolete keys and merging missing options from the bundled defaults.
     *
     * @param config          the user's live configuration
     * @param bundledDefaults defaults of the currently shipped config.yml
     * @return true when the configuration was modified and needs saving
     */
    public static boolean migrate(FileConfiguration config, FileConfiguration bundledDefaults) {
        int version = config.getInt("config-version", 1);
        if (version >= CURRENT_VERSION) {
            // Already current, or written by a newer release: leave untouched.
            return false;
        }
        boolean changed = false;
        if (version < 2) {
            changed |= migrateTo2(config);
        }
        if (bundledDefaults != null) {
            config.setDefaults(bundledDefaults);
            config.options().copyDefaults(true);
            changed = true;
        }
        config.set("config-version", CURRENT_VERSION);
        return true;
    }

    private static boolean migrateTo2(FileConfiguration config) {
        boolean changed = false;
        for (String key : DEAD_KEYS_SINCE_V2) {
            if (config.isSet(key)) {
                config.set(key, null);
                changed = true;
            }
        }
        // The duplicated top-level lock-reason moved into lock-reasons.default;
        // a customized value must survive the cleanup.
        String legacyReason = config.getString("lock-reason");
        if (legacyReason != null && !legacyReason.isBlank()
                && config.getString("lock-reasons.default") == null) {
            config.set("lock-reasons.default", legacyReason);
        }
        if (config.isSet("lock-reason")) {
            config.set("lock-reason", null);
            changed = true;
        }
        return changed;
    }
}
