package org.vwtfafa.lockEnd;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Plays sound effects when players attempt to access the locked End.
 */
public class SoundEffectPlayer {
    private final LockEnd plugin;
    private String soundName;
    private float volume;
    private float pitch;

    public SoundEffectPlayer(LockEnd plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Loads sound configuration from config.
     */
    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        soundName = config.getString("sound-effects.sound", "BLOCK_ANVIL_LAND");
        volume = (float) Math.min(2.0f, Math.max(0.0f, config.getDouble("sound-effects.volume", 1.0)));
        pitch = (float) Math.min(2.0f, Math.max(0.0f, config.getDouble("sound-effects.pitch", 1.0)));
    }

    /**
     * Plays the denial sound for a player.
     * @param player The player who attempted access
     */
    public void playDenialSound(Player player) {
        if (!plugin.getConfig().getBoolean("sound-effects.enabled", false)) {
            return;
        }
        try {
            Sound sound;
            try {
                sound = Sound.valueOf(soundName);
            } catch (IllegalArgumentException e) {
                sound = Sound.BLOCK_ANVIL_LAND;
            }
            Location location = player.getLocation();
            player.playSound(location, sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound name in config: " + soundName);
        }
    }
}