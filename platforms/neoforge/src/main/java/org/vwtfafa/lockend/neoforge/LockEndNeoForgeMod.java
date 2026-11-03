package org.vwtfafa.lockend.neoforge;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vwtfafa.lockend.core.LockStateManager;

import java.nio.file.Path;

/**
 * NeoForge entrypoint of the Lock-End lock for the End dimension, annotated
 * with the verified {@code @Mod("lockend")} javafml contract for NeoForge
 * 26.3 (FML 12 rejects the hyphenated mod id, hence {@code lockend}). Owns
 * the runtime lock state through the shared {@link LockStateManager},
 * persisted via {@link JsonLockStateRepository}, and wires the verified
 * game-bus hooks:
 *
 * <ul>
 *   <li>{@link RegisterCommandsEvent}: the {@code /endlock} command tree.</li>
 *   <li>{@link EntityTravelToDimensionEvent}: cancellable pre-travel veto -
 *       blocking this event stops the dimension transition in both the
 *       entity and the player travel paths (no arrival bounce, no tick
 *       scan).</li>
 *   <li>{@link ServerStartedEvent}: restart safety net - players already
 *       inside the End after a boot with a persisted active lock are
 *       evacuated once.</li>
 *   <li>{@link ServerStoppingEvent}: final state persistence.</li>
 * </ul>
 *
 * <p>All listeners run on the main server thread; the runtime state is
 * managed by the shared core with the same volatile semantics as the Paper
 * plugin.</p>
 */
@Mod("lockend")
public class LockEndNeoForgeMod {

    /** Logger shared by all NeoForge adapter classes (package-visible). */
    static final Logger LOGGER = LoggerFactory.getLogger("lock-end");

    private final LockStateManager state;
    private final JsonLockStateRepository repository;
    private final EndLockGuard guard;

    /**
     * Creates the mod instance, loads configuration and persistence and
     * installs the game-bus listeners. Called exactly once by the javafml
     * language loader because of {@code @Mod("lockend")}.
     */
    public LockEndNeoForgeMod() {
        Path configDirectory = FMLPaths.CONFIGDIR.get();
        NeoForgeEndLockConfig config = NeoForgeEndLockConfig.load(configDirectory);
        repository = new JsonLockStateRepository(configDirectory);

        state = new LockStateManager(false).loadFromPersistence(repository);
        state.setReasonSupplier(config::lockReason);

        guard = new EndLockGuard(state, config.bypassChecker());
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onTravelToDimension);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        LOGGER.info("EndLock (NeoForge) initialized; End is {}", state.isLocked() ? "LOCKED" : "unlocked");
    }

    /**
     * Registers the {@code /endlock} Brigadier command tree through the
     * verified game-bus command registration event.
     *
     * @param event the command registration event
     */
    private void onRegisterCommands(RegisterCommandsEvent event) {
        new LockEndCommands(state, repository, guard).registerAll(event.getDispatcher());
    }

    /**
     * Veto hook: blocks the pending travel into a locked End before the
     * transition happens, so no post-arrival cleanup is needed.
     *
     * @param event the pre-travel event
     */
    private void onTravelToDimension(EntityTravelToDimensionEvent event) {
        guard.onTravelToDimension(event);
    }

    /**
     * Lifecycle hook: stores the live server and runs the persisted-lock
     * restart evacuation.
     *
     * @param event the server started event
     */
    private void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        guard.attachServer(server);
        guard.handleServerStarted();
    }

    /**
     * Lifecycle hook: persists the final state so a persisted lock
     * re-applies on the next boot (where the restart evacuation clears any
     * player stranded inside).
     *
     * @param event the server stopping event
     */
    private void onServerStopping(ServerStoppingEvent event) {
        repository.save(state.getState());
    }

    /**
     * @return the shared runtime lock state of this NeoForge installation
     */
    public LockStateManager getState() {
        return state;
    }
}
