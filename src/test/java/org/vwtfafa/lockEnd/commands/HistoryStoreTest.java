package org.vwtfafa.lockEnd.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesLegacyLocalTimestampsToVersionedUtcStorage() throws Exception {
        Path historyFile = temporaryDirectory.resolve("history.yml");
        Files.writeString(historyFile, """
                entries:
                  - timestamp: "2026-10-01T12:34:56"
                    actor: Admin
                    action: LOCK
                    source: COMMAND
                    previous-state: false
                """);

        HistoryStore store = new HistoryStore(historyFile.toFile(), Logger.getLogger("HistoryStoreTest"));
        List<HistoryEntry> entries = store.load();

        assertEquals(1, entries.size());
        assertEquals(LocalDateTime.parse("2026-10-01T12:34:56")
                .atZone(ZoneId.systemDefault()).toInstant(), entries.get(0).timestamp());
        assertTrue(store.needsRewrite());
        assertTrue(store.isWritable());
        assertTrue(store.save(entries));
        assertTrue(Files.readString(historyFile).contains("schema-version: 1"));
        assertTrue(Files.readString(historyFile).contains("2026-10-01T"));

        HistoryStore reloaded = new HistoryStore(historyFile.toFile(), Logger.getLogger("HistoryStoreTest"));
        assertEquals(entries, reloaded.load());
        assertFalse(reloaded.needsRewrite());
    }

    @Test
    void savesAndLoadsUtcInstantsWithoutLosingPrecision() {
        Path historyFile = temporaryDirectory.resolve("history.yml");
        HistoryStore store = new HistoryStore(historyFile.toFile(), Logger.getLogger("HistoryStoreTest"));
        HistoryEntry entry = new HistoryEntry(Instant.parse("2026-10-01T12:34:56.123456789Z"),
                "Admin", "LOCK", "COMMAND", false);

        assertTrue(store.save(List.of(entry)));
        assertEquals(List.of(entry), new HistoryStore(historyFile.toFile(),
                Logger.getLogger("HistoryStoreTest")).load());
    }

    @Test
    void malformedHistoryIsPreservedAndCannotBeOverwritten() throws Exception {
        Path historyFile = temporaryDirectory.resolve("history.yml");
        String malformedHistory = "entries: [unterminated\n";
        Files.writeString(historyFile, malformedHistory);
        HistoryStore store = new HistoryStore(historyFile.toFile(), Logger.getLogger("HistoryStoreTest"));

        assertTrue(store.load().isEmpty());
        assertFalse(store.isWritable());
        assertFalse(store.save(List.of()));
        assertEquals(malformedHistory, Files.readString(historyFile));
    }

    @Test
    void invalidEntriesStructureIsPreservedAndCannotBeOverwritten() throws Exception {
        Path historyFile = temporaryDirectory.resolve("history.yml");
        String invalidHistory = "schema-version: 1\nentries: not-a-list\n";
        Files.writeString(historyFile, invalidHistory);
        HistoryStore store = new HistoryStore(historyFile.toFile(), Logger.getLogger("HistoryStoreTest"));

        assertTrue(store.load().isEmpty());
        assertFalse(store.isWritable());
        assertFalse(store.save(List.of()));
        assertEquals(invalidHistory, Files.readString(historyFile));
    }

    @Test
    void newerSchemaIsReadOnly() throws Exception {
        Path historyFile = temporaryDirectory.resolve("history.yml");
        HistoryStore store = new HistoryStore(historyFile.toFile(), Logger.getLogger("HistoryStoreTest"));
        HistoryEntry entry = new HistoryEntry(Instant.parse("2026-10-01T12:34:56Z"),
                "Admin", "LOCK", "COMMAND", false);
        assertTrue(store.save(List.of(entry)));

        Files.writeString(historyFile, Files.readString(historyFile).replace("schema-version: 1",
                "schema-version: 2"));
        String futureHistory = Files.readString(historyFile);

        assertEquals(List.of(entry), store.load());
        assertFalse(store.isWritable());
        assertFalse(store.save(List.of()));
        assertEquals(futureHistory, Files.readString(historyFile));
    }
}
