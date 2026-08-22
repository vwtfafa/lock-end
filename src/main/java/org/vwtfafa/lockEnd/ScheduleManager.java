package org.vwtfafa.lockEnd;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Owns the scheduled lock/unlock action: persistence, countdown, preview
 * notifications and execution.
 */
public class ScheduleManager {
    private static final long SCHEDULE_RECHECK_TICKS = 20L * 60L;
    private final LockEnd plugin;
    private final PreviewNotificationManager previewManager;
    private LocalDateTime scheduledUnlockTime;
    private String scheduledAction = "unlock";
    private boolean schedulePaused;
    private BukkitTask checkTask;
    private BukkitTask countdownTask;

    public ScheduleManager(LockEnd plugin) {
        this.plugin = plugin;
        this.previewManager = new PreviewNotificationManager(plugin);
    }

    /**
     * Loads the persisted schedule from config without arming any tasks.
     */
    public void loadFromConfig() {
        scheduledUnlockTime = null;
        scheduledAction = plugin.getConfig().getString("scheduled-unlock.action", "unlock").toLowerCase(Locale.ROOT);
        if (!scheduledAction.equals("lock") && !scheduledAction.equals("unlock")) {
            scheduledAction = "unlock";
        }
        if (!plugin.getConfig().getBoolean("scheduled-unlock.enabled", false)) {
            return;
        }
        String persistedDate = plugin.getConfig().getString("scheduled-unlock.target-datetime", "");
        if (persistedDate != null && !persistedDate.isBlank()) {
            scheduledUnlockTime = parseScheduleTime(persistedDate);
        }
        if (scheduledUnlockTime == null) {
            String mode = plugin.getConfig().getString("scheduled-unlock.mode", "days");
            if ("datetime".equalsIgnoreCase(mode)) {
                scheduledUnlockTime = parseScheduleTime(plugin.getConfig().getString("scheduled-unlock.datetime", ""));
            } else {
                int days = plugin.getConfig().getInt("scheduled-unlock.days", 7);
                scheduledUnlockTime = LocalDateTime.now().plusDays(days);
                persistScheduledUnlockTime();
            }
        }
    }

    /**
     * Reloads the schedule after a config reload: cancels all tasks,
     * re-reads state and re-arms when the End is locked.
     */
    public void reload() {
        cancelAll();
        schedulePaused = plugin.getConfig().getBoolean("schedule.paused", false);
        loadFromConfig();
        if (plugin.isLocked() && scheduledUnlockTime != null) {
            arm();
        }
    }

    /**
     * Arms preview, countdown and due-check tasks for the pending action.
     */
    public void arm() {
        if (scheduledUnlockTime == null || schedulePaused) {
            return;
        }
        cancelCheck();
        cancelCountdown();
        if (scheduledAction.equals("lock")) {
            previewManager.schedulePreviewLock(scheduledUnlockTime);
        } else {
            previewManager.schedulePreviewUnlock(scheduledUnlockTime);
        }
        scheduleCountdown();
        scheduleDueCheck();
    }

    /**
     * Schedules an unlock in the given number of days and persists the schedule.
     */
    public void scheduleUnlockInDays(int days) {
        setPending(LocalDateTime.now().plusDays(days), "unlock");
        plugin.getConfig().set("scheduled-unlock.enabled", true);
        plugin.getConfig().set("scheduled-unlock.action", scheduledAction);
        plugin.getConfig().set("scheduled-unlock.mode", "days");
        plugin.getConfig().set("scheduled-unlock.days", days);
        persistScheduledUnlockTime();
        if (plugin.isLocked()) {
            arm();
        }
    }

    /**
     * Schedules an unlock at an absolute point in time and persists the schedule.
     */
    public void scheduleUnlockAt(LocalDateTime time) {
        setPending(time, "unlock");
        plugin.getConfig().set("scheduled-unlock.enabled", true);
        plugin.getConfig().set("scheduled-unlock.action", scheduledAction);
        plugin.getConfig().set("scheduled-unlock.mode", "datetime");
        plugin.getConfig().set("scheduled-unlock.datetime", time.format(LockEnd.SCHEDULE_FORMAT));
        persistScheduledUnlockTime();
        if (plugin.isLocked()) {
            arm();
        }
    }

    /**
     * Schedules a lock in the given number of minutes and persists the schedule.
     */
    public void scheduleLockInMinutes(int minutes) {
        setPending(LocalDateTime.now().plusMinutes(minutes), "lock");
        saveScheduledAction();
        arm();
    }

    /**
     * Schedules a lock at an absolute point in time and persists the schedule.
     */
    public void scheduleLockAt(LocalDateTime time) {
        setPending(time, "lock");
        saveScheduledAction();
        arm();
    }

    public void pause() {
        schedulePaused = true;
        plugin.getConfig().set("schedule.paused", true);
        plugin.saveConfig();
        cancelCheck();
        cancelCountdown();
        previewManager.cancelPreview("unlock");
        plugin.getLogger().info("Schedule paused by System");
    }

    public void resume() {
        schedulePaused = false;
        plugin.getConfig().set("schedule.paused", false);
        plugin.saveConfig();
        if (plugin.isLocked() && scheduledUnlockTime != null) {
            arm();
        }
        plugin.getLogger().info("Schedule resumed by System");
    }

    public boolean isPaused() {
        return schedulePaused;
    }

