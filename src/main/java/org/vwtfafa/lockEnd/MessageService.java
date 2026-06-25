package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

public final class MessageService {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final LockEnd plugin;
    private FileConfiguration langConfig;
    private final boolean useMiniMessage;

    public MessageService(LockEnd plugin) {
        this.plugin = plugin;
        this.useMiniMessage = plugin.getConfig().getBoolean("messages.use-minimessage", true);
        reload();
    }

    public void reload() {
        String code = plugin.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        String fileName = "messages_" + code + ".yml";
        File langFile = new File(plugin.getDataFolder(), fileName);
        if (langFile.exists()) {
            langConfig = YamlConfiguration.loadConfiguration(langFile);
            return;
        }
        try (InputStream in = plugin.getResource(fileName)) {
            if (in != null) {
                langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in));
                return;
            }
        } catch (Exception ignored) {
        }
        try (InputStream in = plugin.getResource("messages_en.yml")) {
            if (in != null) {
                langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in));
            }
        } catch (Exception ignored) {
        }
    }

    public String raw(String key) {
        if (langConfig == null) {
            return key;
        }
        return langConfig.getString(key, key);
    }

    public String format(String key, String... replacements) {
        String message = raw(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }

    public Component component(String key, String... replacements) {
        return parse(format(key, replacements));
    }

    public Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        if (useMiniMessage && message.contains("<")) {
            try {
                return MINI.deserialize(message);
            } catch (Exception ignored) {
            }
        }
        return LEGACY.deserialize(message);
    }

    public void send(org.bukkit.command.CommandSender sender, String key, String... replacements) {
        sender.sendMessage(component(key, replacements));
    }
}
