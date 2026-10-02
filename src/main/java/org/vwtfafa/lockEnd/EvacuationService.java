package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.List;

/**
 * Warns and moves players out of the End after a lock becomes active.
 */
public class EvacuationService {
    private final LockEnd plugin;
    private BukkitTask task;
    private volatile boolean shuttingDown;

    public EvacuationService(LockEnd plugin) {
        this.plugin = plugin;
    }

    /**
     * Warns all players inside the End and schedules their evacuation.
     */
    public void schedule() {
        if (!plugin.getConfig().getBoolean("evacuation.enabled", false)) {
            return;
        }
        cancel();
        long warningSeconds = Math.max(0, plugin.getConfig().getLong("evacuation.warning-seconds", 10));
        Component warning = plugin.message("evacuation-warning",
                Map.of("%seconds%", String.valueOf(warningSeconds)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null) {
                continue;
            }
            if (plugin.isGuardedEndWorld(player.getWorld())
                    && (!plugin.getConfig().getBoolean("evacuation.exclude-bypass", true)
                    || !plugin.getWhitelistChecker().canBypass(player, player.getWorld()))) {
                player.sendMessage(warning);
            }
        }
        task = Bukkit.getScheduler().runTaskLater(plugin, this::evacuate, warningSeconds * 20L);
    }

    /**
     * Cancels a pending evacuation.
     */
    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        shuttingDown = true;
        cancel();
    }

    private void evacuate() {
        task = null;
        if (!plugin.isLocked() || !plugin.getConfig().getBoolean("evacuation.enabled", false)) {
            return;
        }
        String configuredTarget = plugin.getConfig().getString("evacuation.target-world", "world");
        World targetWorld = null;
        if (configuredTarget != null && !configuredTarget.isBlank()) {
            targetWorld = resolveConfiguredWorld(configuredTarget);
        }
        if (targetWorld == null) {
            targetWorld = Bukkit.getWorlds().stream()
                    .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                    .findFirst()
                    .orElse(null);
        }
        if (targetWorld == null) {
            plugin.getLogger().warning("Could not evacuate End players: no target world is available.");
            return;
        }
        Location target = targetWorld.getSpawnLocation();
        Component completeMessage = plugin.message("evacuation-complete", Map.of());
        List<Player> players = List.copyOf(Bukkit.getOnlinePlayers());
        for (Player player : players) {
            if (player == null) {
                continue;
            }
            if (!plugin.isGuardedEndWorld(player.getWorld())
                    || (plugin.getConfig().getBoolean("evacuation.exclude-bypass", true)
                    && plugin.getWhitelistChecker().canBypass(player, player.getWorld()))) {
                continue;
            }
            player.teleportAsync(target).thenAccept(success -> {
                if (!success || shuttingDown) {
                    return;
                }
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!canNotifyCompletion(shuttingDown, plugin.isEnabled(), player.isOnline())) {
                            return;
                        }
                        player.sendMessage(completeMessage);
                        plugin.recordEvacuatedPlayer();
                    });
                } catch (IllegalPluginAccessException exception) {
                    if (!shuttingDown) {
                        plugin.getLogger().warning("Could not schedule evacuation completion: " + exception.getMessage());
                    }
                }
            });
        }
    }

    static boolean canNotifyCompletion(boolean shuttingDown, boolean pluginEnabled, boolean playerOnline) {
        return !shuttingDown && pluginEnabled && playerOnline;
    }

    private World resolveConfiguredWorld(String configuredTarget) {
        if (configuredTarget.contains(":")) {
            NamespacedKey key = NamespacedKey.fromString(configuredTarget);
            if (key != null) {
                World namespacedWorld = Bukkit.getWorld(key);
                if (namespacedWorld != null) {
                    return namespacedWorld;
                }
            }
        }
        return Bukkit.getWorld(configuredTarget);
    }
}
