package org.vwtfafa.lockend.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vwtfafa.lockend.api.LockAction;
import org.vwtfafa.lockend.core.LockScheduler;
import org.vwtfafa.lockend.core.LockStateManager;
import org.vwtfafa.lockend.core.PersistedSchedule;
import org.vwtfafa.lockend.core.ScheduleSupport;

import java.time.Clock;
import java.time.LocalDateTime;
import java.nio.file.Path;

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
 *   <li>{@link ServerLifecycleEvents#SERVER_STOPPING}: final state and
 *       schedule persistence.</li>
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
 *       {@link LockScheduler}.</li>
 *   <li>{@link CommandRegistrationCallback#EVENT}: the {@code /endlock}
 *       command tree.</li>
 * </ul>
 *
 * <p>All callbacks run on the main server thread; the runtime state is
 * managed by the shared core with the same volatile semantics as the Paper
 * plugin. Scheduled actions persist in
 * {@code config/lock-end.schedule.json} ({@link JsonScheduleRepository}),
 * countdown and preview behavior mirrors the Paper plugin: countdown lines
 * go to admins, the preview broadcasts to everyone.</p>
 */
public class LockEndFabricMod implements ModInitializer {

    /** Logger shared by all Fabric adapter classes (package-visible). */
    static final Logger LOGGER = LoggerFactory.getLogger("lock-end");

    private static final int SCHEDULE_TICK_CADENCE = 20;

    private LockStateManager state;
    private JsonLockStateRepository repository;
    private FabricEndLockConfig config;
    private EndLockGuard guard;
    private JsonScheduleRepository scheduleRepository;
    private LockScheduler scheduler;
    private volatile MinecraftServer liveServer;
    private int scheduleTickOffset;

    @Override
    public void onInitialize() {
        Path configDirectory = FabricLoader.getInstance().getConfigDir();
        config = FabricEndLockConfig.load(configDirectory);
        repository = new JsonLockStateRepository(configDirectory);

        state = new LockStateManager(false).loadFromPersistence(repository);
        state.setReasonSupplier(config::lockReason);

        guard = new EndLockGuard(state, config.bypassChecker(), config.endScope(), config.blockEntities());
        scheduleRepository = new JsonScheduleRepository(configDirectory);
        scheduler = new LockScheduler(new SchedulerCallbacks(), Clock.systemDefaultZone());
        applyScheduleSettings();
        restorePersistedSchedule();

        registerLifecycleHooks();
        registerDimensionHooks();
        registerCommands();

        LOGGER.info("EndLock (Fabric) initialized; End is {}", state.isLocked() ? "LOCKED" : "unlocked");
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
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            repository.save(state.getState());
            persistRuntimeSchedule();
            liveServer = null;
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
        // poll at a one-second cadence.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            guard.tick(server);
            scheduleTickOffset = (scheduleTickOffset + 1) % SCHEDULE_TICK_CADENCE;
            if (scheduleTickOffset == 0 && liveServer != null) {
                scheduler.tick(state.isLocked());
            }
        });
    }

    /**
     * Installs the {@code /endlock} Brigadier command tree through the
     * verified Fabric command API v2 callback.
     */
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
                new LockEndCommands(state, repository, guard, this)
                        .registerAll(dispatcher, buildContext, selection));
    }

    /**
     * {@code System} is the Paper plugin's actor name for scheduled state
     * changes (no player identity).
     */
    boolean changeStateScheduled(boolean lock) {
        boolean changed = state.updateLocked(lock,
                lock ? LockAction.SCHEDULED_LOCK : LockAction.SCHEDULED_UNLOCK, "System", null);
        if (changed) {
            repository.save(state.getState());
            if (lock) {
                guard.evacuate();
            }
        }
        return changed;
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
     * Builds the schedule status line mirroring the Paper plugin's
     * {@code schedule-status} message shape.
     *
     * @return the status line; never {@code null}
     */
    String scheduleStatusLine() {
        LocalDateTime target = scheduler.target();
        if (target == null) {
            return "No scheduled action.";
        }
        String remaining = ScheduleSupport.formatDuration(
                Math.max(0L, scheduler.remainingSeconds()));
        return "Action: " + scheduler.action()
                + " | Target: " + target.format(ScheduleSupport.SCHEDULE_FORMAT)
                + " | Remaining: " + remaining
                + " | Paused: " + scheduler.isPaused();
    }

    /**
     * @return the currently active settings
     */
    FabricEndLockConfig currentSettings() {
        return config;
    }

    /**
     * Reloads the settings file and republishes its values to the live
     * consumers: the lock state reason supplier, the guard rules and the
     * schedule notification settings.
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
        return config.parseFailureMessage();
    }

    /**
     * @return the shared runtime lock state of this Fabric installation
     */
    public LockStateManager getState() {
        return state;
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
     * executes the due state change through {@link #changeStateScheduled},
     * sends countdown lines to admins and broadcasts the one-shot preview
     * to everyone, mirroring the Paper plugin.
     */
    private final class SchedulerCallbacks implements LockScheduler.Callbacks {

        private static final String PREFIX = "[EndLock] ";

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
                server.getPlayerList().broadcastSystemMessage(Component.literal(lock
                        ? "The End is now locked! (scheduled) "
                        + describeReason()
                        : "The End has been unlocked. (scheduled)"), false);
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
            boolean lock = "lock".equalsIgnoreCase(action);
            String rendered = PREFIX + "The End will be "
                    + (lock ? "locked" : "unlocked")
                    + " in " + ScheduleSupport.formatDuration(remainingSeconds) + ".";
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
            boolean lock = "lock".equalsIgnoreCase(action);
            server.getPlayerList().broadcastSystemMessage(Component.literal(lock
                    ? "The End will be locked in " + secondsBefore + " seconds!"
                    : "The End will be unlocked in " + secondsBefore + " seconds!"), false);
        }

        private String describeReason() {
            String reason = state.getLockReason();
            return reason.isBlank() ? "" : "Reason: " + reason;
        }
    }
}
