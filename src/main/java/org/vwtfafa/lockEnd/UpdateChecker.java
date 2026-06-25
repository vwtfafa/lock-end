package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

public class UpdateChecker {
    private static final String RELEASES_URL = "https://github.com/vwtfafa/lock-end/releases";
    private static final String API_URL = "https://api.github.com/repos/vwtfafa/lock-end/releases/latest";

    private final JavaPlugin plugin;
    private final String currentVersion;
    private String latestVersion;
    private String downloadUrl = RELEASES_URL;
    private boolean updateAvailable;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URLConnection connection = new URL(API_URL).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                String json = response.toString();
                latestVersion = extractJsonValue(json, "tag_name");
                String htmlUrl = extractJsonValue(json, "html_url");
                if (htmlUrl != null && !htmlUrl.isBlank()) {
                    downloadUrl = htmlUrl;
                }
                updateAvailable = isNewerVersion(latestVersion, currentVersion);

                if (updateAvailable) {
                    plugin.getLogger().info("EndLock update available: " + currentVersion + " -> " + latestVersion);
                    plugin.getLogger().info("Download: " + downloadUrl);
                    notifyOps();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Update check failed: " + e.getMessage());
            }
        });
    }

    private void notifyOps() {
        if (!plugin.getConfig().getBoolean("update-checker.notify-ops", true)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Component prefix = Component.text("[EndLock] ", NamedTextColor.GOLD);
            Component text = Component.text("Update available: ", NamedTextColor.GRAY)
                    .append(Component.text(latestVersion, NamedTextColor.YELLOW))
                    .append(Component.text(" — ", NamedTextColor.GRAY));
            Component link = Component.text("Download", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(downloadUrl))
                    .hoverEvent(HoverEvent.showText(Component.text(downloadUrl, NamedTextColor.WHITE)));

            Component message = prefix.append(text).append(link);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp() || player.hasPermission("endlock.admin")) {
                    player.sendMessage(message);
                }
            }
        });
    }

    private String extractJsonValue(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start == -1) {
            return null;
        }
        start += needle.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? null : json.substring(start, end);
    }

    private boolean isNewerVersion(String newVersion, String currentVersion) {
        if (newVersion == null || currentVersion == null) {
            return false;
        }
        try {
            newVersion = newVersion.replaceFirst("^v", "");
            currentVersion = currentVersion.replaceFirst("^v", "");
            String[] newParts = newVersion.split("\\.");
            String[] currentParts = currentVersion.split("\\.");
            for (int i = 0; i < Math.max(newParts.length, currentParts.length); i++) {
                int newNum = i < newParts.length ? Integer.parseInt(newParts[i].replaceAll("[^0-9].*", "")) : 0;
                int currentNum = i < currentParts.length ? Integer.parseInt(currentParts[i].replaceAll("[^0-9].*", "")) : 0;
                if (newNum > currentNum) {
                    return true;
                }
                if (newNum < currentNum) {
                    return false;
                }
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
