package org.vwtfafa.lockend.fabric;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.vwtfafa.lockend.core.LockStateManager;

/**
 * Guards the End dimension against entries while the shared runtime lock is
 * active. Because Fabric API exposes no cancellable dimension-change event,
 * coverage comes from two verified hooks that agree on server-thread
 * execution:
 *
 * <ul>
 *   <li>an immediate bounce in
 *       {@code ServerEntityLevelChangeEvents#AFTER_PLAYER_CHANGE_LEVEL} when
 *       a player arrives in the End while the lock is active, and</li>
 *   <li>a throttled scan on
 *       {@code ServerTickEvents#END_SERVER_TICK} that catches players who
 *       reached the End through a route the arrival event cannot observe
 *       (including players logged back in after a restart with a persisted
 *       lock).</li>
 * </ul>
 *
 * <p>All bounces teleport to the overworld respawn point via a
 * {@link TeleportTransition} with a do-nothing post-teleport handler (no
 * portal sound, no portal ticket for the destination we are leaving). The
 * destination is always the overworld, so a lapsed arrival can never chain
 * deeper into the End.</p>
 */
final class EndLockGuard {

    private static final long SCAN_INTERVAL_TICKS = 20L;
    private static final String LOCKED_MESSAGE_PREFIX = "The End is locked: ";

    private final LockStateManager state;
    private volatile MinecraftServer server;
    private long ticksSinceScan;

    /**
     * Creates the guard around the shared runtime lock state.
     *
     * @param state the shared runtime lock state
     */
    EndLockGuard(LockStateManager state) {
        this.state = state;
    }

    /**
     * Stores the server so scans can run between lifecycle events.
     *
     * @param currentServer the running server; not {@code null}
     */
    void attachServer(MinecraftServer currentServer) {
        this.server = currentServer;
    }

    /**
     * Restart safety net: runs once when the level is live, so a boot with a
     * persisted active lock clears players who were stranded in the End
     * before the shutdown.
     */
    void handleServerStarted() {
        if (state.isLocked()) {
            evacuate();
        }
    }

    /**
     * Periodic scan callback; invoked on every server tick.
     *
     * @param currentServer the running server
     */
    void tick(MinecraftServer currentServer) {
        if (!state.isLocked()) {
            return;
        }
        if (++ticksSinceScan < SCAN_INTERVAL_TICKS) {
            return;
        }
        ticksSinceScan = 0;
        evacuate();
    }

    /**
     * Arrival hook: bounces a player who just moved into the End while the
     * lock is active.
     *
     * @param player      the moved player
     * @param origin      the level the player came from
     * @param destination the level the player arrived in
     */
    void onPlayerLevelChange(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
        if (!state.isLocked() || !isEndLevel(destination)) {
            return;
        }
        teleportOut(player);
        LockEndFabricMod.LOGGER.info("Bounced {} out of the locked End (arrived from {})",
                player.getName().getString(), origin.dimension().identifier());
    }

    /**
     * Moves every player currently inside a guarded End level out. Idempotent;
     * the tick gate keeps this a no-op while the End is unlocked.
     */
    void evacuate() {
        MinecraftServer currentServer = server;
        if (currentServer == null) {
            return;
        }
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            if (isEndLevel(player.level())) {
                teleportOut(player);
            }
        }
    }

    private void teleportOut(ServerPlayer player) {
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

    private static boolean isEndLevel(Level level) {
        return level.dimension().equals(Level.END);
    }
}
