package org.vwtfafa.lockEnd.commands;

import org.bukkit.Bukkit;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /**
     * Encapsulates a history filter (type + value) with matching logic.
     */
    private record HistoryFilter(String type, String value) {
        boolean matches(HistoryEntry entry) {
            if (type == null || value == null) return true;
            return switch (type) {
                case "player" -> entry.actor().equalsIgnoreCase(value);
                case "action" -> entry.action().equalsIgnoreCase(value);
                default -> true;
            };
        }
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
                sender.sendMessage(plugin.msg("history.filter-player").replace("%player%", filter.value()));
            } else if (filter.type().equals("action")) {
                sender.sendMessage(plugin.msg("history.filter-action").replace("%action%", filter.value()));
            }
            if (displayedHistory.isEmpty()) {
                sender.sendMessage(plugin.msg("history.filter-no-results")
                        .replace("%type%", filter.type())
                        .replace("%value%", filter.value()));
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 0) {
            return List.of("1");
        }

        // Determine what tokens have been used
        boolean hasPage = false;
        boolean hasFormat = false;
        boolean hasFilterType = false;
        String filterType = null;

        // Scan args to determine state
        int i = 0;
        if (i < args.length && args[i].matches("\\d+")) {
            hasPage = true;
            i++;
        }
        if (i < args.length && (args[i].equalsIgnoreCase("json") || args[i].equalsIgnoreCase("csv"))) {
            hasFormat = true;
            i++;
        }
        if (i < args.length && (args[i].equalsIgnoreCase("player") || args[i].equalsIgnoreCase("action"))) {
            hasFilterType = true;
            filterType = args[i].toLowerCase();
            i++;
        }

        // Now provide completions based on next expected token
        if (!hasPage && args.length == 1) {
            // First arg: page numbers, json, csv, player, action
            Stream.of("1", "2", "3", "4", "5", "json", "csv", "player", "action")
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .forEach(completions::add);
            return completions;
        }

        if (hasPage && !hasFormat && !hasFilterType && args.length == (hasPage ? 2 : 1)) {
            // Second arg after page: json, csv, player, action
            Stream.of("json", "csv", "player", "action")
                    .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                    .forEach(completions::add);
            return completions;
        }

        if (!hasPage && !hasFormat && hasFilterType && args.length == 2) {
            // First arg was filter type: suggest filter values
            return getFilterValueCompletions(filterType, args[1]);
        }

        if (hasPage && hasFormat && !hasFilterType && args.length == (hasPage ? 3 : 2)) {
            // After page and format: suggest player, action
            Stream.of("player", "action")
                    .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                    .forEach(completions::add);
            return completions;
        }

        if (hasPage && !hasFormat && hasFilterType && args.length == 3) {
            // After page and filter type: suggest filter values
            return getFilterValueCompletions(filterType, args[2]);
        }

        if (!hasPage && hasFormat && !hasFilterType && args.length == 2) {
            // After format (no page): suggest player, action
            Stream.of("player", "action")
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .forEach(completions::add);
            return completions;
        }

        if (hasFilterType && args.length > (hasPage ? 3 : 2) + (hasFormat ? 1 : 0)) {
            // After filter type + value: nothing more
            return completions;
        }

        return completions;
    }

    private List<String> getFilterValueCompletions(String filterType, String partial) {
        List<String> completions = new ArrayList<>();
        if ("player".equals(filterType)) {
            // Online players + unique actors from history
            Set<String> players = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Bukkit.getOnlinePlayers().forEach(p -> players.add(p.getName()));
            history.stream().map(HistoryEntry::actor).distinct().forEach(players::add);
            players.stream()
                    .filter(p -> p.toLowerCase().startsWith(partial.toLowerCase()))
                    .forEach(completions::add);
        } else if ("action".equals(filterType)) {
            // Unique actions from history
            Set<String> actions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            history.stream().map(HistoryEntry::action).distinct().forEach(actions::add);
            actions.stream()
                    .filter(a -> a.toLowerCase().startsWith(partial.toLowerCase()))
                    .forEach(completions::add);
        }
        return completions;
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

    private File export(String format, HistoryFilter filter) {
        // Apply filter to export data
        List<HistoryEntry> exportHistory = history;
        if (filter != null && filter.value() != null) {
            exportHistory = history.stream()
                    .filter(filter::matches)
                    .collect(Collectors.toList());
        }

        // Use timestamped filename to avoid overwrites
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
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