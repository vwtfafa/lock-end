package org.vwtfafa.lockend.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.vwtfafa.lockend.core.history.HistoryEntry;
import org.vwtfafa.lockend.core.history.HistoryPersistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fabric persistence of the bounded lock history
 * ({@code config/lock-end.history.json}). The record shape matches the Paper
 * plugin's schema version 2 field for field; a corrupt file is never
 * rewritten (the store switches to read-only, mirroring the Paper store's
 * strict corruption policy).
 */
final class JsonHistoryStore implements HistoryPersistence {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping().create();
    private static final int SCHEMA_VERSION = 2;
    private static final Path FILE = Path.of("lock-end.history.json");

    private final Path file;
    private final Logger logger;
    private boolean writable = true;
    private boolean needsRewrite;
    private boolean lastWriteFailed;

    /**
     * Creates the store around the mod's config directory.
     *
     * @param configDirectory the directory holding the history file
     */
    JsonHistoryStore(Path configDirectory) {
        this(configDirectory, LockEndFabricMod.LOGGER);
    }

    /**
     * Constructor variant with an injectable logger for tests.
     */
    JsonHistoryStore(Path configDirectory, Logger logger) {
        this.file = configDirectory.resolve(FILE);
        this.logger = logger;
    }

    /**
     * Reads the complete history. A missing file yields an empty list;
     * malformed data marks the store read-only so the invalid file is never
     * truncated.
     *
     * @return the entries, oldest first; never {@code null}
     */
    List<HistoryEntry> load() {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        JsonObject root;
        try {
            String json = Files.readString(file);
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception exception) {
            readOnly("Could not read the lock history; the existing file will not be overwritten.",
                    exception);
            return new ArrayList<>();
        }

        if (!root.has("schemaVersion") || root.get("schemaVersion").isJsonNull()) {
            readOnly("Lock history lacks a schema version; the file will not be overwritten.", null);
            return new ArrayList<>();
        }
        int schemaVersion = root.get("schemaVersion").getAsInt();
        if (schemaVersion != SCHEMA_VERSION) {
            if (schemaVersion > SCHEMA_VERSION) {
                readOnly("Lock history schema version " + schemaVersion
                        + " is newer than this EndLock version supports;"
                        + " the file will not be overwritten.", null);
                return new ArrayList<>();
            }
            readOnly("Lock history schema version " + schemaVersion + " is not supported;"
                    + " entries would need a migration that is not offered for mods.", null);
            return new ArrayList<>();
        }

        JsonArray rawEntries;
        if (root.has("entries") && root.get("entries").isJsonArray()) {
            rawEntries = root.getAsJsonArray("entries");
        } else {
            readOnly("Lock history entries are missing or invalid;"
                    + " the existing file will not be overwritten.", null);
            return new ArrayList<>();
        }

        List<HistoryEntry> entries = new ArrayList<>();
        for (int index = 0; index < rawEntries.size(); index++) {
            try {
                entries.add(readEntry(rawEntries.get(index).getAsJsonObject()));
            } catch (RuntimeException exception) {
                readOnly("Could not read lock history entry " + (index + 1)
                        + "; the history file will not be rewritten.", exception);
                return new ArrayList<>();
            }
        }
        return entries;
    }

    /**
     * @return file-level corruption and fallback state, or {@code AVAILABLE}
     */
    String status() {
        if (!writable) {
            return "READ_ONLY";
        }
        return lastWriteFailed ? "WRITE_FAILED" : "AVAILABLE";
    }

    /**
     * @return {@code true} if the file should be rewritten at boot (reserved
     *         for schema migrations; the mods only support schema 2)
     */
    boolean needsRewrite() {
        return needsRewrite;
    }

    @Override
    public boolean save(List<HistoryEntry> entries) {
        if (!writable) {
            return false;
        }
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray rawEntries = new JsonArray();
        for (HistoryEntry entry : entries) {
            JsonObject raw = new JsonObject();
            raw.addProperty("timestamp", entry.timestamp().toString());
            raw.addProperty("actor", entry.actor());
            if (entry.actorUuid() != null) {
                raw.addProperty("actorUuid", entry.actorUuid().toString());
            }
            raw.addProperty("action", entry.action());
            raw.addProperty("source", entry.source());
            if (entry.subjectUuid() != null) {
                raw.addProperty("subjectUuid", entry.subjectUuid().toString());
            }
            raw.addProperty("result", entry.result());
            if (entry.previousState() != null) {
                raw.addProperty("previousState", entry.previousState());
            }
            if (entry.newState() != null) {
                raw.addProperty("newState", entry.newState());
            }
            rawEntries.add(raw);
        }
        root.add("entries", rawEntries);
        boolean written = JsonFileIO.writeAtomically(file, GSON.toJson(root), logger);
        lastWriteFailed = !written;
        if (written) {
            needsRewrite = false;
        }
        return written;
    }

    private HistoryEntry readEntry(JsonObject raw) {
        Instant timestamp = parseInstant(required(raw, "timestamp"));
        String actor = required(raw, "actor");
        String action = required(raw, "action");
        String source = required(raw, "source");
        UUID actorUuid = optionalUuid(raw.get("actorUuid"));
        UUID subjectUuid = optionalUuid(raw.get("subjectUuid"));
        String result = raw.has("result") && !raw.get("result").isJsonNull()
                ? raw.get("result").getAsString() : "RECORDED";
        Boolean previousState = optionalState(raw, "previousState");
        Boolean newState = optionalState(raw, "newState");

        return new HistoryEntry(timestamp, actor, actorUuid, action, source,
                subjectUuid, result, previousState, newState);
    }

    private static String required(JsonObject raw, String field) {
        if (!raw.has(field) || raw.get(field).isJsonNull()) {
            throw new JsonParseException("required history field '" + field + "' is missing");
        }
        return raw.get(field).getAsString();
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new JsonParseException("history timestamp is not an ISO instant: " + value);
        }
    }

    private static UUID optionalUuid(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return UUID.fromString(element.getAsString());
    }

    private static Boolean optionalState(JsonObject raw, String field) {
        if (!raw.has(field) || raw.get(field).isJsonNull()) {
            return null;
        }
        String text = raw.get(field).getAsString();
        if (text.equalsIgnoreCase("true")) {
            return true;
        }
        if (text.equalsIgnoreCase("false")) {
            return false;
        }
        throw new JsonParseException("history state value is not a boolean: " + text);
    }

    private void readOnly(String message, Exception cause) {
        writable = false;
        if (cause == null) {
            logger.warn(message);
        } else {
            logger.warn(message, cause);
        }
    }
}
