package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;
import org.vwtfafa.lockEnd.commands.ConfigValidatorCommand;
import org.vwtfafa.lockEnd.commands.LockHistoryCommand;
import org.vwtfafa.lockEnd.commands.UndoCommand;
import org.vwtfafa.lockEnd.util.AsyncLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class LockEnd extends JavaPlugin implements Listener, TabCompleter {
    private boolean locked = false;
    private FileConfiguration langConfig;
    private String langCode = "en";
    private UpdateChecker updateChecker;
    private MetricsManager metricsManager;
    private File logDir;
    private File logFile;
    private MiniMessage miniMessage;
    private boolean miniMessageEnabled;
    private LocalDateTime scheduledUnlockTime;
    private LockEndExpansion placeholderExpansion;
    private int lockCount = 0;
    private int blockedCount = 0;
    private String lockReason = "Maintenance";

    // v1.6 new features
    private LockReasonManager lockReasonManager;
    private GracePeriodTask gracePeriodTask;
    private WhitelistChecker whitelistChecker;
    private PreviewNotificationManager previewManager;
    private SoundEffectPlayer soundPlayer;
    private LockHistoryCommand historyCommand;
    private UndoCommand undoCommand;
    private ConfigValidatorCommand configValidatorCommand;
    private AsyncLogger asyncLogger;

    // Logging & Analytics
    private final Map<UUID, Long> lastAttemptTimes = new java.util.concurrent.ConcurrentHashMap<>();
    private int rateLimitSeconds = 5;

    // Schedule pause/resume
    private boolean schedulePaused = false;
    private BukkitTask scheduledUnlockTask;
    private BukkitTask countdownTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        locked = getConfig().getBoolean("locked", false);
        lockCount = getConfig().getInt("stats.lock-count", 0);
        blockedCount = getConfig().getInt("stats.blocked-count", 0);
        langCode = getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        loadLanguage(langCode);
        lockReason = getConfig().getString("lock-reason", "Maintenance");

        // v1.6: Lock reason manager
        lockReasonManager = new LockReasonManager(getConfig());

        // v1.6: Grace period task
        gracePeriodTask = new GracePeriodTask(this);

        // v1.6: Whitelist checker
        whitelistChecker = new WhitelistChecker(getConfig());

        // v1.6: Preview notifications
        previewManager = new PreviewNotificationManager(this);

        // v1.6: Sound effects
        soundPlayer = new SoundEffectPlayer(this);

        // v1.6: Admin commands
        historyCommand = new LockHistoryCommand(this);
        undoCommand = new UndoCommand(this);
        configValidatorCommand = new ConfigValidatorCommand(this);

        // v1.6: Async logger
        if (getConfig().getBoolean("logging.enabled", true)) {
            logDir = new File(getDataFolder(), "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            String configuredLogFile = getConfig().getString("logging.log-file", "EndLock.log");
            Path logDirectory = logDir.toPath().toAbsolutePath().normalize();
            Path configuredPath = logDirectory.resolve(configuredLogFile).normalize();
            if (!configuredPath.startsWith(logDirectory)) {
                getLogger().warning("Invalid logging.log-file path; using EndLock.log instead.");
                configuredPath = logDirectory.resolve("EndLock.log");
            }
            logFile = configuredPath.toFile();
            asyncLogger = new AsyncLogger();
            asyncLogger.initialize(logFile);
        }

        miniMessageEnabled = getConfig().getBoolean("hooks.mini-message", true);
        if (miniMessageEnabled) {
            this.miniMessage = MiniMessage.miniMessage();
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        // Register commands
        if (getCommand("endlock") != null) {
            getCommand("endlock").setExecutor(this);
            getCommand("endlock").setTabCompleter(this);
        }
        if (getCommand("lock") != null) {
            getCommand("lock").setExecutor(this);
            getCommand("lock").setTabCompleter(this);
        }
        if (getCommand("el") != null) {
            getCommand("el").setExecutor(this);
            getCommand("el").setTabCompleter(this);
        }

        // v1.6: Rate limit config
        rateLimitSeconds = getConfig().getInt("logging.rate-limit-seconds", 5);
        schedulePaused = getConfig().getBoolean("schedule.paused", false);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null && getConfig().getBoolean("hooks.placeholderapi", true)) {
            placeholderExpansion = new LockEndExpansion(this);
            placeholderExpansion.register();
        }

        if (getConfig().getBoolean("update-checker.enabled", true)) {
            updateChecker = new UpdateChecker(this);
            updateChecker.checkForUpdates();
        }

        // Initialize bStats metrics
        if (getConfig().getBoolean("metrics.enabled", true)) {
            metricsManager = new MetricsManager(this);
        }

        loadScheduledUnlock();
        if (locked && scheduledUnlockTime != null) {
            scheduleUnlock();
        }

        getLogger().info("EndLock v" + getDescription().getVersion() + " enabled (Paper 26.2+)");
    }

    @Override
    public void onDisable() {
        getConfig().set("locked", locked);
        saveConfig();
        cancelScheduledUnlock();
        cancelCountdown();
        if (previewManager != null) {
            previewManager.cancelAll();
        }
        if (gracePeriodTask != null) {
            gracePeriodTask.cancel();
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        if (asyncLogger != null) {
            asyncLogger.shutdown();
        }
        getLogger().info("EndLock disabled");
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean changeLockState(boolean newLocked, String actor, String action, boolean recordStats) {
        if (locked == newLocked) {
            return false;
        }

        boolean previousState = locked;
        locked = newLocked;
        historyCommand.recordPreviousState(previousState);
        getConfig().set("locked", locked);
        saveConfig();

        if (!locked) {
            cancelScheduledUnlock();
            previewManager.cancelPreview("unlock");
        }
        broadcastMessage(locked ? "broadcast-locked" : "broadcast-unlocked", actor);
        logAction(actor, action);
        historyCommand.addEntry(action + " by " + actor + " (previously " + (previousState ? "locked" : "unlocked") + ")");
        if (recordStats && locked) {
            incrementStats(true);
        }
        return true;
    }

    public boolean undoLastAction(String actor) {
        if (historyCommand.getLastPreviousState() == null) {
            return false;
        }
        boolean restored = historyCommand.getLastPreviousState();
        boolean changed = changeLockState(restored, actor, "UNDO", false);
        historyCommand.clearLastPreviousState();
        return changed;
    }

    private void loadLanguage(String code) {
        String fileName = "messages_" + code + ".yml";
        File langFile = new File(getDataFolder(), fileName);
        if (!langFile.exists()) {
            try (InputStream in = getResource(fileName)) {
                if (in != null) {
                    langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in));
                    return;
                }
            } catch (Exception ignored) {}
            try (InputStream in = getResource("messages_de.yml")) {
                if (in != null) {
                    langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in));
                    return;
                }
            } catch (Exception ignored) {}
        } else {
            langConfig = YamlConfiguration.loadConfiguration(langFile);
        }
    }

    public String msg(String key) {
        if (langConfig == null) return key;
        return langConfig.getString(key, key);
    }

    private Component miniMsg(String key) {
        return messageComponent(msg(key));
    }

    private Component messageComponent(String raw) {
        if (miniMessageEnabled && miniMessage != null) {
            return miniMessage.deserialize(raw);
        }
        return LegacyComponentSerializer.legacySection().deserialize(raw);
    }

    private void broadcastMessage(String key, String playerName) {
        if (!getConfig().getBoolean("broadcast.enabled", true)) {
            return;
        }

        boolean notifyAll = getConfig().getBoolean("broadcast.notify-all", true);
        boolean useActionbar = getConfig().getBoolean("broadcast.use-actionbar", true);
        String rawMessage = msg(key).replace("%player%", playerName);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (notifyAll || player.isOp() || player.hasPermission("endlock.admin")) {
                if (useActionbar) {
                    sendActionBar(player, locked ? miniMsg("actionbar-locked") : miniMsg("actionbar-unlocked"));
                } else {
                    player.sendMessage(messageComponent(rawMessage));
                }
            }
        }
    }

    private void sendActionBar(Player player, Component message) {
        try {
            player.sendActionBar(message);
        } catch (Exception e) {
            player.sendMessage(message);
        }
    }

    private void incrementStats(boolean lockAction) {
        if (!getConfig().getBoolean("stats.enabled", true)) {
            return;
        }
        if (lockAction) {
            lockCount++;
            getConfig().set("stats.lock-count", lockCount);
        } else {
            blockedCount++;
            getConfig().set("stats.blocked-count", blockedCount);
        }
        saveConfig();
    }

    public int getLockCount() {
        return lockCount;
    }

    public int getBlockedCount() {
        return blockedCount;
    }

    private void loadScheduledUnlock() {
        if (!getConfig().getBoolean("scheduled-unlock.enabled", false)) {
            return;
        }
        String mode = getConfig().getString("scheduled-unlock.mode", "days");
        if ("datetime".equalsIgnoreCase(mode)) {
            String date = getConfig().getString("scheduled-unlock.datetime", "");
            if (!date.isBlank()) {
                try {
                    scheduledUnlockTime = LocalDateTime.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                } catch (Exception ignored) {
                }
            }
        } else {
            int days = getConfig().getInt("scheduled-unlock.days", 7);
            scheduledUnlockTime = LocalDateTime.now().plusDays(days);
        }
    }

    private void scheduleUnlock() {
        if (scheduledUnlockTime == null || schedulePaused) {
            return;
        }
        cancelScheduledUnlock();
        cancelCountdown();
        // Schedule preview notification before unlock
        previewManager.schedulePreviewUnlock(scheduledUnlockTime);
        scheduleCountdown();
        long secondsDelay = java.time.Duration.between(java.time.LocalDateTime.now(), scheduledUnlockTime).getSeconds();
        long ticksDelay = Math.max(secondsDelay, 0) * 20L;
        scheduledUnlockTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (locked && !schedulePaused) {
                changeLockState(false, "System", "SCHEDULED_UNLOCK", false);
                getLogger().info("Scheduled unlock executed.");
            }
            scheduledUnlockTask = null;
        }, ticksDelay);
    }

    private void cancelScheduledUnlock() {
        if (scheduledUnlockTask != null) {
            scheduledUnlockTask.cancel();
            scheduledUnlockTask = null;
        }
    }

    private void scheduleCountdown() {
        if (!getConfig().getBoolean("scheduled-unlock.countdown.enabled", false)) {
            return;
        }
        long startBefore = getConfig().getLong("scheduled-unlock.countdown.start-before", 300);
        long interval = Math.max(1, getConfig().getLong("scheduled-unlock.countdown.interval", 10));
        countdownTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!locked || scheduledUnlockTime == null || schedulePaused) {
                return;
            }
            long remaining = java.time.Duration.between(LocalDateTime.now(), scheduledUnlockTime).getSeconds();
            if (remaining < 0 || remaining > startBefore) {
                return;
            }
            String message = msg("countdown-notification").replace("%time%", formatDuration(remaining));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp() || player.hasPermission("endlock.admin")) {
                    player.sendMessage(messageComponent(message));
                }
            }
        }, 0L, interval * 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + remainingSeconds + "s";
        return remainingSeconds + "s";
    }

    public void pauseSchedule() {
        schedulePaused = true;
        getConfig().set("schedule.paused", true);
        saveConfig();
        cancelScheduledUnlock();
        previewManager.cancelPreview("unlock");
        getLogger().info("Schedule paused by " + "System");
    }

    public void resumeSchedule() {
        schedulePaused = false;
        getConfig().set("schedule.paused", false);
        saveConfig();
        if (locked && scheduledUnlockTime != null) {
            scheduleUnlock();
        }
        getLogger().info("Schedule resumed by " + "System");
    }

    public boolean isSchedulePaused() {
        return schedulePaused;
    }

    private void sendJoinNotification(Player player) {
        if (!getConfig().getBoolean("join-notifications.enabled", false) || !locked) {
            return;
        }
        player.sendMessage(messageComponent(msg("join-notification")));
    }

    public boolean isLocked() {
        return locked;
    }

    public String getRemainingText() {
        if (!locked) {
            return "Unlocked";
        }
        return scheduledUnlockTime != null ? scheduledUnlockTime.toString() : "Permanent";
    }

    private void logAction(String player, String action) {
        if (!getConfig().getBoolean("logging.enabled", true)) {
            return;
        }
        String message = String.format("%s - Player: %s - Status: %s", action, player, locked ? "LOCKED" : "UNLOCKED");
        if (asyncLogger != null) {
            asyncLogger.log(message);
        } else {
            writeToLogFile(message);
        }
    }

    /**
     * v1.6: Logs attempt with rate limiting and detailed info.
     */
    private void logAttempt(Player player, World sourceWorld, String method) {
        if (!getConfig().getBoolean("logging.log-attempts", true)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Rate limit check
        if (lastAttemptTimes.containsKey(playerId)) {
            long lastAttempt = lastAttemptTimes.get(playerId);
            if (now - lastAttempt < rateLimitSeconds * 1000L) {
                return;
            }
        }
        lastAttemptTimes.put(playerId, now);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logMessage = String.format("[%s] Attempt - Player: %s - World: %s - Method: %s - Status: LOCKED\n",
                timestamp, player.getName(), sourceWorld.getName(), method);

        if (asyncLogger != null) {
            asyncLogger.log(logMessage.trim());
        } else {
            writeToLogFile(logMessage.trim());
        }
    }

    private void writeToLogFile(String message) {
        if (logFile == null) {
            return;
        }
        try {
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.append(message);
                writer.append("\n");
                writer.flush();
            }
        } catch (IOException e) {
            getLogger().warning("Error writing to log file: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = List.of("status", "lock", "unlock", "test", "stats",
                    "unlockin", "unlockat", "reload", "history", "undo", "validateconfig",
                    "pause", "resume", "cancel", "reason");
            StringUtil.copyPartialMatches(args[0], options, completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "unlockin" -> {
                    List<String> days = List.of("1", "7", "30");
                    StringUtil.copyPartialMatches(args[1], days, completions);
                }
                case "unlockat" -> {
                    java.time.LocalDate tomorrow = java.time.LocalDate.now().plusDays(1);
                    String dateHint = tomorrow.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    List<String> dates = List.of(dateHint);
                    StringUtil.copyPartialMatches(args[1], dates, completions);
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("unlockat")) {
                List<String> times = List.of("00:00", "12:00", "23:59");
                StringUtil.copyPartialMatches(args[2], times, completions);
            }
        }
        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "status" -> {
                    String status = locked ? msg("closed") : msg("open");
                    sender.sendMessage(msg("status").replace("%status%", status));
                    return true;
                }
                case "lock" -> {
                    if (!sender.hasPermission("endlock.admin")) {
                        sender.sendMessage(msg("permission"));
                        return true;
                    }
                    if (!locked) {
                        sender.sendMessage(msg("toggle").replace("%status%", msg("closed")));
                        changeLockState(true, sender.getName(), "LOCK", true);

                        // v1.6: Grace period
                        if (getConfig().getBoolean("grace-period.enabled", false)) {
                            int duration = getConfig().getInt("grace-period.duration", 10);
                            gracePeriodTask.startGracePeriod(duration);
                        }
                    } else {
                        sender.sendMessage(msg("already-locked"));
                    }
                    return true;
                }
                case "unlock" -> {
                    if (!sender.hasPermission("endlock.admin")) {
                        sender.sendMessage(msg("permission"));
                        return true;
                    }
                    if (locked) {
                        sender.sendMessage(msg("toggle").replace("%status%", msg("open")));
                        changeLockState(false, sender.getName(), "UNLOCK", false);
                    } else {
                        sender.sendMessage(msg("already-unlocked"));
                    }
                    return true;
                }
                case "test" -> {
                    if (getConfig().getBoolean("test-command.enabled", true)) {
                        String status = locked ? msg("closed") : msg("open");
                        sender.sendMessage(msg("test-success"));
                        sender.sendMessage(msg("test-info").replace("%status%", status));
                        logAction(sender.getName(), "TEST");
                        return true;
                    } else {
                        sender.sendMessage("§cTest command is disabled!");
                        return false;
                    }
                }
                case "stats" -> {
                    sender.sendMessage("§7Stats: §aLock count §f" + getConfig().getInt("stats.lock-count", 0) + " §7| §cBlocked count §f" + getConfig().getInt("stats.blocked-count", 0));
                    return true;
                }
                case "unlockin" -> {
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /endlock unlockin <days>");
                        return true;
                    }
                    try {
                        int days = Integer.parseInt(args[1]);
                        scheduledUnlockTime = LocalDateTime.now().plusDays(days);
                        getConfig().set("scheduled-unlock.enabled", true);
                        getConfig().set("scheduled-unlock.mode", "days");
                        getConfig().set("scheduled-unlock.days", days);
                        saveConfig();
                        sender.sendMessage("§aScheduled unlock in " + days + " days.");
                        if (locked) {
                            previewManager.schedulePreviewUnlock(scheduledUnlockTime);
                            scheduleUnlock();
                        }
                    } catch (Exception e) {
                        sender.sendMessage("§cUsage: /endlock unlockin <days>");
                    }
                    return true;
                }
                case "unlockat" -> {
                    if (args.length < 3) {
                        sender.sendMessage("§cUsage: /endlock unlockat <yyyy-MM-dd> <HH:mm>");
                        return true;
                    }
                    try {
                        scheduledUnlockTime = LocalDateTime.parse(args[1] + " " + args[2], DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                        getConfig().set("scheduled-unlock.enabled", true);
                        getConfig().set("scheduled-unlock.mode", "datetime");
                        getConfig().set("scheduled-unlock.datetime", scheduledUnlockTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                        saveConfig();
                        sender.sendMessage("§aScheduled unlock at " + scheduledUnlockTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ".");
                        if (locked) {
                            previewManager.schedulePreviewUnlock(scheduledUnlockTime);
                            scheduleUnlock();
                        }
                    } catch (Exception e) {
                        sender.sendMessage("§cUsage: /endlock unlockat <yyyy-MM-dd> <HH:mm>");
                    }
                    return true;
                }
                case "cancel" -> {
                    if (!sender.hasPermission("endlock.admin")) {
                        sender.sendMessage(msg("permission"));
                        return true;
                    }
                    scheduledUnlockTime = null;
                    getConfig().set("scheduled-unlock.enabled", false);
                    saveConfig();
                    cancelScheduledUnlock();
                    cancelCountdown();
                    previewManager.cancelAll();
                    sender.sendMessage(msg("schedule-cancelled"));
                    return true;
                }
                case "reason" -> {
                    if (!sender.hasPermission("endlock.admin")) {
                        sender.sendMessage(msg("permission"));
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(msg("reason-usage"));
                        return true;
                    }
                    lockReason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                    getConfig().set("lock-reason", lockReason);
                    getConfig().set("lock-reasons.default", lockReason);
                    saveConfig();
                    lockReasonManager = new LockReasonManager(getConfig());
                    sender.sendMessage(msg("reason-set").replace("%reason%", lockReason));
                    return true;
                }
                case "pause" -> {
                    if (!sender.hasPermission("endlock.admin")) {
                        sender.sendMessage(msg("permission"));
                        return true;
                    }
                    pauseSchedule();
                    sender.sendMessage(msg("schedule-paused"));
                    return true;
                }
                case "resume" -> {
                    if (!sender.hasPermission("endlock.admin")) {
                        sender.sendMessage(msg("permission"));
                        return true;
                    }
                    resumeSchedule();
                    sender.sendMessage(msg("schedule-resumed"));
                    return true;
                }
                case "history" -> {
                    if (!sender.hasPermission("endlock.history")) {
                        sender.sendMessage(msg("permission"));
                        return true;
                    }
                    historyCommand.onCommand(sender, command, label, args);
                    return true;
                }
                case "undo" -> {
                    if (!sender.hasPermission("endlock.undo")) {
                        sender.sendMessage(msg("permission"));
                        return true;
                    }
                    undoCommand.onCommand(sender, command, label, args);
                    return true;
                }
                case "validateconfig" -> {
                    if (!sender.hasPermission("endlock.validate")) {
                        sender.sendMessage(msg("permission"));
                        return true;
                    }
                    configValidatorCommand.onCommand(sender, command, label, args);
                    return true;
                }
                case "reload" -> {
                    if (!sender.hasPermission("endlock.reload")) {
                        sender.sendMessage(msg("permission"));
                        return true;
                    }
                    cancelScheduledUnlock();
                    previewManager.cancelAll();
                    reloadConfig();
                    langCode = getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
                    loadLanguage(langCode);
                    lockReason = getConfig().getString("lock-reason", "Maintenance");
                    // Refresh managers with new config
                    lockReasonManager = new LockReasonManager(getConfig());
                    whitelistChecker = new WhitelistChecker(getConfig());
                    rateLimitSeconds = getConfig().getInt("logging.rate-limit-seconds", 5);
                    schedulePaused = getConfig().getBoolean("schedule.paused", false);
                    loadScheduledUnlock();
                    soundPlayer.loadConfig();
                    if (locked && scheduledUnlockTime != null) {
                        scheduleUnlock();
                    }
                    if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null && getConfig().getBoolean("hooks.placeholderapi", true)) {
                        if (placeholderExpansion == null) {
                            placeholderExpansion = new LockEndExpansion(this);
                            placeholderExpansion.register();
                        }
                    }
                    sender.sendMessage(msg("reload-success"));
                    return true;
                }
            }
        }

        if (!(sender instanceof Player) || sender.hasPermission("endlock.toggle")) {
            boolean newLocked = !locked;
            String status = newLocked ? msg("closed") : msg("open");
            sender.sendMessage(msg("toggle").replace("%status%", status));
            changeLockState(newLocked, sender.getName(), newLocked ? "LOCK" : "UNLOCK", newLocked);
            if (newLocked) {
                // Grace period on lock
                if (getConfig().getBoolean("grace-period.enabled", false)) {
                    int duration = getConfig().getInt("grace-period.duration", 10);
                    gracePeriodTask.startGracePeriod(duration);
                }
            }
            return true;
        } else {
            sender.sendMessage(msg("permission"));
            return true;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        handleEndAccess(event, "PORTAL");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        handleEndAccess(event, "TELEPORT_" + event.getCause().name());
    }

    private void handleEndAccess(PlayerTeleportEvent event, String method) {
        Player player = event.getPlayer();
        if (!locked || event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (event.getTo().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        if (!getConfig().getBoolean("end.block-return", false)
                && event.getFrom().getWorld().getEnvironment() == World.Environment.THE_END) {
            return;
        }
        List<String> configuredWorlds = getConfig().getStringList("end.worlds");
        if (!configuredWorlds.isEmpty() && configuredWorlds.stream().noneMatch(name ->
                name.equalsIgnoreCase(event.getTo().getWorld().getName()))) {
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY
                && !getConfig().getBoolean("end.block-end-gateway", true)) {
            return;
        }
        if (whitelistChecker.canBypass(player)) return;
        event.setCancelled(true);
        String reason = lockReasonManager.getReason("default");
        player.sendMessage(messageComponent(msg("locked-reason").replace("%reason%", reason)));
        soundPlayer.playDenialSound(player);
        if (getConfig().getBoolean("logging.log-attempts", true)) {
            logAttempt(player, player.getWorld(), method);
        }
        incrementStats(false);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!locked || !getConfig().getBoolean("join-notifications.enabled", false)) {
            return;
        }
        sendJoinNotification(event.getPlayer());
    }
}