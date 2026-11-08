package org.vwtfafa.lockend.core.sound;

import java.util.Locale;

/**
 * Normalizes configured denial-sound names into {@code namespace:path} keys,
 * mirroring the Paper plugin's plain-path resolution path (its registry-
 * based enum lookup stays Paper-side; the fallback translation for enum-
 * style names is shared here).
 */
public final class SoundKeys {

    /**
     * The fallback denial sound key when a configured value is blank.
     */
    public static final String DEFAULT_SOUND_PATH = "minecraft:block.anvil.land";

    private SoundKeys() {
    }

    /**
     * Normalizes a configured sound value. Accepts bare vanilla paths
     * ({@code block.anvil.land}), full keys with namespace
     * ({@code minecraft:block.anvil.land}) and enum-style names
     * ({@code BLOCK_ANVIL_LAND}). Invalid values return {@code null}; the
     * caller decides whether to fall back to {@link #DEFAULT_SOUND_PATH}
     * (like the Paper plugin, which warns once).
     *
     * @param configured the configured value
     * @return the normalized {@code namespace:path} key, or {@code null}
     */
    public static String normalize(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_SOUND_PATH;
        }
        String trimmed = configured.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            String path = trimmed.equals(trimmed.toUpperCase(Locale.ROOT))
                    ? enumNameToKeyPath(trimmed)
                    : normalized;
            return "minecraft:" + path;
        }
        return normalized;
    }

    /**
     * Fallback translation for enum-style names. Most vanilla keys use
     * dots, but note blocks and music discs keep underscores inside a
     * dotted prefix chain.
     *
     * @param name the enum-style upper-case name
     * @return the normalized key path
     */
    public static String enumNameToKeyPath(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("music_disc_")) {
            return "music_disc." + lower.substring("music_disc_".length());
        }
        if (lower.startsWith("block_note_block_")) {
            return "block.note_block." + lower.substring("block_note_block_".length());
        }
        return lower.replace('_', '.');
    }
}
