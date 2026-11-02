package org.vwtfafa.lockend.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads and writes the Fabric mod's settings file
 * ({@code config/lock-end.json}) holding the human-editable settings. The
 * persisted lock state lives in a separate file owned by
 * {@link JsonLockStateRepository}; the settings file is write-through on
 * every boot and must not be edited at runtime.
 */
final class FabricEndLockConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DEFAULT_REASON = "Maintenance";
    private static final String CONFIG_FILE_NAME = "lock-end.json";

    private final Path file;
    private final String lockReason;

    private FabricEndLockConfig(Path file, String lockReason) {
        this.file = file;
        this.lockReason = lockReason;
    }

    /**
     * Loads the settings from the given file, creating a fresh default file
     * when it does not exist. Unparsable files fall back to defaults instead
     * of failing the mod boot; a backup copy of the offending file is kept
     * next to it.
     *
     * @param configDirectory the directory holding the settings file
     * @return the loaded settings; never {@code null}
     */
    static FabricEndLockConfig load(Path configDirectory) {
        Path file = configDirectory.resolve(CONFIG_FILE_NAME);
        if (!Files.exists(file)) {
            FabricEndLockConfig defaults = new FabricEndLockConfig(file, DEFAULT_REASON);
            defaults.save();
            return defaults;
        }
        try {
            String json = Files.readString(file);
            StoredConfig stored = GSON.fromJson(json, StoredConfig.class);
            if (stored == null) {
                stored = new StoredConfig();
            }
            return new FabricEndLockConfig(file, stored.reasonOrDefault());
        } catch (IOException | JsonParseException exception) {
            backupCorruptFile(file, exception);
            FabricEndLockConfig defaults = new FabricEndLockConfig(file, DEFAULT_REASON);
            defaults.save();
            return defaults;
        }
    }

    /**
     * @return the configured lock reason; never {@code null}
     */
    String lockReason() {
        return lockReason;
    }

    /**
     * Writes the current settings back to disk.
     */
    void save() {
        StoredConfig stored = new StoredConfig();
        stored.lockReason = lockReason;
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(stored));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write the Lock-End settings file", exception);
        }
    }

    private static void backupCorruptFile(Path file, Exception cause) {
        try {
            Path backup = file.resolveSibling(CONFIG_FILE_NAME + ".corrupt");
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveFailure) {
            addSuppressedQuietly(moveFailure, cause);
        }
    }

    private static void addSuppressedQuietly(Exception suppressed, Exception primary) {
        if (primary != suppressed) {
            primary.addSuppressed(suppressed);
        }
    }

    /**
     * JSON wire format of the settings file.
     */
    private static final class StoredConfig {
        String lockReason;

        String reasonOrDefault() {
            if (lockReason == null || lockReason.isBlank()) {
                return DEFAULT_REASON;
            }
            return lockReason;
        }
    }
}
