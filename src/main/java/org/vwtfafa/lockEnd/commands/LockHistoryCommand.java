package org.vwtfafa.lockEnd.commands;

import org.bukkit.command.CommandSender;
import org.vwtfafa.lockEnd.LockEnd;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Command to view lock history.
 */
public class LockHistoryCommand {
    private static final java.util.concurrent.atomic.AtomicLong EXPORT_COUNTER = new java.util.concurrent.atomic.AtomicLong();
    private static final int PAGE_SIZE = 10;
    private final LockEnd plugin;
    private final List<HistoryEntry> history = new ArrayList<>();
    private final HistoryStore historyStore;
    private final int maxEntries;
    private final int retentionDays;
    private Boolean lastPreviousState;

    public LockHistoryCommand(LockEnd plugin) {
        this.plugin = plugin;
        this.maxEntries = Math.max(1, plugin.getConfig().getInt("history.max-entries", 1000));
        this.retentionDays = plugin.getConfig().getInt("history.retention-days", 30);
        historyStore = new HistoryStore(new File(plugin.getDataFolder(), "history.yml"), plugin.getLogger());
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
            send(sender, plugin.message("permission", Map.of()));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            send(sender, plugin.message("history-storage-status", Map.of(
                    "%status%", historyStore.status(),
                    "%entries%", String.valueOf(history.size()))));
            return true;
        }

