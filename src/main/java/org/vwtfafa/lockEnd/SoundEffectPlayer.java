package org.vwtfafa.lockEnd;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Plays sound effects when players attempt to access the locked End.
 */
public class SoundEffectPlayer {
    private static final Key DEFAULT_SOUND = Key.key(Key.MINECRAFT_NAMESPACE, "block.anvil.land");

    private final LockEnd plugin;
    private Key denialSound = DEFAULT_SOUND;
    private float volume;
    private float pitch;
    private boolean enabled;
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
        this.denialSound = parseSoundKey(config.getString("sound-effects.sound", "BLOCK_ANVIL_LAND"));
        volume = (float) Math.min(2.0f, Math.max(0.0f, config.getDouble("sound-effects.volume", 1.0)));
        pitch = (float) Math.min(2.0f, Math.max(0.0f, config.getDouble("sound-effects.pitch", 1.0)));
    }

    /**
     * Resolves the configured sound name into a namespaced key. Both enum
     * style constants (BLOCK_ANVIL_LAND) and full keys (minecraft:block.anvil.land)
     * are accepted; invalid values fall back to the default with a warning.
     */
    private Key parseSoundKey(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_SOUND;
        }
        String normalized = configured.trim().toLowerCase(Locale.ROOT).replace('_', '.');
        if (!normalized.contains(":")) {
            normalized = Key.MINECRAFT_NAMESPACE + ":" + normalized;
        }
        try {
            return Key.key(normalized);
        } catch (InvalidKeyException exception) {
            if (!warnedInvalidSound) {
                warnedInvalidSound = true;
                plugin.getLogger().warning("Invalid sound-effects.sound value '" + configured
                        + "', using block.anvil.land instead.");
            }
            return DEFAULT_SOUND;
        }
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
