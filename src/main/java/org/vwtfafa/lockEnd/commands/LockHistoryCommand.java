package org.vwtfafa.lockEnd.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.vwtfafa.lockEnd.LockEnd;

import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Command to view lock history.
 */
public class LockHistoryCommand {
    private static final java.util.concurrent.atomic.AtomicLong EXPORT_COUNTER = new java.util.concurrent.atomic.AtomicLong();
    private final LockEnd plugin;
    private final List<HistoryEntry> history = new ArrayList<>();
    private final File historyFile;
    private final int maxEntries;
    private final int retentionDays;
    private Boolean lastPreviousState;

    public LockHistoryCommand(LockEnd plugin) {
        this.plugin = plugin;
        this.maxEntries = Math.max(1, plugin.getConfig().getInt("history.max-entries", 1000));
        this.retentionDays = plugin.getConfig().getInt("history.retention-days", 30);
        historyFile = new File(plugin.getDataFolder(), "history.yml");
        loadHistory();
    }

    /**
     * Encapsulates a history filter (type + value) with matching logic.
     */
    record HistoryFilter(String type, String value) {
        boolean matches(HistoryEntry entry) {
            if (type == null || value == null) return true;
            return switch (type) {
                case "player" -> entry.actor().equalsIgnoreCase(value);
                case "action" -> entry.action().equalsIgnoreCase(value);
                default -> true;
            };
        }
    }

