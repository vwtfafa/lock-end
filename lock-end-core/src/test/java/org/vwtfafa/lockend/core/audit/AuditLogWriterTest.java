package org.vwtfafa.lockend.core.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

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
        AuditLogWriter writer = new AuditLogWriter(
                Logger.getLogger(AuditLogWriterTest.class.getName()));
        writer.initialize(log);

        writer.log("LOCK - Player: Alice - Status: LOCKED");
        writer.shutdown(); // waits for the queue to drain

        List<String> lines = Files.readAllLines(log);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).endsWith("] LOCK - Player: Alice - Status: LOCKED"));
    }

    @Test
    void uninitializedWriterDropsMessagesInsteadOfFailing() {
        AuditLogWriter writer = new AuditLogWriter(
                Logger.getLogger(AuditLogWriterTest.class.getName()));
        writer.log("dropped");

        writer.shutdown();
        assertTrue(true, "no exception reached the caller");
    }

    @Test
    void nullFileInitializationSkipsTheWriterLoop() throws Exception {
        AuditLogWriter writer = new AuditLogWriter(
                Logger.getLogger(AuditLogWriterTest.class.getName()));
        writer.initialize(null);
        writer.log("dropped");

        writer.shutdown();
        assertTrue(true, "no exception reached the caller");
    }
}
