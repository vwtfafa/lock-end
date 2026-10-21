package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class UpdateChecker implements AutoCloseable {
    private static final URI LATEST_RELEASE_URI =
            URI.create("https://api.github.com/repos/vwtfafa/lock-end/releases/latest");
    private static final String RELEASE_URL = "https://github.com/vwtfafa/lock-end/releases";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final LockEnd plugin;
    private final String currentVersion;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    private final AtomicBoolean checking = new AtomicBoolean();
    private volatile String latestVersion;
    private volatile boolean updateAvailable;

    public UpdateChecker(LockEnd plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

    /**
     * Fetches the latest version from GitHub asynchronously and notifies admins
     */
    public void checkForUpdates() {
        if (!checking.compareAndSet(false, true)) {
            return;
        }
        boolean notifyOps = plugin.getConfig().getBoolean("update-checker.notify-ops", true);
        boolean notifyChat = plugin.getConfig().getBoolean("update-checker.notify-chat", true);
        try {
            plugin.getPaperScheduler().runAsync(() -> {
                try {
                    // Fetch the latest release from the GitHub API
                    HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE_URI)
                            .timeout(HTTP_TIMEOUT)
                            .header("User-Agent", "EndLock/" + currentVersion)
                            .header("Accept", "application/vnd.github+json")
                            .GET()
                            .build();
                    HttpResponse<String> response = httpClient.send(request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                    // Parse the version from the JSON response
                    String tagName = parseLatestTag(response.statusCode(), response.body());
                    if (tagName == null) {
                        if (response.statusCode() != 200) {
                            plugin.getLogger().warning("Update check failed: HTTP " + response.statusCode());
                        }
                        return;
                    }

                    latestVersion = tagName;
                    updateAvailable = isNewerVersion(latestVersion, currentVersion);

                    if (updateAvailable && notifyOps) {
                        plugin.getLogger().info("========================================");
                        plugin.getLogger().info("EndLock update available!");
                        plugin.getLogger().info("Current version: " + currentVersion);
                        plugin.getLogger().info("New version: " + latestVersion);
                        plugin.getLogger().info("Release page: " + RELEASE_URL);
                        plugin.getLogger().info("========================================");
                    }

                    // Notify online operators via chat; the server may shut down
                    // before the follow-up task can be scheduled.
                    if (updateAvailable && notifyChat) {
                        try {
                            notifyOnlineAdmins();
                        } catch (IllegalPluginAccessException exception) {
                            plugin.getLogger().fine("Skipped update chat notification: plugin is shutting down.");
                        }
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    plugin.getLogger().warning("Update check failed: " + e.getMessage());
                } finally {
                    checking.set(false);
                }
            });
        } catch (RuntimeException exception) {
            checking.set(false);
            plugin.getLogger().fine("Skipped update check: plugin is shutting down.");
        }
    }

    /**
     * Sends a chat notification with the release link to online operators
     */
    private void notifyOnlineAdmins() {
        try {
            plugin.getPaperScheduler().executeGlobal(() -> {
                Component message = plugin.message("update-available", Map.of("%version%", latestVersion))
                        .clickEvent(ClickEvent.openUrl(RELEASE_URL))
                        .hoverEvent(HoverEvent.showText(plugin.message("update-download", Map.of())));
                for (Player player : Bukkit.getOnlinePlayers()) {
                    plugin.getPaperScheduler().executeEntity(player, () -> {
                        if (player.hasPermission("endlock.admin")) {
                            player.sendMessage(message);
                        }
                    }, () -> {});
                }
            });
        } catch (IllegalPluginAccessException exception) {
            plugin.getLogger().fine("Skipped update chat notification: plugin is shutting down.");
        }
    }

    /**
     * Extracts the tag name from a GitHub latest-release response; null when
     * the request did not succeed or the payload carries no tag.
     */
    static String parseLatestTag(int statusCode, String body) {
        return statusCode == 200 ? parseTagName(body) : null;
    }

    /**
     * Extracts the release tag from a GitHub releases API response.
     */
    static String parseTagName(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                return null;
            }
            JsonElement tagName = element.getAsJsonObject().get("tag_name");
            if (tagName == null || !tagName.isJsonPrimitive() || !tagName.getAsJsonPrimitive().isString()) {
                return null;
            }
            return tagName.getAsString();
        } catch (RuntimeException exception) {
            return null;
        }
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

    /**
     * Stops the HTTP client and any in-flight request. Safe to call more than
     * once.
     */
    @Override
    public void close() {
        httpClient.shutdownNow();
    }
}
