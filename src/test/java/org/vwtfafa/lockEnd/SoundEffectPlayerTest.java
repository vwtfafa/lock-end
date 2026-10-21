package org.vwtfafa.lockEnd;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests enum-name and raw-key sound resolution.
 */
class SoundEffectPlayerTest {

    @Test
    void resolvesBukkitEnumNamesWithTheirRealKeys() {
        assertEquals(Key.key("minecraft:block.anvil.land"),
                SoundEffectPlayer.resolveSoundKey("BLOCK_ANVIL_LAND"));
        assertEquals(Key.key("minecraft:block.note_block.pling"),
                SoundEffectPlayer.resolveSoundKey("BLOCK_NOTE_BLOCK_PLING"));
        assertEquals(Key.key("minecraft:music_disc.cat"),
                SoundEffectPlayer.resolveSoundKey("MUSIC_DISC_CAT"));
    }

    @Test
    void keepsRawKeyPathsAndNamespaces() {
        assertEquals(Key.key("minecraft:block.note_block.pling"),
                SoundEffectPlayer.resolveSoundKey("block.note_block.pling"));
        assertEquals(Key.key("minecraft:custom_sound"),
                SoundEffectPlayer.resolveSoundKey("custom_sound"));
        assertEquals(Key.key("mymod:epic_sound_blast"),
                SoundEffectPlayer.resolveSoundKey("mymod:epic_sound_blast"));
    }

    @Test
    void invalidKeysReturnNull() {
        assertNull(SoundEffectPlayer.resolveSoundKey("bad key with spaces"));
    }

    @Test
    void blankUsesTheDefault() {
        assertEquals(Key.key("minecraft:block.anvil.land"), SoundEffectPlayer.resolveSoundKey(" "));
    }
}
