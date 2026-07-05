package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
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
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LockEnd extends JavaPlugin implements Listener, TabCompleter {
    private boolean locked = false;
    private FileConfiguration langConfig;
    private String langCode = "de";
    private UpdateChecker updateChecker;
    private MetricsManager metricsManager;
    private File logDir;
    private File logFile;
    private MiniMessage miniMessage = MiniMessage.miniMessage();
    private LocalDateTime scheduledUnlockTime;
    private LockEndExpansion placeholderExpansion;
    private int lockCount = 0;
    private int blockedCount = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        locked = getConfig().getBoolean("locked", false);
        lockCount = getConfig().getInt("stats.lock-count", 0);
        blockedCount = getConfig().getInt("stats.blocked-count", 0);
        langCode = getConfig().getString("language", "de").toLowerCase(Locale.ROOT);
        loadLanguage(langCode);
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("endlock") != null) {
            getCommand("endlock").setExecutor(this);
            getCommand("endlock").setTabCompleter(this);
        }
        if (getCommand("lock") != null) {
            getCommand("lock").setExecutor(this);
            getCommand("lock").setTabCompleter(this);
        }

        // Initialisiere Logging
        if (getConfig().getBoolean("logging.enabled", true)) {
            logDir = new File(getDataFolder(), "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            logFile = new File(logDir, getConfig().getString("logging.log-file", "EndLock.log"));
        }

        // Initialisiere bStats Metriken
        if (getConfig().getBoolean("metrics.enabled", true)) {
            metricsManager = new MetricsManager(this);
        }

        // Initialisiere Update Checker
        if (getConfig().getBoolean("update-checker.enabled", true)) {
            updateChecker = new UpdateChecker(this);
            updateChecker.checkForUpdates();
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null && getConfig().getBoolean("hooks.placeholderapi", true)) {
            placeholderExpansion = new LockEndExpansion(this);
            placeholderExpansion.register();
        }

        loadScheduledUnlock();
        if (locked && scheduledUnlockTime != null) {
            scheduleUnlock();
        }

        getLogger().info("EndLock v" + getDescription().getVersion() + " aktiviert (Paper 26.2+)");
    }

    @Override
    public void onDisable() {
        getConfig().set("locked", locked);
        saveConfig();
        getLogger().info("EndLock deaktiviert");
    }

    private void loadLanguage(String code) {
        String fileName = "messages_" + code + ".yml";
        File langFile = new File(getDataFolder(), fileName);
        if (!langFile.exists()) {
            // Lade aus resources, falls nicht im Plugin-Ordner
            try (InputStream in = getResource(fileName)) {
                if (in != null) {
                    langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in));
                    return;
                }
            } catch (Exception ignored) {}
            // Fallback auf Deutsch
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

    private String msg(String key) {
        if (langConfig == null) return key;
        return langConfig.getString(key, key);
    }

    private void broadcastMessage(String key, String playerName) {
        if (!getConfig().getBoolean("broadcast.enabled", true)) {
            return;
        }

        boolean notifyAll = getConfig().getBoolean("broadcast.notify-all", true);
        boolean useActionbar = getConfig().getBoolean("broadcast.use-actionbar", true);
        String message = msg(key).replace("%player%", playerName);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (notifyAll || player.isOp() || player.hasPermission("endlock.admin")) {
                if (useActionbar) {
                    sendActionBar(player, locked ? msg("actionbar-locked") : msg("actionbar-unlocked"));
                } else {
                    player.sendMessage(message);
                }
            }
        }
    }

    private void sendActionBar(Player player, String message) {
        try {
            player.sendActionBar(Component.text(message));
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
        if (scheduledUnlockTime == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (locked) {
                locked = false;
                getConfig().set("locked", false);
                saveConfig();
                getLogger().info("Scheduled unlock executed.");
            }
        }, 20L * 60L * 60L * 24L);
    }

    private void sendJoinNotification(Player player) {
        if (!getConfig().getBoolean("join-notifications.enabled", false) || !locked) {
            return;
        }
        String message = getConfig().getBoolean("join-notifications.show-remaining", true)
            ? msg("join-notification")
            : msg("join-notification");
        player.sendMessage(message);
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

        try {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String timestamp = now.format(formatter);
            String logMessage = String.format("[%s] %s - Player: %s - Status: %s\n",
                timestamp, action, player, locked ? "LOCKED" : "UNLOCKED");

            if (logFile != null && !logFile.exists()) {
                logFile.createNewFile();
            }

            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.append(logMessage);
                writer.flush();
            }
        } catch (IOException e) {
            getLogger().warning("Fehler beim Schreiben in Log-Datei: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = List.of("status", "lock", "unlock", "test", "stats", "unlockin", "unlockat");
            StringUtil.copyPartialMatches(args[0], options, completions);
        }
        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "status" -> {
                    String status = locked ? msg("closed") : msg("open");
                    sender.sendMessage(msg("status").replace("%status%", status));
                    return true;
                }
                case "lock" -> {
                    if (!locked) {
                        locked = true;
                        getConfig().set("locked", true);
                        saveConfig();
                        sender.sendMessage(msg("toggle").replace("%status%", msg("closed")));
                        broadcastMessage("broadcast-locked", sender.getName());
                        logAction(sender.getName(), "LOCK");
                        incrementStats(true);
                    } else {
                        sender.sendMessage("§cThe End is already locked!");
                    }
                    return true;
                }
                case "unlock" -> {
                    if (locked) {
                        locked = false;
                        getConfig().set("locked", false);
                        saveConfig();
                        sender.sendMessage(msg("toggle").replace("%status%", msg("open")));
                        broadcastMessage("broadcast-unlocked", sender.getName());
                        logAction(sender.getName(), "UNLOCK");
                    } else {
                        sender.sendMessage("§cThe End is already unlocked!");
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
            locked = !locked;
            String status = locked ? msg("closed") : msg("open");
            sender.sendMessage(msg("toggle").replace("%status%", status));
            getConfig().set("locked", locked);
            saveConfig();
            broadcastMessage(locked ? "broadcast-locked" : "broadcast-unlocked", sender.getName());
            logAction(sender.getName(), locked ? "LOCK" : "UNLOCK");
            if (locked) {
                incrementStats(true);
            }
            return true;
        } else {
            sender.sendMessage(msg("permission"));
            return true;
        }
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!locked) return;
        if (event.getTo() != null && event.getTo().getWorld().getEnvironment() == World.Environment.THE_END) {
            event.setCancelled(true);
            if (event.getPlayer() != null) {
                event.getPlayer().sendMessage(msg("locked"));
                if (getConfig().getBoolean("logging.log-attempts", true)) {
                    logAction(event.getPlayer().getName(), "BLOCKED_PORTAL");
                    incrementStats(false);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!locked) return;
        if (event.getTo() != null && event.getTo().getWorld().getEnvironment() == World.Environment.THE_END) {
            event.setCancelled(true);
            if (event.getPlayer() != null) {
                event.getPlayer().sendMessage(msg("locked"));
                if (getConfig().getBoolean("logging.log-attempts", true)) {
                    logAction(event.getPlayer().getName(), "BLOCKED_TELEPORT");
                    incrementStats(false);
                }
            }
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
