package org.vwtfafa.lockEnd.commands;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class HistoryStore {
    private static final int SCHEMA_VERSION = 1;

    private final Path historyFile;
    private final Logger logger;
    private boolean writable = true;
    private boolean needsRewrite;

    HistoryStore(File historyFile, Logger logger) {
        this.historyFile = historyFile.toPath();
        this.logger = logger;
    }

    List<HistoryEntry> load() {
        if (!Files.exists(historyFile)) {
            return new ArrayList<>();
        }

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(historyFile.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            writable = false;
            logger.log(Level.SEVERE, "Could not load history; the existing file will not be overwritten.",
                    exception);
            return new ArrayList<>();
        }

        int schemaVersion = readSchemaVersion(config);
        if (schemaVersion < 0) {
            writable = false;
            logger.severe("History has an invalid schema version; the existing file will not be overwritten.");
            return new ArrayList<>();
        }
        if (schemaVersion > SCHEMA_VERSION) {
            writable = false;
            logger.severe("History schema version " + schemaVersion
                    + " is newer than this EndLock version supports; the file will not be overwritten.");
        }
        needsRewrite = schemaVersion < SCHEMA_VERSION;

        Object rawEntriesValue = config.get("entries");
        if (!(rawEntriesValue instanceof List<?> rawEntries)) {
            writable = false;
            logger.severe("History entries are missing or invalid; the existing file will not be overwritten.");
            return new ArrayList<>();
        }

        List<HistoryEntry> entries = new ArrayList<>();
        for (int index = 0; index < rawEntries.size(); index++) {
            Object rawEntry = rawEntries.get(index);
            try {
                if (rawEntry instanceof Map<?, ?> entry) {
                    entries.add(readEntry(entry, schemaVersion == 0));
                } else if (schemaVersion == 0 && rawEntry != null) {
                    entries.add(new HistoryEntry(Instant.now(), "unknown", rawEntry.toString(), "legacy", false));
                    needsRewrite = true;
                } else {
                    throw new IllegalArgumentException("entry is not a mapping");
                }
            } catch (RuntimeException exception) {
                writable = false;
                logger.log(Level.WARNING, "Could not read history entry " + (index + 1)
                        + "; the history file will not be rewritten.", exception);
            }
        }
        return entries;
    }

    boolean needsRewrite() {
        return needsRewrite;
    }

    boolean isWritable() {
        return writable;
    }

    boolean save(List<HistoryEntry> entries) {
        if (!writable) {
            return false;
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("schema-version", SCHEMA_VERSION);
        List<Map<String, Object>> rawEntries = new ArrayList<>();
        for (HistoryEntry entry : entries) {
            rawEntries.add(Map.of(
                    "timestamp", entry.timestamp().toString(),
                    "actor", entry.actor(),
                    "action", entry.action(),
                    "source", entry.source(),
                    "previous-state", entry.previousState()));
        }
        config.set("entries", rawEntries);

        Path temporaryFile = null;
        try {
            Path parent = historyFile.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, ".history-", ".tmp");
            config.save(temporaryFile.toFile());
            replaceAtomically(temporaryFile, historyFile);
            needsRewrite = false;
            return true;
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Could not save history; the previous history file was preserved.", exception);
            return false;
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException exception) {
                    logger.log(Level.WARNING, "Could not remove temporary history file " + temporaryFile, exception);
                }
            }
        }
    }

    private int readSchemaVersion(YamlConfiguration config) {
        Object value = config.get("schema-version");
        if (value == null) {
            return 0;
        }

        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    static void replaceAtomically(Path temporaryFile, Path targetFile) throws IOException {
        try {
            Files.move(temporaryFile, targetFile, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private HistoryEntry readEntry(Map<?, ?> entry, boolean legacy) {
        Object rawTimestamp = entry.get("timestamp");
        Object rawActor = entry.get("actor");
        Object rawAction = entry.get("action");
        Object rawSource = entry.get("source");
        Object rawPreviousState = entry.get("previous-state");
        if (rawTimestamp == null || rawActor == null || rawAction == null || rawSource == null
                || rawPreviousState == null) {
            throw new IllegalArgumentException("required entry field is missing");
        }

        String previousStateValue = rawPreviousState.toString();
        if (!previousStateValue.equalsIgnoreCase("true") && !previousStateValue.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("previous-state is not a boolean");
        }

        Instant timestamp = legacy
                ? LocalDateTime.parse(rawTimestamp.toString()).atZone(ZoneId.systemDefault()).toInstant()
                : Instant.parse(rawTimestamp.toString());
        return new HistoryEntry(timestamp, rawActor.toString(), rawAction.toString(), rawSource.toString(),
                Boolean.parseBoolean(previousStateValue));
    }
}
