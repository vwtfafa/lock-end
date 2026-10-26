package org.vwtfafa.lockEnd;

import dev.faststats.bukkit.BukkitContext;
import dev.faststats.Metrics;

/**
 * Starts FastStats metrics when a project token is available in the environment.
 */
public final class FastStatsManager {
    private static final String TOKEN_ENVIRONMENT_VARIABLE = "71be256f6d91c1cc2e1cb18f506a3d44";

    private final BukkitContext context;

    public FastStatsManager(LockEnd plugin) {
        String token = System.getenv(TOKEN_ENVIRONMENT_VARIABLE);
        if (token == null || token.isBlank()) {
            plugin.getLogger().warning("FastStats metrics disabled: FASTSTATS_TOKEN is not set.");
            context = null;
            return;
        }

        context = new BukkitContext.Factory(plugin, token.trim())
                .metrics(Metrics.Factory::create)
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
