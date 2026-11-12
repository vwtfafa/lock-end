package org.vwtfafa.lockend.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import dev.faststats.ErrorTracker;
import dev.faststats.Metrics;
import dev.faststats.fabric.FabricContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vwtfafa.lockend.api.LockAction;
import org.vwtfafa.lockend.api.LockEndApi;
import org.vwtfafa.lockend.core.GracePeriod;
import org.vwtfafa.lockend.core.LockScheduler;
import org.vwtfafa.lockend.core.LockStateManager;
import org.vwtfafa.lockend.core.PersistedSchedule;
import org.vwtfafa.lockend.core.ScheduleSupport;
import org.vwtfafa.lockend.core.UndoTracker;
import org.vwtfafa.lockend.core.api.SharedLockEndApiProvider;
import org.vwtfafa.lockend.core.audit.AttemptRateLimiter;
import org.vwtfafa.lockend.core.audit.AuditLogWriter;
import org.vwtfafa.lockend.core.config.ModEndLockConfig;
import org.vwtfafa.lockend.core.history.HistoryBuffer;
import org.vwtfafa.lockend.core.history.HistoryEntry;
import org.vwtfafa.lockend.core.json.JsonHistoryStore;
import org.vwtfafa.lockend.core.json.JsonLockStateRepository;
import org.vwtfafa.lockend.core.json.JsonMessageOverrides;
import org.vwtfafa.lockend.core.json.JsonScheduleRepository;
import org.vwtfafa.lockend.core.json.JsonStatsStore;
import org.vwtfafa.lockend.core.messages.ModMessages;
import org.vwtfafa.lockend.core.stats.LockStatistics;

