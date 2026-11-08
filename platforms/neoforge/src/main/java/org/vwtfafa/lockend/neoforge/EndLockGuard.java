package org.vwtfafa.lockend.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import org.vwtfafa.lockend.core.EndDimensionScope;
import org.vwtfafa.lockend.core.GracePeriod;
import org.vwtfafa.lockend.core.LockBypassChecker;
import org.vwtfafa.lockend.core.LockStateManager;

import java.util.UUID;

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
 *   <li>players who already live in the End when the lock becomes active
 *       are the {@link EndLockEvacuation} service's business (or, when it
 *       is disabled, they remain, like on Paper); the restart safety net
 *       re-arms that pass in the mod's {@code ServerStarted} hook.</li>
 * </ul>
 *
 * <p>A running grace period delays enforcement: travels into the locked
 * End arrive with a {@code grace-period-active} notice instead of a veto,
 * mirroring the Paper plugin. Real denials send the locked message, play
 * the configured denial sound and report the attempt through
 * {@link BlockedAttemptReporter} (section 3 parity: stats counter plus a
 * rate-limited audit-logging line; the NeoForge veto reports the
 * {@code PRE_TRAVEL} method - Fabric's arrival bounce reports
 * {@code ARRIVAL}).</p>
 *
 * <p>End gateways would stay inside a single dimension, so they never cross
 * the pre-travel event and are not blocked there; the Paper
 * {@code block-end-gateway} behavior has no NeoForge equivalent.</p>
 *
 * <p>The active rules ({@code bypass}, {@code scope}, {@code blockEntities})
 * are volatile and replaceable at runtime through {@link #applyRules} when
 * {@code /endlock reload} publishes new settings.</p>
 */
final class EndLockGuard {

    /**
     * Reports a player access attempt blocked by the pre-travel veto
     * (section 3 parity: stats counter plus a rate-limited audit-logging
     * line). The mod installs the implementation on boot.
     */
    @FunctionalInterface
    interface BlockedAttemptReporter {
        void reportBlockedAttempt(String playerName, UUID playerId,
                                  String originWorld, String method);
    }

    private static final String LOCKED_MESSAGE_PREFIX = "The End is locked: ";

    private final LockStateManager state;
    private final GracePeriod gracePeriod;
    private final SoundEffects soundEffects;
    private volatile LockBypassChecker bypassChecker;
    private volatile EndDimensionScope endScope;
    private volatile boolean blockEntities;
    private volatile BlockedAttemptReporter blockedAttemptReporter;

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
     * Installs the blocked-attempt reporter (called on every vetoed
     * player travel).
     *
     * @param reporter the reporter; {@code null} disables reporting
     */
    void setBlockedAttemptReporter(BlockedAttemptReporter reporter) {
        this.blockedAttemptReporter = reporter;
    }

    /**
     * @param level the level to test
     * @return {@code true} if the level is inside the guarded End scope
     */
    boolean isGuarded(Level level) {
        return endScope.isGuarded(dimensionKey(level));
    }

    /**
     * Pre-travel veto: cancels a pending transition into a guarded End
     * dimension while the lock is active. Players are vetoed without bypass;
     * non-player entities are vetoed while {@code blockEntities} is enabled.
     * Runs on the server thread inside the vanilla travel path, so
     * cancelling here means the traveler simply does not travel. A running
     * grace period holds enforcement and announces the window instead.
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
            if (gracePeriod.isActive()) {
                player.sendSystemMessage(Component.literal(
                        LockEndNeoForgeMod.messages().text("grace-period-active")));
                return;
            }
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(
                    LOCKED_MESSAGE_PREFIX + state.getLockReason()));
            soundEffects.playDenial(player);
            reportAttempt(player, originKey);
            LockEndNeoForgeMod.LOGGER.info("Blocked {} from entering the locked End",
                    player.getName().getString());
        } else if (blockEntities) {
            event.setCanceled(true);
            LockEndNeoForgeMod.LOGGER.info("Blocked entity {} from entering the locked End",
                    traveler.getName().getString());
        }
    }

    private void reportAttempt(ServerPlayer player, String originKey) {
        BlockedAttemptReporter reporter = blockedAttemptReporter;
        if (reporter != null) {
            reporter.reportBlockedAttempt(player.getName().getString(), player.getUUID(),
                    originKey, "PRE_TRAVEL");
        }
    }

    private boolean canBypass(ServerPlayer player, ResourceKey<Level> destination) {
        return bypassChecker.canBypass(
                player.getName().getString(),
                player.getUUID(),
                destination.identifier().toString(),
                () -> player.permissions().hasPermission(Permissions.COMMANDS_ADMIN));
    }

    private static String dimensionKey(Level level) {
        return level.dimension().identifier().toString();
    }
}
