package org.vwtfafa.apicons;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.vwtfafa.lockEnd.api.EndLockApi;

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
    /** 4000 ticks = 4 minutes. */
    private static final long SHUTDOWN_DELAY_TICKS = 4000L;

    private static final String NOT_AVAILABLE = "EndLock API not available - running without integration";

    @Override
    public void onEnable() {
        // Startup lookup only as a status probe; every later use resolves the
        // provider again (§6: registration is removed and re-registered on
        // every EndLock disable/enable cycle).
        RegisteredServiceProvider<EndLockApi> registration =
                getServer().getServicesManager().getRegistration(EndLockApi.class);
        if (registration == null) {
            getSLF4JLogger().info(NOT_AVAILABLE);
        } else {
            EndLockApi api = registration.getProvider();
            getSLF4JLogger().info("startup status: locked={}, reason={}",
                    api.isLocked(), api.getLockReason());
        }

        // The listener is registered even without the service: it proves that
        // it is also robust against an EndLock that appears later (re-enable).
        getServer().getPluginManager().registerEvents(new EndLockStateChangeLogger(this), this);

        // Same path on Paper and Folia: global region scheduler, never
        // Bukkit.getScheduler().
        getServer().getGlobalRegionScheduler().runAtFixedRate(
                this, task -> logStatusTick(), STATUS_TICK_INTERVAL_TICKS,
                STATUS_TICK_INTERVAL_TICKS);

        // Stop the prototype server without further input after 4 minutes.
        getServer().getGlobalRegionScheduler().runDelayed(
                this, task -> getServer().shutdown(), SHUTDOWN_DELAY_TICKS);
    }

    private void logStatusTick() {
        RegisteredServiceProvider<EndLockApi> registration =
                getServer().getServicesManager().getRegistration(EndLockApi.class);
        EndLockApi api = registration == null ? null : registration.getProvider();
        if (api == null) {
            getSLF4JLogger().info("status tick: " + NOT_AVAILABLE);
            return;
        }
        getSLF4JLogger().info("status tick: locked={}, reason={}",
                api.isLocked(), api.getLockReason());
    }
}
