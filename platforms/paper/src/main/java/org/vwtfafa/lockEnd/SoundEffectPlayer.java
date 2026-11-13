package org.vwtfafa.lockEnd;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Plays sound effects when players attempt to access the locked End.
 */
public class SoundEffectPlayer {
    private static final Key DEFAULT_SOUND = Key.key(Key.MINECRAFT_NAMESPACE, "block.anvil.land");

    private final LockEnd plugin;
    private volatile Key denialSound = DEFAULT_SOUND;
    private volatile float volume;
    private volatile float pitch;
    private volatile boolean enabled;
    private boolean warnedInvalidSound;

    public SoundEffectPlayer(LockEnd plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Loads sound configuration from config.
     */
    public void loadConfig() {
        var config = plugin.getConfig();
        this.enabled = config.getBoolean("sound-effects.enabled", true);
        this.denialSound = parseSoundKey(config.getString(
                "sound-effects.sound", "minecraft:block.anvil.land"));
        volume = (float) Math.min(2.0f, Math.max(0.0f, config.getDouble("sound-effects.volume", 1.0)));
        pitch = (float) Math.min(2.0f, Math.max(0.0f, config.getDouble("sound-effects.pitch", 1.0)));
    }

    /**
     * Resolves the configured sound name into a namespaced key. Enum style
     * constants (BLOCK_ANVIL_LAND, BLOCK_NOTE_BLOCK_PLING, MUSIC_DISC_CAT)
     * are translated to vanilla key paths and validated through Paper's
     * sound registry when a server is present. Raw key paths and namespaced
     * custom sounds are kept as-is. Invalid values return null.
     */
    static Key resolveSoundKey(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_SOUND;
        }
        String trimmed = configured.trim();
        // Enum-style names are ALL_CAPS with underscores (BLOCK_ANVIL_LAND).
        // Lowercase raw paths (custom_sound) must keep underscores.
        if (!trimmed.contains(":") && trimmed.equals(trimmed.toUpperCase(Locale.ROOT))) {
            Key enumKey = resolveEnumStyleKey(trimmed);
            if (enumKey != null) {
                return enumKey;
            }
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            String path = trimmed.equals(trimmed.toUpperCase(Locale.ROOT))
                    ? enumNameToKeyPath(trimmed)
                    : normalized;
            normalized = Key.MINECRAFT_NAMESPACE + ":" + path;
        }
        try {
            return Key.key(normalized);
        } catch (InvalidKeyException exception) {
            return null;
        }
    }

    /**
     * Resolves a Bukkit enum-style name via the sound registry when
     * available, otherwise via the deterministic {@link #enumNameToKeyPath}
     * translation used by unit tests without a live server.
     */
    private static Key resolveEnumStyleKey(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        String path = enumNameToKeyPath(upper);
        try {
            NamespacedKey namespacedKey = NamespacedKey.minecraft(path);
            org.bukkit.Sound registered = Registry.SOUNDS.get(namespacedKey);
            if (registered != null) {
                return Key.key(namespacedKey.getNamespace(), namespacedKey.getKey());
            }
        } catch (LinkageError | RuntimeException ignored) {
            // No server registry (unit tests) or unknown sound.
        }
        try {
            return Key.key(Key.MINECRAFT_NAMESPACE, path);
        } catch (InvalidKeyException exception) {
            return null;
        }
    }

    /**
     * Fallback translation for enum-style names. Most vanilla keys use dots,
     * but note blocks, music discs and a few entity names keep underscores.
     */
    static String enumNameToKeyPath(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("music_disc_")) {
            return "music_disc." + lower.substring("music_disc_".length());
        }
        if (lower.startsWith("block_note_block_")) {
            return "block.note_block." + lower.substring("block_note_block_".length());
        }
        return lower.replace('_', '.');
    }

    private Key parseSoundKey(String configured) {
        Key resolved = resolveSoundKey(configured);
        if (resolved != null) {
            return resolved;
        }
        if (!warnedInvalidSound) {
            warnedInvalidSound = true;
            plugin.getLogger().warning("Invalid sound-effects.sound value '" + configured
                    + "', using block.anvil.land instead.");
        }
        return DEFAULT_SOUND;
    }

    /**
     * Plays the denial sound for a player.
     * @param player The player who attempted access
     */
    public void playDenialSound(Player player) {
        if (!enabled) {
            return;
        }
        player.playSound(Sound.sound(denialSound, Sound.Source.MASTER, volume, pitch));
    }
}
