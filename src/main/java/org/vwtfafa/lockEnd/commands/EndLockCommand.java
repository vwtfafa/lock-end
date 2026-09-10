package org.vwtfafa.lockEnd.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.vwtfafa.lockEnd.LockEnd;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/**
 * Main /endlock command (aliases /lock and /el) modeled as a native Brigadier
 * tree: permissions are enforced per node, so clients only see subcommands
 * they may execute, and suggestions are delivered by the server.
 */
public final class EndLockCommand {
    private static final DateTimeFormatter DATE_HINT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*([mhd])", Pattern.CASE_INSENSITIVE);
    private static final List<String> PAGES = List.of("1", "2", "3");
    private static final List<String> HISTORY_TOKENS = List.of("json", "csv", "player", "action");
    private static final List<String> TIMES = List.of("00:00", "12:00", "23:59");
    private static final List<String> LOCK_DURATIONS = List.of("15", "30", "60", "30m", "2h");
    private static final List<String> UNLOCK_DURATIONS = List.of("1", "7", "30", "12h", "7d");

    private final LockEnd plugin;

    public EndLockCommand(LockEnd plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds the /endlock command tree.
     */
    public LiteralCommandNode<CommandSourceStack> buildNode() {
        return literal("endlock")
                // Bare /endlock toggles the lock state; the permission is checked
                // at runtime because a root requirement would hide public children.
                .executes(this::toggle)

                .then(publicCommand("status").executes(this::status))
                .then(publicCommand("stats").executes(this::stats))
                .then(adminCommand("test").executes(this::test))

                .then(literal("lock").requires(hasAnyPermission("endlock.admin", "endlock.toggle"))
                        .executes(this::lock)
                        .then(literal("in").requires(hasPermission("endlock.toggle"))
                                .then(durationArgument("duration", LOCK_DURATIONS)
                                        .executes(this::lockIn))))
                .then(literal("unlock").requires(hasAnyPermission("endlock.admin", "endlock.toggle"))
                        .executes(this::unlock)
                        .then(literal("in").requires(hasPermission("endlock.toggle"))
                                .then(durationArgument("duration", UNLOCK_DURATIONS)
                                        .executes(this::unlockIn))))

                .then(togglePermissionCommand("unlockin")
                        .then(durationArgument("days", UNLOCK_DURATIONS).executes(this::unlockIn)))
                .then(togglePermissionCommand("unlockat")
                        .then(dateTimeArguments().executes(this::unlockAt)))
                .then(togglePermissionCommand("lockin")
                        .then(durationArgument("minutes", LOCK_DURATIONS).executes(this::lockIn)))
                .then(togglePermissionCommand("lockat")
                        .then(dateTimeArguments().executes(this::lockAt)))

                .then(adminCommand("schedule")
                        .then(literal("status").executes(this::scheduleStatus))
                        .then(literal("clear").executes(this::scheduleClear)))
                .then(adminCommand("cancel").executes(this::cancelSchedule))
                .then(adminCommand("reason")
                        .then(argument("reason", greedyString()).executes(this::setReason)))
                .then(adminCommand("pause").executes(this::pauseSchedule))
                .then(adminCommand("resume").executes(this::resumeSchedule))
                .then(commandWithPermission("reload", "endlock.reload").executes(this::reload))

                .then(commandWithPermission("undo", "endlock.undo").executes(this::undo))
                .then(commandWithPermission("validateconfig", "endlock.validate").executes(this::validateConfig))

                .then(historyCommand())
                .build();
    }

    // --- Tree helpers -------------------------------------------------

    private static Predicate<CommandSourceStack> hasPermission(String permission) {
        return source -> source.getSender().hasPermission(permission);
    }

    private static Predicate<CommandSourceStack> hasAnyPermission(String... permissions) {
        return source -> {
            for (String permission : permissions) {
                if (source.getSender().hasPermission(permission)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static LiteralArgumentBuilder<CommandSourceStack> publicCommand(String name) {
        return literal(name);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminCommand(String name) {
        return literal(name).requires(hasPermission("endlock.admin"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> togglePermissionCommand(String name) {
        return literal(name).requires(hasPermission("endlock.toggle"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandWithPermission(String name, String permission) {
        return literal(name).requires(hasPermission(permission));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> durationArgument(String name, List<String> suggestions) {
        return argument(name, word())
                .suggests(suggest(suggestions));
    }

    /**
     * Two word arguments (date + time) shared by unlockat and lockat.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> dateTimeArguments() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        return argument("date", word())
                .suggests(suggest(List.of(tomorrow.format(DATE_HINT_FORMAT))))
                .then(argument("time", word())
                        .suggests(suggest(TIMES)));
    }

    private static SuggestionProvider<CommandSourceStack> suggest(List<String> options) {
        return (context, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            options.stream()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(remaining))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    // --- Executors ----------------------------------------------------

    private int toggle(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!sender.hasPermission("endlock.toggle")) {
            sender.sendMessage(plugin.msg("permission"));
            return 1;
        }
        boolean newLocked = !plugin.isLocked();
        String status = newLocked ? plugin.msg("closed") : plugin.msg("open");
        sender.sendMessage(plugin.msg("toggle").replace("%status%", status));
        plugin.changeLockState(newLocked, sender.getName(), newLocked ? "LOCK" : "UNLOCK");
        return 1;
    }

    private int status(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String status = plugin.isLocked() ? plugin.msg("closed") : plugin.msg("open");
        sender.sendMessage(plugin.msg("status").replace("%status%", status));
        return 1;
    }

    private int stats(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(plugin.msg("stats-header")
                .replace("%lockcount%", String.valueOf(plugin.getLockCount()))
                .replace("%blockedcount%", String.valueOf(plugin.getBlockedCount())));
        sender.sendMessage(plugin.msg("stats-line-unlocks")
                .replace("%unlockcount%", String.valueOf(plugin.getUnlockCount())));
        sender.sendMessage(plugin.msg("stats-line-evacuated")
                .replace("%evacuated%", String.valueOf(plugin.getEvacuatedCount())));
        return 1;
    }

    private int test(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!plugin.getConfig().getBoolean("test-command.enabled", true)) {
            sender.sendMessage(plugin.msg("test-disabled"));
            return 1;
        }
        String status = plugin.isLocked() ? plugin.msg("closed") : plugin.msg("open");
        sender.sendMessage(plugin.msg("test-success"));
        sender.sendMessage(plugin.msg("test-info").replace("%status%", status));
        plugin.logTestAction(sender.getName());
        return 1;
    }

    private int lock(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!sender.hasPermission("endlock.admin")) {
            sender.sendMessage(plugin.msg("permission"));
            return 1;
        }
        if (plugin.isLocked()) {
            sender.sendMessage(plugin.msg("already-locked"));
            return 1;
        }
        sender.sendMessage(plugin.msg("toggle").replace("%status%", plugin.msg("closed")));
        plugin.changeLockState(true, sender.getName(), "LOCK");
        return 1;
    }

    private int unlock(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!sender.hasPermission("endlock.admin")) {
            sender.sendMessage(plugin.msg("permission"));
            return 1;
        }
        if (!plugin.isLocked()) {
            sender.sendMessage(plugin.msg("already-unlocked"));
            return 1;
        }
        sender.sendMessage(plugin.msg("toggle").replace("%status%", plugin.msg("open")));
        plugin.changeLockState(false, sender.getName(), "UNLOCK");
        return 1;
    }

    private int unlockIn(CommandContext<CommandSourceStack> context) {
        handleUnlockIn(context.getSource().getSender(), firstPresent(context, "days", "duration"));
        return 1;
    }

    private int lockIn(CommandContext<CommandSourceStack> context) {
        handleLockIn(context.getSource().getSender(), firstPresent(context, "duration", "minutes"));
        return 1;
    }

    private int unlockAt(CommandContext<CommandSourceStack> context) {
        handleUnlockAt(context.getSource().getSender(),
                getString(context, "date"), getString(context, "time"));
        return 1;
    }

    private int lockAt(CommandContext<CommandSourceStack> context) {
        handleLockAt(context.getSource().getSender(),
                getString(context, "date"), getString(context, "time"));
        return 1;
    }

    private int scheduleStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().getSender().sendMessage(plugin.buildScheduleStatusMessage());
        return 1;
    }

    private int scheduleClear(CommandContext<CommandSourceStack> context) {
        plugin.clearSchedule();
        context.getSource().getSender().sendMessage(plugin.msg("schedule-cleared"));
        return 1;
    }

    private int cancelSchedule(CommandContext<CommandSourceStack> context) {
        plugin.clearSchedule();
        context.getSource().getSender().sendMessage(plugin.msg("schedule-cancelled"));
        return 1;
    }

    private int setReason(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String reason = getString(context, "reason").trim();
        plugin.setLockReason(reason);
        sender.sendMessage(plugin.msg("reason-set").replace("%reason%", plugin.sanitize(reason)));
        return 1;
    }

    private int pauseSchedule(CommandContext<CommandSourceStack> context) {
        plugin.pauseSchedule();
        context.getSource().getSender().sendMessage(plugin.msg("schedule-paused"));
        return 1;
    }

    private int resumeSchedule(CommandContext<CommandSourceStack> context) {
        plugin.resumeSchedule();
        context.getSource().getSender().sendMessage(plugin.msg("schedule-resumed"));
        return 1;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        plugin.reloadPlugin();
        context.getSource().getSender().sendMessage(plugin.msg("reload-success"));
        return 1;
    }

    private int undo(CommandContext<CommandSourceStack> context) {
        plugin.getUndoCommand().execute(context.getSource().getSender(), new String[0]);
        return 1;
    }

    private int validateConfig(CommandContext<CommandSourceStack> context) {
        plugin.getConfigValidatorCommand().execute(context.getSource().getSender(), new String[0]);
        return 1;
    }

    /**
     * History supports [page] [json|csv|player|action value]; every branch
     * funnels into the shared history handler by rebuilding its arguments.
     */
    private LiteralArgumentBuilder<CommandSourceStack> historyCommand() {
        return commandWithPermission("history", "endlock.history")
                .executes(context -> {
                    showHistory(context.getSource().getSender(), new String[0]);
                    return 1;
                })
                .then(argument("page", integer(1))
                        .suggests(suggest(PAGES))
                        .executes(context -> {
                            showHistory(context.getSource().getSender(), argsWith(pageArg(context)));
                            return 1;
                        })
                        .then(historyTail()))
                .then(historyTail());
    }

    /**
     * The optional second token: an export format or a filter type followed
     * by its value. A third word allows format + filter + value
     * (e.g. "json player Steve", optionally preceded by a page).
     */
    private RequiredArgumentBuilder<CommandSourceStack, String> historyTail() {
        return argument("token", word())
                .suggests(suggest(HISTORY_TOKENS))
                .executes(context -> {
                    showHistory(context.getSource().getSender(),
                            argsWith(pageArg(context), getString(context, "token")));
                    return 1;
                })
                .then(argument("value", word())
                        .executes(context -> {
                            showHistory(context.getSource().getSender(),
                                    argsWith(pageArg(context), getString(context, "token"),
                                            getString(context, "value")));
                            return 1;
                        })
                        .then(argument("filterValue", word())
                                .executes(context -> {
                                    showHistory(context.getSource().getSender(),
                                            argsWith(pageArg(context), getString(context, "token"),
                                                    getString(context, "value"),
                                                    getString(context, "filterValue")));
                                    return 1;
                                })));
    }

    private static String[] argsWith(String... parts) {
        List<String> args = new ArrayList<>();
        for (String part : parts) {
            if (part != null) {
                args.add(part);
            }
        }
        return args.toArray(String[]::new);
    }

    /**
     * Reads an integer argument only when its node actually participated in
     * the parse (the page is optional in every branch).
     */
    private static String pageArg(CommandContext<CommandSourceStack> context) {
        boolean present = context.getNodes().stream()
                .anyMatch(node -> node.getNode().getName().equals("page"));
        return present ? String.valueOf(getInteger(context, "page")) : null;
    }

    /**
     * Reads the first present string argument (solo and "in" aliases
     * use different argument names for the same value).
     */
    private static String firstPresent(CommandContext<CommandSourceStack> context, String... names) {
        for (String name : names) {
            try {
                return getString(context, name);
            } catch (IllegalArgumentException ignored) {
                // Try the next alias name.
            }
        }
        return "";
    }

    private void showHistory(CommandSender sender, String[] args) {
        plugin.getHistoryCommand().execute(sender, args);
    }

    // --- Shared scheduling handlers -----------------------------------

    /**
     * A bare number keeps its legacy meaning (days); "12h"/"7d" style
     * durations schedule an exact point in time instead.
     */
    private void handleUnlockIn(CommandSender sender, String raw) {
        Integer days = tryParsePositiveInt(raw);
        if (days != null) {
            plugin.scheduleUnlockInDays(days);
            sender.sendMessage(plugin.msg("scheduled-unlock-set-days").replace("%days%", String.valueOf(days)));
            return;
        }
        LocalDateTime target = parseDurationTarget(raw);
        if (target == null) {
            sender.sendMessage(plugin.msg("scheduled-unlock-invalid"));
            return;
        }
        plugin.scheduleUnlockAt(target);
        sender.sendMessage(plugin.msg("scheduled-unlock-set-at")
                .replace("%datetime%", target.format(LockEnd.SCHEDULE_FORMAT)));
    }

    private void handleUnlockAt(CommandSender sender, String date, String time) {
        try {
            LocalDateTime target = LocalDateTime.parse(date + " " + time, LockEnd.SCHEDULE_FORMAT);
            if (target.isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException();
            }
            plugin.scheduleUnlockAt(target);
            sender.sendMessage(plugin.msg("scheduled-unlock-set-at")
                    .replace("%datetime%", target.format(LockEnd.SCHEDULE_FORMAT)));
        } catch (Exception e) {
            sender.sendMessage(plugin.msg("scheduled-unlock-invalid"));
        }
    }

    /**
     * A bare number keeps its legacy meaning (minutes); "90m"/"2h" style
     * durations schedule an exact point in time instead.
     */
    private void handleLockIn(CommandSender sender, String raw) {
        Integer minutes = tryParsePositiveInt(raw);
        if (minutes != null) {
            plugin.scheduleLockInMinutes(minutes);
            sender.sendMessage(plugin.msg("scheduled-lock-set"));
            return;
        }
        LocalDateTime target = parseDurationTarget(raw);
        if (target == null) {
            sender.sendMessage(plugin.msg("scheduled-lock-invalid"));
            return;
        }
        plugin.scheduleLockAt(target);
        sender.sendMessage(plugin.msg("scheduled-lock-set"));
    }

    private void handleLockAt(CommandSender sender, String date, String time) {
        try {
            LocalDateTime target = LocalDateTime.parse(date + " " + time, LockEnd.SCHEDULE_FORMAT);
            if (target.isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException();
            }
            plugin.scheduleLockAt(target);
            sender.sendMessage(plugin.msg("scheduled-lock-set"));
        } catch (Exception e) {
            sender.sendMessage(plugin.msg("scheduled-lock-invalid"));
        }
    }

    /**
     * Parses a positive integer; null for anything else.
     */
    private static Integer tryParsePositiveInt(String input) {
        try {
            int value = Integer.parseInt(input.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Parses natural durations like "90m", "2h" or "7d" into an absolute
     * target time from now; null for anything else.
     */
    static LocalDateTime parseDurationTarget(String input) {
        if (input == null) {
            return null;
        }
        Matcher matcher = DURATION_PATTERN.matcher(input.trim());
        if (!matcher.matches()) {
            return null;
        }
        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        return switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "m" -> now.plusMinutes(amount);
            case "h" -> now.plusHours(amount);
            default -> now.plusDays(amount);
        };
    }
}
