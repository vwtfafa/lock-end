package org.vwtfafa.lockEnd;

import dev.faststats.bukkit.BukkitContext;
import dev.faststats.data.Metric;

/**
 * Starts FastStats metrics when a project token is available in the environment.
 */
public final class FastStatsManager {
    private static final String TOKEN_ENVIRONMENT_VARIABLE = "FASTSTATS_TOKEN";

    private final BukkitContext context;

    public FastStatsManager(LockEnd plugin) {
        String token = System.getenv(TOKEN_ENVIRONMENT_VARIABLE);
        if (token == null || token.isBlank()) {
            plugin.getLogger().warning("FastStats metrics disabled: FASTSTATS_TOKEN is not set.");
            context = null;
            return;
        }

        context = new BukkitContext.Factory(plugin, token.trim())
                .metrics(factory -> factory
                        .addMetric(Metric.number("endlock_lock_count", () ->
                                plugin.isStatsEnabled() ? plugin.getLockCount() : null))
                        .addMetric(Metric.number("endlock_unlock_count", () ->
                                plugin.isStatsEnabled() ? plugin.getUnlockCount() : null))
                        .addMetric(Metric.number("endlock_blocked_attempt_count", () ->
                                plugin.isStatsEnabled() ? plugin.getBlockedCount() : null))
                        .create())
                .create();
        context.ready();
        plugin.getLogger().info("FastStats metrics enabled.");
    }

    public void shutdown() {
        if (context != null) {
            context.shutdown();
        }
    }
}
