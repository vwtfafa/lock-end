package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LockEnd extends JavaPlugin implements Listener, TabCompleter {
    private boolean locked;
    private MessageService messages;
    private LogService logService;
    private StatsService stats;
    private ScheduleService schedule;
    private UpdateChecker updateChecker;
    private MetricsManager metricsManager;
    private DiscordHook discordHook;
    private LuckPermsHook luckPermsHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("data.yml", false);
        locked = getConfig().getBoolean("locked", false);

        messages = new MessageService(this);
        logService = new LogService(this);
        stats = new StatsService(this);
        schedule = new ScheduleService(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("endlock") != null) {
            getCommand("endlock").setExecutor(this);
            getCommand("endlock").setTabCompleter(this);
        }
        if (getCommand("lock") != null) {
            getCommand("lock").setExecutor(this);
            getCommand("lock").setTabCompleter(this);
        }

        if (getConfig().getBoolean("metrics.enabled", true)) {
            metricsManager = new MetricsManager(this, stats);
        }
        if (getConfig().getBoolean("update-checker.enabled", true)) {
            updateChecker = new UpdateChecker(this);
            updateChecker.checkForUpdates();
        }

        discordHook = new DiscordHook(this);
        luckPermsHook = new LuckPermsHook(this);
        setupPlaceholderApi();

        getLogger().info("EndLock v" + getDescription().getVersion() + " enabled (Paper 26.2+)");
    }

    @Override
    public void onDisable() {
        getConfig().set("locked", locked);
        saveConfig();
        if (schedule != null) {
            schedule.shutdown();
        }
        getLogger().info("EndLock disabled");
    }

    private void setupPlaceholderApi() {
        if (!getConfig().getBoolean("integrations.placeholderapi.enabled", true)) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        new PlaceholderHook(this).register();
        getLogger().info("PlaceholderAPI expansion registered");
    }

    public boolean isLocked() {
        return locked;
    }

    public MessageService getMessages() {
        return messages;
    }

    public StatsService getStats() {
        return stats;
    }

    public ScheduleService getSchedule() {
        return schedule;
    }

    public void setLocked(boolean locked, String actor, boolean broadcast) {
        boolean changed = this.locked != locked;
        this.locked = locked;
        getConfig().set("locked", locked);
        saveConfig();

        if (changed) {
            if (locked) {
                stats.incrementLocks();
            } else {
                stats.incrementUnlocks();
                schedule.clearSchedule();
            }
            if (broadcast) {
                broadcastMessage(locked ? "broadcast-locked" : "broadcast-unlocked", actor);
            }
            logService.logAction(actor, locked ? "LOCK" : "UNLOCK");
            discordHook.notifyLockChange(locked, actor);
            luckPermsHook.refreshPlayers();
        }
    }

    private void broadcastMessage(String key, String playerName) {
        if (!getConfig().getBoolean("broadcast.enabled", true)) {
            return;
        }
        boolean notifyAll = getConfig().getBoolean("broadcast.notify-all", true);
        boolean useActionbar = getConfig().getBoolean("broadcast.use-actionbar", true);
        Component message = messages.component(key, "%player%", playerName);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (notifyAll || player.isOp() || player.hasPermission("endlock.admin")) {
                if (useActionbar) {
                    player.sendActionBar(messages.component(locked ? "actionbar-locked" : "actionbar-unlocked"));
                } else {
                    player.sendMessage(message);
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = List.of("status", "lock", "unlock", "test", "stats", "export", "unlockin", "unlockat");
            StringUtil.copyPartialMatches(args[0], options, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("unlockin")) {
            StringUtil.copyPartialMatches(args[1], List.of("1d", "3d", "7d", "14d", "30d", "1h", "6h", "12h"), completions);
        }
        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "status" -> {
                    sendStatus(sender);
                    return true;
                }
                case "lock" -> {
                    if (!locked) {
                        setLocked(true, sender.getName(), true);
                        messages.send(sender, "toggle", "%status%", messages.raw("closed"));
                    } else {
                        messages.send(sender, "already-locked");
                    }
                    return true;
                }
                case "unlock" -> {
                    if (locked) {
                        setLocked(false, sender.getName(), true);
                        messages.send(sender, "toggle", "%status%", messages.raw("open"));
                    } else {
                        messages.send(sender, "already-unlocked");
                    }
                    return true;
                }
                case "unlockin" -> {
                    if (!sender.hasPermission("endlock.toggle") && sender instanceof Player) {
                        messages.send(sender, "permission");
                        return true;
                    }
                    if (args.length < 2) {
                        messages.send(sender, "unlockin-usage");
                        return true;
                    }
                    if (schedule.scheduleIn(args[1])) {
                        messages.send(sender, "unlockin-success",
                                "%duration%", args[1],
                                "%remaining%", schedule.formatRemaining(messages));
                    } else {
                        messages.send(sender, "unlockin-invalid");
                    }
                    return true;
                }
                case "unlockat" -> {
                    if (!sender.hasPermission("endlock.toggle") && sender instanceof Player) {
                        messages.send(sender, "permission");
                        return true;
                    }
                    if (args.length < 3) {
                        messages.send(sender, "unlockat-usage");
                        return true;
                    }
                    String dateTime = args[1] + " " + args[2];
                    if (schedule.scheduleAt(dateTime)) {
                        messages.send(sender, "unlockat-success",
                                "%datetime%", dateTime,
                                "%remaining%", schedule.formatRemaining(messages));
                    } else {
                        messages.send(sender, "unlockat-invalid");
                    }
                    return true;
                }
                case "stats" -> {
                    messages.send(sender, "stats",
                            "%locks%", String.valueOf(stats.getLockCount()),
                            "%unlocks%", String.valueOf(stats.getUnlockCount()),
                            "%blocked%", String.valueOf(stats.getBlockedCount()));
                    return true;
                }
                case "export" -> {
                    if (sender instanceof Player player && !player.hasPermission("endlock.admin")) {
                        messages.send(sender, "permission");
                        return true;
                    }
                    try {
                        File export = logService.exportJson();
                        messages.send(sender, "export-success", "%file%", export.getName());
                    } catch (Exception e) {
                        messages.send(sender, "export-failed");
                    }
                    return true;
                }
                case "test" -> {
                    if (!getConfig().getBoolean("test-command.enabled", true)) {
                        messages.send(sender, "test-disabled");
                        return false;
                    }
                    String status = locked ? messages.raw("closed") : messages.raw("open");
                    messages.send(sender, "test-success");
                    messages.send(sender, "test-info", "%status%", status);
                    logService.logAction(sender.getName(), "TEST");
                    return true;
                }
                case "stats" -> {
                    sender.sendMessage("§7Stats: §aLock count §f" + getConfig().getInt("stats.lock-count", 0) + " §7| §cBlocked count §f" + getConfig().getInt("stats.blocked-count", 0));
                    return true;
                }
                case "unlockin" -> {
                    if (args.length >= 2) {
                        try {
                            int days = Integer.parseInt(args[1]);
                            scheduledUnlockTime = LocalDateTime.now().plusDays(days);
                            getConfig().set("scheduled-unlock.enabled", true);
                            getConfig().set("scheduled-unlock.mode", "days");
                            getConfig().set("scheduled-unlock.days", days);
                            saveConfig();
                            sender.sendMessage("§aScheduled unlock in " + days + " days.");
                        } catch (Exception e) {
                            sender.sendMessage("§cUsage: /endlock unlockin <days>");
                        }
                    }
                    return true;
                }
                case "unlockat" -> {
                    if (args.length >= 2) {
                        try {
                            scheduledUnlockTime = LocalDateTime.parse(args[1] + " " + args[2], DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                            getConfig().set("scheduled-unlock.enabled", true);
                            getConfig().set("scheduled-unlock.mode", "datetime");
                            getConfig().set("scheduled-unlock.datetime", scheduledUnlockTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                            saveConfig();
                            sender.sendMessage("§aScheduled unlock at " + scheduledUnlockTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ".");
                        } catch (Exception e) {
                            sender.sendMessage("§cUsage: /endlock unlockat <yyyy-MM-dd> <HH:mm>");
                        }
                    }
                    return true;
                }
            }
        }

        if (!(sender instanceof Player) || sender.hasPermission("endlock.toggle")) {
            setLocked(!locked, sender.getName(), true);
            messages.send(sender, "toggle", "%status%", locked ? messages.raw("closed") : messages.raw("open"));
            return true;
        }
        messages.send(sender, "permission");
        return true;
    }

    private void sendStatus(CommandSender sender) {
        String status = locked ? messages.raw("closed") : messages.raw("open");
        messages.send(sender, "status", "%status%", status);
        if (schedule.hasSchedule()) {
            messages.send(sender, "status-scheduled", "%remaining%", schedule.formatRemaining(messages));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("join-notification.enabled", true) || !locked) {
            return;
        }
        Player player = event.getPlayer();
        if (schedule.hasSchedule()) {
            messages.send(player, "join-scheduled", "%remaining%", schedule.formatRemaining(messages));
        } else {
            messages.send(player, "join-locked");
        }
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!locked) {
            return;
        }
        if (event.getTo() == null || event.getTo().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        player.sendMessage(messages.component("locked"));
        if (getConfig().getBoolean("logging.log-attempts", true)) {
            String from = player.getWorld().getName();
            String to = event.getTo().getWorld().getName();
            logService.logBlock(player.getName(), "BLOCKED_PORTAL", from, to, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
            stats.incrementBlocked();
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!locked) {
            return;
        }
        if (event.getTo() == null || event.getTo().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        player.sendMessage(messages.component("locked"));
        if (getConfig().getBoolean("logging.log-attempts", true)) {
            String from = player.getWorld().getName();
            String to = event.getTo().getWorld().getName();
            logService.logBlock(player.getName(), "BLOCKED_TELEPORT", from, to, event.getCause());
            stats.incrementBlocked();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!locked || !getConfig().getBoolean("join-notifications.enabled", false)) {
            return;
        }
        sendJoinNotification(event.getPlayer());
    }
}
