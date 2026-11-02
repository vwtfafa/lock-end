package org.vwtfafa.lockend.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.vwtfafa.lockend.api.LockState;
import org.vwtfafa.lockend.api.LockStateSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Fabric lock state persistence contract: missing or corrupt
 * data never boots the End locked, and state round-trips survive process
 * boundaries (file-based).
 */
class JsonLockStateRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadWithoutStateFileIsUnlocked() {
        JsonLockStateRepository repository = new JsonLockStateRepository(tempDir);

        assertEquals(LockState.UNLOCKED, repository.load().state());
    }

    @Test
    void saveAndLoadRoundtripAcrossRepositoryInstances() throws IOException {
        // Second instance models a server restart: only the file persists.
        JsonLockStateRepository first = new JsonLockStateRepository(tempDir);
        JsonLockStateRepository second = new JsonLockStateRepository(tempDir);

        first.save(new LockStateSnapshot(LockState.LOCKED, "ignored", 0L));
        assertEquals(LockState.LOCKED, second.load().state());

        first.save(new LockStateSnapshot(LockState.UNLOCKED, "ignored", 0L));
        assertEquals(LockState.UNLOCKED, second.load().state());
    }

    @Test
    void persistedFileContainsTheMasterRecord() throws IOException {
        JsonLockStateRepository repository = new JsonLockStateRepository(tempDir);
        repository.save(new LockStateSnapshot(LockState.LOCKED, "ignored", 0L));

        String json = Files.readString(tempDir.resolve("lock-end.state.json"));
        assertTrue(json.contains("\"locked\": true"));
    }

    @Test
    void corruptStateFileNeverLocksTheEnd() throws IOException {
        Files.writeString(tempDir.resolve("lock-end.state.json"), "{ not json at all ]]");

        JsonLockStateRepository repository = new JsonLockStateRepository(tempDir);

        assertEquals(LockState.UNLOCKED, repository.load().state());
        assertNotNull(repository.load());
    }
}
