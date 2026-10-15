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
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class HistoryStore {
    private static final int SCHEMA_VERSION = 2;

    private final Path historyFile;
    private final Logger logger;
    private boolean writable = true;
    private boolean needsRewrite;
    private boolean lastWriteFailed;

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
                    entries.add(readEntry(entry, schemaVersion));
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

    String status() {
        if (!writable) {
            return "READ_ONLY";
        }
        return lastWriteFailed ? "WRITE_FAILED" : "AVAILABLE";
    }

    boolean save(List<HistoryEntry> entries) {
        if (!writable) {
            return false;
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("schema-version", SCHEMA_VERSION);
        List<Map<String, Object>> rawEntries = new ArrayList<>();
        for (HistoryEntry entry : entries) {
            Map<String, Object> rawEntry = new java.util.LinkedHashMap<>();
            rawEntry.put("timestamp", entry.timestamp().toString());
            rawEntry.put("actor", entry.actor());
            if (entry.actorUuid() != null) {
                rawEntry.put("actor-uuid", entry.actorUuid().toString());
            }
            rawEntry.put("action", entry.action());
            rawEntry.put("source", entry.source());
            if (entry.subjectUuid() != null) {
                rawEntry.put("subject-uuid", entry.subjectUuid().toString());
            }
            rawEntry.put("result", entry.result());
            if (entry.previousState() != null) {
                rawEntry.put("previous-state", entry.previousState());
            }
            if (entry.newState() != null) {
                rawEntry.put("new-state", entry.newState());
            }
            rawEntries.add(rawEntry);
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
            lastWriteFailed = false;
            return true;
        } catch (IOException exception) {
            lastWriteFailed = true;
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

    private HistoryEntry readEntry(Map<?, ?> entry, int schemaVersion) {
        Object rawTimestamp = entry.get("timestamp");
        Object rawActor = entry.get("actor");
        Object rawAction = entry.get("action");
        Object rawSource = entry.get("source");
        Object rawPreviousState = entry.get("previous-state");
        if (rawTimestamp == null || rawActor == null || rawAction == null || rawSource == null
                || (schemaVersion < 2 && rawPreviousState == null)) {
            throw new IllegalArgumentException("required entry field is missing");
        }

        Boolean previousState = readNullableBoolean(rawPreviousState);
        Boolean newState = readNullableBoolean(entry.get("new-state"));
        String result = entry.get("result") == null ? "LEGACY" : entry.get("result").toString();
        UUID actorUuid = readUuid(entry.get("actor-uuid"));
        UUID subjectUuid = readUuid(entry.get("subject-uuid"));
        Instant timestamp;
        if (schemaVersion == 0) {
            timestamp = LocalDateTime.parse(rawTimestamp.toString()).atZone(ZoneId.systemDefault()).toInstant();
        } else {
            timestamp = Instant.parse(rawTimestamp.toString());
        }

        return new HistoryEntry(timestamp, rawActor.toString(), actorUuid, rawAction.toString(),
                rawSource.toString(), subjectUuid, result, previousState, newState);
    }

    private Boolean readNullableBoolean(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        if (text.equalsIgnoreCase("true")) {
            return true;
        }
        if (text.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("state value is not a boolean");
    }

    private UUID readUuid(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }
}
