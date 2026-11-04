package org.vwtfafa.lockend.fabric;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import org.vwtfafa.lockend.core.EndDimensionScope;
import org.vwtfafa.lockend.core.LockBypassChecker;
import org.vwtfafa.lockend.core.LockStateManager;

/**
 * Guards the guarded End dimensions against entries while the shared runtime
 * lock is active. Because Fabric API exposes no cancellable dimension-change
 * event, coverage comes from verified hooks that agree on server-thread
 * execution:
 *
 * <ul>
 *   <li>an immediate bounce in
 *       {@code ServerEntityLevelChangeEvents#AFTER_PLAYER_CHANGE_LEVEL} when
 *       a player arrives in a guarded End dimension while the lock is
 *       active,</li>
 *   <li>an immediate bounce in
 *       {@code ServerEntityLevelChangeEvents#AFTER_ENTITY_CHANGE_LEVEL} for
 *       non-player entities when {@code blockEntities} is enabled (entities
 *       never bypass), and</li>
 *   <li>a throttled scan on {@code ServerTickEvents#END_SERVER_TICK} that
 *       catches players who reached the End through a route the arrival
 *       events cannot observe (including players logged back in after a
 *       restart with a persisted lock).</li>
 * </ul>
 *
 * <p>All bounces teleport to the overworld respawn point via a
 * {@link TeleportTransition} with a do-nothing post-teleport handler (no
 * portal sound, no portal ticket for the destination we are leaving). The
 * destination is always the overworld, so a lapsed arrival can never chain
 * deeper into the End.</p>
 *
 * <p>End gateways would stay inside a single dimension, so they never pass
 * the level-change events and are not blocked there; the Paper
 * {@code block-end-gateway} behavior has no Fabric API equivalent.</p>
 *
 * <p>The active rules ({@code bypass}, {@code scope}, {@code blockEntities})
 * are volatile and replaceable at runtime through {@link #applyRules} when
 * {@code /endlock reload} publishes new settings.</p>
 */
final class EndLockGuard {

    private static final long SCAN_INTERVAL_TICKS = 20L;
    private static final String LOCKED_MESSAGE_PREFIX = "The End is locked: ";
    private static final Identifier WHITELIST_BYPASS_PERMISSION =
            Identifier.fromNamespaceAndPath("lock-end", "whitelist.bypass");

    private final LockStateManager state;
    private volatile LockBypassChecker bypassChecker;
    private volatile EndDimensionScope endScope;
    private volatile boolean blockEntities;
    private volatile MinecraftServer server;
    private long ticksSinceScan;

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
     * Arrival hook: bounces a player who just moved into a guarded End
     * dimension while the lock is active.
     *
     * @param player      the moved player
     * @param origin      the level the player came from
     * @param destination the level the player arrived in
     */
    void onPlayerLevelChange(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
        if (!state.isLocked() || !isEntryAttempt(origin, destination) || canBypass(player, destination)) {
            return;
        }
        teleportOut(player);
        LockEndFabricMod.LOGGER.info("Bounced {} out of the locked End (arrived from {})",
                player.getName().getString(), origin.dimension().identifier());
    }

    /**
     * Arrival hook: bounces a non-player entity that just moved into a
     * guarded End dimension while the lock is active and entity blocking is
     * enabled. Entities have no bypass options, mirroring the Paper baseline.
     *
     * @param original    the entity as it existed at the origin
     * @param moved       the entity that arrived in the destination
     * @param origin      the level the entity came from
     * @param destination the level the entity arrived in
     */
    void onEntityLevelChange(Entity original, Entity moved, ServerLevel origin, ServerLevel destination) {
        if (!state.isLocked() || !blockEntities || !isEntryAttempt(origin, destination)) {
            return;
        }
        LockEndFabricMod.LOGGER.info("Bounced entity {} out of the locked End (arrived from {})",
                moved.getName().getString(), origin.dimension().identifier());
        bounceEntity(moved);
    }

    /**
     * Moves every player currently inside a guarded End dimension out.
     * Idempotent; the tick gate keeps this a no-op while the End is unlocked.
     */
    void evacuate() {
        MinecraftServer currentServer = server;
        if (currentServer == null) {
            return;
        }
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            if (endScope.isGuarded(dimensionKey(player.level())) && !canBypass(player, player.level())) {
                teleportOut(player);
            }
        }
    }

    private boolean canBypass(ServerPlayer player, ServerLevel destination) {
        String worldKey = destination.dimension().identifier().toString();
        Identifier worldPermission = Identifier.fromNamespaceAndPath(
                "lock-end", "bypass.world." + worldKey.replace(':', '.'));
        PermissionContextOwner permissionOwner = (PermissionContextOwner) player;
        return bypassChecker.canBypass(
                player.getName().getString(),
                player.getUUID(),
                worldKey,
                () -> permissionOwner.checkPermission(worldPermission, PermissionLevel.ADMINS)
                        || permissionOwner.checkPermission(
                                WHITELIST_BYPASS_PERMISSION, PermissionLevel.ADMINS));
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
                respawnVec(respawn),
                Vec3.ZERO,
                respawn.yaw(),
                respawn.pitch(),
                TeleportTransition.DO_NOTHING));
        player.sendSystemMessage(Component.literal(
                LOCKED_MESSAGE_PREFIX + state.getLockReason()));
    }

    private void bounceEntity(Entity entity) {
        MinecraftServer currentServer = server;
        if (currentServer == null) {
            return;
        }
        ServerLevel overworld = currentServer.overworld();
        LevelData.RespawnData respawn = overworld.getRespawnData();
        entity.teleport(new TeleportTransition(
                overworld,
                respawnVec(respawn),
                Vec3.ZERO,
                respawn.yaw(),
                respawn.pitch(),
                TeleportTransition.DO_NOTHING));
    }

    private boolean isEntryAttempt(Level origin, Level destination) {
        return endScope.isEntryAttempt(dimensionKey(origin), dimensionKey(destination));
    }

    private static String dimensionKey(Level level) {
        return level.dimension().identifier().toString();
    }

    private static Vec3 respawnVec(LevelData.RespawnData respawn) {
        return new Vec3(respawn.pos().getX() + 0.5, respawn.pos().getY(), respawn.pos().getZ() + 0.5);
    }
}
