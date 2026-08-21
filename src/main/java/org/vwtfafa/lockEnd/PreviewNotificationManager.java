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
    private static final long PREVIEW_RECHECK_TICKS = 20L * 60L;
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
        if (!plugin.getConfig().getBoolean("preview-notifications.enabled", false)) {
            return;
        }
        cancelPreview("lock");
        int previewSeconds = plugin.getConfig().getInt("preview-notifications.seconds", 30);

        schedulePreview("lock", lockTime, previewSeconds, () -> {
            if (plugin.isLocked()) {
                return;
            }
            String message = plugin.msg("preview-lock").replace("%seconds%", String.valueOf(previewSeconds));
            sendPreviewToAll(message);
        });
    }

    /**
     * Schedules a preview notification before unlock.
     * @param unlockTime The time when unlock will occur
     */
    public void schedulePreviewUnlock(LocalDateTime unlockTime) {
        if (!plugin.getConfig().getBoolean("preview-notifications.enabled", false)) {
            return;
        }
        cancelPreview("unlock");
        int previewSeconds = plugin.getConfig().getInt("preview-notifications.seconds", 30);

        schedulePreview("unlock", unlockTime, previewSeconds, () -> {
            if (!plugin.isLocked()) {
                return;
            }
            String message = plugin.msg("preview-unlock").replace("%seconds%", String.valueOf(previewSeconds));
            sendPreviewToAll(message);
        });
    }

    private void schedulePreview(String type, LocalDateTime targetTime, int previewSeconds, Runnable notification) {
        long remainingMillis = Duration.between(LocalDateTime.now(), targetTime).toMillis();
        long previewMillis = previewSeconds * 1000L;
        long delayMillis = Math.max(0L, remainingMillis - previewMillis);
        long delayTicks = Math.max(1L, (delayMillis + 49L) / 50L);
        delayTicks = Math.min(delayTicks, PREVIEW_RECHECK_TICKS);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            long remaining = Duration.between(LocalDateTime.now(), targetTime).toMillis();
            if (remaining > previewMillis) {
                schedulePreview(type, targetTime, previewSeconds, notification);
                return;
            }
            notification.run();
            previewTasks.remove(type);
        }, delayTicks);
        previewTasks.put(type, task);
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