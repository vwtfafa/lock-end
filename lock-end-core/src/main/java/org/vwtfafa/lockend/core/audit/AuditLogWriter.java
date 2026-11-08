package org.vwtfafa.lockend.core.audit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Asynchronous audit log writer, keeping blocking file I/O off the server
 * thread. Structurally identical to the Paper plugin's {@code AsyncLogger}:
 * a single daemon thread drains a queue and appends one timestamped line per
 * entry. The mods reuse it instead of their platform scheduler because the
 * file target lies inside the mod's own data directory.
 */
public final class AuditLogWriter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private final Consumer<String> warningSink;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(command -> {
        Thread thread = new Thread(command, "EndLock-AuditWriter");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Path logFile;
    private volatile boolean running;
    private volatile boolean started;

    /**
     * Creates the writer around the platform's warning output.
     *
     * @param warningSink receives write-failure warnings; not {@code null}
     */
    public AuditLogWriter(Consumer<String> warningSink) {
        this.warningSink = warningSink;
    }

    /**
     * Selects the audit log file. Paths must be normalized and sandboxed by
     * the caller (config validation); the file is only created on the first
     * written entry.
     *
     * @param newLogFile the target file; {@code null} disables writing
     */
    public void initialize(Path newLogFile) {
        this.logFile = newLogFile;
        if (!started && newLogFile != null) {
            running = true;
            started = true;
            executor.submit(this::drain);
        }
    }

    /**
     * Queues one raw line; no I/O runs on the calling thread. A disabled or
     * uninitialized writer drops the line silently, like a disabled Paper
     * logger.
     *
     * @param message the line to append
     */
    public void log(String message) {
        Path target = logFile;
        if (target == null || !running) {
            return;
        }
        logQueue.add(message);
    }

    /**
     * Stops the writer and waits for queued entries to be written.
     */
    public void shutdown() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    warningSink.accept("Audit log writer did not terminate within the timeout");
                }
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        started = false;
    }

    private void drain() {
        while (running || !logQueue.isEmpty()) {
            try {
                String message = logQueue.poll(200, TimeUnit.MILLISECONDS);
                if (message != null) {
                    writeLine(message);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                if (!running) {
                    break;
                }
            }
        }
    }

    private void writeLine(String message) {
        Path target = logFile;
        if (target == null) {
            return;
        }
        try {
            write(message, target);
        } catch (IOException | UncheckedIOException exception) {
            warningSink.accept("Error writing to the audit log file: "
                    + exception.getMessage());
        }
    }

    private void write(String message, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String line = String.format("[%s] %s%n",
                TIMESTAMP_FORMAT.format(LocalDateTime.now()), message);
        Files.writeString(target, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
