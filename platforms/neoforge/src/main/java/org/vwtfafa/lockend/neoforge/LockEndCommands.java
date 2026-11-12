package org.vwtfafa.lockend.neoforge;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.vwtfafa.lockend.api.LockAction;
import org.vwtfafa.lockend.core.LockStateManager;
import org.vwtfafa.lockend.core.ScheduleSupport;
import org.vwtfafa.lockend.core.messages.ModMessages;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The NeoForge side of the Lock-End command surface, matching the Paper
 * per-node permission style via NeoForge {@link LockEndPermissions}:
 * {@code /endlock lock|unlock|reason set|reason clear|schedule status|
 * schedule clear|cancel|pause|resume|evacuate preview|evacuate execute}
 * require {@code lockend.admin}; the scheduled opening commands
 * {@code /endlock unlockin|unlockat|lockin|lockat} require
 * {@code lockend.toggle}; {@code /endlock undo} requires
 * {@code lockend.undo}; {@code /endlock history} requires
 * {@code lockend.history}; {@code /endlock reload} requires
 * {@code lockend.reload}; and {@code /endlock status|stats} are public.
 * Defaults fall back to the vanilla admin/op permission; console sources
 * always pass the command predicates.
 *
 * <p>Player-facing feedback resolves through the shared message keys, so a
 * {@code config/lock-end-messages.json} override file controls every line
 * this class sends.</p>
 *
 * <p>The tree is registered through the game-bus {@code RegisterCommandsEvent}.
 * Deliberately a copy of the Fabric adapter's command surface with the
 * permission checks mapped onto NeoForge named permission nodes.</p>
 */
final class LockEndCommands {

    private static final String COMMAND_NAME = "endlock";
    private static final List<String> HISTORY_TOKENS = List.of("json", "csv", "player", "action");
    private static final List<String> PAGE_SUGGESTIONS = List.of("1", "2", "3");
    private static final DateTimeFormatter DATE_HINT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LockStateManager state;
    private final LockEndNeoForgeMod mod;

    /**
     * Creates the command tree around the shared state and the owning mod
     * instance (which publishes settings, messaging, history and statistics).
     *
     * @param state the shared runtime lock state
     * @param mod   the mod owning settings, state and guard
     */
    LockEndCommands(LockStateManager state, LockEndNeoForgeMod mod) {
        this.state = state;
        this.mod = mod;
    }

