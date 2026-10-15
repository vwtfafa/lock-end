package org.vwtfafa.lockEnd;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Warns and moves players out of the End after a lock becomes active.
 */
public class EvacuationService {
    private static final long PREVIEW_VALIDITY_NANOS = Duration.ofSeconds(30).toNanos();

    private final LockEnd plugin;
    private final Map<String, PendingPreview> pendingPreviews = new ConcurrentHashMap<>();
    private ScheduledTask task;
    private volatile boolean shuttingDown;

    public EvacuationService(LockEnd plugin) {
        this.plugin = plugin;
    }

    /**
     * Warns players covered by the End lock and schedules their evacuation.
     */
    public void schedule() {
        if (!plugin.getConfig().getBoolean("evacuation.enabled", false)) {
            return;
        }
        cancel();
        long warningSeconds = Math.max(0, plugin.getConfig().getLong("evacuation.warning-seconds", 10));
        boolean excludeBypass = plugin.getConfig().getBoolean("evacuation.exclude-bypass", true);
        Component warning = plugin.message("evacuation-warning",
                Map.of("%seconds%", String.valueOf(warningSeconds)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getPaperScheduler().executeEntity(player, () -> {
                World playerWorld = player.getWorld();
                if (plugin.isGuardedEndWorld(playerWorld)
                        && (!excludeBypass || !plugin.getWhitelistChecker().canBypass(player, playerWorld))) {
                    player.sendMessage(warning);
                }
            }, () -> {});
        }
        task = plugin.getPaperScheduler().runGlobalLater(this::evacuate, warningSeconds * 20L);
    }

    /**
     * Shows a side-effect-free preview of the players an End evacuation would
     * affect. The preview is bound to the requester and expires after 30 seconds.
     */
    public void preview(CommandSender sender, String requester, UUID requesterUuid) {
        if (!canEvacuate(sender)) {
            return;
        }
        long now = System.nanoTime();
        pendingPreviews.entrySet().removeIf(entry -> now > entry.getValue().expiresAtNanos());
        collectPlan(result -> {
            if (result.errorKey() != null) {
                send(sender, result.errorKey(), Map.of());
                return;
            }
            pendingPreviews.put(requester,
                    new PendingPreview(result.plan(), System.nanoTime() + PREVIEW_VALIDITY_NANOS));
            sendPreview(sender, "evacuation-preview", result.plan());
        });
    }

    /**
     * Executes only after a recent preview by the same requester. A fresh
     * snapshot is compared immediately before starting teleports.
     */
    public void executePreview(CommandSender sender, String requester, UUID requesterUuid) {
        if (!canEvacuate(sender)) {
            return;
        }
        PendingPreview previous = pendingPreviews.get(requester);
        if (previous == null) {
            send(sender, "evacuation-preview-required", Map.of());
            return;
        }
        if (System.nanoTime() > previous.expiresAtNanos()) {
            pendingPreviews.remove(requester, previous);
            send(sender, "evacuation-preview-expired", Map.of());
            return;
        }
        collectPlan(result -> {
            if (result.errorKey() != null) {
                send(sender, result.errorKey(), Map.of());
                return;
            }
            if (System.nanoTime() > previous.expiresAtNanos()) {
                pendingPreviews.remove(requester, previous);
                send(sender, "evacuation-preview-expired", Map.of());
                return;
            }
            Plan current = result.plan();
            if (!previous.plan().matches(current)) {
                PendingPreview refreshed = new PendingPreview(
                        current, System.nanoTime() + PREVIEW_VALIDITY_NANOS);
                if (!pendingPreviews.replace(requester, previous, refreshed)) {
                    send(sender, "evacuation-preview-required", Map.of());
                    return;
                }
                sendPreview(sender, "evacuation-preview-changed", current);
                return;
            }
            if (!plugin.isLocked()) {
                pendingPreviews.remove(requester);
                send(sender, "evacuation-not-locked", Map.of());
                return;
            }
            if (!pendingPreviews.remove(requester, previous)) {
                send(sender, "evacuation-preview-required", Map.of());
                return;
            }
            send(sender, "evacuation-execution-started",
                    Map.of("%count%", String.valueOf(current.eligiblePlayers().size())));
            evacuate(current, new AuditContext(requester, requesterUuid, current.targetWorld().getName(),
                    "MANUAL", true));
        });
    }

    /**
     * Cancels a pending automatic evacuation.
     */
    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        shuttingDown = true;
        cancel();
        pendingPreviews.clear();
    }

    private boolean canEvacuate(CommandSender sender) {
        if (!plugin.isLocked()) {
            send(sender, "evacuation-not-locked", Map.of());
            return false;
        }
        if (!plugin.getConfig().getBoolean("evacuation.enabled", false)) {
            send(sender, "evacuation-disabled", Map.of());
            return false;
        }
        return true;
    }

