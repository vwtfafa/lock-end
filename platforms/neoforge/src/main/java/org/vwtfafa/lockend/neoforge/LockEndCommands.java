package org.vwtfafa.lockend.neoforge;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.vwtfafa.lockend.api.LockAction;
import org.vwtfafa.lockend.core.LockStateManager;
import org.vwtfafa.lockend.core.LockStateRepository;

import java.util.UUID;

/**
 * The NeoForge side of the Lock-End command surface, matching the Paper
 * per-node permission structure: {@code /endlock lock|unlock|reason set|
 * reason clear|reload} require the game admin operator level, and
 * {@code /endlock status} is public. NeoForge's vanilla permission system
 * does not provide arbitrary named permission nodes, so the Paper
 * {@code endlock.admin} and {@code endlock.reload} distinction is expressed
 * as operator power; permission plugins cannot override it here.
 *
 * <p>The tree is registered through the game-bus {@code RegisterCommandsEvent}
 * using the vanilla 26.3 permission check
 * {@code Commands.hasPermission(Commands.LEVEL_ADMINS)}.</p>
 */
final class LockEndCommands {

    private static final String COMMAND_NAME = "endlock";
    private static final String REASON_USAGE = "Usage: /endlock reason set <reason>";

    private final LockStateManager state;
    private final LockStateRepository repository;
    private final EndLockGuard guard;
    private final LockEndNeoForgeMod mod;

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
            LockEndNeoForgeMod mod) {
        this.state = state;
        this.repository = repository;
        this.guard = guard;
        this.mod = mod;
    }

    /**
     * Registers the {@code /endlock} tree onto the given dispatcher. The
     * permission gate uses {@code Commands.hasPermission} on the vanilla 26.3
     * permission system ({@code CommandSourceStack} is a
     * {@code PermissionSetSupplier}); {@code status} stays public.
     *
     * @param dispatcher the command dispatcher supplied by the registration
     *                   event
     */
    void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(COMMAND_NAME)
                .then(Commands.literal("status").executes(this::executeStatus))
                .then(Commands.literal("lock").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(context -> executeChangeLock(context, true)))
                .then(Commands.literal("unlock").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(context -> executeChangeLock(context, false)))
                .then(Commands.literal("reason")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.literal("set")
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(this::executeSetReason)))
                        .then(Commands.literal("clear").executes(this::executeClearReason)))
                .then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(this::executeReload)));
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
