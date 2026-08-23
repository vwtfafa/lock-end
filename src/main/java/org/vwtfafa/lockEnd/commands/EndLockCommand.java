package org.vwtfafa.lockEnd.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.vwtfafa.lockEnd.LockEnd;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main /endlock command (with aliases /lock and /el), registered as a Brigadier command.
 */
public class EndLockCommand implements BasicCommand {
    private static final DateTimeFormatter DATE_HINT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*([mhd])", Pattern.CASE_INSENSITIVE);
    /** Permission required per subcommand; entries missing here are public. */
    private static final Map<String, String> SUBCOMMAND_PERMISSIONS = Map.ofEntries(
            Map.entry("lock", "endlock.admin"),
            Map.entry("unlock", "endlock.admin"),
            Map.entry("test", "endlock.admin"),
            Map.entry("unlockin", "endlock.toggle"),
            Map.entry("unlockat", "endlock.toggle"),
            Map.entry("lockin", "endlock.toggle"),
            Map.entry("lockat", "endlock.toggle"),
            Map.entry("schedule", "endlock.admin"),
            Map.entry("cancel", "endlock.admin"),
            Map.entry("reason", "endlock.admin"),
            Map.entry("pause", "endlock.admin"),
            Map.entry("resume", "endlock.admin"),
            Map.entry("reload", "endlock.reload"),
            Map.entry("history", "endlock.history"),
            Map.entry("undo", "endlock.undo"),
            Map.entry("validateconfig", "endlock.validate"));
    private static final List<String> SUBCOMMANDS = List.of("status", "stats", "lock", "unlock",
            "test", "unlockin", "unlockat", "lockin", "lockat", "schedule", "cancel",
            "reason", "pause", "resume", "reload", "history", "undo", "validateconfig");
    private final LockEnd plugin;