    private void collectPlan(Consumer<PlanResult> completion) {
        World targetWorld = resolveTargetWorld();
        if (targetWorld == null) {
            completion.accept(new PlanResult(null, "evacuation-target-unavailable"));
            return;
        }
        List<Player> players = List.copyOf(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            completion.accept(new PlanResult(new Plan(targetWorld, Set.of(), 0), null));
            return;
        }

        boolean excludeBypass = plugin.getConfig().getBoolean("evacuation.exclude-bypass", true);
        Set<UUID> eligiblePlayers = ConcurrentHashMap.newKeySet();
        AtomicInteger bypassedPlayers = new AtomicInteger();
        AtomicInteger remaining = new AtomicInteger(players.size());
        for (Player player : players) {
            AtomicBoolean resolved = new AtomicBoolean();
            Runnable completed = () -> {
                if (resolved.compareAndSet(false, true) && remaining.decrementAndGet() == 0) {
                    completePlan(completion,
                            new PlanResult(new Plan(targetWorld, Set.copyOf(eligiblePlayers),
                                    bypassedPlayers.get()), null));
                }
            };
            boolean scheduled = plugin.getPaperScheduler().executeEntity(player, () -> {
                try {
                    if (!player.isOnline()) {
                        return;
                    }
                    World playerWorld = player.getWorld();
                    if (!plugin.isGuardedEndWorld(playerWorld)) {
                        return;
                    }
                    if (excludeBypass && plugin.getWhitelistChecker().canBypass(player, playerWorld)) {
                        bypassedPlayers.incrementAndGet();
                        return;
                    }
                    eligiblePlayers.add(player.getUniqueId());
                } finally {
                    completed.run();
                }
            }, completed);
            if (!scheduled) {
                completed.run();
            }
        }
    }

    private void completePlan(Consumer<PlanResult> completion, PlanResult result) {
        if (shuttingDown) {
            return;
        }
        try {
            plugin.getPaperScheduler().executeGlobal(() -> {
                if (!shuttingDown) {
                    completion.accept(result);
                }
            });
        } catch (IllegalPluginAccessException exception) {
            if (!shuttingDown) {
                plugin.getLogger().warning("Could not finish End evacuation preview: " + exception.getMessage());
            }
        }
    }

    private void sendPreview(CommandSender sender, String key, Plan plan) {
        send(sender, key, Map.of(
                "%count%", String.valueOf(plan.eligiblePlayers().size()),
                "%bypassed%", String.valueOf(plan.bypassedPlayers()),
                "%world%", plan.targetWorld().getName()));
    }

    private void send(CommandSender sender, String key, Map<String, String> placeholders) {
        plugin.getPaperScheduler().sendMessage(sender, plugin.message(key, placeholders));
    }