    /**
     * Executes the history display/export.
     * @param sender The command sender
     * @param args The command arguments
     * @return true when handled
     */
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("endlock.history")) {
            sender.sendMessage(plugin.msg("permission"));
            return true;
        }

        if (history.isEmpty()) {
            sender.sendMessage(plugin.msg("history.empty"));
            return true;
        }

        // Parse arguments in fixed order: [page] [json|csv] [player|action] <value>
        int page = 1;
        String format = null;
        HistoryFilter filter = null;

        int index = 0;
        // Arg 0: page number OR filter type (if no page given)
        if (args.length > 0) {
            String arg0 = args[0];
            if (arg0.matches("\\d+")) {
                page = Math.max(1, Integer.parseInt(arg0));
                index = 1;
            }
        }

        // Arg at current index: format (json|csv) OR filter type
        if (index < args.length) {
            String arg = args[index];
            if (arg.equalsIgnoreCase("json") || arg.equalsIgnoreCase("csv")) {
                format = arg.toLowerCase();
                index++;
            }
        }

        // Arg at current index: filter type (player|action)
        if (index < args.length) {
            String arg = args[index];
            if (arg.equalsIgnoreCase("player") || arg.equalsIgnoreCase("action")) {
                String filterType = arg.toLowerCase();
                index++;
                if (index < args.length) {
                    filter = new HistoryFilter(filterType, args[index]);
                    index++;
                } else {
                    sender.sendMessage(plugin.msg("history-usage"));
                    return true;
                }
            }
        }

        // Apply export if format specified
        if (format != null) {
            File exportFile = export(format, filter);
            if (exportFile == null) {
                sender.sendMessage(plugin.msg("history-export-failed"));
                return true;
            }
            sender.sendMessage(plugin.msg("history-exported").replace("%file%", exportFile.getName()));
            return true;
        }

        // Apply filtering
        List<HistoryEntry> displayedHistory = history;
        if (filter != null && filter.value() != null) {
            displayedHistory = history.stream()
                    .filter(filter::matches)
                    .collect(Collectors.toList());
            if (filter.type().equals("player")) {
                sender.sendMessage(plugin.msg("history.filter-player")
                        .replace("%player%", plugin.sanitize(filter.value())));
            } else if (filter.type().equals("action")) {
                sender.sendMessage(plugin.msg("history.filter-action")
                        .replace("%action%", plugin.sanitize(filter.value())));
            }
            if (displayedHistory.isEmpty()) {
                sender.sendMessage(plugin.msg("history.filter-no-results")
                        .replace("%type%", filter.type())
                        .replace("%value%", plugin.sanitize(filter.value())));
                return true;
            }
        }

        // Pagination
        int pageSize = 10;
        int end = displayedHistory.size() - ((page - 1) * pageSize);
        int start = Math.max(0, end - pageSize);
        if (start >= displayedHistory.size() || end <= 0) {
            sender.sendMessage(plugin.msg("history-page-empty"));
            return true;
        }
        sender.sendMessage(plugin.msg("history-header-page").replace("%page%", String.valueOf(page)));
        for (int i = end - 1; i >= start; i--) {
            sender.sendMessage("  " + displayedHistory.get(i).display());
        }
        return true;
    }

    /**
     * Trims a history list to the retention window and the maximum size.
     * A non-positive retentionDays disables age filtering; entries beyond
     * maxEntries are dropped oldest-first.
     */
    static List<HistoryEntry> applyRotation(List<HistoryEntry> entries, int maxEntries, int retentionDays) {
        List<HistoryEntry> result = entries;
        if (retentionDays > 0) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            result = result.stream()
                    .filter(entry -> !entry.timestamp().isBefore(cutoff))
                    .collect(Collectors.toList());
        }
        if (maxEntries > 0 && result.size() > maxEntries) {
            result = result.subList(result.size() - maxEntries, result.size());
        }
        return new ArrayList<>(result);
    }

    /**
     * Adds an entry to the history.
     * @param entry The history entry
     */
    public void addEntry(String actor, String action, boolean previousState, String source) {
        history.add(new HistoryEntry(java.time.LocalDateTime.now(), actor, action, source, previousState));
        List<HistoryEntry> rotated = applyRotation(history, maxEntries, retentionDays);
        history.clear();
        history.addAll(rotated);
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
        List<HistoryEntry> rotated = applyRotation(history, maxEntries, retentionDays);
        history.clear();
        history.addAll(rotated);
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

    private File export(String format, HistoryFilter filter) {
        // Apply filter to export data
        List<HistoryEntry> exportHistory = history;
        if (filter != null && filter.value() != null) {
            exportHistory = history.stream()
                    .filter(filter::matches)
                    .collect(Collectors.toList());
        }

        // Timestamp with millis plus a counter to avoid overwrites on rapid exports.
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss-SSS"))
                + "-" + EXPORT_COUNTER.incrementAndGet();
        File exportFile = new File(plugin.getDataFolder(), "history-" + timestamp + "." + format.toLowerCase());
        try {
            if (format.equalsIgnoreCase("json")) {
                StringBuilder json = new StringBuilder("[\n");
                for (int i = 0; i < exportHistory.size(); i++) {
                    HistoryEntry entry = exportHistory.get(i);
                    json.append("  {\"timestamp\":\"").append(escape(entry.timestamp().toString()))
                            .append("\",\"actor\":\"").append(escape(entry.actor()))
                            .append("\",\"action\":\"").append(escape(entry.action()))
                            .append("\",\"source\":\"").append(escape(entry.source()))
                            .append("\",\"previousState\":").append(entry.previousState()).append("}");
                    if (i < exportHistory.size() - 1) json.append(',');
                    json.append('\n');
                }
                json.append(']');
                Files.writeString(exportFile.toPath(), json.toString(), StandardCharsets.UTF_8);
            } else {
                try (FileWriter writer = new FileWriter(exportFile, StandardCharsets.UTF_8)) {
                    writer.write("timestamp,actor,action,source,previousState\n");
                    for (HistoryEntry entry : exportHistory) {
                        writer.write(csv(entry.timestamp().toString()) + "," + csv(entry.actor()) + ","
                                + csv(entry.action()) + "," + csv(entry.source()) + ","
                                + entry.previousState() + "\n");
                    }
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not export history: " + exception.getMessage());
            return null;
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
