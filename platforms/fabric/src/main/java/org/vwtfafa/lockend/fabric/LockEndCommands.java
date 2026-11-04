package org.vwtfafa.lockend.fabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import org.vwtfafa.lockend.api.LockAction;
import org.vwtfafa.lockend.core.LockStateManager;
import org.vwtfafa.lockend.core.LockStateRepository;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * The Fabric side of the Lock-End command surface, matching the Paper
 * per-node permission style: {@code /endlock lock|unlock|reason set|reason
 * clear} require the {@code lock-end:admin} node, {@code /endlock reload}
 * requires {@code lock-end:reload}, and {@code /endlock status} is public
 * (all with an operator-level fallback). History, undo and scheduling
 * commands follow once their Paper behavior is extracted into the shared
 * core.
 *
 * <p>Commands are registered for every command selection (integrated and
 * dedicated servers); clients only see subcommands they may execute.</p>
 */
final class LockEndCommands {

    private static final String COMMAND_NAME = "endlock";
    private static final String NAMESPACE = "lock-end";
    private static final String ADMIN_PERMISSION_NODE = "admin";
    private static final String RELOAD_PERMISSION_NODE = "reload";
    private static final String REASON_USAGE = "Usage: /endlock reason set <reason>";

    private final LockStateManager state;
    private final LockStateRepository repository;
    private final EndLockGuard guard;
    private final LockEndFabricMod mod;

    /**
     * Creates the command tree around the shared state, the persistence
     * repository, the dimension guard and the owning mod instance (which
     * publishes settings and reload).
     *
     * @param state      the shared runtime lock state
     * @param repository the persistence contract implementation
     * @param guard      the dimension guard used for immediate evacuation
     * @param mod        the mod owning settings, state and guard
     */
    LockEndCommands(
            LockStateManager state,
            LockStateRepository repository,
            EndLockGuard guard,
            LockEndFabricMod mod) {
        this.state = state;
        this.repository = repository;
        this.guard = guard;
        this.mod = mod;
    }

    /**
     * Registers the {@code /endlock} tree using the verified Fabric command
     * API v2 callback context. Permission gate: the per-subcommand
     * {@code lock-end:admin} / {@code lock-end:reload} nodes, each falling
     * back to the game master/admin operator level (level 2 semantics,
     * matching the Paper permission defaults); {@code status} is public.
     *
     * @param dispatcher      the command dispatcher
     * @param buildContext    the command build context
     * @param commandSelection the selection; unused because the tree is
     *                         identical on dedicated and integrated servers
     */
    void registerAll(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext,
            Commands.CommandSelection commandSelection) {
        dispatcher.register(Commands.literal(COMMAND_NAME)
                .then(Commands.literal("status").executes(this::executeStatus))
                .then(Commands.literal("lock").requires(requires(ADMIN_PERMISSION_NODE))
                        .executes(context -> executeChangeLock(context, true)))
                .then(Commands.literal("unlock").requires(requires(ADMIN_PERMISSION_NODE))
                        .executes(context -> executeChangeLock(context, false)))
                .then(Commands.literal("reason")
                        .requires(requires(ADMIN_PERMISSION_NODE))
                        .then(Commands.literal("set")
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(this::executeSetReason)))
                        .then(Commands.literal("clear").executes(this::executeClearReason)))
                .then(Commands.literal("reload").requires(requires(RELOAD_PERMISSION_NODE))
                        .executes(this::executeReload)));
    }

    private static Predicate<CommandSourceStack> requires(String node) {
        return PermissionPredicates.require(
                Identifier.fromNamespaceAndPath(NAMESPACE, node), PermissionLevel.ADMINS);
    }

    private int executeChangeLock(CommandContext<CommandSourceStack> context, boolean lock) {
        CommandSourceStack source = context.getSource();
        boolean changed = state.updateLocked(lock, lock ? LockAction.LOCK : LockAction.UNLOCK,
                actorName(source), actorUuid(source));
        if (!changed) {
            source.sendFailure(Component.literal(lock
                    ? "The End is already locked."
                    : "The End is already unlocked."));
            return Command.SINGLE_SUCCESS;
        }

        repository.save(state.getState());
        if (lock) {
            guard.evacuate();
        }
        broadcast(source.getServer(), lock);
        source.sendSuccess(() -> Component.literal(lock ? "Locked the End." : "Unlocked the End."), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String reason = state.getLockReason();
        if (state.isLocked()) {
            source.sendSuccess(() -> Component.literal(
                    "The End is LOCKED. Reason: " + (reason.isBlank() ? "-" : reason)), false);
        } else {
            source.sendSuccess(() -> Component.literal("The End is unlocked."), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeSetReason(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String reason = StringArgumentType.getString(context, "reason").trim();
        if (reason.isEmpty()) {
            source.sendFailure(Component.literal(REASON_USAGE));
            return Command.SINGLE_SUCCESS;
        }
        mod.currentSettings().setLockReason(reason);
        source.sendSuccess(() -> Component.literal("Lock reason set to: " + reason), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeClearReason(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        mod.currentSettings().clearReason();
        source.sendSuccess(() -> Component.literal("Lock reason cleared; restored default: "
                + mod.currentSettings().lockReason()), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String failure = mod.reloadSettings();
        if (failure == null) {
            source.sendSuccess(() -> Component.literal("Reloaded the Lock-End settings."), true);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "Lock-End settings reloaded, but recovery was needed: " + failure
                            + ". Fix the file and run /endlock reload again."), true);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void broadcast(MinecraftServer server, boolean locked) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(locked
                ? "The End is now locked! " + describeReason()
                : "The End has been unlocked."), false);
    }

    private String describeReason() {
        String reason = state.getLockReason();
        return reason.isBlank() ? "" : "Reason: " + reason;
    }

    private static String actorName(CommandSourceStack source) {
        return source.getTextName();
    }

    private static UUID actorUuid(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player.getUUID();
        }
        return null;
    }
}
