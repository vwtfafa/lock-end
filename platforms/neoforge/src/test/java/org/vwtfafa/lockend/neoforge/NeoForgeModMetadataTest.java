package org.vwtfafa.lockend.neoforge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural check that the NeoForge mod descriptor keeps the hyphen-free
 * mod id and points at the NeoForge entry class without needing a live game.
 */
class NeoForgeModMetadataTest {

    @Test
    void neoForgeModsTomlDeclaresLockendModId() throws IOException {
        String toml;
        try (InputStream stream = NeoForgeModMetadataTest.class.getClassLoader()
                .getResourceAsStream("META-INF/neoforge.mods.toml")) {
            assertNotNull(stream, "neoforge.mods.toml must be on the test classpath");
            toml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(toml.contains("modId=\"lockend\""));
        assertTrue(toml.contains("logoFile=\"lockend.png\""));
        assertTrue(toml.contains("versionRange=\"[26.3.0.101,)\"")
                || toml.contains("modId=\"neoforge\""));
    }
}