import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fabric entrypoint of the Lock-End lock for the End dimension. Owns the
 * runtime lock state through the shared {@link LockStateManager}, persisted
 * via {@link JsonLockStateRepository}, and wires the verified Fabric API
 * hooks:
 *
 * <ul>
 *   <li>{@link ServerLifecycleEvents#SERVER_STARTED}: restart safety net -
 *       players already inside the End after a boot with a persisted active
 *       lock are evacuated once.</li>
 *   <li>{@link ServerLifecycleEvents#SERVER_STOPPING}: final state, history,
 *       statistics, schedule and audit-log persistence.</li>
 *   <li>{@link ServerEntityLevelChangeEvents#AFTER_PLAYER_CHANGE_LEVEL}:
 *       immediate bounce of a player who just arrived in the End while the
 *       lock is active; non-player entities are bounced the same way through
 *       {@link ServerEntityLevelChangeEvents#AFTER_ENTITY_CHANGE_LEVEL} while
 *       {@code blockEntities} is enabled. Fabric API exposes no cancellable
 *       dimension-change event, so the bounce happens after arrival (see the
 *       migration plan section 5.2: mixins only where necessary).</li>
 *   <li>{@link ServerTickEvents#END_SERVER_TICK}: throttled scan that
 *       catches players who reached the End by a route the arrival event
 *       could not cover, plus the roughly once-per-second poll of the shared
 *       {@link LockScheduler} together with the debounced history flush and
 *       statistics persistence.</li>
 *   <li>{@link CommandRegistrationCallback#EVENT}: the {@code /endlock}
 *       command tree, including undo, stats and the paginated history
 *       view.</li>
 * </ul>
 *
 * <p>All callbacks run on the main server thread; the runtime state is
 * managed by the shared core with the same volatile semantics as the Paper
 * plugin. Scheduled actions persist in
 * {@code config/lock-end.schedule.json} ({@link JsonScheduleRepository}),
 * countdown and preview behavior mirrors the Paper plugin: countdown lines
 * go to admins, the preview broadcasts to everyone. History lives in
 * {@code config/lock-end.history.json}, statistics counters in
 * {@code config/lock-end.stats.json}, message overrides in
 * {@code config/lock-end-messages.json}, and the audit log under
 * {@code config/logs/} - all mirroring the Paper plugin's behavior and
 * schemas. Lock state changes broadcast through the {@code broadcast}
 * settings, locking optionally starts a grace period, and the
 * {@link EndLockEvacuation} service moves End residents out with the
 * Paper-identical warning, preview and execute flow.</p>
 */
public class LockEndFabricMod implements ModInitializer {

    /** Logger shared by all Fabric adapter classes (package-visible). */
    static final Logger LOGGER = LoggerFactory.getLogger("lock-end");

    private static final String FASTSTATS_TOKEN_ENV = "LOCKEND_FASTSTATS_TOKEN";
    private static final String FASTSTATS_PUBLIC_TOKEN = "71be256f6d91c1cc2e1cb18f506a3d44";
    private static final int SCHEDULE_TICK_CADENCE = 20;
    private static final DateTimeFormatter ATTEMPT_LOG_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String FALLBACK_LOG_FILE = "EndLock.log";

    private static final ModMessages MESSAGES = new ModMessages();

    /**
     * Shared 4.x API surface published after init and cleared on server stop.
     * Consumers must call {@link #getApi()} only while a server is running.
     */
    private static volatile LockEndApi sharedApi;

    private Path configDirectory;
    private LockStateManager state;
    private JsonLockStateRepository repository;
    private ModEndLockConfig config;
    private EndLockGuard guard;
    private JsonScheduleRepository scheduleRepository;
    private LockScheduler scheduler;
    private FabricContext fastStatsContext;
    private volatile MinecraftServer liveServer;
    private int scheduleTickOffset;

    private LockStatistics stats;
    private JsonStatsStore statsStore;
    private long lastStatsRevision;
    private JsonHistoryStore historyStore;
    private HistoryBuffer history;
    private LockHistoryService historyService;
    private UndoTracker undoTracker;
    private AuditLogWriter audit;
    private AttemptRateLimiter attemptRateLimiter;
    private GracePeriod gracePeriod;
    private SoundEffects soundEffects;
    private EndLockEvacuation evacuation;

    @Override
    public void onInitialize() {
        configDirectory = FabricLoader.getInstance().getConfigDir();
        config = ModEndLockConfig.load(configDirectory);
        repository = new JsonLockStateRepository(configDirectory);

        JsonMessageOverrides.writeDefaultsIfMissing(configDirectory, MESSAGES, LOGGER);
        MESSAGES.setOverrides(JsonMessageOverrides.load(configDirectory, LOGGER));

        state = new LockStateManager(false).loadFromPersistence(repository);
        state.setReasonSupplier(config::lockReason);

        stats = new LockStatistics(config.statsEnabled());
        statsStore = new JsonStatsStore(configDirectory, LOGGER);
        JsonStatsStore.StatsCounts loadedStats = statsStore.load();
        stats.loadCounts(loadedStats.lockCount(), loadedStats.unlockCount(),
                loadedStats.blockedCount(), loadedStats.evacuatedCount());

        historyStore = new JsonHistoryStore(configDirectory, LOGGER);
        rebuildHistoryService();

        undoTracker = new UndoTracker();
        attemptRateLimiter = new AttemptRateLimiter(config.logRateLimitSeconds());
        audit = new AuditLogWriter(message -> LOGGER.warn(message));
        configureAuditLogging();

        gracePeriod = new GracePeriod(Clock.systemDefaultZone());
        soundEffects = new SoundEffects();
        soundEffects.loadFrom(config);
        evacuation = new EndLockEvacuation(this);
        guard = new EndLockGuard(state, config.bypassChecker(), config.endScope(),
                config.blockEntities(), gracePeriod, soundEffects);
        guard.setBlockedAttemptReporter(this::reportBlockedAttempt);
        scheduleRepository = new JsonScheduleRepository(configDirectory);
        scheduler = new LockScheduler(new SchedulerCallbacks(), Clock.systemDefaultZone());
        applyScheduleSettings();
        restorePersistedSchedule();

        registerLifecycleHooks();
        registerDimensionHooks();
        registerJoinHook();
        registerCommands();
        configureFastStats();
        publishSharedApi();

        LOGGER.info("EndLock (Fabric) initialized; End is {}", state.isLocked() ? "LOCKED" : "unlocked");
    }

    /**
     * Returns the shared 4.x {@link LockEndApi} for this Fabric installation.
     *
     * @return the live API provider
     * @throws IllegalStateException if called before init or after server stop
     */
    public static LockEndApi getApi() {
        LockEndApi current = sharedApi;
        if (current == null) {
            throw new IllegalStateException(
                    "EndLock Fabric API is not initialized (mod not ready or server stopped)");
        }
        return current;
    }

    private void publishSharedApi() {
        sharedApi = new SharedLockEndApiProvider(state, () -> config.lockReason());
    }

    private static void clearSharedApi() {
        sharedApi = null;
    }

    /**
     * @return the shared message registry and renderer for every Fabric
     *         adapter class
     */
    static ModMessages messages() {
        return MESSAGES;
    }

    /**
     * Rebuilds the history buffer from the store contents and republishes
     * it; the rotation runs inside {@link HistoryBuffer}'s constructor.
     */
    private void rebuildHistoryService() {
        List<HistoryEntry> stored = historyStore.load();
        history = new HistoryBuffer(historyStore, stored,
                config.historyMaxEntries(), config.historyRetentionDays());
        historyService = new LockHistoryService(configDirectory, history, historyStore);
    }

    /**
     * Configures the audit log file. The name is sandboxed into
     * {@code config/logs/}; a path-traversal attempt falls back to the
     * default name like the Paper plugin's config validator.
     */
    private void configureAuditLogging() {
        String rawName = config.logFile();
        if (rawName == null || rawName.isBlank() || rawName.contains("/")
                || rawName.contains("\\") || rawName.contains("..")) {
            LOGGER.warn("Unsupported audit log file name '{}'; falling back to {}.",
                    rawName, FALLBACK_LOG_FILE);
            rawName = FALLBACK_LOG_FILE;
        }
        Path logFile = configDirectory.resolve("logs").resolve(rawName);
        audit.initialize(config.loggingEnabled() ? logFile : null);
    }

    private void configureFastStats() {
        String token = System.getenv(FASTSTATS_TOKEN_ENV);
        if (token == null || token.isBlank()) {
            token = FASTSTATS_PUBLIC_TOKEN;
        }

        ErrorTracker errorTracker = ErrorTracker.contextAware();
        fastStatsContext = new FabricContext.Factory("lock-end", token)
                .errorTrackerService(errorTracker)
                .metrics(Metrics.Factory::create)
                .create();
    }

    /**
     * Installs the lifecycle hooks: the restarted-server evacuation once the
     * world is live, and the final persistence writes on shutdown.
     */
    private void registerLifecycleHooks() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            liveServer = server;
            guard.attachServer(server);
            guard.handleServerStarted();
            // Re-publish after a previous server stop cleared the static.
            if (sharedApi == null) {
                publishSharedApi();
            }
            // Boot safety net for a persisted active lock: schedule the
            // evacuation pass the same way the Paper plugin re-arms it on
            // a join into the locked End.
            if (state.isLocked() && config.evacuationEnabled() && !evacuation.isPending()) {
                evacuation.schedule();
            }
            if (fastStatsContext != null) {
                fastStatsContext.ready();
                LOGGER.info("FastStats metrics and error tracking enabled.");
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            persistLockState();
            persistRuntimeSchedule();
            history.flush();
            saveStats();
            evacuation.shutdown();
            audit.shutdown();
            if (fastStatsContext != null) {
                fastStatsContext.shutdown();
            }
            liveServer = null;
            clearSharedApi();
        });
    }

    /**
     * Installs the dimension restriction hooks covering all verified entry
     * routes into the End.
     */
    private void registerDimensionHooks() {
        // Immediate bounce after arrival: Fabric API exposes no cancellable
        // dimension-change event, so an arrival while locked is reverted by
        // teleporting the player back out.
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
                (player, origin, destination) ->
                        guard.onPlayerLevelChange(player, origin, destination));
        // Non-player entity arrivals are bounced the same way while
        // blockEntities is enabled.
        ServerEntityLevelChangeEvents.AFTER_ENTITY_CHANGE_LEVEL.register(guard::onEntityLevelChange);
        // Throttled scan as the safety net for players who reached the End
        // by a route the arrival events could not cover, plus the scheduler
        // poll at a one-second cadence with the debounced persistence pass.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            guard.tick(server);
            scheduleTickOffset = (scheduleTickOffset + 1) % SCHEDULE_TICK_CADENCE;
            if (scheduleTickOffset == 0 && liveServer != null) {
                scheduler.tick(state.isLocked());
                history.flushIfDirty();
                evacuation.tickSecond();
                saveStatsIfChanged();
            }
        });
    }

    /**
     * Installs the join notification hook mirroring the Paper plugin's
     * {@code onPlayerJoin}: re-arms the waiting evacuation pass when a
     * player joins a locked guarded End, and sends the locked-End notice to
     * every joining player while the notification setting is enabled.
     */
    private void registerJoinHook() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (state.isLocked() && config.evacuationEnabled()
                    && guard.isGuarded(player.level()) && !evacuation.isPending()) {
                evacuation.schedule();
            }
            if (state.isLocked() && config.joinNotificationsEnabled()) {
                player.sendSystemMessage(Component.literal(messages().text("join-notification")));
            }
        });
    }

    /**
     * Installs the {@code /endlock} Brigadier command tree through the
     * verified Fabric command API v2 callback.
     */
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
                new LockEndCommands(state, this)
                        .registerAll(dispatcher, buildContext, selection));
    }

    /**
     * @return {@code true} while the End lock is active
     */
    boolean isLocked() {
        return state.isLocked();
    }

    /**
     * @return the shared runtime lock state of this Fabric installation
     */
    public LockStateManager getState() {
        return state;
    }

    /**
     * @return the operational statistics counters
     */
    LockStatistics statistics() {
        return stats;
    }

    /**
     * @return the history view (pagination, filters, exports)
     */
    LockHistoryService historyService() {
        return historyService;
    }

    /**
     * @return the bounded history buffer (evacuation outcomes are recorded
     *         here)
     */
    HistoryBuffer history() {
        return history;
    }

    /**
     * @return the running server, or {@code null} before startup / after
     *         shutdown
     */
    MinecraftServer liveServer() {
        return liveServer;
    }

    /**
     * @return {@code true} if the level is inside the guarded End scope
     */
    boolean isGuarded(Level level) {
        return guard.isGuarded(level);
    }

    /**
     * @return the evacuation service (warnings, preview/execute command)
     */
    EndLockEvacuation evacuation() {
        return evacuation;
    }

    /**
     * Central state-change execution mirroring the Paper plugin's
     * {@code changeLockState}: records the undo target, statistics, audit
     * log line, history entry and persistence, broadcasts the new state,
     * snapshots End residents, then starts (or cancels) the grace period
     * and evacuation services.
     *
     * @param lock       the target state
     * @param action     the trigger identifier for the shared API state
     * @param actionName the action string recorded in history and audit log
     * @param actor      display name of the acting identity
     * @param actorUuid  UUID of the acting player, or {@code null}
     * @return {@code true} when the state changed
     */
    boolean changeLockState(boolean lock, LockAction action, String actionName,
                            String actor, UUID actorUuid) {
        boolean previousState = state.isLocked();
        if (previousState == lock) {
            return false;
        }
        state.updateLocked(lock, action, actor, actorUuid);
        undoTracker.recordPreviousState(previousState);
        stats.recordStateChange(lock);
        saveStatsIfChanged();
        persistLockState();
        broadcastLockState(lock, actor);
        logAction(actionName, actor);
        history.addEntry(actor, actorUuid, actionName, actionName, "SUCCESS",
                null, previousState, lock);
        if (lock) {
            guard.captureResidents();
            startGracePeriodIfEnabled();
            evacuation.schedule();
        } else {
            guard.clearResidents();
            gracePeriod.cancel();
            evacuation.cancel();
        }
        return true;
    }

    private void persistLockState() {
        try {
            repository.save(state.getState());
        } catch (UncheckedIOException exception) {
            LOGGER.warn("Could not persist the Lock-End lock state; it may not survive a restart.", exception);
        }
    }

    /**
     * Broadcasts the lock state change mirroring the Paper plugin's
     * {@code MessageService.broadcastLockState}: the action bar or chat
     * variants, admins only when {@code notify-all} is off.
     *
     * @param locked     the new state
     * @param playerName acting identity for the {@code %player%} token
     */
    private void broadcastLockState(boolean locked, String playerName) {
        MinecraftServer server = liveServer;
        if (!config.broadcastEnabled() || server == null) {
            return;
        }
        boolean notifyAll = config.broadcastNotifyAll();
        boolean useActionbar = config.broadcastUseActionbar();
        String componentText = useActionbar
                ? messages().text(locked ? "actionbar-locked" : "actionbar-unlocked")
                : messages().text(locked ? "broadcast-locked" : "broadcast-unlocked",
                        Map.of("%player%", playerName));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!notifyAll && !player.permissions().hasPermission(Permissions.COMMANDS_ADMIN)) {
                continue;
            }
            if (useActionbar) {
                player.sendOverlayMessage(Component.literal(componentText));
            } else {
                player.sendSystemMessage(Component.literal(componentText));
            }
        }
    }

    /**
     * Starts the grace period when the config enables it (called right after
     * a lock becomes active).
     */
    private void startGracePeriodIfEnabled() {
        if (config.gracePeriodEnabled()) {
            gracePeriod.start(Math.max(0, config.gracePeriodDuration()));
            LOGGER.info("Grace period started for {} seconds.", config.gracePeriodDuration());
        }
    }

    /**
     * Re-applies the recorded pre-change state, mirroring the Paper plugin's
     * {@code undoLastAction}.
     *
     * @param actor     display name of the acting identity
     * @param actorUuid UUID of the acting player, or {@code null}
     * @return {@code true} when a pending undo existed and was executed
     */
    boolean undoLastAction(String actor, UUID actorUuid) {
        Boolean previousState = undoTracker.getLastPreviousState();
        if (previousState == null) {
            return false;
        }
        boolean changed = changeLockState(previousState, LockAction.UNDO, "UNDO",
                actor, actorUuid);
        if (changed) {
            undoTracker.clearLastPreviousState();
        }
        return changed;
    }

    /**
     * Logs one state change into the audit log (Paper format:
     * {@code ACTION - Player: NAME - Status: LOCKED|UNLOCKED}).
     */
    private void logAction(String action, String actor) {
        if (!config.loggingEnabled()) {
            return;
        }
        audit.log(String.format("%s - Player: %s - Status: %s",
                action, actor, state.isLocked() ? "LOCKED" : "UNLOCKED"));
    }

    /**
     * Guards' blocked-attempt hook: counts the attempt unconditionally and
     * writes the rate-limited audit line (Paper format:
     * {@code [ts] Attempt - Player: NAME - World: KEY - Method: METHOD
     * - Status: LOCKED}).
     */
    private void reportBlockedAttempt(String playerName, UUID playerId,
                                      String originWorld, String method) {
        if (config.logAttempts()
                && attemptRateLimiter.shouldLog(playerId, System.currentTimeMillis())) {
            String timestamp = LocalDateTime.now().format(ATTEMPT_LOG_FORMAT);
            audit.log(String.format("[%s] Attempt - Player: %s - World: %s - Method: %s - Status: LOCKED",
                    timestamp, playerName, originWorld, method));
        }
        stats.recordBlockedAttempt();
    }

    /**
     * Persists the statistics counters into the JSON store.
     */
    private void saveStats() {
        if (!statsStore.save(new JsonStatsStore.StatsCounts(
                stats.lockCount(), stats.unlockCount(),
                stats.blockedCount(), stats.evacuatedCount()))) {
            LOGGER.warn("Could not persist the Lock-End statistics counters.");
        }
    }

    /**
     * Debounced persistence: writes once per one-second pass when new
     * events were recorded since the last write.
     */
    private void saveStatsIfChanged() {
        long revision = stats.revision();
        if (revision != lastStatsRevision) {
            lastStatsRevision = revision;
            saveStats();
        }
    }

    /**
     * {@code System} is the Paper plugin's actor name for scheduled state
     * changes (no player identity).
     */
    boolean changeStateScheduled(boolean lock) {
        return changeLockState(lock,
                lock ? LockAction.SCHEDULED_LOCK : LockAction.SCHEDULED_UNLOCK,
                lock ? "SCHEDULED_LOCK" : "SCHEDULED_UNLOCK",
                "System", null);
    }

    /**
     * Schedules a pending action at an absolute target and persists it.
     *
     * @param action the action name ({@code lock} or {@code unlock})
     * @param target the absolute execution time; must lie in the future (the
     *               command layer validates)
     * @return {@code true} when the schedule was accepted
     */
    boolean applyScheduled(String action, LocalDateTime target) {
        if (!ScheduleSupport.isSupportedAction(action) || target == null) {
            return false;
        }
        scheduler.schedule(action, target);
        scheduleRepository.save(new PersistedSchedule(action, target, false));
        LOGGER.info("Scheduled {} at {}; pending action set.", action,
                target.format(ScheduleSupport.SCHEDULE_FORMAT));
        return true;
    }

    /**
     * Clears the pending action and its persistence.
     */
    void cancelSchedule() {
        scheduler.cancel();
        scheduleRepository.clear();
        LOGGER.info("Schedule cleared.");
    }

    /**
     * Pauses the pending schedule without moving its target and persists the
     * paused flag.
     */
    void pauseSchedule() {
        scheduler.pause();
        persistRuntimeSchedule();
        LOGGER.info("Schedule paused.");
    }

    /**
     * Resumes a paused schedule (a lapsed target executes on the next tick)
     * and persists the resumed flag.
     */
    void resumeSchedule() {
        scheduler.resume();
        persistRuntimeSchedule();
        LOGGER.info("Schedule resumed.");
    }

    /**
     * @return {@code true} if a pending scheduled action exists
     */
    boolean hasSchedule() {
        return scheduler.hasAction();
    }

    /**
     * @return {@code true} while the pending schedule is paused
     */
    boolean isSchedulePaused() {
        return scheduler.isPaused();
    }

    /**
     * @return the scheduled action name, or {@code -} when none is pending
     *         (matching the Paper plugin's status line)
     */
    String scheduleAction() {
        return scheduler.target() == null ? "-" : scheduler.action();
    }

    /**
     * @return the formatted schedule target, or the "none" message when no
     *         action is pending
     */
    String scheduleTargetText() {
        LocalDateTime target = scheduler.target();
        return target == null
                ? messages().text("schedule-none")
                : target.format(ScheduleSupport.SCHEDULE_FORMAT);
    }

    /**
     * @return the remaining time of the schedule, or {@code -} when none is
     *         pending (matching the Paper plugin's status line)
     */
    String scheduleRemainingText() {
        LocalDateTime target = scheduler.target();
        if (target == null) {
            return "-";
        }
        return ScheduleSupport.formatDuration(
                Math.max(0L, scheduler.remainingSeconds()));
    }

    /**
     * @return the currently active settings
     */
    ModEndLockConfig currentSettings() {
        return config;
    }

    /**
     * Reloads the settings file and republishes its values to the live
     * consumers: the lock state reason supplier, the guard rules, the
     * schedule notification settings, the message overrides, statistics
     * recording, the attempt rate limiter, the audit log and the history
     * buffer sizing.
     *
     * @return {@code null} when the file was read cleanly, otherwise a
     *         human-readable recovery notice (defaults are then active for
     *         the unreadable file and it stays preserved as
     *         {@code lock-end.json.corrupt})
     */
    String reloadSettings() {
        config = config.reload();
        state.setReasonSupplier(config::lockReason);
        guard.applyRules(config.bypassChecker(), config.endScope(), config.blockEntities());
        applyScheduleSettings();
        refreshRuntimeServices();
        return config.parseFailureMessage();
    }

    /**
     * Republishes the runtime services from the freshly loaded config.
     * The history buffer is rebuilt; pending entries are flushed first so
     * no record is lost by the rebuild.
     */
    private void refreshRuntimeServices() {
        MESSAGES.setOverrides(JsonMessageOverrides.load(configDirectory, LOGGER));
        stats.setEnabled(config.statsEnabled());
        attemptRateLimiter.setRateLimitSeconds(config.logRateLimitSeconds());
        soundEffects.loadFrom(config);
        history.flush();
        List<HistoryEntry> retained = history.snapshot();
        history = new HistoryBuffer(historyStore, retained,
                config.historyMaxEntries(), config.historyRetentionDays());
        historyService = new LockHistoryService(configDirectory, history, historyStore);
        audit.shutdown();
        audit = new AuditLogWriter(message -> LOGGER.warn(message));
        configureAuditLogging();
    }

    /**
     * Publishes the countdown and preview notification settings from the
     * active config.
     */
    private void applyScheduleSettings() {
        scheduler.setCountdownSettings(config.countdownEnabled(),
                config.countdownStartBeforeSeconds(), config.countdownIntervalSeconds());
        scheduler.setPreviewSettings(config.previewEnabled(), config.previewSeconds());
    }

    /**
     * Restores the runtime schedule from the persisted file. A lapsed or
     * invalid persisted schedule is cleared (never executed on boot) and its
     * file cleaned up so the condition does not repeat.
     */
    private void restorePersistedSchedule() {
        PersistedSchedule persisted = scheduleRepository.load();
        LockScheduler.RestoreResult result = scheduler.restore(
                persisted.isEmpty() ? null : persisted.action(),
                persisted.target(),
                persisted.paused());
        if (result == LockScheduler.RestoreResult.RESUMED) {
            LOGGER.info("Restored scheduled {} at {}{}.", persisted.action(),
                    persisted.target().format(ScheduleSupport.SCHEDULE_FORMAT),
                    persisted.paused() ? " (paused)" : "");
        } else if (result == LockScheduler.RestoreResult.LAPSED_CLEARED) {
            LOGGER.info("Persisted schedule had already passed the restart; cleared.");
            scheduleRepository.clear();
        } else if (result == LockScheduler.RestoreResult.INVALID_CLEARED) {
            LOGGER.warn("Persisted schedule holds an unsupported action '{}'; cleared.", persisted.action());
            scheduleRepository.clear();
        }
    }

    /**
     * Writes the current runtime schedule to the persistence file.
     */
    private void persistRuntimeSchedule() {
        LocalDateTime target = scheduler.target();
        if (target == null) {
            scheduleRepository.clear();
            return;
        }
        scheduleRepository.save(new PersistedSchedule(scheduler.action(), target, scheduler.isPaused()));
    }

    /**
     * The scheduler callbacks, delivered in the server tick context:
     * executes the due state change through the central
     * {@link #changeLockState} path, sends countdown lines to admins and
     * broadcasts the one-shot preview to everyone, mirroring the Paper
     * plugin.
     */
    private final class SchedulerCallbacks implements LockScheduler.Callbacks {

        @Override
        public void executeScheduled(boolean lock) {
            MinecraftServer server = liveServer;
            if (server == null) {
                return;
            }
            boolean changed = changeStateScheduled(lock);
            if (!changed) {
                LOGGER.info("Scheduled {} target state already active; skipped.", lock ? "lock" : "unlock");
            } else {
                LOGGER.info("Scheduled {} executed.", lock ? "lock" : "unlock");
            }
            scheduleRepository.clear();
        }

        @Override
        public void discardScheduled() {
            scheduleRepository.clear();
            LOGGER.info("Scheduled {} cleared because the End already reached that state.",
                    scheduler.action());
        }

        @Override
        public void announceCountdown(String action, long remainingSeconds) {
            MinecraftServer server = liveServer;
            if (server == null) {
                return;
            }
            String key = "lock".equalsIgnoreCase(action)
                    ? "countdown-lock-notification" : "countdown-notification";
            String rendered = messages().text(key,
                    Map.of("%time%", ScheduleSupport.formatDuration(remainingSeconds)));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.permissions().hasPermission(Permissions.COMMANDS_ADMIN)) {
                    player.sendSystemMessage(Component.literal(rendered));
                }
            }
        }

        @Override
        public void announcePreview(String action, long secondsBefore) {
            MinecraftServer server = liveServer;
            if (server == null) {
                return;
            }
            String key = "lock".equalsIgnoreCase(action) ? "preview-lock" : "preview-unlock";
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    messages().text(key, Map.of("%seconds%", String.valueOf(secondsBefore)))),
                    false);
        }
    }
}