package org.vwtfafa.lockEnd.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.vwtfafa.lockEnd.LockEnd;

import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Command to view lock history.
 */
public class LockHistoryCommand implements CommandExecutor, TabCompleter {
    private final LockEnd plugin;
    private final List<HistoryEntry> history = new ArrayList<>();
    private final File historyFile;
    private Boolean lastPreviousState;

    public LockHistoryCommand(LockEnd plugin) {
        this.plugin = plugin;
        historyFile = new File(plugin.getDataFolder(), "history.yml");
        loadHistory();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("endlock.history")) {
            sender.sendMessage(plugin.msg("permission"));
            return true;
        }

        if (history.isEmpty()) {
            sender.sendMessage(plugin.msg("history.empty"));
            return true;
        }

        int page = 1;
        if (args.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                sender.sendMessage(plugin.msg("history-usage"));
                return true;
            }
        }
        if (args.length > 2 && (args[2].equalsIgnoreCase("json") || args[2].equalsIgnoreCase("csv"))) {
            File exportFile = export(args[2]);
            sender.sendMessage(plugin.msg("history-exported").replace("%file%", exportFile.getName()));
            return true;
        }

        int pageSize = 10;
        int end = history.size() - ((page - 1) * pageSize);
        int start = Math.max(0, end - pageSize);
        if (start >= history.size() || end <= 0) {
            sender.sendMessage(plugin.msg("history-page-empty"));
            return true;
        }
        sender.sendMessage(plugin.msg("history-header-page").replace("%page%", String.valueOf(page)));
        for (int i = end - 1; i >= start; i--) {
            sender.sendMessage("  " + history.get(i).display());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }

    /**
     * Adds an entry to the history.
     * @param entry The history entry
     */
    public void addEntry(String actor, String action, boolean previousState, String source) {
        history.add(new HistoryEntry(java.time.LocalDateTime.now(), actor, action, source, previousState));
        // Keep only last 100 entries
        if (history.size() > 100) {
            history.remove(0);
        }
        saveHistory();
    }

    public void recordPreviousState(boolean previousState) {
        lastPreviousState = previousState;
    }

    public Boolean getLastPreviousState() {
        return lastPreviousState;
    }

    public void clearLastPreviousState() {
        lastPreviousState = null;
    }

    private void loadHistory() {
        if (!historyFile.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(historyFile);
        for (Object rawEntry : config.getList("entries", List.of())) {
            if (rawEntry instanceof Map<?, ?> entry) {
                try {
                    history.add(new HistoryEntry(
                            java.time.LocalDateTime.parse(String.valueOf(entry.get("timestamp"))),
                            String.valueOf(entry.get("actor")),
                            String.valueOf(entry.get("action")),
                            String.valueOf(entry.get("source")),
                            Boolean.parseBoolean(String.valueOf(entry.get("previous-state")))));
                } catch (RuntimeException ignored) {
                    plugin.getLogger().warning("Skipping invalid history entry.");
                }
            } else if (rawEntry != null) {
                history.add(new HistoryEntry(java.time.LocalDateTime.now(), "unknown", rawEntry.toString(), "legacy", false));
            }
        }
        if (history.size() > 100) {
            history.subList(0, history.size() - 100).clear();
        }
    }

    private void saveHistory() {
        YamlConfiguration historyConfig = new YamlConfiguration();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (HistoryEntry entry : history) {
            entries.add(Map.of(
                "timestamp", entry.timestamp().toString(),
                "actor", entry.actor(),
                "action", entry.action(),
                "source", entry.source(),
                "previous-state", entry.previousState()));
        }
        historyConfig.set("entries", entries);
        try {
            historyConfig.save(historyFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save history: " + exception.getMessage());
        }
    }

    private File export(String format) {
        File exportFile = new File(plugin.getDataFolder(), "history." + format.toLowerCase());
        try {
            if (format.equalsIgnoreCase("json")) {
                StringBuilder json = new StringBuilder("[\n");
                for (int i = 0; i < history.size(); i++) {
                    HistoryEntry entry = history.get(i);
                    json.append("  {\"timestamp\":\"").append(escape(entry.timestamp().toString()))
                            .append("\",\"actor\":\"").append(escape(entry.actor()))
                            .append("\",\"action\":\"").append(escape(entry.action()))
                            .append("\",\"source\":\"").append(escape(entry.source()))
                            .append("\",\"previousState\":").append(entry.previousState()).append("}");
                    if (i < history.size() - 1) json.append(',');
                    json.append('\n');
                }
                json.append(']');
                Files.writeString(exportFile.toPath(), json.toString(), StandardCharsets.UTF_8);
            } else {
                try (FileWriter writer = new FileWriter(exportFile, StandardCharsets.UTF_8)) {
                    writer.write("timestamp,actor,action,source,previousState\n");
                    for (HistoryEntry entry : history) {
                        writer.write(csv(entry.timestamp().toString()) + "," + csv(entry.actor()) + ","
                                + csv(entry.action()) + "," + csv(entry.source()) + ","
                                + entry.previousState() + "\n");
                    }
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not export history: " + exception.getMessage());
        }
        return exportFile;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}