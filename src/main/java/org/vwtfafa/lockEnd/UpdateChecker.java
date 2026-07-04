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

public class UpdateChecker {
    private final JavaPlugin plugin;
    private final String currentVersion;
    private String latestVersion = null;
    private boolean updateAvailable = false;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /**
     * Lädt die neueste Version von GitHub asynchron und benachrichtigt Ops
     */
    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Abrufen der neuesten Version von GitHub API
                URL url = new URL("https://api.github.com/repos/vwtfafa/lock-end/releases/latest");
                URLConnection connection = url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                StringBuilder response = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Parse die Version aus der JSON-Antwort
                String json = response.toString();
                int tagIndex = json.indexOf("\"tag_name\":\"");
                if (tagIndex != -1) {
                    int startIndex = tagIndex + 12;
                    int endIndex = json.indexOf("\"", startIndex);
                    latestVersion = json.substring(startIndex, endIndex);
                    updateAvailable = isNewerVersion(latestVersion, currentVersion);

                    if (updateAvailable) {
                        plugin.getLogger().info("========================================");
                        plugin.getLogger().info("EndLock Update verfügbar!");
                        plugin.getLogger().info("Aktuelle Version: " + currentVersion);
                        plugin.getLogger().info("Neue Version: " + latestVersion);
                        plugin.getLogger().info("Download: https://github.com/vwtfafa/lock-end/releases");
                        plugin.getLogger().info("========================================");

                        // Benachrichtige online Ops
                        notifyOps();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Update-Check fehlgeschlagen: " + e.getMessage());
            }
        });
    }

    /**
     * Sendet eine Nachricht an alle online Ops über verfügbare Updates
     */
    private void notifyOps() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Component message = Component.text("[EndLock] Update available: " + latestVersion)
                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .clickEvent(ClickEvent.openUrl("https://github.com/vwtfafa/lock-end/releases"))
                .hoverEvent(HoverEvent.showText(Component.text("Open the latest release")));
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
