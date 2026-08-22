package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Loads language files and converts raw message strings into Adventure components.
 */
public class MessageService {
    private final JavaPlugin plugin;
    private FileConfiguration langConfig;
    private MiniMessage miniMessage;
    private boolean miniMessageEnabled;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads the configured language file and MiniMessage setting from config.
     */
    public void loadFromConfig() {
        String langCode = plugin.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        loadLanguage(langCode);
        miniMessageEnabled = plugin.getConfig().getBoolean("hooks.mini-message", true);
        miniMessage = miniMessageEnabled ? MiniMessage.miniMessage() : null;
    }

    private void loadLanguage(String code) {
        String fileName = "messages_" + code + ".yml";
        File langFile = new File(plugin.getDataFolder(), fileName);
        if (!langFile.exists()) {
            try (InputStream in = plugin.getResource(fileName)) {
                if (in != null) {
                    langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                    return;
                }
            } catch (Exception ignored) {}
            try (InputStream in = plugin.getResource("messages_de.yml")) {
                if (in != null) {
                    langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
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

    public boolean hasMessage(String key) {
        return langConfig != null && langConfig.isString(key);
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
        String rawMessage = msg(broadcastKey).replace("%player%", playerName);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (notifyAll || player.isOp() || player.hasPermission("endlock.admin")) {
                if (useActionbar) {
                    player.sendActionBar(miniMsg(locked ? "actionbar-locked" : "actionbar-unlocked"));
                } else {
                    player.sendMessage(messageComponent(rawMessage));
                }
            }
        }
    }
}
