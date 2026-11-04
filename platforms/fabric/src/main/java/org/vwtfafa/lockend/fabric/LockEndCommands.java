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

/**
 * The Fabric side of the Lock-End command surface. A small subset of the
 * Paper command tree: {@code /endlock lock}, {@code /endlock unlock},
 * {@code /endlock status} and {@code /endlock reason set|clear} (the runtime
 * lock-reason step from the Paper {@code reason} command). History, undo and
 * scheduling commands follow once their Paper behavior is extracted into the
 * shared core.
 *
 * <p>Commands are registered for every command selection (integrated and
 * dedicated servers) and require operator level 2.</p>
 */
final class LockEndCommands {

    private static final String COMMAND_NAME = "endlock";
    private static final String NAMESPACE = "lock-end";
    private static final String COMMAND_PERMISSION_NODE = "command";

    private final LockStateManager state;
    private final LockStateRepository repository;
    private final EndLockGuard guard;
    private final FabricEndLockConfig config;

    /**
     * Creates the command tree around the shared state, the persistence
     * repository, the dimension guard and the settings file owner.
     *
     * @param state      the shared runtime lock state
     * @param repository the persistence contract implementation
     * @param guard      the dimension guard used for immediate evacuation
     * @param config     the settings file owner for runtime reason changes
     */
    LockEndCommands(
            LockStateManager state,
            LockStateRepository repository,
            EndLockGuard guard,
            FabricEndLockConfig config) {
        this.state = state;
        this.repository = repository;
        this.guard = guard;
        this.config = config;
    }

    /**
     * Registers the {@code /endlock} tree using the verified Fabric command
     * API v2 callback context. Permission gate: the {@code lock-end:command}
     * node, falling back to the game master/admin operator level (level 2
     * semantics, matching the Paper permission default).
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
                .requires(PermissionPredicates.require(
                        Identifier.fromNamespaceAndPath(NAMESPACE, COMMAND_PERMISSION_NODE),
                        PermissionLevel.ADMINS))
                .then(Commands.literal("lock").executes(context -> executeChangeLock(context, true)))
                .then(Commands.literal("unlock").executes(context -> executeChangeLock(context, false)))
                .then(Commands.literal("status").executes(this::executeStatus))
                .then(Commands.literal("reason")
                        .then(Commands.literal("set")
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(this::executeSetReason)))
                        .then(Commands.literal("clear").executes(this::executeClearReason))));
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
            source.sendFailure(Component.literal("Usage: /endlock reason set <reason>"));
            return Command.SINGLE_SUCCESS;
        }
        config.setLockReason(reason);
        source.sendSuccess(() -> Component.literal("Lock reason set to: " + reason), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeClearReason(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        config.clearReason();
        source.sendSuccess(() -> Component.literal("Lock reason cleared; restored default: "
                + config.lockReason()), true);
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
