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
import org.vwtfafa.lockend.core.GracePeriod;
import org.vwtfafa.lockend.core.LockBypassChecker;
import org.vwtfafa.lockend.core.LockStateManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
 *       active (a running grace period is announced instead of enforced),</li>
 *   <li>an immediate bounce in
 *       {@code ServerEntityLevelChangeEvents#AFTER_ENTITY_CHANGE_LEVEL} for
 *       non-player entities when {@code blockEntities} is enabled (entities
 *       never bypass), and</li>
 *   <li>a throttled scan on {@code ServerTickEvents#END_SERVER_TICK} that
 *       only catches players who entered afterwards: the residents recorded
 *       when the lock became active (or at boot with a persisted lock) stay
 *       in place - moving them out is the mod's evacuation service business
 *       (or, when it is disabled, they remain, like on Paper).</li>
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

    /**
     * Reports a player arrival blocked by the lock (section 3 parity: stats
     * counter plus a rate-limited audit-logging line). The mod installs the
     * implementation on boot.
     */
    @FunctionalInterface
    interface BlockedAttemptReporter {
        void reportBlockedAttempt(String playerName, java.util.UUID playerId,
                                  String originWorld, String method);
    }

    private static final long SCAN_INTERVAL_TICKS = 20L;
    private static final String LOCKED_MESSAGE_PREFIX = "The End is locked: ";
    private static final Identifier WHITELIST_BYPASS_PERMISSION =
            Identifier.fromNamespaceAndPath("lock-end", "whitelist.bypass");

    private final LockStateManager state;
    private final GracePeriod gracePeriod;
    private final SoundEffects soundEffects;
    private volatile LockBypassChecker bypassChecker;
    private volatile EndDimensionScope endScope;
    private volatile boolean blockEntities;
    private volatile MinecraftServer server;
    private volatile BlockedAttemptReporter blockedAttemptReporter;
    private volatile Set<UUID> residentsAtLock = Set.of();
    private long ticksSinceScan;

    /**
     * Creates the guard around the shared runtime lock state and the
     * configured rules.
     *
     * @param state         the shared runtime lock state
     * @param bypassChecker the configured bypass (whitelist) rules
     * @param endScope      the guarded End dimension scope
     * @param blockEntities {@code true} if non-player entities may not enter
     * @param gracePeriod   the grace-period window consulted before
     *                      enforcement
     * @param soundEffects  the denial-sound player shared by all routes
     */
    EndLockGuard(LockStateManager state,
                 LockBypassChecker bypassChecker,
                 EndDimensionScope endScope,
                 boolean blockEntities,
                 GracePeriod gracePeriod,
                 SoundEffects soundEffects) {
        this.state = state;
        this.bypassChecker = bypassChecker;
        this.endScope = endScope;
        this.blockEntities = blockEntities;
        this.gracePeriod = gracePeriod;
        this.soundEffects = soundEffects;
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
     * Installs the blocked-attempt reporter (called on every bounced arrival).
     *
     * @param reporter the reporter; {@code null} disables reporting
     */
    void setBlockedAttemptReporter(BlockedAttemptReporter reporter) {
        this.blockedAttemptReporter = reporter;
    }

    /**
     * Restart handover: records who currently lives in the guarded End so
     * the arrival scan only bounces players who entered afterwards. The
     * regular evacuation pass decides whether those residents are moved out.
     */
    void handleServerStarted() {
        captureResidents();
    }

    /**
     * Snapshots the players currently inside guarded End dimensions as
     * residents; while the lock is active only new arrivals are bounced by
     * the tick scan (residents are the evacuation service's business).
     */
    void captureResidents() {
        MinecraftServer currentServer = server;
        if (currentServer == null) {
            return;
        }
        Set<UUID> residents = new HashSet<>();
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            if (endScope.isGuarded(dimensionKey(player.level()))) {
                residents.add(player.getUUID());
            }
        }
        residentsAtLock = Set.copyOf(residents);
        LockEndFabricMod.LOGGER.debug("Recorded {} End residents for the active lock.", residents.size());
    }

    /**
     * Clears the residents snapshot (used when the End unlocks again).
     */
    void clearResidents() {
        residentsAtLock = Set.of();
    }

    /**
     * @param level the level to test
     * @return {@code true} if the level is inside the guarded End scope
     */
    boolean isGuarded(Level level) {
        return endScope.isGuarded(dimensionKey(level));
    }

    /**
     * Periodic arrival-scan callback; invoked on every server tick. Players
     * who were already inside the End when the lock became active are not
     * evicted here (enforcement is the evacuation service's business); this
     * scan only catches players who arrived by a route the level-change
     * events could not observe.
     *
     * @param currentServer the running server
     */
    void tick(MinecraftServer currentServer) {
        if (!state.isLocked() || gracePeriod.isActive()) {
            return;
        }
        if (++ticksSinceScan < SCAN_INTERVAL_TICKS) {
            return;
        }
        ticksSinceScan = 0;
        Set<UUID> residents = residentsAtLock;
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            if (residents.contains(player.getUUID())
                    || !endScope.isGuarded(dimensionKey(player.level()))
                    || canBypass(player, player.level())) {
                continue;
            }
            LockEndFabricMod.LOGGER.info("Bounced {} out of the locked End (late arrival scan)",
                    player.getName().getString());
            bounceArrival(player);
            reportAttempt(player, "unknown", "LATE_SCAN");
        }
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
        if (gracePeriod.isActive()) {
            player.sendSystemMessage(Component.literal(
                    LockEndFabricMod.messages().text("grace-period-active")));
            return;
        }
        LockEndFabricMod.LOGGER.info("Bounced {} out of the locked End (arrived from {})",
                player.getName().getString(), origin.dimension().identifier());
        bounceArrival(player);
        reportAttempt(player, origin.dimension().identifier().toString(), "ARRIVAL");
    }

    private void reportAttempt(ServerPlayer player, String originWorld, String method) {
        BlockedAttemptReporter reporter = blockedAttemptReporter;
        if (reporter != null) {
            reporter.reportBlockedAttempt(player.getName().getString(), player.getUUID(),
                    originWorld, method);
        }
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

    /**
     * Bounces one arrival back to the safe destination: teleports the
     * player to the overworld respawn point, plays the denial sound and
     * sends the locked message with the current reason.
     *
     * @param player the arriving player
     */
    private void bounceArrival(ServerPlayer player) {
        teleportOut(player);
        soundEffects.playDenial(player);
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
