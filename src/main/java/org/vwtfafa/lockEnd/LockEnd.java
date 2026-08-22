package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.vwtfafa.lockEnd.commands.ConfigValidatorCommand;
import org.vwtfafa.lockEnd.commands.EndLockCommand;
import org.vwtfafa.lockEnd.commands.LockHistoryCommand;
import org.vwtfafa.lockEnd.commands.UndoCommand;
import org.vwtfafa.lockEnd.util.AsyncLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class LockEnd extends JavaPlugin implements Listener {
    public static final DateTimeFormatter SCHEDULE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final long SCHEDULE_RECHECK_TICKS = 20L * 60L;
    private boolean locked = false;
    private FileConfiguration langConfig;
    private String langCode = "en";
    private UpdateChecker updateChecker;
    private File logDir;
    private File logFile;
    private MiniMessage miniMessage;
    private boolean miniMessageEnabled;
    private LocalDateTime scheduledUnlockTime;
    private String scheduledAction = "unlock";
    private LockEndExpansion placeholderExpansion;
    private int lockCount = 0;
    private int blockedCount = 0;

    // v1.6 new features
    private LockReasonManager lockReasonManager;
    private GracePeriodTask gracePeriodTask;
    private WhitelistChecker whitelistChecker;
    private PreviewNotificationManager previewManager;
    private SoundEffectPlayer soundPlayer;
    private LockHistoryCommand historyCommand;
    private UndoCommand undoCommand;
    private ConfigValidatorCommand configValidatorCommand;
    private AsyncLogger asyncLogger;

    // Logging & Analytics
    private final Map<UUID, Long> lastAttemptTimes = new java.util.concurrent.ConcurrentHashMap<>();
    private int rateLimitSeconds = 5;

    // Schedule pause/resume
    private boolean schedulePaused = false;
    private BukkitTask scheduledUnlockTask;
    private BukkitTask countdownTask;
    private BukkitTask evacuationTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        locked = getConfig().getBoolean("locked", false);
        lockCount = getConfig().getInt("stats.lock-count", 0);
        blockedCount = getConfig().getInt("stats.blocked-count", 0);
        langCode = getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        loadLanguage(langCode);
        // v1.6: Lock reason manager
        lockReasonManager = new LockReasonManager(getConfig());

        // v1.6: Grace period task
        gracePeriodTask = new GracePeriodTask(this);

        // v1.6: Whitelist checker
        whitelistChecker = new WhitelistChecker(getConfig());

        // v1.6: Preview notifications
        previewManager = new PreviewNotificationManager(this);

        // v1.6: Sound effects
        soundPlayer = new SoundEffectPlayer(this);

        // v1.6: Admin commands
        historyCommand = new LockHistoryCommand(this);
        undoCommand = new UndoCommand(this);
        configValidatorCommand = new ConfigValidatorCommand(this);

        configureAsyncLogger();

        miniMessageEnabled = getConfig().getBoolean("hooks.mini-message", true);
        if (miniMessageEnabled) {
            this.miniMessage = MiniMessage.miniMessage();
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        // Register /endlock (aliases: /lock, /el) via Paper's Brigadier lifecycle API
        EndLockCommand endLockCommand = new EndLockCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        "endlock",
                        "Globally locks or unlocks access to the End dimension",
                        List.of("lock", "el"),
                        endLockCommand));

        // v1.6: Rate limit config
        rateLimitSeconds = getConfig().getInt("logging.rate-limit-seconds", 5);
        schedulePaused = getConfig().getBoolean("schedule.paused", false);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null && getConfig().getBoolean("hooks.placeholderapi", true)) {
            placeholderExpansion = new LockEndExpansion(this);
            placeholderExpansion.register();
        }

        if (getConfig().getBoolean("update-checker.enabled", true)) {
            updateChecker = new UpdateChecker(this);
            updateChecker.checkForUpdates();
        }

        // Initialize bStats metrics
        if (getConfig().getBoolean("metrics.enabled", true)) {
            new MetricsManager(this);
        }

        loadScheduledUnlock();
        if (scheduledUnlockTime != null) {
            scheduleUnlock();
        }

        getLogger().info("EndLock v" + getPluginMeta().getVersion() + " enabled (Paper 26.2+)");
    }

    @Override
    public void onDisable() {
        getConfig().set("locked", locked);
        saveConfig();
        cancelScheduledUnlock();
        cancelCountdown();
        cancelEvacuation();
        if (previewManager != null) {
            previewManager.cancelAll();
        }
        if (gracePeriodTask != null) {
            gracePeriodTask.cancel();
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        if (asyncLogger != null) {
            asyncLogger.shutdown();
        }
        getLogger().info("EndLock disabled");
    }

    public boolean changeLockState(boolean newLocked, String actor, String action, boolean recordStats) {
        if (locked == newLocked) {
            return false;
        }

        boolean previousState = locked;
        locked = newLocked;
        historyCommand.recordPreviousState(previousState);
        if (recordStats && locked) {
            incrementStats(true);
        }
        getConfig().set("locked", locked);
        saveConfig();

        if (!locked) {
            cancelScheduledUnlock();
            previewManager.cancelPreview("unlock");
            cancelEvacuation();
            gracePeriodTask.cancel();
        }
        broadcastMessage(locked ? "broadcast-locked" : "broadcast-unlocked", actor);
        logAction(actor, action);
        historyCommand.addEntry(actor, action, previousState, action);
        if (locked) {
            scheduleEvacuation();
        }
        return true;
    }

    private void scheduleEvacuation() {
        if (!getConfig().getBoolean("evacuation.enabled", false)) {
            return;
        }
        cancelEvacuation();
        long warningSeconds = Math.max(0, getConfig().getLong("evacuation.warning-seconds", 10));
        String warning = msg("evacuation-warning");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() == World.Environment.THE_END
                    && (!getConfig().getBoolean("evacuation.exclude-bypass", true)
                    || !whitelistChecker.canBypass(player, player.getWorld()))) {
                player.sendMessage(messageComponent(warning.replace("%seconds%", String.valueOf(warningSeconds))));
            }
        }
        evacuationTask = Bukkit.getScheduler().runTaskLater(this, this::evacuateEndPlayers, warningSeconds * 20L);
    }

    private void evacuateEndPlayers() {
        evacuationTask = null;
        if (!locked || !getConfig().getBoolean("evacuation.enabled", false)) {
            return;
        }
        World targetWorld = Bukkit.getWorld(getConfig().getString("evacuation.target-world", "world"));
        if (targetWorld == null) {
            targetWorld = Bukkit.getWorlds().stream()
                    .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                    .findFirst()
                    .orElse(null);
        }
        if (targetWorld == null) {
            getLogger().warning("Could not evacuate End players: no target world is available.");
            return;
        }
        Location target = targetWorld.getSpawnLocation();
        Component completeMessage = messageComponent(msg("evacuation-complete"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() != World.Environment.THE_END
                    || (getConfig().getBoolean("evacuation.exclude-bypass", true)
                    && whitelistChecker.canBypass(player, player.getWorld()))) {
                continue;
            }
            player.teleportAsync(target).thenAccept(success -> {
                if (success) {
                    player.sendMessage(completeMessage);
                }
            });
        }
    }

    private void cancelEvacuation() {
        if (evacuationTask != null) {
            evacuationTask.cancel();
            evacuationTask = null;
        }
    }

    public boolean undoLastAction(String actor) {
        if (historyCommand.getLastPreviousState() == null) {
            return false;
        }
        boolean restored = historyCommand.getLastPreviousState();
        boolean changed = changeLockState(restored, actor, "UNDO", false);
        historyCommand.clearLastPreviousState();
        return changed;
    }

    private void configureAsyncLogger() {
        if (asyncLogger != null) {
            asyncLogger.shutdown();
            asyncLogger = null;
        }
        logFile = null;
        if (!getConfig().getBoolean("logging.enabled", true)) {
            return;
        }

        logDir = new File(getDataFolder(), "logs");
        if (!logDir.exists() && !logDir.mkdirs()) {
            getLogger().warning("Could not create logging directory: " + logDir);
            return;
        }
        String configuredLogFile = getConfig().getString("logging.log-file", "EndLock.log");
        Path logDirectory = logDir.toPath().toAbsolutePath().normalize();
        Path configuredPath = logDirectory.resolve(configuredLogFile).normalize();
        if (!configuredPath.startsWith(logDirectory)) {
            getLogger().warning("Invalid logging.log-file path; using EndLock.log instead.");
            configuredPath = logDirectory.resolve("EndLock.log");
        }
        logFile = configuredPath.toFile();
        asyncLogger = new AsyncLogger(getLogger());
        asyncLogger.initialize(logFile);
    }

    private void loadLanguage(String code) {
        String fileName = "messages_" + code + ".yml";
        File langFile = new File(getDataFolder(), fileName);
        if (!langFile.exists()) {
            try (InputStream in = getResource(fileName)) {
                if (in != null) {
                    langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                    return;
                }
            } catch (Exception ignored) {}
            try (InputStream in = getResource("messages_de.yml")) {
                if (in != null) {
                    langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                    return;
                }
            } catch (Exception ignored) {}
        } else {
            langConfig = YamlConfiguration.loadConfiguration(langFile);
        }
    }

    public String msg(String key) {
        if (langConfig == null) return key;
        return langConfig.getString(key, key);
    }

    public boolean hasMessage(String key) {
        return langConfig != null && langConfig.isString(key);
    }

    private Component miniMsg(String key) {
        return messageComponent(msg(key));
    }

    public Component messageComponent(String raw) {
        if (miniMessageEnabled && miniMessage != null) {
            return miniMessage.deserialize(raw);
        }
        return LegacyComponentSerializer.legacySection().deserialize(raw);
    }

    private void broadcastMessage(String key, String playerName) {
        if (!getConfig().getBoolean("broadcast.enabled", true)) {
            return;
        }

        boolean notifyAll = getConfig().getBoolean("broadcast.notify-all", true);
        boolean useActionbar = getConfig().getBoolean("broadcast.use-actionbar", true);
        String rawMessage = msg(key).replace("%player%", playerName);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (notifyAll || player.isOp() || player.hasPermission("endlock.admin")) {
                if (useActionbar) {
                    player.sendActionBar(locked ? miniMsg("actionbar-locked") : miniMsg("actionbar-unlocked"));
                } else {
                    player.sendMessage(messageComponent(rawMessage));
                }
            }
        }
    }

    /**
     * Updates the in-memory stat counters. Blocked attempts are only written
     * back to disk when the plugin disables (or on the next explicit config
     * save) to avoid file I/O on every blocked access.
     */
    private void incrementStats(boolean lockAction) {
        if (!getConfig().getBoolean("stats.enabled", true)) {
            return;
        }
        if (lockAction) {
            lockCount++;
            getConfig().set("stats.lock-count", lockCount);
        } else {
            blockedCount++;
            getConfig().set("stats.blocked-count", blockedCount);
        }
    }

    public int getLockCount() {
        return lockCount;
    }

    public int getBlockedCount() {
        return blockedCount;
    }

    public String getLockReason() {
        return lockReasonManager.getReason("default");
    }

    /**
     * Starts the grace period if it is enabled in the config.
     */
    public void startGracePeriodIfEnabled() {
        if (getConfig().getBoolean("grace-period.enabled", false)) {
            int duration = getConfig().getInt("grace-period.duration", 10);
            gracePeriodTask.startGracePeriod(duration);
        }
    }

    /**
     * Schedules an unlock in the given number of days and persists the schedule.
     */
    public void scheduleUnlockInDays(int days) {
        scheduledUnlockTime = LocalDateTime.now().plusDays(days);
        scheduledAction = "unlock";
        getConfig().set("scheduled-unlock.enabled", true);
        getConfig().set("scheduled-unlock.action", scheduledAction);
        getConfig().set("scheduled-unlock.mode", "days");
        getConfig().set("scheduled-unlock.days", days);
        getConfig().set("scheduled-unlock.target-datetime", scheduledUnlockTime.format(SCHEDULE_FORMAT));
        saveConfig();
        if (locked) {
            scheduleUnlock();
        }
    }

    /**
     * Schedules an unlock at an absolute point in time and persists the schedule.
     */
    public void scheduleUnlockAt(LocalDateTime time) {
        scheduledUnlockTime = time;
        scheduledAction = "unlock";
        getConfig().set("scheduled-unlock.enabled", true);
        getConfig().set("scheduled-unlock.action", scheduledAction);
        getConfig().set("scheduled-unlock.mode", "datetime");
        getConfig().set("scheduled-unlock.datetime", time.format(SCHEDULE_FORMAT));
        getConfig().set("scheduled-unlock.target-datetime", time.format(SCHEDULE_FORMAT));
        saveConfig();
        if (locked) {
            scheduleUnlock();
        }
    }

    /**
     * Schedules a lock in the given number of minutes and persists the schedule.
     */
    public void scheduleLockInMinutes(int minutes) {
        scheduledUnlockTime = LocalDateTime.now().plusMinutes(minutes);
        scheduledAction = "lock";
        saveScheduledAction();
        scheduleUnlock();
    }

    /**
     * Schedules a lock at an absolute point in time and persists the schedule.
     */
    public void scheduleLockAt(LocalDateTime time) {
        scheduledUnlockTime = time;
        scheduledAction = "lock";
        saveScheduledAction();
        scheduleUnlock();
    }

    /**
     * Updates and persists the default lock reason.
     */
    public void setLockReason(String reason) {
        getConfig().set("lock-reason", reason);
        getConfig().set("lock-reasons.default", reason);
        saveConfig();
        lockReasonManager = new LockReasonManager(getConfig());
    }

    /**
     * Builds the localized schedule status line for commands and placeholders.
     */
    public String buildScheduleStatusMessage() {
        String target = scheduledUnlockTime == null ? msg("schedule-none") : scheduledUnlockTime.format(SCHEDULE_FORMAT);
        String remaining = scheduledUnlockTime == null ? "-" : formatDuration(getScheduledRemainingSeconds());
        return msg("schedule-status")
                .replace("%action%", scheduledUnlockTime == null ? "-" : scheduledAction)
                .replace("%target%", target)
                .replace("%remaining%", remaining)
                .replace("%paused%", String.valueOf(schedulePaused));
    }

    /**
     * Logs a test command invocation.
     */
    public void logTestAction(String actor) {
        logAction(actor, "TEST");
    }

    /**
     * Reloads configuration, language files and all dependent managers.
     */
    public void reloadPlugin() {
        cancelScheduledUnlock();
        previewManager.cancelAll();
        cancelCountdown();
        reloadConfig();
        langCode = getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        loadLanguage(langCode);
        lockReasonManager = new LockReasonManager(getConfig());
        whitelistChecker = new WhitelistChecker(getConfig());
        rateLimitSeconds = getConfig().getInt("logging.rate-limit-seconds", 5);
        schedulePaused = getConfig().getBoolean("schedule.paused", false);
        configureAsyncLogger();
        miniMessageEnabled = getConfig().getBoolean("hooks.mini-message", true);
        miniMessage = miniMessageEnabled ? MiniMessage.miniMessage() : null;
        loadScheduledUnlock();
        soundPlayer.loadConfig();
        if (locked && scheduledUnlockTime != null) {
            scheduleUnlock();
        }
        boolean placeholderEnabled = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null
                && getConfig().getBoolean("hooks.placeholderapi", true);
        if (placeholderEnabled && placeholderExpansion == null) {
            placeholderExpansion = new LockEndExpansion(this);
            placeholderExpansion.register();
        } else if (!placeholderEnabled && placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (getConfig().getBoolean("update-checker.enabled", true)) {
            updateChecker = new UpdateChecker(this);
            updateChecker.checkForUpdates();
        }
    }

    public LockHistoryCommand getHistoryCommand() {
        return historyCommand;
    }

    public UndoCommand getUndoCommand() {
        return undoCommand;
    }

    public ConfigValidatorCommand getConfigValidatorCommand() {
        return configValidatorCommand;
    }

    public boolean hasScheduledAction() {
        return scheduledUnlockTime != null;
    }

    private void loadScheduledUnlock() {
        scheduledUnlockTime = null;
        scheduledAction = getConfig().getString("scheduled-unlock.action", "unlock").toLowerCase(Locale.ROOT);
        if (!scheduledAction.equals("lock") && !scheduledAction.equals("unlock")) {
            scheduledAction = "unlock";
        }
        if (!getConfig().getBoolean("scheduled-unlock.enabled", false)) {
            return;
        }
        String persistedDate = getConfig().getString("scheduled-unlock.target-datetime", "");
        if (persistedDate != null && !persistedDate.isBlank()) {
            scheduledUnlockTime = parseScheduleTime(persistedDate);
        }
        if (scheduledUnlockTime == null) {
            String mode = getConfig().getString("scheduled-unlock.mode", "days");
            if ("datetime".equalsIgnoreCase(mode)) {
                scheduledUnlockTime = parseScheduleTime(getConfig().getString("scheduled-unlock.datetime", ""));
            } else {
                int days = getConfig().getInt("scheduled-unlock.days", 7);
                scheduledUnlockTime = LocalDateTime.now().plusDays(days);
                persistScheduledUnlockTime();
            }
        }
    }

    private LocalDateTime parseScheduleTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, SCHEDULE_FORMAT);
        } catch (Exception exception) {
            return null;
        }
    }

    private void persistScheduledUnlockTime() {
        if (scheduledUnlockTime != null) {
            getConfig().set("scheduled-unlock.target-datetime", scheduledUnlockTime.format(SCHEDULE_FORMAT));
            saveConfig();
        }
    }

    private void saveScheduledAction() {
        getConfig().set("scheduled-unlock.enabled", true);
        getConfig().set("scheduled-unlock.action", scheduledAction);
        getConfig().set("scheduled-unlock.target-datetime", scheduledUnlockTime.format(SCHEDULE_FORMAT));
        saveConfig();
    }

    private void scheduleUnlock() {
        if (scheduledUnlockTime == null || schedulePaused) {
            return;
        }
        cancelScheduledUnlock();
        cancelCountdown();
        if (scheduledAction.equals("lock")) {
            previewManager.schedulePreviewLock(scheduledUnlockTime);
        } else {
            previewManager.schedulePreviewUnlock(scheduledUnlockTime);
        }
        scheduleCountdown();
        scheduleUnlockCheck();
    }

    private void scheduleUnlockCheck() {
        if (scheduledUnlockTime == null || schedulePaused) {
            return;
        }
        long remainingMillis = java.time.Duration.between(LocalDateTime.now(), scheduledUnlockTime).toMillis();
        if (remainingMillis <= 0) {
            boolean targetLocked = scheduledAction.equals("lock");
            if (locked != targetLocked) {
                changeLockState(targetLocked, "System", "SCHEDULED_" + scheduledAction.toUpperCase(Locale.ROOT), false);
                getLogger().info("Scheduled " + scheduledAction + " executed.");
            }
            scheduledUnlockTime = null;
            getConfig().set("scheduled-unlock.enabled", false);
            getConfig().set("scheduled-unlock.target-datetime", null);
            saveConfig();
            return;
        }

        long remainingTicks = Math.max(1L, (remainingMillis + 49L) / 50L);
        long delay = Math.min(remainingTicks, SCHEDULE_RECHECK_TICKS);
        scheduledUnlockTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            scheduledUnlockTask = null;
            scheduleUnlockCheck();
        }, delay);
    }

    private void cancelScheduledUnlock() {
        if (scheduledUnlockTask != null) {
            scheduledUnlockTask.cancel();
            scheduledUnlockTask = null;
        }
    }

    private void scheduleCountdown() {
        if (!getConfig().getBoolean("scheduled-unlock.countdown.enabled", false)) {
            return;
        }
        long startBefore = getConfig().getLong("scheduled-unlock.countdown.start-before", 300);
        long interval = Math.max(1, getConfig().getLong("scheduled-unlock.countdown.interval", 10));
        countdownTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (scheduledUnlockTime == null || schedulePaused) {
                return;
            }
            long remaining = java.time.Duration.between(LocalDateTime.now(), scheduledUnlockTime).getSeconds();
            if (remaining < 0 || remaining > startBefore) {
                return;
            }
            String messageKey = scheduledAction.equals("lock") ? "countdown-lock-notification" : "countdown-notification";
            String message = msg(messageKey).replace("%time%", formatDuration(remaining));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp() || player.hasPermission("endlock.admin")) {
                    player.sendMessage(messageComponent(message));
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

    public void pauseSchedule() {
        schedulePaused = true;
        getConfig().set("schedule.paused", true);
        saveConfig();
        cancelScheduledUnlock();
        cancelCountdown();
        previewManager.cancelPreview("unlock");
        getLogger().info("Schedule paused by " + "System");
    }

    public void resumeSchedule() {
        schedulePaused = false;
        getConfig().set("schedule.paused", false);
        saveConfig();
        if (locked && scheduledUnlockTime != null) {
            scheduleUnlock();
        }
        getLogger().info("Schedule resumed by " + "System");
    }

    public boolean isSchedulePaused() {
        return schedulePaused;
    }

    private void sendJoinNotification(Player player) {
        if (!getConfig().getBoolean("join-notifications.enabled", false) || !locked) {
            return;
        }
        player.sendMessage(messageComponent(msg("join-notification")));
    }

    public boolean isLocked() {
        return locked;
    }

    public String getRemainingText() {
        if (!locked) {
            return "Unlocked";
        }
        return scheduledUnlockTime != null ? scheduledUnlockTime.toString() : "Permanent";
    }

    public LocalDateTime getScheduledTime() {
        return scheduledUnlockTime;
    }

    public String getScheduledAction() {
        return scheduledAction;
    }

    public long getScheduledRemainingSeconds() {
        if (scheduledUnlockTime == null) {
            return -1;
        }
        return Math.max(0, java.time.Duration.between(LocalDateTime.now(), scheduledUnlockTime).getSeconds());
    }

    public void clearSchedule() {
        scheduledUnlockTime = null;
        scheduledAction = "unlock";
        getConfig().set("scheduled-unlock.enabled", false);
        getConfig().set("scheduled-unlock.action", scheduledAction);
        getConfig().set("scheduled-unlock.target-datetime", null);
        saveConfig();
        cancelScheduledUnlock();
        cancelCountdown();
        previewManager.cancelAll();
    }

    private void logAction(String player, String action) {
        if (!getConfig().getBoolean("logging.enabled", true)) {
            return;
        }
        String message = String.format("%s - Player: %s - Status: %s", action, player, locked ? "LOCKED" : "UNLOCKED");
        if (asyncLogger != null) {
            asyncLogger.log(message);
        } else {
            writeToLogFile(message);
        }
    }

    /**
     * v1.6: Logs attempt with rate limiting and detailed info.
     */
    private void logAttempt(Player player, World sourceWorld, String method) {
        if (!getConfig().getBoolean("logging.log-attempts", true)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Rate limit check
        if (lastAttemptTimes.containsKey(playerId)) {
            long lastAttempt = lastAttemptTimes.get(playerId);
            if (now - lastAttempt < rateLimitSeconds * 1000L) {
                return;
            }
        }
        lastAttemptTimes.put(playerId, now);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logMessage = String.format("[%s] Attempt - Player: %s - World: %s - Method: %s - Status: LOCKED\n",
                timestamp, player.getName(), sourceWorld.getName(), method);

        if (asyncLogger != null) {
            asyncLogger.log(logMessage.trim());
        } else {
            writeToLogFile(logMessage.trim());
        }
    }

    private void writeToLogFile(String message) {
        if (logFile == null) {
            return;
        }
        try {
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.append(message);
                writer.append("\n");
                writer.flush();
            }
        } catch (IOException e) {
            getLogger().warning("Error writing to log file: " + e.getMessage());
        }
    }

    // PlayerPortalEvent extends PlayerTeleportEvent, so this handler
    // also receives portal transitions.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        handleEndAccess(event, "TELEPORT_" + event.getCause().name());
    }

    private void handleEndAccess(PlayerTeleportEvent event, String method) {
        Player player = event.getPlayer();
        if (!locked || event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (event.getTo().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        if (!getConfig().getBoolean("end.block-return", false)
                && event.getFrom().getWorld().getEnvironment() == World.Environment.THE_END) {
            return;
        }
        List<String> configuredWorlds = getConfig().getStringList("end.worlds");
        if (!configuredWorlds.isEmpty() && configuredWorlds.stream().noneMatch(name ->
                name.equalsIgnoreCase(event.getTo().getWorld().getName()))) {
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY
                && !getConfig().getBoolean("end.block-end-gateway", true)) {
            return;
        }
        if (whitelistChecker.canBypass(player, event.getTo().getWorld())) return;
        event.setCancelled(true);
        String reason = lockReasonManager.getReason("default");
        player.sendMessage(messageComponent(msg("locked-reason").replace("%reason%", reason)));
        soundPlayer.playDenialSound(player);
        if (getConfig().getBoolean("logging.log-attempts", true)) {
            logAttempt(player, player.getWorld(), method);
        }
        incrementStats(false);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!locked || !getConfig().getBoolean("join-notifications.enabled", false)) {
            return;
        }
        sendJoinNotification(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastAttemptTimes.remove(event.getPlayer().getUniqueId());
    }
}
