package org.vwtfafa.lockend.neoforge;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.vwtfafa.lockend.api.LockAction;
import org.vwtfafa.lockend.core.LockStateManager;
import org.vwtfafa.lockend.core.LockStateRepository;
import org.vwtfafa.lockend.core.ScheduleSupport;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The NeoForge side of the Lock-End command surface, matching the Paper
 * per-node permission structure: {@code /endlock lock|unlock|reason set|
 * reason clear|schedule|cancel|pause|resume|reload} require the game admin
 * operator level, and {@code /endlock status} is public. NeoForge's vanilla
 * permission system does not provide arbitrary named permission nodes, so
 * the Paper {@code endlock.admin}/{@code endlock.reload}/{@code endlock.toggle}
 * distinction is expressed as operator power; permission plugins cannot
 * override it here.
 *
 * <p>The tree is registered through the game-bus {@code RegisterCommandsEvent}
 * using the vanilla 26.3 permission check
 * {@code Commands.hasPermission(Commands.LEVEL_ADMINS)}. Deliberately a copy
 * of the Fabric adapter's command surface with the permission check swapped
 * to the vanilla operator level.</p>
 */
final class LockEndCommands {

    private static final String COMMAND_NAME = "endlock";
    private static final String REASON_USAGE = "Usage: /endlock reason set <reason>";
    private static final String SCHEDULED_UNLOCK_USAGE =
            "Usage: /endlock unlockin <days|duration> or /endlock unlockat <yyyy-MM-dd> <HH:mm>";
    private static final String SCHEDULED_LOCK_USAGE =
            "Usage: /endlock lockin <minutes|duration> or /endlock lockat <yyyy-MM-dd> <HH:mm>";
    private static final List<String> LOCK_DURATIONS = List.of("15", "30", "60", "30m", "2h");
    private static final List<String> UNLOCK_DURATIONS = List.of("1", "7", "30", "12h", "7d");
    private static final DateTimeFormatter DATE_HINT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
                .then(Commands.literal("unlockin").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests(LockEndCommands::suggestUnlockDurations)
                                .executes(this::executeUnlockIn)))
                .then(Commands.literal("unlockat").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(dateTimeArguments()
                                .executes(this::executeUnlockAt)))
                .then(Commands.literal("lockin").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests(LockEndCommands::suggestLockDurations)
                                .executes(this::executeLockIn)))
                .then(Commands.literal("lockat").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(dateTimeArguments()
                                .executes(this::executeLockAt)))
                .then(Commands.literal("schedule")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.literal("status").executes(this::executeScheduleStatus))
                        .then(Commands.literal("clear").executes(this::executeScheduleClear)))
                .then(Commands.literal("cancel")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(this::executeCancelSchedule))
                .then(Commands.literal("pause")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(this::executePauseSchedule))
                .then(Commands.literal("resume")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(this::executeResumeSchedule))
                .then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(this::executeReload)));
    }

    /**
     * Two word arguments (date + time) shared by unlockat and lockat, with
     * the Paper plugin's date and time suggestions.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> dateTimeArguments() {
        String tomorrow = LocalDate.now().plusDays(1).format(DATE_HINT_FORMAT);
        return Commands.argument("date", StringArgumentType.word())
                .suggests((context, builder) -> {
                    builder.suggest(tomorrow);
                    return builder.buildFuture();
                })
                .then(Commands.argument("time", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("00:00");
                            builder.suggest("12:00");
                            builder.suggest("23:59");
                            return builder.buildFuture();
                        }));
    }

    /**
     * Adds every suggestion still matching the typed prefix, mirroring the
     * Paper plugin's suggestion behavior.
     */
    private static CompletableFuture<Suggestions> suggestDurations(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            List<String> options) {
        String remaining = builder.getRemainingLowerCase();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestUnlockDurations(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestDurations(context, builder, UNLOCK_DURATIONS);
    }

    private static CompletableFuture<Suggestions> suggestLockDurations(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestDurations(context, builder, LOCK_DURATIONS);
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

    // --- Scheduled actions ------------------------------------------------

    /**
     * A bare number keeps its legacy meaning (days); "12h"/"7d" style
     * durations schedule an exact point in time instead, mirroring the Paper
     * plugin's {@code unlockin} split.
     */
    private int executeUnlockIn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String raw = StringArgumentType.getString(context, "value");
        Integer days = ScheduleSupport.tryParsePositiveInt(raw);
        if (days != null) {
            mod.applyScheduled("unlock", LocalDateTime.now().plusDays(days));
            source.sendSuccess(() -> Component.literal("Scheduled unlock in " + days + " days."), true);
            return Command.SINGLE_SUCCESS;
        }
        LocalDateTime target = ScheduleSupport.parseDurationTarget(raw, LocalDateTime.now());
        if (target == null) {
            source.sendFailure(Component.literal(SCHEDULED_UNLOCK_USAGE));
            return Command.SINGLE_SUCCESS;
        }
        mod.applyScheduled("unlock", target);
        source.sendSuccess(() -> Component.literal("Scheduled unlock at "
                + target.format(ScheduleSupport.SCHEDULE_FORMAT) + "."), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeUnlockAt(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        LocalDateTime target = ScheduleSupport.parseScheduleTime(
                StringArgumentType.getString(context, "date") + " "
                        + StringArgumentType.getString(context, "time"));
        if (target == null || target.isBefore(LocalDateTime.now())) {
            source.sendFailure(Component.literal(SCHEDULED_UNLOCK_USAGE));
            return Command.SINGLE_SUCCESS;
        }
        mod.applyScheduled("unlock", target);
        source.sendSuccess(() -> Component.literal("Scheduled unlock at "
                + target.format(ScheduleSupport.SCHEDULE_FORMAT) + "."), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * A bare number keeps its legacy meaning (minutes); duration tokens
     * schedule an exact point in time instead, mirroring the Paper plugin's
     * {@code lockin} split.
     */
    private int executeLockIn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String raw = StringArgumentType.getString(context, "value");
        Integer minutes = ScheduleSupport.tryParsePositiveInt(raw);
        if (minutes != null) {
            mod.applyScheduled("lock", LocalDateTime.now().plusMinutes(minutes));
            source.sendSuccess(() -> Component.literal("Scheduled lock in " + minutes + " minutes."), true);
            return Command.SINGLE_SUCCESS;
        }
        LocalDateTime target = ScheduleSupport.parseDurationTarget(raw, LocalDateTime.now());
        if (target == null) {
            source.sendFailure(Component.literal(SCHEDULED_LOCK_USAGE));
            return Command.SINGLE_SUCCESS;
        }
        mod.applyScheduled("lock", target);
        source.sendSuccess(() -> Component.literal("Scheduled lock at "
                + target.format(ScheduleSupport.SCHEDULE_FORMAT) + "."), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeLockAt(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        LocalDateTime target = ScheduleSupport.parseScheduleTime(
                StringArgumentType.getString(context, "date") + " "
                        + StringArgumentType.getString(context, "time"));
        if (target == null || target.isBefore(LocalDateTime.now())) {
            source.sendFailure(Component.literal(SCHEDULED_LOCK_USAGE));
            return Command.SINGLE_SUCCESS;
        }
        mod.applyScheduled("lock", target);
        source.sendSuccess(() -> Component.literal("Scheduled lock at "
                + target.format(ScheduleSupport.SCHEDULE_FORMAT) + "."), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeScheduleStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
                () -> Component.literal("[EndLock] " + mod.scheduleStatusLine()), false);
        return Command.SINGLE_SUCCESS;
    }

    private int executeScheduleClear(CommandContext<CommandSourceStack> context) {
        mod.cancelSchedule();
        context.getSource().sendSuccess(
                () -> Component.literal("Scheduled action cleared."), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeCancelSchedule(CommandContext<CommandSourceStack> context) {
        mod.cancelSchedule();
        context.getSource().sendSuccess(
                () -> Component.literal("Scheduled action cancelled."), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executePauseSchedule(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!mod.hasSchedule()) {
            source.sendFailure(Component.literal("No schedule to pause."));
            return Command.SINGLE_SUCCESS;
        }
        mod.pauseSchedule();
        source.sendSuccess(() -> Component.literal(
                "Scheduled action paused. Use /endlock resume to resume."), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeResumeSchedule(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!mod.isSchedulePaused()) {
            source.sendFailure(Component.literal("No paused schedule to resume."));
            return Command.SINGLE_SUCCESS;
        }
        mod.resumeSchedule();
        source.sendSuccess(() -> Component.literal("Scheduled action resumed."), true);
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
