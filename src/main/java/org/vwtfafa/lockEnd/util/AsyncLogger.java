package org.vwtfafa.lockEnd.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous logger to prevent main thread lag from file I/O.
 */
public class AsyncLogger {
    private static final AsyncLogger INSTANCE = new AsyncLogger();
    private final Queue<LogEntry> logQueue = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EndLock-AsyncLogger");
        t.setDaemon(true);
        return t;
    });
    private File logFile;
    private volatile boolean running = false;

    public AsyncLogger() {}

    public static AsyncLogger getInstance() {
        return INSTANCE;
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
     * Shuts down the async logger.
     */
    public void shutdown() {
        running = false;
        processRemaining();
        executor.shutdown();
    }

    private void startProcessing() {
        executor.submit(() -> {
            while (running || !logQueue.isEmpty()) {
                processNext();
                try {
                    Thread.sleep(50); // 50ms interval
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void processNext() {
        LogEntry entry = logQueue.poll();
        if (entry != null) {
            writeToFile(entry.message);
        }
    }

    private void processRemaining() {
        LogEntry entry;
        while ((entry = logQueue.poll()) != null) {
            writeToFile(entry.message);
        }
    }

    private void writeToFile(String message) {
        if (logFile == null) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String timestamp = now.format(formatter);
            String logMessage = String.format("[%s] %s\n", timestamp, message);

            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                writer.print(logMessage);
            }
        } catch (IOException e) {
            Bukkit.getLogger().severe("Error writing to log file: " + e.getMessage());
        }
    }

    private static class LogEntry {
        final String message;

        LogEntry(String message) {
            this.message = message;
        }
    }
}