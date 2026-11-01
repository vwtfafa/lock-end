package org.vwtfafa.apicons;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.vwtfafa.lockEnd.api.EndLockApi;

/**
 * Isolates references to EndLock's compileOnly API from the consumer's main
 * plugin class so the consumer can still load when EndLock is absent.
 */
final class EndLockApiIntegration {

    private final JavaPlugin plugin;

    EndLockApiIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        RegisteredServiceProvider<EndLockApi> registration =
                plugin.getServer().getServicesManager().getRegistration(EndLockApi.class);
        if (registration == null) {
            plugin.getSLF4JLogger().info("EndLock is enabled but its API service is unavailable.");
        } else {
            EndLockApi api = registration.getProvider();
            plugin.getSLF4JLogger().info("startup status: locked={}, reason={}",
                    api.isLocked(), api.getLockReason());
        }

        plugin.getServer().getPluginManager().registerEvents(
                new EndLockStateChangeLogger(plugin), plugin);
    }

    void logStatusTick() {
        RegisteredServiceProvider<EndLockApi> registration =
                plugin.getServer().getServicesManager().getRegistration(EndLockApi.class);
        EndLockApi api = registration == null ? null : registration.getProvider();
        if (api == null) {
            plugin.getSLF4JLogger().info("status tick: EndLock API not available");
            return;
        }
        plugin.getSLF4JLogger().info("status tick: locked={}, reason={}",
                api.isLocked(), api.getLockReason());
    }
}
