package org.vwtfafa.lockEnd;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;

public class MetricsManager {
    private final JavaPlugin plugin;
    private final Metrics metrics;

    // bStats Plugin ID for EndLock - https://bstats.org/what-is-my-plugin-id
    private static final int PLUGIN_ID = 32010;

    public MetricsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.metrics = new Metrics(plugin, PLUGIN_ID);
        initializeCharts();
    }

    /**
     * Initialisiert benutzerdefinierte Charts für Metriken
     */
    private void initializeCharts() {
        // Chart: Zusammenfassung der Plugin-Konfiguration
        metrics.addCustomChart(new SimplePie("lock_state", () -> {
            boolean locked = plugin.getConfig().getBoolean("locked", false);
            return locked ? "Locked" : "Unlocked";
        }));

        // Chart: Sprache
        metrics.addCustomChart(new SimplePie("language", () -> {
            String lang = plugin.getConfig().getString("language", "en");
            return lang != null ? lang.toUpperCase() : "Unknown";
        }));

        // Chart: Update Checker Status
        metrics.addCustomChart(new SimplePie("update_checker_enabled", () -> {
            boolean enabled = plugin.getConfig().getBoolean("update-checker.enabled", true);
            return enabled ? "Enabled" : "Disabled";
        }));

        // Chart: Metriken selbst aktiviert
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