    /**
     * Registers the {@code /endlock} tree onto the given dispatcher. Named
     * NeoForge permission nodes gate the mutating branches; {@code status}
     * and {@code stats} stay public.
     *
     * @param dispatcher the command dispatcher supplied by the registration
     *                   event
     */
    void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(COMMAND_NAME)
                .then(Commands.literal("status").executes(this::executeStatus))
                .then(Commands.literal("stats").executes(this::executeStats))
                .then(Commands.literal("lock").requires(LockEndPermissions.requires(LockEndPermissions.ADMIN))
                        .executes(context -> executeChangeLock(context, true)))
                .then(Commands.literal("unlock").requires(LockEndPermissions.requires(LockEndPermissions.ADMIN))
                        .executes(context -> executeChangeLock(context, false)))
                .then(Commands.literal("reason")
                        .requires(LockEndPermissions.requires(LockEndPermissions.ADMIN))
                        .then(Commands.literal("set")
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(this::executeSetReason)))
                        .then(Commands.literal("clear").executes(this::executeClearReason)))
                .then(Commands.literal("unlockin").requires(LockEndPermissions.requires(LockEndPermissions.TOGGLE))
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests(LockEndCommands::suggestUnlockDurations)
                                .executes(this::executeUnlockIn)))
                .then(Commands.literal("unlockat").requires(LockEndPermissions.requires(LockEndPermissions.TOGGLE))
                        .then(dateTimeArguments()
                                .executes(this::executeUnlockAt)))
                .then(Commands.literal("lockin").requires(LockEndPermissions.requires(LockEndPermissions.TOGGLE))
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests(LockEndCommands::suggestLockDurations)
                                .executes(this::executeLockIn)))
                .then(Commands.literal("lockat").requires(LockEndPermissions.requires(LockEndPermissions.TOGGLE))
                        .then(dateTimeArguments()
                                .executes(this::executeLockAt)))
                .then(Commands.literal("schedule")
                        .requires(LockEndPermissions.requires(LockEndPermissions.ADMIN))
                        .then(Commands.literal("status").executes(this::executeScheduleStatus))
                        .then(Commands.literal("clear").executes(this::executeScheduleClear)))
                .then(Commands.literal("evacuate")
                        .requires(LockEndPermissions.requires(LockEndPermissions.ADMIN))
                        .then(Commands.literal("preview")
                                .executes(this::executeEvacuationPreview))
                        .then(Commands.literal("execute")
                                .executes(this::executeEvacuationExecute)))
                .then(Commands.literal("cancel")
                        .requires(LockEndPermissions.requires(LockEndPermissions.ADMIN))
                        .executes(this::executeCancelSchedule))
                .then(Commands.literal("pause")
                        .requires(LockEndPermissions.requires(LockEndPermissions.ADMIN))
                        .executes(this::executePauseSchedule))
                .then(Commands.literal("resume")
                        .requires(LockEndPermissions.requires(LockEndPermissions.ADMIN))
                        .executes(this::executeResumeSchedule))
                .then(Commands.literal("undo").requires(LockEndPermissions.requires(LockEndPermissions.UNDO))
                        .executes(this::executeUndo))
                .then(historyCommand())
                .then(Commands.literal("reload").requires(LockEndPermissions.requires(LockEndPermissions.RELOAD))
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
        return suggestDurations(context, builder, List.of("1", "7", "30", "12h", "7d"));
    }

    private static CompletableFuture<Suggestions> suggestLockDurations(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestDurations(context, builder, List.of("15", "30", "60", "30m", "2h"));
    }

    private static CompletableFuture<Suggestions> suggestPages(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (String option : PAGE_SUGGESTIONS) {
            if (option.startsWith(remaining)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    private static ModMessages messages() {
        return LockEndNeoForgeMod.messages();
    }

    // --- Lock state ---------------------------------------------------

    private int executeChangeLock(CommandContext<CommandSourceStack> context, boolean lock) {
        CommandSourceStack source = context.getSource();
        boolean changed = mod.changeLockState(lock, lock ? LockAction.LOCK : LockAction.UNLOCK,
                lock ? "LOCK" : "UNLOCK", source.getTextName(), actorUuid(source));
        if (!changed) {
            source.sendFailure(Component.literal(
                    lock ? messages().text("already-locked") : messages().text("already-unlocked")));
            return Command.SINGLE_SUCCESS;
        }
        source.sendSuccess(() -> Component.literal(messages().text("toggle", Map.of(
                "%status%", messages().text(lock ? "closed" : "open")))), false);
        return Command.SINGLE_SUCCESS;
    }

    private int executeUndo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!mod.undoLastAction(source.getTextName(), actorUuid(source))) {
            source.sendSuccess(() -> Component.literal(messages().text("undo.empty")), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendSuccess(() -> Component.literal(
                messages().text("undo.success", Map.of("%action%",
                        messages().text(mod.isLocked() ? "closed" : "open")))), false);
        return Command.SINGLE_SUCCESS;
    }

    private int executeStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String status = messages().text(
                state.isLocked() ? "closed" : "open");
        source.sendSuccess(() -> Component.literal(
                messages().text("status", Map.of("%status%", status))), false);
        if (state.isLocked() && !state.getLockReason().isBlank()) {
            String reason = state.getLockReason();
            source.sendSuccess(() -> Component.literal(
                    messages().text("locked-reason",
                            Map.of("%reason%", reason))), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeSetReason(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String reason = StringArgumentType.getString(context, "reason").trim();
        if (reason.isEmpty()) {
            source.sendFailure(Component.literal(messages().text("reason-usage")));
            return Command.SINGLE_SUCCESS;
        }
        mod.currentSettings().setLockReason(reason);
        source.sendSuccess(() -> Component.literal(
                messages().text("reason-set", LockEndCommands.placeholders("%reason%", reason))), true);
        return Command.SINGLE_SUCCESS;
    }

    private static Map<String, String> placeholders(String key, String value) {
        return Map.of(key, value);
    }

    private int executeClearReason(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        mod.currentSettings().clearReason();
        source.sendSuccess(() -> Component.literal(
                messages().text("reason-cleared", placeholders("%reason%",
                        mod.currentSettings().lockReason()))), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String failure = mod.reloadSettings();
        if (failure == null) {
            source.sendSuccess(() -> Component.literal(messages().text("reload-success")), true);
        } else {
            source.sendSuccess(() -> Component.literal(
                    messages().text("reload-recovered",
                            LockEndCommands.placeholders("%failure%", failure))), true);
        }
        return Command.SINGLE_SUCCESS;
    }

    // --- Scheduled actions ------------------------------------------------

    private int executeUnlockIn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String raw = StringArgumentType.getString(context, "value");
        Integer days = ScheduleSupport.tryParsePositiveInt(raw);
        if (days != null) {
            mod.applyScheduled("unlock", LocalDateTime.now().plusDays(days));
            source.sendSuccess(() -> Component.literal(
                    messages().text("scheduled-unlock-set-days",
                            placeholders("%days%", String.valueOf(days)))), true);
            return Command.SINGLE_SUCCESS;
        }
        LocalDateTime target = ScheduleSupport.parseDurationTarget(raw, LocalDateTime.now());
        if (target == null) {
            source.sendFailure(Component.literal(messages().text("scheduled-unlock-invalid")));
            return Command.SINGLE_SUCCESS;
        }
        mod.applyScheduled("unlock", target);
        source.sendSuccess(() -> Component.literal(
                messages().text("scheduled-unlock-set-at", placeholders("%datetime%",
                        target.format(ScheduleSupport.SCHEDULE_FORMAT)))), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeUnlockAt(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        LocalDateTime target = ScheduleSupport.parseScheduleTime(
                StringArgumentType.getString(context, "date") + " "
                        + StringArgumentType.getString(context, "time"));
        if (target == null || target.isBefore(LocalDateTime.now())) {
            source.sendFailure(Component.literal(messages().text("scheduled-unlock-invalid")));
            return Command.SINGLE_SUCCESS;
        }
        mod.applyScheduled("unlock", target);
        source.sendSuccess(() -> Component.literal(
                messages().text("scheduled-unlock-set-at", placeholders("%datetime%",
                        target.format(ScheduleSupport.SCHEDULE_FORMAT)))), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeLockIn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String raw = StringArgumentType.getString(context, "value");
        Integer minutes = ScheduleSupport.tryParsePositiveInt(raw);
        if (minutes != null) {
            mod.applyScheduled("lock", LocalDateTime.now().plusMinutes(minutes));
            source.sendSuccess(() -> Component.literal(
                    messages().text("scheduled-lock-set-minutes",
                            placeholders("%minutes%", String.valueOf(minutes)))), true);
            return Command.SINGLE_SUCCESS;
        }
        LocalDateTime target = ScheduleSupport.parseDurationTarget(raw, LocalDateTime.now());
        if (target == null) {
            source.sendFailure(Component.literal(messages().text("scheduled-lock-invalid")));
            return Command.SINGLE_SUCCESS;
        }
        mod.applyScheduled("lock", target);
        source.sendSuccess(() -> Component.literal(
                messages().text("scheduled-lock-set-at", placeholders("%datetime%",
                        target.format(ScheduleSupport.SCHEDULE_FORMAT)))), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeLockAt(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        LocalDateTime target = ScheduleSupport.parseScheduleTime(
                StringArgumentType.getString(context, "date") + " "
                        + StringArgumentType.getString(context, "time"));
        if (target == null || target.isBefore(LocalDateTime.now())) {
            source.sendFailure(Component.literal(messages().text("scheduled-lock-invalid")));
            return Command.SINGLE_SUCCESS;
        }
        mod.applyScheduled("lock", target);
        source.sendSuccess(() -> Component.literal(
                messages().text("scheduled-lock-set-at", placeholders("%datetime%",
                        target.format(ScheduleSupport.SCHEDULE_FORMAT)))), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeScheduleStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
                () -> Component.literal(messages().text("schedule-status", Map.of(
                        "%action%", mod.scheduleAction(),
                        "%target%", mod.scheduleTargetText(),
                        "%remaining%", mod.scheduleRemainingText(),
                        "%paused%", String.valueOf(mod.isSchedulePaused())))), false);
        return Command.SINGLE_SUCCESS;
    }

    private int executeScheduleClear(CommandContext<CommandSourceStack> context) {
        mod.cancelSchedule();
        context.getSource().sendSuccess(
                () -> Component.literal(messages().text("schedule-cleared")), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeCancelSchedule(CommandContext<CommandSourceStack> context) {
        mod.cancelSchedule();
        context.getSource().sendSuccess(
                () -> Component.literal(messages().text("schedule-cancel")), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executePauseSchedule(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!mod.hasSchedule()) {
            source.sendFailure(Component.literal(messages().text("schedule-pause-none")));
            return Command.SINGLE_SUCCESS;
        }
        mod.pauseSchedule();
        source.sendSuccess(() -> Component.literal(messages().text("schedule-paused")), true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeResumeSchedule(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!mod.isSchedulePaused()) {
            source.sendFailure(Component.literal(messages().text("schedule-not-paused")));
            return Command.SINGLE_SUCCESS;
        }
        mod.resumeSchedule();
        source.sendSuccess(() -> Component.literal(messages().text("schedule-resumed")), true);
        return Command.SINGLE_SUCCESS;
    }

    // --- Evacuation ----------------------------------------------------

    /**
     * Shows the side-effect-free evacuation preview, mirroring the Paper
     * plugin's preview/execute pair.
     */
    private int executeEvacuationPreview(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        mod.evacuation().showPreview(source, source.getTextName(), actorUuid(source));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Executes a previously shown preview (30-second validity, drift
     * re-check), mirroring the Paper plugin.
     */
    private int executeEvacuationExecute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        mod.evacuation().executePreview(source, source.getTextName(), actorUuid(source));
        return Command.SINGLE_SUCCESS;
    }

    // --- Stats --------------------------------------------------------

    private int executeStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(messages().text("stats-header", Map.of(
                "%lockcount%", String.valueOf(mod.statistics().lockCount()),
                "%blockedcount%", String.valueOf(mod.statistics().blockedCount())))), false);
        source.sendSuccess(() -> Component.literal(messages().text("stats-line-unlocks",
                placeholders("%unlockcount%", String.valueOf(mod.statistics().unlockCount())))), false);
        source.sendSuccess(() -> Component.literal(messages().text("stats-line-evacuated",
                placeholders("%evacuated%", String.valueOf(mod.statistics().evacuatedCount())))), false);
        return Command.SINGLE_SUCCESS;
    }

    // --- History ------------------------------------------------------

    /**
     * History supports [page] [json|csv|player|action value]; every branch
     * funnels into the shared history handler by rebuilding its arguments,
     * mirroring the Paper plugin's tree.
     */
    private LiteralArgumentBuilder<CommandSourceStack> historyCommand() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("history")
                .requires(LockEndPermissions.requires(LockEndPermissions.HISTORY))
                .executes(this::executeHistoryRoot)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .suggests(LockEndCommands::suggestPages)
                        .executes(this::executeHistoryPage)
                        .then(historyTail()))
                .then(historyTail());
    }

    /**
     * The optional trailing token: an export format or a filter type
     * followed by its value. A third word allows format + filter + value
     * (e.g. "json player Steve", optionally preceded by a page).
     */
    private RequiredArgumentBuilder<CommandSourceStack, String> historyTail() {
        return Commands.argument("token", StringArgumentType.word())
                .suggests((context, builder) ->
                        suggestDurations(context, builder, HISTORY_TOKENS))
                .executes(this::executeHistoryOne)
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(this::executeHistoryTwo)
                        .then(Commands.argument("filterValue", StringArgumentType.word())
                                .executes(this::executeHistoryThree)));
    }

    private int executeHistoryRoot(CommandContext<CommandSourceStack> context) {
        return funnelHistory(context);
    }

    private int executeHistoryPage(CommandContext<CommandSourceStack> context) {
        return funnelHistory(context);
    }

    private int executeHistoryOne(CommandContext<CommandSourceStack> context) {
        return funnelHistory(context);
    }

    private int executeHistoryTwo(CommandContext<CommandSourceStack> context) {
        return funnelHistory(context);
    }

    private int executeHistoryThree(CommandContext<CommandSourceStack> context) {
        return funnelHistory(context);
    }

    /**
     * Rebuilds the Paper-style positional arguments from the parsed Brigadier
     * nodes and runs the shared history view.
     */
    private int funnelHistory(CommandContext<CommandSourceStack> context) {
        List<String> args = new ArrayList<>();
        String page = pageArg(context);
        if (page != null) {
            args.add(page);
        }
        for (String name : List.of("token", "value", "filterValue")) {
            if (context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals(name))) {
                args.add(StringArgumentType.getString(context, name));
            }
        }
        mod.historyService().showHistory(context.getSource(),
                args.toArray(String[]::new));
        return Command.SINGLE_SUCCESS;
    }

    private static String pageArg(CommandContext<CommandSourceStack> context) {
        boolean present = context.getNodes().stream()
                .anyMatch(node -> node.getNode().getName().equals("page"));
        return present ? String.valueOf(IntegerArgumentType.getInteger(context, "page")) : null;
    }

    private static UUID actorUuid(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player.getUUID();
        }
        return null;
    }
}
