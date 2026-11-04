package org.vwtfafa.lockend.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vwtfafa.lockend.core.LockStateManager;

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
 *   <li>{@link ServerLifecycleEvents#SERVER_STOPPING}: final state
 *       persistence.</li>
 *   <li>{@link ServerEntityLevelChangeEvents#AFTER_PLAYER_CHANGE_LEVEL}:
 *       immediate bounce of a player who just arrived in the End while the
 *       lock is active. Fabric API exposes no cancellable dimension-change
 *       event, so the bounce happens after arrival (see the migration plan
 *       section 5.2: mixins only where necessary).</li>
 *   <li>{@link ServerTickEvents#END_SERVER_TICK}: throttled scan that
 *       catches players who reached the End by a route the arrival event
 *       could not cover.</li>
 *   <li>{@link CommandRegistrationCallback#EVENT}: the {@code /endlock}
 *       command tree.</li>
 * </ul>
 *
 * <p>All callbacks run on the main server thread; the runtime state is
 * managed by the shared core with the same volatile semantics as the Paper
 * plugin.</p>
 */
public class LockEndFabricMod implements ModInitializer {

    /** Logger shared by all Fabric adapter classes (package-visible). */
    static final Logger LOGGER = LoggerFactory.getLogger("lock-end");

    private LockStateManager state;
    private JsonLockStateRepository repository;
    private FabricEndLockConfig config;
    private EndLockGuard guard;

    @Override
    public void onInitialize() {
        Path configDirectory = FabricLoader.getInstance().getConfigDir();
        config = FabricEndLockConfig.load(configDirectory);
        repository = new JsonLockStateRepository(configDirectory);

        state = new LockStateManager(false).loadFromPersistence(repository);
        state.setReasonSupplier(config::lockReason);

        guard = new EndLockGuard(state, config.bypassChecker());
        registerLifecycleHooks();
        registerDimensionHooks();
        registerCommands();

        LOGGER.info("EndLock (Fabric) initialized; End is {}", state.isLocked() ? "LOCKED" : "unlocked");
    }

    /**
     * Installs the lifecycle hooks: the restarted-server evacuation once the
     * world is live, and a final persistence write on shutdown.
     */
    private void registerLifecycleHooks() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            guard.attachServer(server);
            guard.handleServerStarted();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> repository.save(state.getState()));
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
        // Throttled scan as the safety net for players who reached the End
        // by a route the arrival event could not cover.
        ServerTickEvents.END_SERVER_TICK.register(guard::tick);
    }

    /**
     * Installs the {@code /endlock} Brigadier command tree through the
     * verified Fabric command API v2 callback.
     */
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
                new LockEndCommands(state, repository, guard, config).registerAll(dispatcher, buildContext, selection));
    }

    /**
     * @return the shared runtime lock state of this Fabric installation
     */
    public LockStateManager getState() {
        return state;
    }
}
