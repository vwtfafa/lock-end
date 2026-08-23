package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads language files and converts raw message strings into Adventure components.
 * Bundled files live under lang/; customized files are read from
 * plugins/EndLock/lang/ and legacy files from the plugin root are migrated there.
 * Parsed templates are cached and reused until the next reload.
 */
public class MessageService {
    private static final String LANG_FOLDER = "lang";
    private static final String FILE_PREFIX = "messages_";

    private final JavaPlugin plugin;
    private FileConfiguration langConfig;
    private MiniMessage miniMessage;
    private boolean miniMessageEnabled;
    private final Map<String, Component> templateCache = new HashMap<>();

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads the configured language file and MiniMessage setting from config.
     */
    public void loadFromConfig() {
        String langCode = plugin.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        migrateLegacyLanguageFile(langCode);
        loadLanguage(langCode);
        miniMessageEnabled = plugin.getConfig().getBoolean("hooks.mini-message", true);
        miniMessage = miniMessageEnabled ? MiniMessage.miniMessage() : null;
        templateCache.clear();
    }

    /**
     * Moves a language file left in the plugin root by pre-lang-folder
     * versions into the lang folder so customizations survive the update.
     */
    private void migrateLegacyLanguageFile(String code) {
        String fileName = FILE_PREFIX + code + ".yml";
        File legacyFile = new File(plugin.getDataFolder(), fileName);
        if (!legacyFile.isFile()) {
            return;
        }
        File targetDir = new File(plugin.getDataFolder(), LANG_FOLDER);
        File targetFile = new File(targetDir, fileName);
        if (targetFile.exists()) {
            plugin.getLogger().info("Ignoring legacy " + fileName + ": " + LANG_FOLDER + "/" + fileName + " already exists.");
            return;
        }
        try {
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                plugin.getLogger().warning("Could not create language directory: " + targetDir);
                return;
            }
            Files.move(legacyFile.toPath(), targetFile.toPath());
            plugin.getLogger().info("Moved legacy " + fileName + " into the " + LANG_FOLDER + "/ folder.");
        } catch (IOException exception) {
            // Keep operating with the legacy location via the load fallback.
            plugin.getLogger().warning("Could not move legacy " + fileName + " into " + LANG_FOLDER + "/: "
                    + exception.getMessage());
        }
    }

    /**
     * Resolution order: lang folder on disk, legacy plugin root on disk,
     * bundled resource of the language, bundled English fallback.
     * Disk and language files get the bundled English file as defaults so
     * partial or outdated customizations never blank out message keys.
     */
    private void loadLanguage(String code) {
        String fileName = FILE_PREFIX + code + ".yml";

        File langFile = new File(new File(plugin.getDataFolder(), LANG_FOLDER), fileName);
        if (!langFile.isFile()) {
            langFile = new File(plugin.getDataFolder(), fileName);
        }
        if (langFile.isFile()) {
            langConfig = YamlConfiguration.loadConfiguration(langFile);
            FileConfiguration defaults = bundledDefaults();
            if (defaults != null) {
                langConfig.setDefaults(defaults);
            }
            return;
        }

        try (InputStream in = plugin.getResource(LANG_FOLDER + "/" + fileName)) {
            if (in != null) {
                langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                FileConfiguration defaults = bundledDefaults();
                if (defaults != null) {
                    langConfig.setDefaults(defaults);
                }
                return;
            }
        } catch (Exception ignored) {}
        try (InputStream in = plugin.getResource(LANG_FOLDER + "/" + FILE_PREFIX + "en.yml")) {
            if (in != null) {
                langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Loads the bundled English file as fallback defaults; null if unavailable.
     */
    private FileConfiguration bundledDefaults() {
        String path = LANG_FOLDER + "/" + FILE_PREFIX + "en.yml";
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        }
    }

    public @NotNull String msg(@NotNull String key) {
        if (langConfig == null) {
            return key;
        }
        String value = langConfig.getString(key, key);
        return value != null ? value : key;
    }

    public boolean hasMessage(String key) {
        // Consults defaults as well, matching the lookup in msg().
        return langConfig != null && langConfig.getString(key, null) != null;
    }

    public Component miniMsg(String key) {
        return messageComponent(msg(key));
    }

    public Component messageComponent(String raw) {
        if (miniMessageEnabled && miniMessage != null) {
            return miniMessage.deserialize(raw);
        }
        return LegacyComponentSerializer.legacySection().deserialize(raw);
    }

    /**
     * Renders a cached, parsed template and inserts %placeholder% values as
     * literal text. Because values are never re-parsed, MiniMessage tags in
     * user-provided input cannot inject formatting.
     *
     * @param key          language key of the template
     * @param placeholders placeholder tokens (including %) mapped to values
     * @return the rendered component; safe to reuse across players
     */
    public Component message(@NotNull String key, @NotNull Map<String, String> placeholders) {
        Component template = templateCache.computeIfAbsent(key, this::miniMsg);
        return applyPlaceholders(template, placeholders);
    }

    /**
     * Inserts %token% placeholder values into an already parsed component.
     */
    static Component applyPlaceholders(Component template, Map<String, String> placeholders) {
        Component result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue();
            result = result.replaceText(TextReplacementConfig.builder()
                    .matchLiteral(entry.getKey())
                    .replacement(value != null ? value : "")
                    .build());
        }
        return result;
    }

    /**
     * Escapes MiniMessage tags in user-provided input so it renders literally
     * instead of being interpreted as formatting when embedded in a message.
     */
    public String sanitize(String input) {
        if (input == null || !miniMessageEnabled || miniMessage == null) {
            return input;
        }
        return miniMessage.escapeTags(input);
    }

    /**
     * Broadcasts a lock state change to all relevant online players.
     * @param locked The new lock state (selects the action bar variant)
     * @param broadcastKey Language key of the chat broadcast
     * @param playerName Name of the acting player (%player% placeholder)
     */
    public void broadcastLockState(boolean locked, String broadcastKey, String playerName) {
        if (!plugin.getConfig().getBoolean("broadcast.enabled", true)) {
            return;
        }

        boolean notifyAll = plugin.getConfig().getBoolean("broadcast.notify-all", true);
        boolean useActionbar = plugin.getConfig().getBoolean("broadcast.use-actionbar", true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null) {
                continue;
            }
            if (notifyAll || player.isOp() || player.hasPermission("endlock.admin")) {
                if (useActionbar) {
                    player.sendActionBar(miniMsg(locked ? "actionbar-locked" : "actionbar-unlocked"));
                } else {
                    String rawMessage = msg(broadcastKey).replace("%player%", sanitize(playerName));
                    player.sendMessage(messageComponent(rawMessage));
                }
            }
        }
    }
}
