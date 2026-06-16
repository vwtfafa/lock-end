package org.vwtfafa.lockEnd;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LockEnd extends JavaPlugin implements Listener, TabCompleter {
    private boolean locked = false;
    private FileConfiguration langConfig;
    private String langCode = "de";
    private UpdateChecker updateChecker;
    private MetricsManager metricsManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        locked = getConfig().getBoolean("locked", false);
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

        // Initialisiere bStats Metriken
        if (getConfig().getBoolean("metrics.enabled", true)) {
            metricsManager = new MetricsManager(this);
        }

        // Initialisiere Update Checker
        if (getConfig().getBoolean("update-checker.enabled", true)) {
            updateChecker = new UpdateChecker(this);
            updateChecker.checkForUpdates();
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = List.of("status", "lock", "unlock");
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
                    }
                    sender.sendMessage(msg("toggle").replace("%status%", msg("closed")));
                    return true;
                }
                case "unlock" -> {
                    if (locked) {
                        locked = false;
                        getConfig().set("locked", false);
                        saveConfig();
                    }
                    sender.sendMessage(msg("toggle").replace("%status%", msg("open")));
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
            }
        }
    }
}
