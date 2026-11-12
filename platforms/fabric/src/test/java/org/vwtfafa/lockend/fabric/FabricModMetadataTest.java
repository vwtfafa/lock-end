package org.vwtfafa.lockend.fabric;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural check that the Fabric mod descriptor still exposes the
 * expected entrypoint and shared-API identity without needing a Minecraft
 * runtime (guard/command behavior stays covered by dedicated/in-game smoke).
 */
class FabricModMetadataTest {

    @Test
    void fabricModJsonDeclaresLockEndEntrypoint() throws IOException {
        String json;
        try (InputStream stream = FabricModMetadataTest.class.getClassLoader()
                .getResourceAsStream("fabric.mod.json")) {
            assertNotNull(stream, "fabric.mod.json must be on the test classpath");
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(json.contains("\"id\": \"lock-end\"") || json.contains("\"id\":\"lock-end\""));
        assertTrue(json.contains("LockEndFabricMod"));
    }
}
