package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.vwtfafa.lockEnd.commands.ConfigValidatorCommand;
import org.vwtfafa.lockEnd.commands.EndLockCommand;
import org.vwtfafa.lockEnd.commands.LockHistoryCommand;
import org.vwtfafa.lockEnd.commands.UndoCommand;
import org.vwtfafa.lockEnd.util.AsyncLogger;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main plugin class: owns the lock state and wires messaging, scheduling,
 * evacuation, logging and commands together.
 */
public final class LockEnd extends JavaPlugin implements Listener {
    public static final DateTimeFormatter SCHEDULE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private boolean locked = false;
    private MessageService messages;
    private ScheduleManager schedules;
    private EvacuationService evacuation;
    private UpdateChecker updateChecker;
    private File logFile;
    private LockEndExpansion placeholderExpansion;
    private int lockCount = 0;
    private int blockedCount = 0;

    // v1.6 new features
    private LockReasonManager lockReasonManager;
    private GracePeriodTask gracePeriodTask;
    private WhitelistChecker whitelistChecker;
    private SoundEffectPlayer soundPlayer;
    private LockHistoryCommand historyCommand;
    private UndoCommand undoCommand;
    private ConfigValidatorCommand configValidatorCommand;
    private AsyncLogger asyncLogger;

    // Logging & Analytics
    private final Map<UUID, Long> lastAttemptTimes = new ConcurrentHashMap<>();
    private int rateLimitSeconds = 5;

    // Cached hot-path config values (refreshed on enable/reload)
    private boolean blockEndGateway;
    private List<String> endWorlds = List.of();
    private boolean logAttempts;
    private boolean statsEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        locked = getConfig().getBoolean("locked", false);
        lockCount = getConfig().getInt("stats.lock-count", 0);
        blockedCount = getConfig().getInt("stats.blocked-count", 0);

        messages = new MessageService(this);
        messages.loadFromConfig();

        lockReasonManager = new LockReasonManager(getConfig());
        gracePeriodTask = new GracePeriodTask(this);
        whitelistChecker = new WhitelistChecker(getConfig());
        soundPlayer = new SoundEffectPlayer(this);
        evacuation = new EvacuationService(this);
        historyCommand = new LockHistoryCommand(this);
        undoCommand = new UndoCommand(this);
        configValidatorCommand = new ConfigValidatorCommand(this);
        configureAsyncLogger();

        Bukkit.getPluginManager().registerEvents(this, this);

