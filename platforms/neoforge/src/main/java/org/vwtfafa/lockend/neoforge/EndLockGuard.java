package org.vwtfafa.lockend.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import org.vwtfafa.lockend.core.EndDimensionScope;
import org.vwtfafa.lockend.core.LockBypassChecker;
import org.vwtfafa.lockend.core.LockStateManager;

/**
 * Guards the guarded End dimensions against entries while the shared runtime
 * lock is active. NeoForge exposes a cancellable pre-travel hook, so coverage
 * is simpler than on Fabric:
 *
 * <ul>
 *   <li>{@link EntityTravelToDimensionEvent} is fired inside the vanilla
 *       travel paths for entities and players (verified against the 26.3
 *       {@code ServerPlayer}/{@code Entity} patches, which return
 *       {@code null} on cancellation, meaning the transition never
 *       happens), so no arrival bounce and no tick scan is needed. Players
 *       are vetoed unless bypassed; non-player entities are vetoed while
 *       {@code blockEntities} is enabled (entities never bypass), and</li>
 *   <li>the restart safety net on {@code ServerStarted} evacuates players
 *       stranded in the End after a boot with a persisted active lock.</li>
 * </ul>
 *
 * <p>End gateways would stay inside a single dimension, so they never cross
 * the pre-travel event and are not blocked there; the Paper
 * {@code block-end-gateway} behavior has no NeoForge equivalent.</p>
 *
 * <p>The destination used when a player must be moved out is always the
 * overworld respawn point via a {@link TeleportTransition} with a
 * do-nothing post-teleport handler (no portal sound, no portal ticket),
 * matching the Fabric adapter's verified mechanics.</p>
 *
 * <p>The active rules ({@code bypass}, {@code scope}, {@code blockEntities})
 * are volatile and replaceable at runtime through {@link #applyRules} when
 * {@code /endlock reload} publishes new settings.</p>
 */
final class EndLockGuard {

    private static final String LOCKED_MESSAGE_PREFIX = "The End is locked: ";
    private static final Identifier WHITELIST_BYPASS_PERMISSION =
            Identifier.fromNamespaceAndPath("lock-end", "whitelist.bypass");

    private final LockStateManager state;
    private volatile LockBypassChecker bypassChecker;
    private volatile EndDimensionScope endScope;
    private volatile boolean blockEntities;
    private volatile MinecraftServer server;

    /**
     * Creates the guard around the shared runtime lock state and the
     * configured rules.
     *
     * @param state         the shared runtime lock state
     * @param bypassChecker the configured bypass (whitelist) rules
     * @param endScope      the guarded End dimension scope
     * @param blockEntities {@code true} if non-player entities may not enter
     */
    EndLockGuard(LockStateManager state,
                 LockBypassChecker bypassChecker,
                 EndDimensionScope endScope,
                 boolean blockEntities) {
        this.state = state;
        this.bypassChecker = bypassChecker;
        this.endScope = endScope;
        this.blockEntities = blockEntities;
    }

    /**
     * Publishes newly loaded settings; used by {@code /endlock reload}.
     *
     * @param newChecker    the freshly loaded bypass rules
     * @param newScope      the freshly loaded End dimension scope
     * @param newBlockEntities the freshly loaded entity-block setting
     */
    void applyRules(LockBypassChecker newChecker, EndDimensionScope newScope, boolean newBlockEntities) {
        this.bypassChecker = newChecker;
        this.endScope = newScope;
        this.blockEntities = newBlockEntities;
    }

    /**
     * Stores the server so evacuation can run between lifecycle events.
     *
     * @param currentServer the running server; not {@code null}
     */
    void attachServer(MinecraftServer currentServer) {
        this.server = currentServer;
    }

    /**
     * Restart safety net: runs once when the level is live, so a boot with a
     * persisted active lock clears players who were stranded in the End
     * before the shutdown. Runtime arrivals are vetoed by the pre-travel
     * event instead.
     */
    void handleServerStarted() {
        if (state.isLocked()) {
            evacuate();
        }
    }

    /**
     * Pre-travel veto: cancels a pending transition into a guarded End
     * dimension while the lock is active. Players are vetoed without bypass;
     * non-player entities are vetoed while {@code blockEntities} is enabled.
     * Runs on the server thread inside the vanilla travel path, so
     * cancelling here means the traveler simply does not travel.
     *
     * @param event the pre-travel event; cancellation is respected by both
     *              the entity and the player travel paths
     */
    void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!state.isLocked()) {
            return;
        }
        String destinationKey = event.getDimension().identifier().toString();
        String originKey = event.getEntity().level().dimension().identifier().toString();
        if (!endScope.isEntryAttempt(originKey, destinationKey)) {
            return;
        }
        Entity traveler = event.getEntity();
        if (traveler instanceof ServerPlayer player) {
            if (canBypass(player, event.getDimension())) {
                return;
            }
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(
                    LOCKED_MESSAGE_PREFIX + state.getLockReason()));
            LockEndNeoForgeMod.LOGGER.info("Blocked {} from entering the locked End",
                    player.getName().getString());
        } else if (blockEntities) {
            event.setCanceled(true);
            LockEndNeoForgeMod.LOGGER.info("Blocked entity {} from entering the locked End",
                    traveler.getName().getString());
        }
    }

    /**
     * Moves every player currently inside a guarded End dimension out.
     * Idempotent and only invoked while the lock is active (startup recovery
     * and the lock command).
     */
    void evacuate() {
        MinecraftServer currentServer = server;
        if (currentServer == null) {
            return;
        }
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            if (endScope.isGuarded(dimensionKey(player.level()))
                    && !canBypass(player, player.level().dimension())) {
                teleportOut(player);
            }
        }
    }

    private boolean canBypass(ServerPlayer player, ResourceKey<Level> destination) {
        return bypassChecker.canBypass(
                player.getName().getString(),
                player.getUUID(),
                destination.identifier().toString(),
                () -> player.permissions().hasPermission(Permissions.COMMANDS_ADMIN));
    }

    /**
     * Teleports a player out of the End to the overworld respawn point and
     * tells them why. No-op while no live server reference exists.
     *
     * @param player the player to move
     */
    void teleportOut(ServerPlayer player) {
        MinecraftServer currentServer = server;
        if (currentServer == null) {
            return;
        }
        ServerLevel overworld = currentServer.overworld();
        LevelData.RespawnData respawn = overworld.getRespawnData();
        player.teleport(new TeleportTransition(
                overworld,
                new Vec3(respawn.pos().getX() + 0.5, respawn.pos().getY(), respawn.pos().getZ() + 0.5),
                Vec3.ZERO,
                respawn.yaw(),
                respawn.pitch(),
                TeleportTransition.DO_NOTHING));
        player.sendSystemMessage(Component.literal(
                LOCKED_MESSAGE_PREFIX + state.getLockReason()));
    }

    private static String dimensionKey(Level level) {
        return level.dimension().identifier().toString();
    }
}