    public EndLockCommand(LockEnd plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            // Natural alias: "/endlock lock in 5m" behaves like "/endlock lockin 5m".
            if (args.length >= 3 && (sub.equals("lock") || sub.equals("unlock"))
                    && args[1].equalsIgnoreCase("in")) {
                args = new String[]{sub + "in", args[2]};
                sub = args[0];
            }
            switch (sub) {
                case "status" -> {
                    String status = plugin.isLocked() ? plugin.msg("closed") : plugin.msg("open");
                    sender.sendMessage(plugin.msg("status").replace("%status%", status));
                    return;
                }
                case "lock" -> {
                    if (!sender.hasPermission("endlock.admin")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    if (plugin.isLocked()) {
                        sender.sendMessage(plugin.msg("already-locked"));
                    } else {
                        sender.sendMessage(plugin.msg("toggle").replace("%status%", plugin.msg("closed")));
                        plugin.changeLockState(true, sender.getName(), "LOCK");
                    }
                    return;
                }
                case "unlock" -> {
                    if (!sender.hasPermission("endlock.admin")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    if (plugin.isLocked()) {
                        sender.sendMessage(plugin.msg("toggle").replace("%status%", plugin.msg("open")));
                        plugin.changeLockState(false, sender.getName(), "UNLOCK");
                    } else {
                        sender.sendMessage(plugin.msg("already-unlocked"));
                    }
                    return;
                }
                case "test" -> {
                    if (!sender.hasPermission("endlock.admin")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    if (plugin.getConfig().getBoolean("test-command.enabled", true)) {
                        String status = plugin.isLocked() ? plugin.msg("closed") : plugin.msg("open");
                        sender.sendMessage(plugin.msg("test-success"));
                        sender.sendMessage(plugin.msg("test-info").replace("%status%", status));
                        plugin.logTestAction(sender.getName());
                    } else {
                        sender.sendMessage(plugin.msg("test-disabled"));
                    }
                    return;
                }
                case "stats" -> {
                    sender.sendMessage(plugin.msg("stats-header")
                            .replace("%lockcount%", String.valueOf(plugin.getLockCount()))
                            .replace("%blockedcount%", String.valueOf(plugin.getBlockedCount())));
                    sender.sendMessage(plugin.msg("stats-line-unlocks")
                            .replace("%unlockcount%", String.valueOf(plugin.getUnlockCount())));
                    sender.sendMessage(plugin.msg("stats-line-evacuated")
                            .replace("%evacuated%", String.valueOf(plugin.getEvacuatedCount())));
                    return;
                }
                case "schedule" -> {
                    handleSchedule(sender, args);
                    return;
                }
                case "unlockin" -> {
                    if (!sender.hasPermission("endlock.toggle")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(plugin.msg("scheduled-unlock-invalid"));
                        return;
                    }
                    // A bare number keeps its legacy meaning (days); "12h"/"7d"
                    // style durations schedule an exact point in time instead.
                    Integer days = tryParsePositiveInt(args[1]);
                    if (days != null) {
                        plugin.scheduleUnlockInDays(days);
                        sender.sendMessage(plugin.msg("scheduled-unlock-set-days").replace("%days%", String.valueOf(days)));
                        return;
                    }
                    LocalDateTime target = parseDurationTarget(args[1]);
                    if (target == null) {
                        sender.sendMessage(plugin.msg("scheduled-unlock-invalid"));
                        return;
                    }
                    plugin.scheduleUnlockAt(target);
                    sender.sendMessage(plugin.msg("scheduled-unlock-set-at")
                            .replace("%datetime%", target.format(LockEnd.SCHEDULE_FORMAT)));
                    return;
                }
                case "unlockat" -> {
                    if (!sender.hasPermission("endlock.toggle")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(plugin.msg("scheduled-unlock-invalid"));
                        return;
                    }
                    try {
                        LocalDateTime time = LocalDateTime.parse(args[1] + " " + args[2], LockEnd.SCHEDULE_FORMAT);
                        if (time.isBefore(LocalDateTime.now())) {
                            throw new IllegalArgumentException();
                        }
                        plugin.scheduleUnlockAt(time);
                        sender.sendMessage(plugin.msg("scheduled-unlock-set-at")
                                .replace("%datetime%", time.format(LockEnd.SCHEDULE_FORMAT)));
                    } catch (Exception e) {
                        sender.sendMessage(plugin.msg("scheduled-unlock-invalid"));
                    }
                    return;
                }
                case "lockin" -> {
                    if (!sender.hasPermission("endlock.toggle")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(plugin.msg("scheduled-lock-invalid"));
                        return;
                    }
                    // A bare number keeps its legacy meaning (minutes); "90m"/"2h"
                    // style durations schedule an exact point in time instead.
                    Integer minutes = tryParsePositiveInt(args[1]);
                    if (minutes != null) {
                        plugin.scheduleLockInMinutes(minutes);
                        sender.sendMessage(plugin.msg("scheduled-lock-set"));
                        return;
                    }
                    LocalDateTime target = parseDurationTarget(args[1]);
                    if (target == null) {
                        sender.sendMessage(plugin.msg("scheduled-lock-invalid"));
                        return;
                    }
                    plugin.scheduleLockAt(target);
                    sender.sendMessage(plugin.msg("scheduled-lock-set"));
                    return;
                }
                case "lockat" -> {
                    if (!sender.hasPermission("endlock.toggle")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(plugin.msg("scheduled-lock-invalid"));
                        return;
                    }
                    try {
                        LocalDateTime time = LocalDateTime.parse(args[1] + " " + args[2], LockEnd.SCHEDULE_FORMAT);
                        if (time.isBefore(LocalDateTime.now())) {
                            throw new IllegalArgumentException();
                        }
                        plugin.scheduleLockAt(time);
                        sender.sendMessage(plugin.msg("scheduled-lock-set"));
                    } catch (Exception e) {
                        sender.sendMessage(plugin.msg("scheduled-lock-invalid"));
                    }
                    return;
                }
                case "cancel" -> {
                    if (!sender.hasPermission("endlock.admin")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    plugin.clearSchedule();
                    sender.sendMessage(plugin.msg("schedule-cancelled"));
                    return;
                }
                case "reason" -> {
                    if (!sender.hasPermission("endlock.admin")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(plugin.msg("reason-usage"));
                        return;
                    }
                    String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                    plugin.setLockReason(reason);
                    sender.sendMessage(plugin.msg("reason-set").replace("%reason%", plugin.sanitize(reason)));
                    return;
                }
                case "pause" -> {
                    if (!sender.hasPermission("endlock.admin")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    plugin.pauseSchedule();
                    sender.sendMessage(plugin.msg("schedule-paused"));
                    return;
                }
                case "resume" -> {
                    if (!sender.hasPermission("endlock.admin")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    plugin.resumeSchedule();
                    sender.sendMessage(plugin.msg("schedule-resumed"));
                    return;
                }
                case "history" -> {
                    if (!sender.hasPermission("endlock.history")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    plugin.getHistoryCommand().execute(sender, args);
                    return;
                }
                case "undo" -> {
                    if (!sender.hasPermission("endlock.undo")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    plugin.getUndoCommand().execute(sender, args);
                    return;
                }
                case "validateconfig" -> {
                    if (!sender.hasPermission("endlock.validate")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    plugin.getConfigValidatorCommand().execute(sender, args);
                    return;
                }
                case "reload" -> {
                    if (!sender.hasPermission("endlock.reload")) {
                        sender.sendMessage(plugin.msg("permission"));
                        return;
                    }
                    plugin.reloadPlugin();
                    sender.sendMessage(plugin.msg("reload-success"));
                    return;
                }
                default -> {
                    // Unknown subcommands must not fall through to the toggle action.
                    sender.sendMessage(plugin.msg("usage"));
                    return;
                }
            }
        }

        // Bare /endlock toggles the lock state.
        if (!(sender instanceof org.bukkit.entity.Player) || sender.hasPermission("endlock.toggle")) {
            boolean newLocked = !plugin.isLocked();
            String status = newLocked ? plugin.msg("closed") : plugin.msg("open");
            sender.sendMessage(plugin.msg("toggle").replace("%status%", status));
            plugin.changeLockState(newLocked, sender.getName(), newLocked ? "LOCK" : "UNLOCK");
        } else {
            sender.sendMessage(plugin.msg("permission"));
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

    private void handleSchedule(CommandSender sender, String[] args) {
        if (!sender.hasPermission("endlock.admin")) {
            sender.sendMessage(plugin.msg("permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.msg("schedule-status-usage"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> sender.sendMessage(plugin.buildScheduleStatusMessage());
            case "clear" -> {
                plugin.clearSchedule();
                sender.sendMessage(plugin.msg("schedule-cleared"));
            }
            default -> sender.sendMessage(plugin.msg("schedule-status-usage"));
        }
    }

    @Override
    public java.util.Collection<String> suggest(CommandSourceStack source, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            // Only offer subcommands the sender could actually execute.
            List<String> visible = SUBCOMMANDS.stream()
                    .filter(sub -> {
                        String permission = SUBCOMMAND_PERMISSIONS.get(sub);
                        return permission == null || source.getSender().hasPermission(permission);
                    })
                    .toList();
            StringUtil.copyPartialMatches(args[0], visible, completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "unlockin" -> StringUtil.copyPartialMatches(args[1], List.of("1", "7", "30", "12h", "7d"), completions);
                case "lockin" -> StringUtil.copyPartialMatches(args[1], List.of("15", "30", "60", "30m", "2h"), completions);
                case "lock", "unlock" -> StringUtil.copyPartialMatches(args[1], List.of("in"), completions);
                case "unlockat" -> {
                    LocalDate tomorrow = LocalDate.now().plusDays(1);
                    StringUtil.copyPartialMatches(args[1],
                            List.of(tomorrow.format(DATE_HINT_FORMAT)), completions);
                }
                case "schedule" -> StringUtil.copyPartialMatches(args[1], List.of("status", "clear"), completions);
                case "history" -> StringUtil.copyPartialMatches(args[1], List.of("1", "2", "3"), completions);
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("unlockat"))) {
            StringUtil.copyPartialMatches(args[2],
                    args[0].equalsIgnoreCase("history") ? List.of("json", "csv") : List.of("00:00", "12:00", "23:59"),
                    completions);
        } else if (args.length == 3 && (sub(args[0]).equals("lock") || sub(args[0]).equals("unlock"))) {
            StringUtil.copyPartialMatches(args[2], List.of("30m", "1h", "1d"), completions);
        }
        return completions;
    }

    private static String sub(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
