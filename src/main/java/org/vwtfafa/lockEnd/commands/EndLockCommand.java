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

/**
 * Main /endlock command (with aliases /lock and /el), registered as a Brigadier command.
 */
public class EndLockCommand implements BasicCommand {
    private static final DateTimeFormatter DATE_HINT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final LockEnd plugin;

    public EndLockCommand(LockEnd plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
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
                        plugin.changeLockState(true, sender.getName(), "LOCK", true);
                        plugin.startGracePeriodIfEnabled();
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
                        plugin.changeLockState(false, sender.getName(), "UNLOCK", false);
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
                    try {
                        int days = Integer.parseInt(args[1]);
                        plugin.scheduleUnlockInDays(days);
                        sender.sendMessage(plugin.msg("scheduled-unlock-set-days").replace("%days%", String.valueOf(days)));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(plugin.msg("scheduled-unlock-invalid"));
                    }
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
                    try {
                        int minutes = Integer.parseInt(args[1]);
                        if (minutes <= 0) {
                            throw new IllegalArgumentException();
                        }
                        plugin.scheduleLockInMinutes(minutes);
                        sender.sendMessage(plugin.msg("scheduled-lock-set"));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(plugin.msg("scheduled-lock-invalid"));
                    }
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
                    sender.sendMessage(plugin.msg("reason-set").replace("%reason%", reason));
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
            plugin.changeLockState(newLocked, sender.getName(), newLocked ? "LOCK" : "UNLOCK", newLocked);
            if (newLocked) {
                plugin.startGracePeriodIfEnabled();
            }
        } else {
            sender.sendMessage(plugin.msg("permission"));
        }
    }

    private void handleSchedule(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.msg("schedule-status-usage"));
            return;
        }
        if (!sender.hasPermission("endlock.admin")) {
            sender.sendMessage(plugin.msg("permission"));
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
            List<String> options = List.of("status", "lock", "unlock", "test", "stats",
                    "unlockin", "unlockat", "lockin", "lockat", "schedule", "reload", "history", "undo", "validateconfig",
                    "pause", "resume", "cancel", "reason");
            StringUtil.copyPartialMatches(args[0], options, completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "unlockin" -> StringUtil.copyPartialMatches(args[1], List.of("1", "7", "30"), completions);
                case "unlockat" -> {
                    LocalDate tomorrow = LocalDate.now().plusDays(1);
                    StringUtil.copyPartialMatches(args[1],
                            List.of(tomorrow.format(DATE_HINT_FORMAT)), completions);
                }
                case "schedule" -> StringUtil.copyPartialMatches(args[1], List.of("status", "clear"), completions);
                case "history" -> StringUtil.copyPartialMatches(args[1], List.of("1", "2", "3"), completions);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("history")) {
            StringUtil.copyPartialMatches(args[2], List.of("json", "csv"), completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("unlockat")) {
            StringUtil.copyPartialMatches(args[2], List.of("00:00", "12:00", "23:59"), completions);
        }
        return completions;
    }
}
