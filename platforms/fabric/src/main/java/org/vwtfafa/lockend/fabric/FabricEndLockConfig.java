package org.vwtfafa.lockend.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.vwtfafa.lockend.core.LockBypassChecker;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

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
    private static final LockBypassChecker EMPTY_BYPASS_CHECKER =
            new LockBypassChecker(List.of(), List.of(), List.of());

    private final Path file;
    private String lockReason;
    private final LockBypassChecker bypassChecker;

    private FabricEndLockConfig(Path file, String lockReason, LockBypassChecker bypassChecker) {
        this.file = file;
        this.lockReason = lockReason;
        this.bypassChecker = bypassChecker;
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
            FabricEndLockConfig defaults = new FabricEndLockConfig(
                    file, DEFAULT_REASON, EMPTY_BYPASS_CHECKER);
            defaults.save();
            return defaults;
        }
        try {
            String json = Files.readString(file);
            StoredConfig stored = GSON.fromJson(json, StoredConfig.class);
            if (stored == null) {
                stored = new StoredConfig();
            }
            return new FabricEndLockConfig(file, stored.reasonOrDefault(), stored.bypassChecker());
        } catch (IOException | JsonParseException exception) {
            backupCorruptFile(file, exception);
            FabricEndLockConfig defaults = new FabricEndLockConfig(
                    file, DEFAULT_REASON, EMPTY_BYPASS_CHECKER);
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
     * @return the configured bypass (whitelist) rules; never {@code null}
     */
    LockBypassChecker bypassChecker() {
        return bypassChecker;
    }

    /**
     * Updates the lock reason at runtime and writes the settings file, so the
     * change survives a restart. The live reason supplier resolves this field
     * on every call, so status and denial feedback switch immediately.
     *
     * @param reason the new reason; must not be {@code null} or blank
     * @throws IllegalArgumentException if the reason is {@code null} or blank
     */
    void setLockReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        this.lockReason = reason.trim();
        save();
    }

    /**
     * Restores the built-in default lock reason and writes the settings file,
     * matching what a fresh default file contains.
     */
    void clearReason() {
        this.lockReason = DEFAULT_REASON;
        save();
    }

    /**
     * Writes the current settings back to disk.
     */
    void save() {
        StoredConfig stored = new StoredConfig();
        stored.lockReason = lockReason;
        if (bypassChecker != null) {
            stored.playerWhitelist = bypassChecker.playerNames();
            stored.uuidWhitelist = bypassChecker.uuids();
            stored.worldWhitelist = bypassChecker.worlds();
        }
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
        List<String> playerWhitelist;
        List<String> uuidWhitelist;
        List<String> worldWhitelist;

        String reasonOrDefault() {
            if (lockReason == null || lockReason.isBlank()) {
                return DEFAULT_REASON;
            }
            return lockReason;
        }

        LockBypassChecker bypassChecker() {
            return new LockBypassChecker(
                    listOrEmpty(playerWhitelist),
                    listOrEmpty(uuidWhitelist),
                    listOrEmpty(worldWhitelist));
        }

        private static List<String> listOrEmpty(List<String> values) {
            return values == null ? List.of() : values;
        }
    }
}