    private void evacuate() {
        task = null;
        if (!plugin.isLocked() || !plugin.getConfig().getBoolean("evacuation.enabled", false)) {
            return;
        }
        World targetWorld = resolveTargetWorld();
        if (targetWorld == null) {
            plugin.getLogger().warning("Could not evacuate End players: no target world is available.");
            return;
        }
        boolean excludeBypass = plugin.getConfig().getBoolean("evacuation.exclude-bypass", true);
        Component completeMessage = plugin.message("evacuation-complete", Map.of());
        Location target = targetWorld.getSpawnLocation();
        AuditContext audit = new AuditContext("System", null, targetWorld.getName(), "AUTOMATIC", false);
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            movePlayer(player, target, excludeBypass, completeMessage, audit);
        }
    }

    private void evacuate(Plan plan, AuditContext audit) {
        Location target = plan.targetWorld().getSpawnLocation();
        boolean excludeBypass = plugin.getConfig().getBoolean("evacuation.exclude-bypass", true);
        Component completeMessage = plugin.message("evacuation-complete", Map.of());
        Set<UUID> plannedPlayers = plan.eligiblePlayers();
        Map<UUID, Player> onlinePlayers = new java.util.HashMap<>();
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            onlinePlayers.put(player.getUniqueId(), player);
        }
        for (UUID playerId : plannedPlayers) {
            Player player = onlinePlayers.get(playerId);
            if (player == null) {
                recordOutcome(audit, playerId, "SKIPPED_OFFLINE");
            } else {
                movePlayer(player, target, excludeBypass, completeMessage, audit);
            }
        }
    }

    private void movePlayer(Player player, Location target, boolean excludeBypass, Component completeMessage,
                            AuditContext audit) {
        AtomicBoolean resolved = new AtomicBoolean();
        boolean scheduled = plugin.getPaperScheduler().executeEntity(player, () -> {
            if (!player.isOnline()) {
                resolveOutcome(audit, player.getUniqueId(), "SKIPPED_OFFLINE", resolved);
                return;
            }
            World playerWorld = player.getWorld();
            if (!plugin.isGuardedEndWorld(playerWorld)) {
                if (audit.requirePlannedPlayer()) {
                    resolveOutcome(audit, player.getUniqueId(), "SKIPPED_LEFT_END", resolved);
                }
                return;
            }
            if (excludeBypass && plugin.getWhitelistChecker().canBypass(player, playerWorld)) {
                if (audit.requirePlannedPlayer()) {
                    resolveOutcome(audit, player.getUniqueId(), "SKIPPED_BYPASS", resolved);
                }
                return;
            }
            try {
                player.teleportAsync(target.clone()).whenComplete((success, error) -> {
                    if (error != null || !Boolean.TRUE.equals(success)) {
                        if (error != null) {
                            plugin.getLogger().warning("Could not evacuate player " + player.getUniqueId()
                                    + " from the End: " + error.getMessage());
                        } else {
                            plugin.getLogger().warning("Could not evacuate player " + player.getUniqueId()
                                    + " from the End.");
                        }
                        resolveOutcome(audit, player.getUniqueId(), "FAILED", resolved);
                        return;
                    }
                    resolveOutcome(audit, player.getUniqueId(), "MOVED", resolved);
                    if (shuttingDown) {
                        return;
                    }
                    plugin.getPaperScheduler().executeEntity(player, () -> {
                        if (!canNotifyCompletion(shuttingDown, plugin.isEnabled(), player.isOnline())) {
                            return;
                        }
                        player.sendMessage(completeMessage);
                        plugin.recordEvacuatedPlayer();
                    }, () -> {});
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not start evacuation for player " + player.getUniqueId()
                        + ": " + exception.getMessage());
                resolveOutcome(audit, player.getUniqueId(), "FAILED", resolved);
            }
        }, () -> resolveOutcome(audit, player.getUniqueId(), "SKIPPED_OFFLINE", resolved));
        if (!scheduled) {
            resolveOutcome(audit, player.getUniqueId(), "SKIPPED_OFFLINE", resolved);
        }
    }

    private void resolveOutcome(AuditContext audit, UUID playerId, String result, AtomicBoolean resolved) {
        if ((audit.requirePlannedPlayer() || "MOVED".equals(result) || "FAILED".equals(result))
                && resolved.compareAndSet(false, true)) {
            recordOutcome(audit, playerId, result);
        }
    }

    private void recordOutcome(AuditContext audit, UUID playerId, String result) {
        if (shuttingDown) {
            return;
        }
        try {
            plugin.getPaperScheduler().executeGlobal(() -> {
                if (!shuttingDown) {
                    plugin.getHistoryCommand().recordEvacuationResult(
                            audit.actor(), audit.actorUuid(), playerId, audit.source(), result, audit.targetWorld());
                }
            });
        } catch (IllegalPluginAccessException exception) {
            if (!shuttingDown) {
                plugin.getLogger().warning("Could not record evacuation outcome for player " + playerId + ": "
                        + exception.getMessage());
            }
        }
    }

    static boolean canNotifyCompletion(boolean shuttingDown, boolean pluginEnabled, boolean playerOnline) {
        return !shuttingDown && pluginEnabled && playerOnline;
    }

    private World resolveTargetWorld() {
        String configuredTarget = plugin.getConfig().getString("evacuation.target-world", "world");
        World targetWorld = null;
        if (configuredTarget != null && !configuredTarget.isBlank()) {
            targetWorld = resolveConfiguredWorld(configuredTarget);
        }
        if (targetWorld == null) {
            targetWorld = Bukkit.getWorlds().stream()
                    .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                    .findFirst()
                    .orElse(null);
        }
        return targetWorld;
    }

    private World resolveConfiguredWorld(String configuredTarget) {
        if (configuredTarget.contains(":")) {
            NamespacedKey key = NamespacedKey.fromString(configuredTarget);
            if (key != null) {
                World namespacedWorld = Bukkit.getWorld(key);
                if (namespacedWorld != null) {
                    return namespacedWorld;
                }
            }
        }
        return Bukkit.getWorld(configuredTarget);
    }

    private record Plan(World targetWorld, Set<UUID> eligiblePlayers, int bypassedPlayers) {
        private boolean matches(Plan other) {
            return samePreview(targetWorld.getName(), eligiblePlayers, bypassedPlayers,
                    other.targetWorld.getName(), other.eligiblePlayers, other.bypassedPlayers);
        }
    }

    static boolean samePreview(String targetWorld, Set<UUID> eligiblePlayers, int bypassedPlayers,
                               String currentTargetWorld, Set<UUID> currentEligiblePlayers,
                               int currentBypassedPlayers) {
        return targetWorld.equals(currentTargetWorld)
                && eligiblePlayers.equals(currentEligiblePlayers)
                && bypassedPlayers == currentBypassedPlayers;
    }

    private record PlanResult(Plan plan, String errorKey) {}

    private record PendingPreview(Plan plan, long expiresAtNanos) {}

    private record AuditContext(String actor, UUID actorUuid, String targetWorld,
                                String source, boolean requirePlannedPlayer) {}
}