        // Register /endlock (aliases: /lock, /el) via Paper's Brigadier lifecycle API
        EndLockCommand endLockCommand = new EndLockCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        "endlock",
                        "Globally locks or unlocks access to the End dimension",
                        List.of("lock", "el"),
                        endLockCommand));

        rateLimitSeconds = getConfig().getInt("logging.rate-limit-seconds", 5);
        refreshCachedConfig();

        schedules = new ScheduleManager(this);
        schedules.loadFromConfig();
        if (schedules.hasAction()) {
            schedules.arm();
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null && getConfig().getBoolean("hooks.placeholderapi", true)) {
            placeholderExpansion = new LockEndExpansion(this);
            placeholderExpansion.register();
        }

        if (getConfig().getBoolean("update-checker.enabled", true)) {
            updateChecker = new UpdateChecker(this);
            updateChecker.checkForUpdates();
        }

        // Initialize bStats metrics. Opt-out is handled globally via the
        // bStats plugin config (plugins/bStats/config.json), not here.
        new MetricsManager(this);

        getLogger().info("EndLock v" + getPluginMeta().getVersion() + " enabled (Paper 26.2+)");
    }

    @Override
    public void onDisable() {
        getConfig().set("locked", locked);
        saveConfig();
        if (schedules != null) {
            schedules.cancelAll();
        }
        if (evacuation != null) {
            evacuation.cancel();
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
            schedules.handleUnlocked();
            evacuation.cancel();
            gracePeriodTask.cancel();
        }
        messages.broadcastLockState(locked, locked ? "broadcast-locked" : "broadcast-unlocked", actor);
        logAction(actor, action);
        historyCommand.addEntry(actor, action, previousState, action);
        if (locked) {
            startGracePeriodIfEnabled();
            evacuation.schedule();
        }
        return true;
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

        File logDir = new File(getDataFolder(), "logs");
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

    public String msg(String key) {
        return messages.msg(key);
    }

    public boolean hasMessage(String key) {
        return messages.hasMessage(key);
    }

    public Component messageComponent(String raw) {
        return messages.messageComponent(raw);
    }

    /**
     * Updates the in-memory stat counters. Blocked attempts are only written
     * back to disk when the plugin disables (or on the next explicit config
     * save) to avoid file I/O on every blocked access.
     */
    private void incrementStats(boolean lockAction) {
        if (!statsEnabled) {
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
     * Updates and persists the default lock reason.
     */
    public void setLockReason(String reason) {
        getConfig().set("lock-reason", reason);
        getConfig().set("lock-reasons.default", reason);
        saveConfig();
        lockReasonManager = new LockReasonManager(getConfig());
    }

    /**
     * Logs a test command invocation.
     */
    public void logTestAction(String actor) {
        logAction(actor, "TEST");
    }

    /**
     * Caches frequently read config values so event handlers avoid
     * repeated FileConfiguration lookups.
     */
    private void refreshCachedConfig() {
        blockEndGateway = getConfig().getBoolean("end.block-end-gateway", true);
        endWorlds = List.copyOf(getConfig().getStringList("end.worlds"));
        logAttempts = getConfig().getBoolean("logging.log-attempts", true);
        statsEnabled = getConfig().getBoolean("stats.enabled", true);
    }

    /**
     * Reloads configuration, language files and all dependent managers.
     */
    public void reloadPlugin() {
        reloadConfig();
        messages.loadFromConfig();
        lockReasonManager = new LockReasonManager(getConfig());
        whitelistChecker = new WhitelistChecker(getConfig());
        rateLimitSeconds = getConfig().getInt("logging.rate-limit-seconds", 5);
        refreshCachedConfig();
        configureAsyncLogger();
        soundPlayer.loadConfig();
        schedules.reload();

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

    public WhitelistChecker getWhitelistChecker() {
        return whitelistChecker;
    }

    // --- Schedule delegates ---

    public boolean hasScheduledAction() {
        return schedules.hasAction();
    }

    public LocalDateTime getScheduledTime() {
        return schedules.getTime();
    }

    public String getScheduledAction() {
        return schedules.getAction();
    }

    public long getScheduledRemainingSeconds() {
        return schedules.getRemainingSeconds();
    }

    public boolean isSchedulePaused() {
        return schedules.isPaused();
    }

    public void pauseSchedule() {
        schedules.pause();
    }

    public void resumeSchedule() {
        schedules.resume();
    }

    public void clearSchedule() {
        schedules.clear();
    }

    public String buildScheduleStatusMessage() {
        return schedules.buildStatusMessage();
    }

    public void scheduleUnlockInDays(int days) {
        schedules.scheduleUnlockInDays(days);
    }

    public void scheduleUnlockAt(LocalDateTime time) {
        schedules.scheduleUnlockAt(time);
    }

    public void scheduleLockInMinutes(int minutes) {
        schedules.scheduleLockInMinutes(minutes);
    }

    public void scheduleLockAt(LocalDateTime time) {
        schedules.scheduleLockAt(time);
    }

    public String getRemainingText() {
        if (!locked) {
            return "Unlocked";
        }
        return schedules.hasAction()
                ? schedules.getTime().format(SCHEDULE_FORMAT)
                : "Permanent";
    }

    /**
     * Escapes MiniMessage tags in user-provided input so it renders literally.
     */
    public String sanitize(String input) {
        return messages.sanitize(input);
    }

    private void sendJoinNotification(Player player) {
        player.sendMessage(messageComponent(msg("join-notification")));
    }

    public boolean isLocked() {
        return locked;
    }

    private void logAction(String player, String action) {
        if (!getConfig().getBoolean("logging.enabled", true)) {
            return;
        }
        String message = String.format("%s - Player: %s - Status: %s", action, player, locked ? "LOCKED" : "UNLOCKED");
        if (asyncLogger != null) {
            asyncLogger.log(message);
        }
    }

    /**
     * v1.6: Logs attempt with rate limiting and detailed info.
     */
    private void logAttempt(Player player, World sourceWorld, String method) {
        if (!logAttempts) {
            return;
        }
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Rate limit check
        Long lastAttempt = lastAttemptTimes.get(playerId);
        if (lastAttempt != null && now - lastAttempt < rateLimitSeconds * 1000L) {
            return;
        }
        lastAttemptTimes.put(playerId, now);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logMessage = String.format("[%s] Attempt - Player: %s - World: %s - Method: %s - Status: LOCKED",
                timestamp, player.getName(), sourceWorld.getName(), method);

        if (asyncLogger != null) {
            asyncLogger.log(logMessage);
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
        if (!endWorlds.isEmpty() && endWorlds.stream().noneMatch(name ->
                name.equalsIgnoreCase(event.getTo().getWorld().getName()))) {
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY
                && !blockEndGateway) {
            return;
        }
        // During the grace period the lock is not yet fully enforced.
        if (gracePeriodTask.isActive()) {
            player.sendMessage(messageComponent(msg("grace-period-active")));
            return;
        }
        if (whitelistChecker.canBypass(player, event.getTo().getWorld())) return;
        event.setCancelled(true);
        String reason = messages.sanitize(lockReasonManager.getReason("default"));
        player.sendMessage(messageComponent(msg("locked-reason").replace("%reason%", reason)));
        soundPlayer.playDenialSound(player);
        if (logAttempts) {
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
