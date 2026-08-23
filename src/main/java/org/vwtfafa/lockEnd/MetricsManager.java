package org.vwtfafa.lockEnd;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Registers bStats metrics with live plugin state and config-based charts.
 */
public class MetricsManager {
    private static final int PLUGIN_ID = 32010;

    private final LockEnd plugin;
    private final Metrics metrics;

    public MetricsManager(LockEnd plugin) {
        this.plugin = plugin;
        this.metrics = new Metrics(plugin, PLUGIN_ID);
        initializeCharts();
        plugin.getLogger().info("bStats metrics enabled (ID: " + PLUGIN_ID + ")");
    }

    /**
     * Initializes custom charts for metrics
     */
    private void initializeCharts() {
        FileConfiguration config = plugin.getConfig();
        metrics.addCustomChart(new SimplePie("lock_state", () ->
                plugin.isLocked() ? "Locked" : "Unlocked"));

        metrics.addCustomChart(new SimplePie("language", () -> {
            String lang = config.getString("language", "en");
            return lang != null ? lang.toUpperCase() : "Unknown";
        }));

        metrics.addCustomChart(new SimplePie("update_checker_enabled", () ->
                chartEnabled(config, "update-checker.enabled", true)));
        metrics.addCustomChart(new SimplePie("join_notifications_enabled", () ->
                chartEnabled(config, "join-notifications.enabled", false)));
        metrics.addCustomChart(new SimplePie("scheduled_unlock_enabled", () ->
                chartEnabled(config, "scheduled-unlock.enabled", false)));
        metrics.addCustomChart(new SimplePie("stats_enabled", () ->
                chartEnabled(config, "stats.enabled", true)));

        metrics.addCustomChart(new SingleLineChart("lock_count", plugin::getLockCount));
        metrics.addCustomChart(new SingleLineChart("blocked_count", plugin::getBlockedCount));
    }

    private static String chartEnabled(FileConfiguration config, String path, boolean def) {
        return config.getBoolean(path, def) ? "Enabled" : "Disabled";
    }

    /**
     * Returns the Metrics instance (for advanced usage)
     */
    public Metrics getMetrics() {
        return metrics;
    }
}
