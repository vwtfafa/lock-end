package org.vwtfafa.lockEnd;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages preview notifications before automatic lock/unlock events.
 */
public class PreviewNotificationManager {
    private final LockEnd plugin;
    private final Map<String, BukkitTask> previewTasks = new HashMap<>();

    public PreviewNotificationManager(LockEnd plugin) {
        this.plugin = plugin;
    }

    /**
     * Schedules a preview notification before lock.
     * @param lockTime The time when lock will occur
     */
    public void schedulePreviewLock(LocalDateTime lockTime) {
        cancelPreview("lock");
        int previewSeconds = plugin.getConfig().getInt("preview-notifications.seconds", 30);

        long previewDelay = Math.max(0, Duration.between(LocalDateTime.now(), lockTime).getSeconds() - previewSeconds);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.isLocked()) {
                return; // Already locked
            }
            String message = plugin.msg("preview-lock").replace("%seconds%", String.valueOf(previewSeconds));
            sendPreviewToAll(message);
            previewTasks.remove("lock");
        }, previewDelay * 20L);

        previewTasks.put("lock", task);
    }

    /**
     * Schedules a preview notification before unlock.
     * @param unlockTime The time when unlock will occur
     */
    public void schedulePreviewUnlock(LocalDateTime unlockTime) {
        cancelPreview("unlock");
        int previewSeconds = plugin.getConfig().getInt("preview-notifications.seconds", 30);

        long previewDelay = Math.max(0, Duration.between(LocalDateTime.now(), unlockTime).getSeconds() - previewSeconds);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isLocked()) {
                return; // Already unlocked
            }
            String message = plugin.msg("preview-unlock").replace("%seconds%", String.valueOf(previewSeconds));
            sendPreviewToAll(message);
            previewTasks.remove("unlock");
        }, previewDelay * 20L);

        previewTasks.put("unlock", task);
    }

    /**
     * Cancels a specific preview task.
     * @param type The preview type ("lock" or "unlock")
     */
    public void cancelPreview(String type) {
        BukkitTask task = previewTasks.get(type);
        if (task != null) {
            task.cancel();
            previewTasks.remove(type);
        }
    }

    /**
     * Cancels all preview tasks.
     */
    public void cancelAll() {
        for (BukkitTask task : previewTasks.values()) {
            task.cancel();
        }
        previewTasks.clear();
    }

    /**
     * Sends a preview message to all online players.
     * @param message The message to send
     */
    private void sendPreviewToAll(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("endlock.admin") || player.isOp()) {
                player.sendMessage(message);
            }
        }
    }
}