    public boolean hasAction() {
        return scheduledUnlockTime != null;
    }

    public LocalDateTime getTime() {
        return scheduledUnlockTime;
    }

    public String getAction() {
        return scheduledAction;
    }

    public long getRemainingSeconds() {
        if (scheduledUnlockTime == null) {
            return -1;
        }
        return Math.max(0, Duration.between(LocalDateTime.now(), scheduledUnlockTime).getSeconds());
    }

    /**
     * Clears the pending action and cancels every related task.
     */
    public void clear() {
        scheduledUnlockTime = null;
        scheduledAction = "unlock";
        plugin.getConfig().set("scheduled-unlock.enabled", false);
        plugin.getConfig().set("scheduled-unlock.action", scheduledAction);
        plugin.getConfig().set("scheduled-unlock.target-datetime", null);
        plugin.saveConfig();
        cancelAll();
    }

    /**
     * Cancels due-check task and unlock previews after a manual unlock.
     * The countdown keeps running so admins still see the pending action.
     */
    public void handleUnlocked() {
        cancelCheck();
        previewManager.cancelPreview("unlock");
    }

    /**
     * Cancels check/countdown tasks and all preview notifications.
     */
    public void cancelAll() {
        cancelCheck();
        cancelCountdown();
        previewManager.cancelAll();
    }

    public void cancelPreviews(String type) {
        previewManager.cancelPreview(type);
    }

    /**
     * Builds the localized schedule status line for commands and placeholders.
     */
    public String buildStatusMessage() {
        String target = scheduledUnlockTime == null ? plugin.msg("schedule-none") : scheduledUnlockTime.format(LockEnd.SCHEDULE_FORMAT);
        String remaining = scheduledUnlockTime == null ? "-" : formatDuration(getRemainingSeconds());
        return plugin.msg("schedule-status")
                .replace("%action%", scheduledUnlockTime == null ? "-" : scheduledAction)
                .replace("%target%", target)
                .replace("%remaining%", remaining)
                .replace("%paused%", String.valueOf(schedulePaused));
    }

    private void setPending(LocalDateTime time, String action) {
        scheduledUnlockTime = time;
        scheduledAction = action;
    }

    private LocalDateTime parseScheduleTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, LockEnd.SCHEDULE_FORMAT);
        } catch (Exception exception) {
            return null;
        }
    }

    private void persistScheduledUnlockTime() {
        if (scheduledUnlockTime != null) {
            plugin.getConfig().set("scheduled-unlock.target-datetime", scheduledUnlockTime.format(LockEnd.SCHEDULE_FORMAT));
            plugin.saveConfig();
        }
    }

    private void saveScheduledAction() {
        plugin.getConfig().set("scheduled-unlock.enabled", true);
        plugin.getConfig().set("scheduled-unlock.action", scheduledAction);
        plugin.getConfig().set("scheduled-unlock.target-datetime", scheduledUnlockTime.format(LockEnd.SCHEDULE_FORMAT));
        plugin.saveConfig();
    }

    private void scheduleDueCheck() {
        if (scheduledUnlockTime == null || schedulePaused) {
            return;
        }
        long remainingMillis = Duration.between(LocalDateTime.now(), scheduledUnlockTime).toMillis();
        if (remainingMillis <= 0) {
            executeDueAction();
            return;
        }

        long remainingTicks = Math.max(1L, (remainingMillis + 49L) / 50L);
        long delay = Math.min(remainingTicks, SCHEDULE_RECHECK_TICKS);
        checkTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            checkTask = null;
            scheduleDueCheck();
        }, delay);
    }

    private void executeDueAction() {
        boolean targetLocked = scheduledAction.equals("lock");
        if (plugin.isLocked() != targetLocked) {
            plugin.changeLockState(targetLocked, "System", "SCHEDULED_" + scheduledAction.toUpperCase(Locale.ROOT), false);
            plugin.getLogger().info("Scheduled " + scheduledAction + " executed.");
        }
        scheduledUnlockTime = null;
        plugin.getConfig().set("scheduled-unlock.enabled", false);
        plugin.getConfig().set("scheduled-unlock.target-datetime", null);
        plugin.saveConfig();
    }

    private void scheduleCountdown() {
        if (!plugin.getConfig().getBoolean("scheduled-unlock.countdown.enabled", false)) {
            return;
        }
        long startBefore = plugin.getConfig().getLong("scheduled-unlock.countdown.start-before", 300);
        long interval = Math.max(1, plugin.getConfig().getLong("scheduled-unlock.countdown.interval", 10));
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (scheduledUnlockTime == null || schedulePaused) {
                return;
            }
            long remaining = Duration.between(LocalDateTime.now(), scheduledUnlockTime).getSeconds();
            if (remaining < 0 || remaining > startBefore) {
                return;
            }
            String messageKey = scheduledAction.equals("lock") ? "countdown-lock-notification" : "countdown-notification";
            String message = plugin.msg(messageKey).replace("%time%", formatDuration(remaining));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp() || player.hasPermission("endlock.admin")) {
                    player.sendMessage(plugin.messageComponent(message));
                }
            }
        }, 0L, interval * 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void cancelCheck() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    /**
     * Formats a duration as a compact human readable string.
     */
    public String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + remainingSeconds + "s";
        return remainingSeconds + "s";
    }
}
