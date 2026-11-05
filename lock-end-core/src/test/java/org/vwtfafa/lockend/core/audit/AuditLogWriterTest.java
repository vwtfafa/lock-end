package org.vwtfafa.lockend.core.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the asynchronous writer against a real temp file: queued entries
 * must appear as timestamped lines, shutdown must drain the queue.
 */
class AuditLogWriterTest {

    @TempDir
    Path temp;

    @Test
    void writtenEntriesContainTimestampAndMessage() throws Exception {
        Path log = temp.resolve("audit.log");
        List<String> warnings = new ArrayList<>();
        AuditLogWriter writer = new AuditLogWriter(warnings::add);
        writer.initialize(log);

        writer.log("LOCK - Player: Alice - Status: LOCKED");
        writer.shutdown(); // waits for the queue to drain

        List<String> lines = Files.readAllLines(log);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).endsWith("] LOCK - Player: Alice - Status: LOCKED"));
    }

    @Test
    void uninitializedWriterDropsMessagesInsteadOfFailing() {
        AuditLogWriter writer = new AuditLogWriter(message -> {
            throw new AssertionError("nothing should be reported");
        });

        writer.log("dropped");

        writer.shutdown();
        assertTrue(true, "no exception reached the caller");
    }

    @Test
    void nullFileInitializationSkipsTheWriterLoop() throws Exception {
        AuditLogWriter writer = new AuditLogWriter(message -> {
            throw new AssertionError("nothing should be reported");
        });
        writer.initialize(null);
        writer.log("dropped");

        writer.shutdown();
        assertTrue(true, "no exception reached the caller");
    }

    @Test
    void writeFailureIsReportedToTheSinkNotThrown() {
        List<String> warnings = new ArrayList<>();
        AuditLogWriter writer = new AuditLogWriter(warnings::add);
        writer.initialize(temp); // a directory: writing must fail

        writer.log("line");
        writer.shutdown();

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).startsWith("Error writing to the audit log file"));
    }

    @Test
    void missingDirectoriesAreCreated() throws Exception {
        Path log = temp.resolve("deep").resolve("audit.log");
        List<String> warnings = new ArrayList<>();
        AuditLogWriter writer = new AuditLogWriter(warnings::add);
        writer.initialize(log);

        writer.log("line");
        writer.shutdown();

        assertEquals(0, warnings.size());
        assertEquals(1, Files.readAllLines(log).size());
    }
}
