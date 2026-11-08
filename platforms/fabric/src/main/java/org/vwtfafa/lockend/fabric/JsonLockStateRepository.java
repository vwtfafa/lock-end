package org.vwtfafa.lockend.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.vwtfafa.lockend.api.LockState;
import org.vwtfafa.lockend.api.LockStateSnapshot;
import org.vwtfafa.lockend.core.LockStateRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Fabric implementation of the shared {@link LockStateRepository} contract,
 * persisting the lock state as a small JSON document in the mod's config
 * directory ({@code config/lock-end.state.json}).
 *
 * <p>The master record is the {@code locked} boolean; the snapshot's reason
 * and capture time are transport information and are ignored by this
 * implementation. Missing or unparsable data never boots the End locked
 * (the shared contract). I/O happens in the tick context of the caller -
 * lock state changes are rare, so a synchronous small file write is
 * acceptable.</p>
 */
final class JsonLockStateRepository implements LockStateRepository {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String STATE_FILE_NAME = "lock-end.state.json";

    private final Path baseConfigDirectory;

    /**
     * Creates the repository around the mod's config directory; the state
     * file name is appended on every access so the path stays resolved
     * against the live directory.
     *
     * @param baseConfigDirectory the directory holding the state file
     */
    JsonLockStateRepository(Path baseConfigDirectory) {
        this.baseConfigDirectory = Objects.requireNonNull(baseConfigDirectory, "baseConfigDirectory");
    }

    @Override
    public void save(LockStateSnapshot state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        StoredState stored = new StoredState();
        stored.locked = state.isLocked();
        try {
            Files.writeString(baseConfigDirectory.resolve(STATE_FILE_NAME), GSON.toJson(stored));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to persist the Lock-End lock state", exception);
        }
    }

    @Override
    public LockStateSnapshot load() {
        Path file = baseConfigDirectory.resolve(STATE_FILE_NAME);
        if (!Files.exists(file)) {
            return new LockStateSnapshot(LockState.UNLOCKED, "", System.currentTimeMillis());
        }
        try {
            String json = Files.readString(file);
            StoredState stored = GSON.fromJson(json, StoredState.class);
            if (stored == null) {
                return new LockStateSnapshot(LockState.UNLOCKED, "", System.currentTimeMillis());
            }
            return new LockStateSnapshot(LockState.fromLocked(stored.locked), "", System.currentTimeMillis());
        } catch (IOException | JsonParseException exception) {
            // Corrupt state files must not lock the End on boot.
            return new LockStateSnapshot(LockState.UNLOCKED, "", System.currentTimeMillis());
        }
    }

    /**
     * JSON wire format of the persisted state.
     */
    private static final class StoredState {
        boolean locked;
    }
}