        if (history.isEmpty()) {
            send(sender, plugin.message("history.empty", Map.of()));
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
                page = parsePageNumber(arg0);
                index = 1;
            }
        }

        // Arg at current index: format (json|csv) OR filter type
        if (index < args.length) {
            String arg = args[index];
            if (arg.equalsIgnoreCase("json") || arg.equalsIgnoreCase("csv")) {
                format = lowerCaseToken(arg);
                index++;
            }
        }

        // Arg at current index: filter type (player|action)
        if (index < args.length) {
            String arg = args[index];
            if (arg.equalsIgnoreCase("player") || arg.equalsIgnoreCase("action")) {
                String filterType = lowerCaseToken(arg);
                index++;
                if (index < args.length) {
                    filter = new HistoryFilter(filterType, args[index]);
                    index++;
                } else {
                    send(sender, plugin.message("history-usage", Map.of()));
                    return true;
                }
            }
        }

        // Apply export if format specified
        if (format != null) {
            File exportFile = export(format, filter);
            if (exportFile == null) {
                send(sender, plugin.message("history-export-failed", Map.of()));
                return true;
            }
            send(sender, plugin.message("history-exported", Map.of("%file%", exportFile.getName())));
            return true;
        }

        // Apply filtering
        List<HistoryEntry> displayedHistory = history;
        if (filter != null && filter.value() != null) {
            displayedHistory = history.stream()
                    .filter(filter::matches)
                    .collect(Collectors.toList());
            if (filter.type().equals("player")) {
                send(sender, plugin.message("history.filter-player",
                        Map.of("%player%", plugin.sanitize(filter.value()))));
            } else if (filter.type().equals("action")) {
                send(sender, plugin.message("history.filter-action",
                        Map.of("%action%", plugin.sanitize(filter.value()))));
            }
            if (displayedHistory.isEmpty()) {
                send(sender, plugin.message("history.filter-no-results", Map.of(
                        "%type%", filter.type(),
                        "%value%", plugin.sanitize(filter.value()))));
                return true;
            }
        }

        // Pagination
        int end = pageEndExclusive(displayedHistory.size(), page);
        int start = Math.max(0, end - PAGE_SIZE);
        if (start >= displayedHistory.size() || end <= 0) {
            send(sender, plugin.message("history-page-empty", Map.of()));
            return true;
        }
        send(sender, plugin.message("history-header-page", Map.of("%page%", String.valueOf(page))));
        for (int i = end - 1; i >= start; i--) {
            send(sender, plugin.messageComponent("  " + displayedHistory.get(i).display()));
        }
        return true;
    }

    private void send(CommandSender sender, net.kyori.adventure.text.Component message) {
        plugin.getPaperScheduler().sendMessage(sender, message);
    }

    static String lowerCaseToken(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    static int parsePageNumber(String value) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    static int pageEndExclusive(int historySize, int page) {
        long pageOffset = Math.max(0L, (long) page - 1L) * PAGE_SIZE;
        return (int) Math.max(0L, historySize - pageOffset);
    }

    /**
     * Trims a history list to the retention window and the maximum size.
     * A non-positive retentionDays disables age filtering; entries beyond
     * maxEntries are dropped oldest-first.
     */
    static List<HistoryEntry> applyRotation(List<HistoryEntry> entries, int maxEntries, int retentionDays) {
        List<HistoryEntry> result = entries;
        if (retentionDays > 0) {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
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
        addEntry(actor, null, action, source, "RECORDED", null, previousState, null);
    }

    public boolean addEntry(String actor, java.util.UUID actorUuid, String action, String source, String result,
                            java.util.UUID subjectUuid, Boolean previousState, Boolean newState) {
        history.add(new HistoryEntry(Instant.now(), actor, actorUuid, action, source, subjectUuid, result,
                previousState, newState));
        List<HistoryEntry> rotated = applyRotation(history, maxEntries, retentionDays);
        history.clear();
        history.addAll(rotated);
        return historyStore.save(history);
    }

    public boolean recordEvacuationResult(String actor, java.util.UUID actorUuid, java.util.UUID playerUuid,
                                          String source, String result, String targetWorld) {
        return addEntry(actor, actorUuid, "EVACUATE", source + " -> " + targetWorld, result, playerUuid,
                null, null);
    }

    public String getStorageStatus() {
        return historyStore.status();
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
        history.addAll(historyStore.load());
        List<HistoryEntry> rotated = applyRotation(history, maxEntries, retentionDays);
        boolean changedByRotation = !history.equals(rotated);
        history.clear();
        history.addAll(rotated);
        if (historyStore.isWritable() && (historyStore.needsRewrite() || changedByRotation)) {
            historyStore.save(history);
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
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss-SSS")
                .withZone(ZoneOffset.UTC).format(Instant.now()) + "-" + EXPORT_COUNTER.incrementAndGet();
        File exportFile = new File(plugin.getDataFolder(), "history-" + timestamp + "." + lowerCaseToken(format));
        Path temporaryFile = null;
        try {
            StringBuilder output = new StringBuilder();
            if (format.equalsIgnoreCase("json")) {
                output.append("[\n");
                for (int i = 0; i < exportHistory.size(); i++) {
                    HistoryEntry entry = exportHistory.get(i);
                    output.append("  {\"timestamp\":\"").append(escape(entry.timestamp().toString()))
                            .append("\",\"actor\":\"").append(escape(entry.actor()))
                            .append("\",\"actorUuid\":").append(jsonString(entry.actorUuid() == null
                                    ? null : entry.actorUuid().toString()))
                            .append(",\"action\":\"").append(escape(entry.action()))
                            .append("\",\"source\":\"").append(escape(entry.source()))
                            .append("\",\"subjectUuid\":").append(jsonString(entry.subjectUuid() == null
                                    ? null : entry.subjectUuid().toString()))
                            .append(",\"result\":\"").append(escape(entry.result()))
                            .append("\",\"previousState\":").append(jsonBoolean(entry.previousState()))
                            .append(",\"newState\":").append(jsonBoolean(entry.newState())).append("}");
                    if (i < exportHistory.size() - 1) output.append(',');
                    output.append('\n');
                }
                output.append(']');
            } else {
                output.append("timestamp,actor,actorUuid,action,source,subjectUuid,result,previousState,newState\n");
                for (HistoryEntry entry : exportHistory) {
                    output.append(csv(entry.timestamp().toString())).append(',')
                            .append(csv(entry.actor())).append(',')
                            .append(csv(entry.actorUuid() == null ? "" : entry.actorUuid().toString())).append(',')
                            .append(csv(entry.action())).append(',')
                            .append(csv(entry.source())).append(',')
                            .append(csv(entry.subjectUuid() == null ? "" : entry.subjectUuid().toString())).append(',')
                            .append(csv(entry.result())).append(',')
                            .append(entry.previousState() == null ? "" : entry.previousState()).append(',')
                            .append(entry.newState() == null ? "" : entry.newState()).append('\n');
                }
            }
            Path exportPath = exportFile.toPath();
            Path parent = exportPath.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, ".history-export-", ".tmp");
            Files.writeString(temporaryFile, output.toString(), StandardCharsets.UTF_8);
            HistoryStore.replaceAtomically(temporaryFile, exportPath);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not export history: " + exception.getMessage());
            return null;
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException exception) {
                    plugin.getLogger().warning("Could not remove temporary history export "
                            + temporaryFile + ": " + exception.getMessage());
                }
            }
        }
        return exportFile;
    }

    private String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    private String jsonBoolean(Boolean value) {
        return value == null ? "null" : value.toString();
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
