package org.vwtfafa.lockend.neoforge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Warns and moves players out of the guarded End after a lock becomes
 * active, mirroring the Paper plugin's {@code EvacuationService}: an
 * automatic warning-then-evacuate pass on every lock, an optional
 * {@code /endlock evacuate preview} that is bound to the requester and
 * expires after 30 seconds, and {@code /endlock evacuate execute} which
 * re-checks the plan for drift right before starting the teleports. All
 * outcome codes ({@code MOVED}, {@code SKIPPED_OFFLINE}, {@code
 * SKIPPED_LEFT_END}, {@code SKIPPED_BYPASS}, {@code FAILED}) are recorded
 * as EVACUATE history entries like on Paper.
 *
 * <p>The mods run on the single server thread, so synchronous teleports
 * replace Paper's async scheduler plumbing; the semantics stay identical.</p>
 */
final class EndLockEvacuation {

    private static final long PREVIEW_VALIDITY_NANOS = Duration.ofSeconds(30).toNanos();

    private final LockEndNeoForgeMod mod;
    private final Map<String, PendingPreview> pendingPreviews = new HashMap<>();
    private int pendingEvacuationSeconds = -1;

    EndLockEvacuation(LockEndNeoForgeMod mod) {
        this.mod = mod;
    }

    /**
     * Warns the players a new lock covers and postpones their move by the
     * configured warning time, mirroring the Paper plugin's schedule.
     */
    void schedule() {
        cancel();
        MinecraftServer server = mod.liveServer();
        if (server == null) {
            return;
        }
        NeoForgeEndLockConfig config = mod.currentSettings();
        long warningSeconds = config.evacuationWarningSeconds();
        String warning = LockEndNeoForgeMod.messages().text("evacuation-warning", Map.of(
                "%seconds%", String.valueOf(warningSeconds)));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.level();
            if (isGuarded(level) && (!config.evacuationExcludeBypass()
                    || !canBypass(player, level))) {
                player.sendSystemMessage(Component.literal(warning));
            }
        }
        pendingEvacuationSeconds = (int) Math.max(0L, warningSeconds);
        if (pendingEvacuationSeconds == 0) {
            evacuateAutomatically();
        }
    }

    /**
     * One-second tick of the pending automatic evacuation (the mods poll on
     * their once-per-second server tick pass).
     */
    void tickSecond() {
        if (pendingEvacuationSeconds < 0) {
            return;
        }
        if (pendingEvacuationSeconds > 0) {
            pendingEvacuationSeconds--;
            return;
        }
        pendingEvacuationSeconds = -1;
        evacuateAutomatically();
    }

    /**
     * Cancels a pending automatic evacuation.
     */
    void cancel() {
        pendingEvacuationSeconds = -1;
    }

    /**
     * @return {@code true} if an automatic evacuation is waiting for its
     *         warning time to elapse
     */
    boolean isPending() {
        return pendingEvacuationSeconds >= 0;
    }

    /**
     * Removes preview state and cancels pending work at shutdown.
     */
    void shutdown() {
        cancel();
        pendingPreviews.clear();
    }

    /**
     * Shows a side-effect-free preview of the players an End evacuation
     * would affect; the preview is bound to the requester and expires after
     * 30 seconds.
     */
    void showPreview(CommandSourceStack source, String requester, UUID requesterUuid) {
        if (!canEvacuate(source, requester)) {
            return;
        }
        long now = System.nanoTime();
        pendingPreviews.values().removeIf(entry -> now > entry.expiresAtNanos());
        PlanResult result = collectPlan();
        if (result.errorKey() != null) {
            send(source, result.errorKey());
            return;
        }
        pendingPreviews.put(requester,
                new PendingPreview(result.plan(), System.nanoTime() + PREVIEW_VALIDITY_NANOS));
        sendPreview(source, "evacuation-preview", result.plan());
    }

    /**
     * Executes only after a recent preview by the same requester; a fresh
     * snapshot is compared immediately before starting teleports.
     */
    void executePreview(CommandSourceStack source, String requester, UUID requesterUuid) {
        if (!canEvacuate(source, requester)) {
            return;
        }
        PendingPreview previous = pendingPreviews.get(requester);
        if (previous == null) {
            send(source, "evacuation-preview-required");
            return;
        }
        if (System.nanoTime() > previous.expiresAtNanos()) {
            pendingPreviews.remove(requester);
            send(source, "evacuation-preview-expired");
            return;
        }
        PlanResult result = collectPlan();
        if (result.errorKey() != null) {
            send(source, result.errorKey());
            return;
        }
        if (System.nanoTime() > previous.expiresAtNanos()) {
            pendingPreviews.remove(requester);
            send(source, "evacuation-preview-expired");
            return;
        }
        Plan current = result.plan();
        if (!previous.plan().matches(current)) {
            pendingPreviews.replace(requester,
                    new PendingPreview(current, System.nanoTime() + PREVIEW_VALIDITY_NANOS));
            sendPreview(source, "evacuation-preview-changed", current);
            return;
        }
        if (!mod.isLocked()) {
            pendingPreviews.remove(requester);
            send(source, "evacuation-not-locked");
            return;
        }
        if (pendingPreviews.remove(requester) == null) {
            send(source, "evacuation-preview-required");
            return;
        }
        send(source, "evacuation-execution-started", Map.of(
                "%count%", String.valueOf(current.eligiblePlayers().size())));
        evacuatePlan(current, new AuditContext(requester, requesterUuid,
                targetWorldName(current), "MANUAL", true));
    }

    private boolean canEvacuate(CommandSourceStack source, String requester) {
        if (requester == null) {
            return false;
        }
        if (!mod.isLocked()) {
            send(source, "evacuation-not-locked");
            return false;
        }
        if (!mod.currentSettings().evacuationEnabled()) {
            send(source, "evacuation-disabled");
            return false;
        }
        return true;
    }

    private PlanResult collectPlan() {
        MinecraftServer server = mod.liveServer();
        if (server == null) {
            return new PlanResult(null, "evacuation-target-unavailable");
        }
        ServerLevel targetLevel = resolveTargetWorld(server);
        if (targetLevel == null) {
            return new PlanResult(null, "evacuation-target-unavailable");
        }
        boolean excludeBypass = mod.currentSettings().evacuationExcludeBypass();
        Set<UUID> eligiblePlayers = new HashSet<>();
        int bypassedPlayers = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isGuarded(player.level())) {
                continue;
            }
            if (excludeBypass && canBypass(player, player.level())) {
                bypassedPlayers++;
                continue;
            }
            eligiblePlayers.add(player.getUUID());
        }
        return new PlanResult(new Plan(targetLevel, Set.copyOf(eligiblePlayers), bypassedPlayers), null);
    }

    private void sendPreview(CommandSourceStack source, String key, Plan plan) {
        send(source, key, Map.of(
                "%count%", String.valueOf(plan.eligiblePlayers().size()),
                "%bypassed%", String.valueOf(plan.bypassedPlayers()),
                "%world%", targetWorldName(plan)));
    }

    private static String targetWorldName(Plan plan) {
        return plan.targetLevel().dimension().identifier().toString();
    }

    private void evacuateAutomatically() {
        MinecraftServer server = mod.liveServer();
        if (server == null) {
            return;
        }
        if (!mod.isLocked() || !mod.currentSettings().evacuationEnabled()) {
            return;
        }
        ServerLevel targetLevel = resolveTargetWorld(server);
        if (targetLevel == null) {
            LockEndNeoForgeMod.LOGGER.warn(
                    "Could not evacuate End players: no target world is available.");
            return;
        }
        AuditContext audit = new AuditContext("System", null,
                targetLevel.dimension().identifier().toString(), "AUTOMATIC", false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            movePlayer(player, targetLevel, audit);
        }
    }

    private void evacuatePlan(Plan plan, AuditContext audit) {
        Map<UUID, ServerPlayer> onlinePlayers = new HashMap<>();
        for (ServerPlayer player : mod.liveServer().getPlayerList().getPlayers()) {
            onlinePlayers.put(player.getUUID(), player);
        }
        for (UUID playerId : plan.eligiblePlayers()) {
            ServerPlayer player = onlinePlayers.get(playerId);
            if (player == null) {
                recordOutcome(audit, playerId, "SKIPPED_OFFLINE");
            } else {
                movePlayer(player, plan.targetLevel(), audit);
            }
        }
    }

    private void movePlayer(ServerPlayer player, ServerLevel targetLevel, AuditContext audit) {
        boolean excludeBypass = mod.currentSettings().evacuationExcludeBypass();
        if (!isGuarded(player.level())) {
            if (audit.requirePlannedPlayer()) {
                recordOutcome(audit, player.getUUID(), "SKIPPED_LEFT_END");
            }
            return;
        }
        if (excludeBypass && canBypass(player, player.level())) {
            if (audit.requirePlannedPlayer()) {
                recordOutcome(audit, player.getUUID(), "SKIPPED_BYPASS");
            }
            return;
        }
        try {
            teleportToTarget(player, targetLevel);
        } catch (RuntimeException exception) {
            LockEndNeoForgeMod.LOGGER.warn("Could not start evacuation for player {}: {}",
                    player.getUUID(), exception.getMessage());
            recordOutcome(audit, player.getUUID(), "FAILED");
            return;
        }
        recordOutcome(audit, player.getUUID(), "MOVED");
        player.sendSystemMessage(Component.literal(
                LockEndNeoForgeMod.messages().text("evacuation-complete")));
        mod.statistics().recordEvacuatedPlayer();
    }

    private void teleportToTarget(ServerPlayer player, ServerLevel targetLevel) {
        LevelData.RespawnData respawn = targetLevel.getRespawnData();
        player.teleport(new TeleportTransition(
                targetLevel,
                respawnVec(respawn),
                Vec3.ZERO,
                respawn.yaw(),
                respawn.pitch(),
                TeleportTransition.DO_NOTHING));
    }

    private void recordOutcome(AuditContext audit, UUID playerId, String result) {
        mod.history().addEntry(audit.actor(), audit.actorUuid(), "EVACUATE",
                audit.source() + " -> " + audit.targetWorld(), result, playerId, null, null);
    }

    private boolean isGuarded(ServerLevel level) {
        return mod.isGuarded(level);
    }

    private boolean canBypass(ServerPlayer player, ServerLevel level) {
        String worldKey = level.dimension().identifier().toString();
        return mod.currentSettings().bypassChecker().canBypass(
                player.getName().getString(), player.getUUID(), worldKey,
                () -> guardPermission(player));
    }

    private boolean guardPermission(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }

    private ServerLevel resolveTargetWorld(MinecraftServer server) {
        String configuredTarget = mod.currentSettings().evacuationTargetWorld();
        if (configuredTarget != null && !configuredTarget.isBlank()) {
            Identifier key = configuredTarget.contains(":")
                    ? Identifier.tryParse(configuredTarget) : null;
            if (key != null) {
                ServerLevel namedLevel = server.getLevel(
                        ResourceKey.create(Registries.DIMENSION, key));
                if (namedLevel != null) {
                    return namedLevel;
                }
            } else if (configuredTarget.contains(":")) {
                LockEndNeoForgeMod.LOGGER.warn("Invalid evacuation.target-world '{}';"
                        + " using the overworld instead.", configuredTarget);
            }
        }
        return server.overworld();
    }

    private static void send(CommandSourceStack source, String key) {
        send(source, key, Map.of());
    }

    private static void send(CommandSourceStack source, String key, Map<String, String> placeholders) {
        source.sendSuccess(() -> Component.literal(
                LockEndNeoForgeMod.messages().text(key, placeholders)), false);
    }

    private static Vec3 respawnVec(LevelData.RespawnData respawn) {
        return new Vec3(respawn.pos().getX() + 0.5, respawn.pos().getY(), respawn.pos().getZ() + 0.5);
    }

    private record Plan(ServerLevel targetLevel, Set<UUID> eligiblePlayers, int bypassedPlayers) {
        private boolean matches(Plan other) {
            return targetLevel().dimension().identifier().equals(other.targetLevel().dimension().identifier())
                    && eligiblePlayers.equals(other.eligiblePlayers())
                    && bypassedPlayers == other.bypassedPlayers();
        }
    }

    private record PlanResult(Plan plan, String errorKey) {
    }

    private record PendingPreview(Plan plan, long expiresAtNanos) {
    }

    private record AuditContext(String actor, UUID actorUuid, String targetWorld,
                                String source, boolean requirePlannedPlayer) {
    }
}
