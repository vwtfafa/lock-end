package org.vwtfafa.apicons;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Read-only EndLock 3.1 API consumer prototype.
 *
 * <p>Follows the consumer contract (docs/3.1-api-contract.md §6-§8):
 * the service provider is never cached and is resolved fresh for every query;
 * a missing EndLock service is a normal state, not an error. The prototype
 * performs no world or player mutations and uses no scheduler other than the
 * global region scheduler (Folia-safe).</p>
 */
public final class EndLockApiConsumerPlugin extends JavaPlugin {

    /** 1200 ticks = 60 seconds. */
    private static final long STATUS_TICK_INTERVAL_TICKS = 1200L;
    /** 6000 ticks = 5 minutes. */
    private static final long SHUTDOWN_DELAY_TICKS = 6000L;
    /** 1200 ticks = 1 minute after enable. */
    private static final long FIRST_COMMAND_DELAY_TICKS = 1200L;
    /** 200 ticks = 10 seconds between command steps. */
    private static final long COMMAND_STEP_TICKS = 200L;

    /** Console-driven sequence covering the command and undo change paths. */
    private static final String[] COMMAND_SEQUENCE = {
            "endlock lock",     // -> event action=LOCK
            "endlock lock",     // guard: already locked -> no event
            "endlock unlock",   // -> event action=UNLOCK
            "endlock undo",     // -> event action=UNDO
            "endlock status",   // read-only smoke check
    };

    private static final String NOT_AVAILABLE = "EndLock API not available - running without integration";

    private EndLockApiIntegration integration;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().isPluginEnabled("EndLock")) {
            // This class is isolated because it references EndLock API types.
            // Do not load it when EndLock is absent: compileOnly types are not
            // available to the runtime class loader in that case.
            integration = new EndLockApiIntegration(this);
            integration.enable();
        } else {
            getSLF4JLogger().info(NOT_AVAILABLE);
        }

        // Same path on Paper and Folia: global region scheduler, never
        // Bukkit.getScheduler().
        getServer().getGlobalRegionScheduler().runAtFixedRate(
                this, task -> logStatusTick(), STATUS_TICK_INTERVAL_TICKS,
                STATUS_TICK_INTERVAL_TICKS);

        // Console-driven path coverage (feasibility section 9, scenarios
        // 1/4/5): console commands execute on the global region thread on
        // both platforms, so dispatchCommand works identically on Folia.
        for (int i = 0; i < COMMAND_SEQUENCE.length; i++) {
            String command = COMMAND_SEQUENCE[i];
            long delay = FIRST_COMMAND_DELAY_TICKS + i * COMMAND_STEP_TICKS;
            getServer().getGlobalRegionScheduler().runDelayed(
                    this, task -> getServer().dispatchCommand(
                            getServer().getConsoleSender(), command), delay);
        }

        // Stop the prototype server without further input after 5 minutes.
        getServer().getGlobalRegionScheduler().runDelayed(
                this, task -> getServer().shutdown(), SHUTDOWN_DELAY_TICKS);
    }

    private void logStatusTick() {
        if (integration == null) {
            getSLF4JLogger().info("status tick: " + NOT_AVAILABLE);
            return;
        }
        integration.logStatusTick();
    }
}
