package org.vwtfafa.lockEnd;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;

public class MetricsManager {
    private final JavaPlugin plugin;
    private final Metrics metrics;

    private static final int PLUGIN_ID = 32010;

    public MetricsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.metrics = new Metrics(plugin, 32010);
        initializeCharts();
    }

    /**
     * Initialisiert benutzerdefinierte Charts für Metriken
     */
    private void initializeCharts() {
        metrics.addCustomChart(new SimplePie("lock_state", () -> {
            boolean locked = plugin.getConfig().getBoolean("locked", false);
            return locked ? "Locked" : "Unlocked";
        }));

        metrics.addCustomChart(new SimplePie("language", () -> {
            String lang = plugin.getConfig().getString("language", "en");
            return lang != null ? lang.toUpperCase() : "Unknown";
        }));

        metrics.addCustomChart(new SimplePie("update_checker_enabled", () -> {
            boolean enabled = plugin.getConfig().getBoolean("update-checker.enabled", true);
            return enabled ? "Enabled" : "Disabled";
        }));

        metrics.addCustomChart(new SimplePie("join_notifications_enabled", () -> {
            boolean enabled = plugin.getConfig().getBoolean("join-notifications.enabled", false);
            return enabled ? "Enabled" : "Disabled";
        }));

        metrics.addCustomChart(new SimplePie("scheduled_unlock_enabled", () -> {
            boolean enabled = plugin.getConfig().getBoolean("scheduled-unlock.enabled", false);
            return enabled ? "Enabled" : "Disabled";
        }));

        metrics.addCustomChart(new SimplePie("stats_enabled", () -> {
            boolean enabled = plugin.getConfig().getBoolean("stats.enabled", true);
            return enabled ? "Enabled" : "Disabled";
        }));

        metrics.addCustomChart(new SingleLineChart("lock_count", () -> {
            if (plugin instanceof LockEnd lockEndPlugin) {
                return lockEndPlugin.getLockCount();
            }
            return plugin.getConfig().getInt("stats.lock-count", 0);
        }));
        metrics.addCustomChart(new SingleLineChart("blocked_count", () -> {
            if (plugin instanceof LockEnd lockEndPlugin) {
                return lockEndPlugin.getBlockedCount();
            }
            return plugin.getConfig().getInt("stats.blocked-count", 0);
        }));
        metrics.addCustomChart(new SimplePie("metrics_enabled", () -> "Enabled"));

        plugin.getLogger().info("bStats Metriken aktiviert (ID: " + PLUGIN_ID + ")");
    }

    /**
     * Gibt die Metrics-Instanz zurück (für erweiterte Nutzung)
     */
    public Metrics getMetrics() {
        return metrics;
    }
}
