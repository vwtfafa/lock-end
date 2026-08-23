package org.vwtfafa.lockEnd;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

import java.util.Locale;

/**
 * Registers bStats metrics with live plugin state. Chart suppliers run on
 * the bStats async thread, so they only read volatile fields and never the
 * (main-thread bound) configuration.
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
        metrics.addCustomChart(new SimplePie("lock_state", () ->
                plugin.isLocked() ? "Locked" : "Unlocked"));

        metrics.addCustomChart(new SimplePie("language", () -> {
            String lang = plugin.getLanguageTag();
            return lang != null ? lang.toUpperCase(Locale.ROOT) : "Unknown";
        }));

        metrics.addCustomChart(new SimplePie("update_checker_enabled", () ->
                plugin.isUpdateCheckerEnabled() ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("join_notifications_enabled", () ->
                plugin.isJoinNotificationsEnabled() ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("scheduled_unlock_enabled", () ->
                plugin.hasScheduledAction() ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("stats_enabled", () ->
                plugin.isStatsEnabled() ? "Enabled" : "Disabled"));

        metrics.addCustomChart(new SingleLineChart("lock_count", plugin::getLockCount));
        metrics.addCustomChart(new SingleLineChart("blocked_count", plugin::getBlockedCount));
    }

    /**
     * Returns the Metrics instance (for advanced usage)
     */
    public Metrics getMetrics() {
        return metrics;
    }
}
