package org.vwtfafa.lockEnd;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;

public class MetricsManager {
    private static final int PLUGIN_ID = 32010;

    private final JavaPlugin plugin;
    private final StatsService stats;

    public MetricsManager(JavaPlugin plugin, StatsService stats) {
        this.plugin = plugin;
        this.stats = stats;
        Metrics metrics = new Metrics(plugin, PLUGIN_ID);
        initializeCharts(metrics);
        plugin.getLogger().info("bStats metrics enabled (ID: " + PLUGIN_ID + ")");
    }

    private void initializeCharts(Metrics metrics) {
        metrics.addCustomChart(new SimplePie("lock_state", () ->
                plugin.getConfig().getBoolean("locked", false) ? "Locked" : "Unlocked"));

        metrics.addCustomChart(new SimplePie("language", () -> {
            String lang = plugin.getConfig().getString("language", "en");
            return lang != null ? lang.toUpperCase() : "Unknown";
        }));

        metrics.addCustomChart(new SimplePie("update_checker_enabled", () ->
                plugin.getConfig().getBoolean("update-checker.enabled", true) ? "Enabled" : "Disabled"));

        metrics.addCustomChart(new SingleLineChart("total_locks", stats::getLockCount));
        metrics.addCustomChart(new SingleLineChart("total_blocked", stats::getBlockedCount));
    }
}
