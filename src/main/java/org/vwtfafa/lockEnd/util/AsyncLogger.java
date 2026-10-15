package org.vwtfafa.lockEnd.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Asynchronous logger to prevent main thread lag from file I/O.
 */
public class AsyncLogger {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BlockingQueue<LogEntry> logQueue = new LinkedBlockingQueue<>();
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EndLock-AsyncLogger");
        t.setDaemon(true);
        return t;
    });
    private File logFile;
    private volatile boolean running = false;

    public AsyncLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Initializes the async logger with the log file.
     * @param logFile The log file to write to
     */
    public void initialize(File logFile) {
        this.logFile = logFile;
        if (!running) {
            running = true;
            startProcessing();
        }
    }

    /**
     * Logs a message asynchronously.
     * @param message The message to log
     */
    public void log(String message) {
        if (logFile == null || !running) {
            return;
        }
        logQueue.add(new LogEntry(message));
    }

    /**
     * Shuts down the async logger, waiting for the executor to finish processing
     * remaining entries before returning.
     */
    public void shutdown() {
        running = false;
        executor.shutdown(); // Prevent new tasks from being accepted
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow(); // Cancel any still-running tasks
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.warning("AsyncLogger executor did not terminate within the timeout");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void startProcessing() {
        executor.submit(() -> {
            while (running || !logQueue.isEmpty()) {
                try {
                    LogEntry entry = logQueue.poll(200, TimeUnit.MILLISECONDS);
                    if (entry != null) {
                        writeToFile(entry.message());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (!running) {
                        break;
                    }
                }
            }
        });
    }

    private void writeToFile(String message) {
        if (logFile == null) {
            return;
        }
        try {
            String logMessage = String.format("[%s] %s\n", TIMESTAMP_FORMAT.format(LocalDateTime.now()), message);
            Files.writeString(logFile.toPath(), logMessage, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.severe("Error writing to log file: " + e.getMessage());
        }
    }

    private record LogEntry(String message) {}
}