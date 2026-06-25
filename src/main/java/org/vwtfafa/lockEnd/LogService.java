package org.vwtfafa.lockEnd;

import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class LogService {
    private final LockEnd plugin;
    private File logDir;
    private File logFile;
    private File jsonFile;

    public LogService(LockEnd plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        if (!plugin.getConfig().getBoolean("logging.enabled", true)) {
            return;
        }
        logDir = new File(plugin.getDataFolder(), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        logFile = new File(logDir, plugin.getConfig().getString("logging.log-file", "EndLock.log"));
        if (plugin.getConfig().getBoolean("logging.json-enabled", true)) {
            jsonFile = new File(logDir, plugin.getConfig().getString("logging.json-file", "EndLock.jsonl"));
        }
    }

    public void logAction(String player, String action) {
        logBlock(player, action, null, null, null);
    }

    public void logBlock(String player, String action, String fromWorld, String toWorld, PlayerTeleportEvent.TeleportCause cause) {
        if (!plugin.getConfig().getBoolean("logging.enabled", true)) {
            return;
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        boolean locked = plugin.isLocked();
        String line = String.format("[%s] %s - Player: %s - Status: %s",
                timestamp, action, player, locked ? "LOCKED" : "UNLOCKED");
        if (fromWorld != null) {
            line += " - From: " + fromWorld;
        }
        if (toWorld != null) {
            line += " - To: " + toWorld;
        }
        if (cause != null) {
            line += " - Cause: " + cause.name();
        }
        appendLine(logFile, line + System.lineSeparator());
        if (jsonFile != null) {
            appendJson(timestamp, player, action, locked, fromWorld, toWorld, cause);
        }
    }

    private void appendLine(File file, String content) {
        try {
            if (file != null && !file.exists()) {
                file.createNewFile();
            }
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.append(content);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Log write failed: " + e.getMessage());
        }
    }

    private void appendJson(String timestamp, String player, String action, boolean locked,
                            String fromWorld, String toWorld, PlayerTeleportEvent.TeleportCause cause) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"timestamp\":\"").append(escape(timestamp)).append("\",");
        json.append("\"player\":\"").append(escape(player)).append("\",");
        json.append("\"action\":\"").append(escape(action)).append("\",");
        json.append("\"locked\":").append(locked);
        if (fromWorld != null) {
            json.append(",\"fromWorld\":\"").append(escape(fromWorld)).append("\"");
        }
        if (toWorld != null) {
            json.append(",\"toWorld\":\"").append(escape(toWorld)).append("\"");
        }
        if (cause != null) {
            json.append(",\"teleportCause\":\"").append(cause.name()).append("\"");
        }
        json.append("}").append(System.lineSeparator());
        appendLine(jsonFile, json.toString());
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public File exportJson() throws IOException {
        if (jsonFile == null || !jsonFile.exists()) {
            throw new IOException("No JSON log file found");
        }
        File export = new File(logDir, "EndLock-export-" + System.currentTimeMillis() + ".jsonl");
        java.nio.file.Files.copy(jsonFile.toPath(), export.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return export;
    }
}
