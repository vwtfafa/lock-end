package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;

/**
 * Warns and moves players out of the End after a lock becomes active.
 */
public class EvacuationService {
    private final LockEnd plugin;
    private BukkitTask task;

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

    private void evacuate() {
        task = null;
        if (!plugin.isLocked() || !plugin.getConfig().getBoolean("evacuation.enabled", false)) {
            return;
        }
        World targetWorld = Bukkit.getWorld(plugin.getConfig().getString("evacuation.target-world", "world"));
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null) {
                continue;
            }
            if (!plugin.isGuardedEndWorld(player.getWorld())
                    || (plugin.getConfig().getBoolean("evacuation.exclude-bypass", true)
                    && plugin.getWhitelistChecker().canBypass(player, player.getWorld()))) {
                continue;
            }
            player.teleportAsync(target).thenAccept(success -> {
                if (success) {
                    // The future may complete off the main thread; FileConfiguration
                    // and most Bukkit state must only be touched on the main thread.
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(completeMessage);
                        plugin.recordEvacuatedPlayer();
                    });
                }
            });
        }
    }
}
