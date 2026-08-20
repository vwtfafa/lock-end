package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {
    private final JavaPlugin plugin;
    private final String currentVersion;
    private volatile String latestVersion;
    private volatile boolean updateAvailable;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /**
     * Lädt die neueste Version von GitHub asynchron und benachrichtigt Ops
     */
    public void checkForUpdates() {
        boolean notifyOps = plugin.getConfig().getBoolean("update-checker.notify-ops", true);
        boolean notifyChat = plugin.getConfig().getBoolean("update-checker.notify-chat", true);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Abrufen der neuesten Version von GitHub API
                URL url = new URL("https://api.github.com/repos/vwtfafa/lock-end/releases/latest");
                URLConnection connection = url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "EndLock/" + currentVersion);

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                // Parse die Version aus der JSON-Antwort
                Matcher matcher = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(response);
                if (matcher.find()) {
                    latestVersion = matcher.group(1);
                    updateAvailable = isNewerVersion(latestVersion, currentVersion);

                    if (updateAvailable) {
                        plugin.getLogger().info("========================================");
                        plugin.getLogger().info("EndLock update available!");
                        plugin.getLogger().info("Current version: " + currentVersion);
                        plugin.getLogger().info("New version: " + latestVersion);
                        plugin.getLogger().info("Release page: https://github.com/vwtfafa/lock-end/releases");
                        plugin.getLogger().info("========================================");

                        // Notify online operators
                        notifyOps(notifyOps, notifyChat);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Update-Check fehlgeschlagen: " + e.getMessage());
            }
        });
    }

    /**
     * Sends a chat notification to online operators about available updates
     */
    private void notifyOps(boolean notifyOps, boolean notifyChat) {
        if (!notifyOps || !notifyChat) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            String releaseUrl = "https://github.com/vwtfafa/lock-end/releases";
            Component message = Component.text("[EndLock] Update available: " + latestVersion + " - Open release page")
                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .clickEvent(ClickEvent.openUrl(releaseUrl))
                .hoverEvent(HoverEvent.showText(Component.text("Open the latest release page")));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp() || player.hasPermission("endlock.admin")) {
                    player.sendMessage(message);
                }
            }
        });
    }

    /**
     * Vergleicht zwei Versionsnummern
     */
    private boolean isNewerVersion(String newVersion, String currentVersion) {
        try {
            // Entferne 'v' Prefix wenn vorhanden
            newVersion = newVersion.replaceFirst("^v", "");
            currentVersion = currentVersion.replaceFirst("^v", "");

            String[] newParts = newVersion.split("\\.");
            String[] currentParts = currentVersion.split("\\.");

            for (int i = 0; i < Math.max(newParts.length, currentParts.length); i++) {
                int newNum = i < newParts.length ? Integer.parseInt(newParts[i]) : 0;
                int currentNum = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;

                if (newNum > currentNum) return true;
                if (newNum < currentNum) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}
