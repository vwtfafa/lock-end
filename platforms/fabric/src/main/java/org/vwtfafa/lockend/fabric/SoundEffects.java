package org.vwtfafa.lockend.fabric;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.vwtfafa.lockend.core.config.ModEndLockConfig;
import org.vwtfafa.lockend.core.sound.SoundKeys;

/**
 * Denial sound for players entering the locked End, mirroring the Paper
 * plugin's {@code SoundEffectPlayer}: the configured value (enum style,
 * bare path or namespaced key) is normalized through the shared
 * {@link SoundKeys} helper and validated against the server's sound
 * registry; an invalid value falls back to the anvil-land default with a
 * one-time warning.
 */
final class SoundEffects {

    private Identifier denialSound;
    private float volume = 1.0f;
    private float pitch = 1.0f;
    private boolean enabled;
    private boolean warnedInvalidSound;

    SoundEffects() {
        denialSound = Identifier.tryParse(SoundKeys.DEFAULT_SOUND_PATH);
    }

    /**
     * Resolves the configured sound against the registry (called on boot and
     * on {@code /endlock reload}).
     *
     * @param config the freshly loaded settings
     */
    void loadFrom(ModEndLockConfig config) {
        enabled = config.soundEffectsEnabled();
        volume = config.soundEffectVolume();
        pitch = config.soundEffectPitch();
        denialSound = resolve(config.soundEffectSound());
    }

    /**
     * Plays the denial sound for one player when enabled.
     *
     * @param player the player attempting access
     */
    void playDenial(net.minecraft.server.level.ServerPlayer player) {
        if (!enabled) {
            return;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(denialSound);
        if (sound == null) {
            LockEndFabricMod.LOGGER.warn("Denial sound build could not be resolved; skipping.");
            return;
        }
        player.playSound(sound, volume, pitch);
    }

    private Identifier resolve(String configured) {
        String normalized = SoundKeys.normalize(configured);
        Identifier id = Identifier.tryParse(normalized);
        if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            if (!warnedInvalidSound) {
                warnedInvalidSound = true;
                LockEndFabricMod.LOGGER.warn("Invalid sound-effects.sound value '{}',"
                        + " using block.anvil.land instead.", configured);
            }
            return Identifier.tryParse(SoundKeys.DEFAULT_SOUND_PATH);
        }
        return id;
    }
}
