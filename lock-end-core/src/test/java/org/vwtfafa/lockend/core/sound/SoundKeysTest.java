package org.vwtfafa.lockend.core.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the shared sound value normalization used before registry lookup.
 */
class SoundKeysTest {

    @Test
    void blankResolvesToTheDefaultSound() {
        assertEquals("minecraft:block.anvil.land", SoundKeys.normalize(null));
        assertEquals("minecraft:block.anvil.land", SoundKeys.normalize("   "));
    }

    @Test
    void barePathsAreNamespaced() {
        assertEquals("minecraft:block.anvil.land", SoundKeys.normalize("block.anvil.land"));
    }

    @Test
    void namespacedValuesPassThroughLowercased() {
        assertEquals("custom:my.sound", SoundKeys.normalize("Custom:My.Sound"));
    }

    @Test
    void enumStyleUpperNamesTranslateToKeyPaths() {
        assertEquals("minecraft:block.anvil.land", SoundKeys.normalize("BLOCK_ANVIL_LAND"));
        assertEquals("minecraft:block.note_block.pling", SoundKeys.normalize("BLOCK_NOTE_BLOCK_PLING"));
        assertEquals("minecraft:music_disc.cat", SoundKeys.normalize("MUSIC_DISC_CAT"));
    }

    @Test
    void bareLowercaseNamesPassThroughNamespaced() {
        assertEquals("minecraft:entity.ghast.shoot", SoundKeys.normalize("entity.ghast.shoot"));
    }
}
