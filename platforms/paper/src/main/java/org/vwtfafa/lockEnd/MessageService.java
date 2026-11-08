package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads language files and converts raw message strings into Adventure components.
 * Bundled files live under lang/ and use MiniMessage formatting; customized files
 * are read from plugins/EndLock/lang/ and legacy files from the plugin root are
 * migrated there. Legacy section codes in custom files are still accepted for
 * compatibility. Parsed templates are cached and reused until the next reload.
 */
public class MessageService {
    private static final String LANG_FOLDER = "lang";
    private static final String FILE_PREFIX = "messages_";
    private static final MiniMessage TAG_STRIPPER = MiniMessage.miniMessage();

    private final LockEnd plugin;
    private volatile FileConfiguration langConfig;
    private volatile Map<String, String> messageStrings = Map.of();
    private volatile MiniMessage miniMessage;
    private volatile boolean miniMessageEnabled;
    private final Map<String, Component> templateCache = new ConcurrentHashMap<>();

    public MessageService(LockEnd plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads the configured language file and MiniMessage setting from config.
     */
    public void loadFromConfig() {
        String rawLang = plugin.getConfig().getString("language", "en");
        if (rawLang == null || rawLang.isBlank()) {
            rawLang = "en";
        }
        String langCode = rawLang.toLowerCase(Locale.ROOT);
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
            publishMessageStrings();
            return;
        }

        try (InputStream in = plugin.getResource(LANG_FOLDER + "/" + fileName)) {
            if (in != null) {
                langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                FileConfiguration defaults = bundledDefaults();
                if (defaults != null) {
                    langConfig.setDefaults(defaults);
                }
                publishMessageStrings();
                return;
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Could not load bundled language file " + fileName, exception);
        }
        try (InputStream in = plugin.getResource(LANG_FOLDER + "/" + FILE_PREFIX + "en.yml")) {
            if (in != null) {
                langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                publishMessageStrings();
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Could not load bundled English language file", exception);
        }
    }

    private void publishMessageStrings() {
        Map<String, String> loadedMessages = new HashMap<>();
        if (langConfig != null) {
            Configuration defaults = langConfig.getDefaults();
            if (defaults != null) {
                for (String key : defaults.getKeys(true)) {
                    String value = langConfig.getString(key);
                    if (value != null) {
                        loadedMessages.put(key, value);
                    }
                }
            }
            for (String key : langConfig.getKeys(true)) {
                String value = langConfig.getString(key);
                if (value != null) {
                    loadedMessages.put(key, value);
                }
            }
        }
        messageStrings = Collections.unmodifiableMap(loadedMessages);
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

    public String msg(String key) {
        return messageStrings.getOrDefault(key, key);
    }

    /**
     * Replaces literal tokens in a raw message string. Used for composing
     * multi-part audit lines where every segment must remain translatable.
     *
     * @param key          language key of the template
     * @param placeholders placeholder tokens (including %) mapped to values
     * @return the formatted raw string
     */
    public String msg(String key, Map<String, String> placeholders) {
        return replacePlaceholders(msg(key), placeholders);
    }

    static String replacePlaceholders(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    public boolean hasMessage(String key) {
        return messageStrings.containsKey(key);
    }

    public Component miniMsg(String key) {
        return messageComponent(msg(key));
    }

    public Component messageComponent(String raw) {
        return render(raw, miniMessage, miniMessageEnabled);
    }

    /**
     * Parses a raw message string into a component. Bundled files use
     * MiniMessage; legacy section codes ({@code §}) in custom files are
     * translated to MiniMessage tags for backwards compatibility. When the
     * MiniMessage hook is disabled, tags are stripped so text never leaks raw
     * markup.
     */
    static Component render(String raw, MiniMessage miniMessage, boolean miniMessageEnabled) {
        if (raw == null) {
            return Component.empty();
        }
        String miniMessageInput = legacyToMiniMessage(raw);
        if (!miniMessageEnabled || miniMessage == null) {
            // Remove MiniMessage markup but keep classic color codes working
            // for custom files written for older versions.
            String withoutMiniMessage = stripMiniMessageTags(raw);
            try {
                return TAG_STRIPPER.deserialize(legacyToMiniMessage(withoutMiniMessage));
            } catch (RuntimeException exception) {
                return Component.text(withoutMiniMessage);
            }
        }
        try {
            return miniMessage.deserialize(miniMessageInput);
        } catch (RuntimeException exception) {
            // Never let a malformed custom message break a command.
            return Component.text(stripMiniMessageTags(miniMessageInput));
        }
    }

    /**
     * Translates the classic section-code palette into MiniMessage tags so
     * custom language files written for older versions keep working without a
     * second parser.
     */
    static String legacyToMiniMessage(String raw) {
        StringBuilder result = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (current == '§' && i + 1 < raw.length()) {
                String tag = legacyTag(raw.charAt(i + 1));
                if (tag != null) {
                    result.append(tag);
                    i++;
                    continue;
                }
            }
            result.append(current);
        }
        return result.toString();
    }

    private static String legacyTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    private static String stripMiniMessageTags(String raw) {
        try {
            return TAG_STRIPPER.stripTags(raw);
        } catch (RuntimeException exception) {
            return raw;
        }
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
    public Component message(String key, Map<String, String> placeholders) {
        Component template = templateCache.computeIfAbsent(key, this::miniMsg);
        return applyPlaceholders(template, placeholders);
    }

    /**
     * Renders a cached template with text placeholders and pre-parsed components.
     * Component placeholders are used for trusted, styled values such as the
     * localized open/closed status where MiniMessage styling must be preserved.
     *
     * @param key                   language key of the template
     * @param placeholders          placeholder tokens mapped to literal text
     * @param componentPlaceholders placeholder tokens mapped to parsed components
     * @return the rendered component; safe to reuse across players
     */
    public Component message(String key, Map<String, String> placeholders,
                             Map<String, Component> componentPlaceholders) {
        Component template = templateCache.computeIfAbsent(key, this::miniMsg);
        return applyComponentPlaceholders(applyPlaceholders(template, placeholders), componentPlaceholders);
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
     * Inserts pre-parsed components for trusted placeholders, preserving their
     * styling. Unlike text placeholders, values are components and therefore
     * cannot be influenced by user input.
     */
    static Component applyComponentPlaceholders(Component template, Map<String, Component> placeholders) {
        Component result = template;
        for (Map.Entry<String, Component> entry : placeholders.entrySet()) {
            Component value = entry.getValue();
            result = result.replaceText(TextReplacementConfig.builder()
                    .matchLiteral(entry.getKey())
                    .replacement(value != null ? value : Component.empty())
                    .build());
        }
        return result;
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

        // Parse the template once and reuse the component for every recipient.
        Component component;
        if (useActionbar) {
            component = message(locked ? "actionbar-locked" : "actionbar-unlocked", Map.of());
        } else {
            component = message(broadcastKey, Map.of("%player%", playerName));
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getPaperScheduler().executeEntity(player, () -> {
                if (notifyAll || player.hasPermission("endlock.admin")) {
                    if (useActionbar) {
                        player.sendActionBar(component);
                    } else {
                        player.sendMessage(component);
                    }
                }
            }, () -> {});
        }
    }

}
