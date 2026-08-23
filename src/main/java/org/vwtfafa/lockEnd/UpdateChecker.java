package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {
    private final JavaPlugin plugin;
    private final String currentVersion;
    private volatile String latestVersion;
    private volatile boolean updateAvailable;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

    /**
     * Fetches the latest version from GitHub asynchronously and notifies admins
     */
    public void checkForUpdates() {
        boolean notifyOps = plugin.getConfig().getBoolean("update-checker.notify-ops", true);
        boolean notifyChat = plugin.getConfig().getBoolean("update-checker.notify-chat", true);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Abrufen der neuesten Version von GitHub API
                URI releasesUri = URI.create("https://api.github.com/repos/vwtfafa/lock-end/releases/latest");
                URLConnection connection = releasesUri.toURL().openConnection();
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

                // Parse the version from the JSON response
                Matcher matcher = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(response);
                if (matcher.find()) {
                    latestVersion = matcher.group(1);
                    updateAvailable = isNewerVersion(latestVersion, currentVersion);

                    if (updateAvailable) {
                        if (notifyOps) {
                            plugin.getLogger().info("========================================");
                            plugin.getLogger().info("EndLock update available!");
                            plugin.getLogger().info("Current version: " + currentVersion);
                            plugin.getLogger().info("New version: " + latestVersion);
                            plugin.getLogger().info("Release page: https://github.com/vwtfafa/lock-end/releases");
                            plugin.getLogger().info("========================================");
                        }

                        // Notify online operators via chat
                        if (notifyChat) {
                            notifyOnlineAdmins();
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Update check failed: " + e.getMessage());
            }
        });
    }

    /**
     * Sends a chat notification with the release link to online operators
     */
    private void notifyOnlineAdmins() {
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
     * Compares two version numbers
     */
    static boolean isNewerVersion(String candidateVersion, String currentVersion) {
        try {
            String newVersion = normalizeVersion(candidateVersion);
            String normalizedCurrent = normalizeVersion(currentVersion);

            String[] newParts = newVersion.split("\\.");
            String[] currentParts = normalizedCurrent.split("\\.");

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

    private static String normalizeVersion(String version) {
        String normalized = version.trim().replaceFirst("^[vV]", "");
        int prereleaseSeparator = normalized.indexOf('-');
        return prereleaseSeparator >= 0 ? normalized.substring(0, prereleaseSeparator) : normalized;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}
