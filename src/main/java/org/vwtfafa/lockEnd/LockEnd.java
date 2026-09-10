package org.vwtfafa.lockEnd;

import com.destroystokyo.paper.event.entity.EntityTeleportEndGatewayEvent;
import net.kyori.adventure.text.Component;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
    /**
     * Strict schedule format: impossible dates like 2026-02-30 are rejected
     * instead of being silently rounded to the end of the month.
     */
    public static final DateTimeFormatter SCHEDULE_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    // Volatile because bStats chart suppliers read them from an async thread.
    private volatile boolean locked = false;
    private MessageService messages;
    private ScheduleManager schedules;
    private EvacuationService evacuation;
    private UpdateChecker updateChecker;
    private File logFile;
    private LockEndExpansion placeholderExpansion;
    private volatile int lockCount = 0;
    private volatile int unlockCount = 0;
    private volatile int blockedCount = 0;
    private volatile int evacuatedCount = 0;

    // Cached chart/report settings so async consumers never touch the config.
    private volatile String languageTag = "en";
    private volatile boolean updateCheckerEnabled = true;
    private volatile boolean joinNotificationsEnabled = false;

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
    private boolean blockEntities;
    private boolean blockEndGateway;
    private List<String> endWorlds = List.of();
    private volatile boolean logAttempts;
    private volatile boolean statsEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        locked = getConfig().getBoolean("locked", false);
        lockCount = getConfig().getInt("stats.lock-count", 0);
        unlockCount = getConfig().getInt("stats.unlock-count", 0);
        blockedCount = getConfig().getInt("stats.blocked-count", 0);
        evacuatedCount = getConfig().getInt("stats.evacuated-count", 0);

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

        // Register /endlock (aliases: /lock, /el) as a native Brigadier command
        // tree via Paper's lifecycle API; permissions live on the tree nodes.
        EndLockCommand endLockCommand = new EndLockCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        endLockCommand.buildNode(),
                        "Globally locks or unlocks access to the End dimension",
                        List.of("lock", "el")));

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

        getLogger().info("EndLock v" + getPluginMeta().getVersion() + " enabled (Paper 26.3+)");
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

    public boolean changeLockState(boolean newLocked, String actor, String action) {
        if (locked == newLocked) {
            return false;
        }

        boolean previousState = locked;
        locked = newLocked;
        historyCommand.recordPreviousState(previousState);
        recordStateChange(locked);
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
        boolean changed = changeLockState(restored, actor, "UNDO");
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
     * Renders a cached message template with %placeholder% values inserted
     * as literal text. The returned component is safe to reuse across players.
     */
    public Component message(String key, Map<String, String> placeholders) {
        return messages.message(key, placeholders);
    }

    /**
     * Updates the in-memory stat counters. Values are only written back to
     * disk when the plugin disables (or on the next explicit config save) to
     * avoid file I/O on every blocked access or state change.
     */
    private void recordStateChange(boolean lockedNow) {
        if (!statsEnabled) {
            return;
        }
        if (lockedNow) {
            lockCount++;
            getConfig().set("stats.lock-count", lockCount);
        } else {
            unlockCount++;
            getConfig().set("stats.unlock-count", unlockCount);
        }
    }

    private void recordBlockedAttempt() {
        if (!statsEnabled) {
            return;
        }
        blockedCount++;
        getConfig().set("stats.blocked-count", blockedCount);
    }

    /**
     * Counts a player successfully moved out of the End by the evacuation.
     */
    public void recordEvacuatedPlayer() {
        if (!statsEnabled) {
            return;
        }
        evacuatedCount++;
        getConfig().set("stats.evacuated-count", evacuatedCount);
    }

    public int getLockCount() {
        return lockCount;
    }

    public int getUnlockCount() {
        return unlockCount;
    }

    public int getBlockedCount() {
        return blockedCount;
    }

    public int getEvacuatedCount() {
        return evacuatedCount;
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public boolean isUpdateCheckerEnabled() {
        return updateCheckerEnabled;
    }

    public boolean isJoinNotificationsEnabled() {
        return joinNotificationsEnabled;
    }

    public boolean isStatsEnabled() {
        return statsEnabled;
    }

    public String getLockReason() {
        return lockReasonManager.getReason("default");
    }

    /**
     * Starts the grace period if it is enabled in the config.
     */
    public void startGracePeriodIfEnabled() {
        if (getConfig().getBoolean("grace-period.enabled", false)) {
            int duration = Math.max(0, getConfig().getInt("grace-period.duration", 10));
            gracePeriodTask.startGracePeriod(duration);
        }
    }

    /**
     * Updates and persists the default lock reason.
     */
    public void setLockReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
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
        blockEntities = getConfig().getBoolean("end.block-entities", true);
        endWorlds = List.copyOf(getConfig().getStringList("end.worlds"));
        logAttempts = getConfig().getBoolean("logging.log-attempts", true);
        statsEnabled = getConfig().getBoolean("stats.enabled", true);
        String rawLanguage = getConfig().getString("language", "en");
        languageTag = (rawLanguage == null || rawLanguage.isBlank()) ? "en" : rawLanguage;
        updateCheckerEnabled = getConfig().getBoolean("update-checker.enabled", true);
        joinNotificationsEnabled = getConfig().getBoolean("join-notifications.enabled", false);
    }

    /**
     * Upgrades an older configuration to the current version: obsolete keys
     * are removed and missing options are filled in from the bundled config.
     */
    private void migrateConfig() {
        FileConfiguration bundled = null;
        try (InputStream in = getResource("config.yml")) {
            if (in != null) {
                bundled = new YamlConfiguration();
                bundled.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException exception) {
            bundled = null;
            getLogger().warning("Could not read the bundled config.yml for migration: " + exception.getMessage());
        }
        if (ConfigMigrator.migrate(getConfig(), bundled)) {
            saveConfig();
            getLogger().info("Configuration migrated to version " + ConfigMigrator.CURRENT_VERSION + ".");
        }
    }

    /**
     * Reloads configuration, language files and all dependent managers.
     */
    public void reloadPlugin() {
        if (evacuation != null) {
            evacuation.cancel();
        }
        if (gracePeriodTask != null) {
            gracePeriodTask.cancel();
        }
        Boolean previousUndoState = historyCommand != null ? historyCommand.getLastPreviousState() : null;
        reloadConfig();
        migrateConfig();
        messages.loadFromConfig();
        lockReasonManager = new LockReasonManager(getConfig());
        whitelistChecker = new WhitelistChecker(getConfig());
        historyCommand = new LockHistoryCommand(this);
        if (previousUndoState != null) {
            historyCommand.recordPreviousState(previousUndoState);
        }
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

    /**
     * Whether the given world is an End world covered by the lock scope.
     */
    public boolean isGuardedEndWorld(World world) {
        return world.getEnvironment() == World.Environment.THE_END
                && (endWorlds.isEmpty()
                || endWorlds.stream().anyMatch(name -> name.equalsIgnoreCase(world.getName())));
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
        player.sendMessage(messages.message("join-notification", Map.of()));
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
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        EndAccessGate.AccessRequest request = new EndAccessGate.AccessRequest(
                event.getTo().getWorld().getEnvironment(),
                event.getTo().getWorld().getName());
        EndAccessGate.Verdict verdict = new EndAccessGate(
                locked, gracePeriodTask.isActive(), blockEndGateway, endWorlds)
                .checkPlayer(request, event.getCause(),
                        () -> whitelistChecker.canBypass(player, event.getTo().getWorld()));
        switch (verdict) {
            case ALLOWED -> {}
            case GRACE_PERIOD -> player.sendMessage(messages.message("grace-period-active", Map.of()));
            case BLOCKED -> {
                event.setCancelled(true);
                player.sendMessage(messages.message("locked-reason",
                        Map.of("%reason%", lockReasonManager.getReason("default"))));
                soundPlayer.playDenialSound(player);
                if (logAttempts) {
                    logAttempt(player, player.getWorld(), method);
                }
                recordBlockedAttempt();
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!locked || !joinNotificationsEnabled) {
            return;
        }
        sendJoinNotification(event.getPlayer());
    }

    // EntityTeleportEvent also receives EntityPortalEvent and
    // EntityTeleportEndGatewayEvent through inheritance, covering portals,
    // gateways and plugin-driven entity teleports in one handler.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (!blockEntities || !locked) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            return; // Player movement is handled by the player teleport handler.
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        EndAccessGate.AccessRequest request = new EndAccessGate.AccessRequest(
                to.getWorld().getEnvironment(),
                to.getWorld().getName());
        EndAccessGate.Verdict verdict = new EndAccessGate(
                locked, gracePeriodTask.isActive(), blockEndGateway, endWorlds)
                .checkEntity(request, event instanceof EntityTeleportEndGatewayEvent);
        if (verdict == EndAccessGate.Verdict.BLOCKED) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastAttemptTimes.remove(event.getPlayer().getUniqueId());
    }
}
