package org.vwtfafa.lockEnd.commands;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.vwtfafa.lockEnd.LockEnd;
import org.vwtfafa.lockend.core.history.HistoryBuffer;
import org.vwtfafa.lockend.core.history.HistoryEntry;
import org.vwtfafa.lockend.core.history.HistoryExport;
import org.vwtfafa.lockend.core.history.HistoryFilter;
import org.vwtfafa.lockend.core.history.HistoryRotation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Command to view lock history.
 */
public class LockHistoryCommand {
    private static final java.util.concurrent.atomic.AtomicLong EXPORT_COUNTER =
            new java.util.concurrent.atomic.AtomicLong();
    private static final long SAVE_DELAY_TICKS = 20L;
    private static final DateTimeFormatter EXPORT_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private final LockEnd plugin;
    private final HistoryStore historyStore;
    private final HistoryBuffer historyBuffer;
    private ScheduledTask pendingSave;

    public LockHistoryCommand(LockEnd plugin) {
        this.plugin = plugin;
        int maxEntries = Math.max(1, plugin.getConfig().getInt("history.max-entries", 1000));
        int retentionDays = plugin.getConfig().getInt("history.retention-days", 30);
        historyStore = new HistoryStore(new File(plugin.getDataFolder(), "history.yml"), plugin.getLogger());
        List<HistoryEntry> loaded = historyStore.load();
        boolean needsRewrite = historyStore.needsRewrite();
        historyBuffer = new HistoryBuffer(historyStore, loaded, maxEntries, retentionDays);
        boolean changedByRotation = !loaded.equals(historyBuffer.snapshot());
        if (historyStore.isWritable() && (needsRewrite || changedByRotation)) {
            // Schema migration / retention trim must land before the next crash.
            historyStore.save(historyBuffer.snapshot());
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

        List<HistoryEntry> snapshot = historyBuffer.snapshot();
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            send(sender, plugin.message("history-storage-status", Map.of(
                    "%status%", historyStore.status(),
                    "%entries%", String.valueOf(snapshot.size()))));
            return true;
        }

        if (snapshot.isEmpty()) {
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
                page = HistoryRotation.parsePageNumber(arg0);
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
            File exportFile = export(format, filter, snapshot);
            if (exportFile == null) {
                send(sender, plugin.message("history-export-failed", Map.of()));
                return true;
            }
            send(sender, plugin.message("history-exported", Map.of("%file%", exportFile.getName())));
            return true;
        }

        // Apply filtering
        List<HistoryEntry> displayedHistory = snapshot;
        if (filter != null && filter.value() != null) {
            displayedHistory = snapshot.stream()
                    .filter(filter::matches)
                    .toList();
            if (filter.type().equals("player")) {
                send(sender, plugin.message("history.filter-player",
                        Map.of("%player%", filter.value())));
            } else if (filter.type().equals("action")) {
                send(sender, plugin.message("history.filter-action",
                        Map.of("%action%", filter.value())));
            }
            if (displayedHistory.isEmpty()) {
                send(sender, plugin.message("history.filter-no-results", Map.of(
                        "%type%", filter.type(),
                        "%value%", filter.value())));
                return true;
            }
        }

        // Pagination
        int end = HistoryRotation.pageEndExclusive(displayedHistory.size(), page);
        int start = Math.max(0, end - HistoryRotation.PAGE_SIZE);
        if (start >= displayedHistory.size() || end <= 0) {
            send(sender, plugin.message("history-page-empty", Map.of()));
            return true;
        }
        send(sender, plugin.message("history-header-page", Map.of("%page%", String.valueOf(page))));
        for (int i = end - 1; i >= start; i--) {
            Component line = Component.text("  ")
                    .append(HistoryEntryDisplay.display(displayedHistory.get(i), plugin.getMessageService()));
            send(sender, line);
        }
        return true;
    }

    private void send(CommandSender sender, Component message) {
        plugin.getPaperScheduler().sendMessage(sender, message);
    }

    static String lowerCaseToken(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * Adds an entry to the history.
     * @param entry The history entry
     */
    public void addEntry(String actor, String action, boolean previousState, String source) {
        addEntry(actor, null, action, source, "RECORDED", null, previousState, null);
    }

    public synchronized boolean addEntry(String actor, UUID actorUuid, String action, String source,
                                         String result, UUID subjectUuid, Boolean previousState,
                                         Boolean newState) {
        historyBuffer.addEntry(actor, actorUuid, action, source, result, subjectUuid, previousState, newState);
        return scheduleSave();
    }

    /**
     * Persists all pending entries immediately. Called before shutdown and
     * before a reload replaces this command instance.
     *
     * @return true when the history file is up to date or was saved
     */
    public synchronized boolean flush() {
        if (pendingSave != null) {
            pendingSave.cancel();
            pendingSave = null;
        }
        return historyBuffer.flush();
    }

    private boolean scheduleSave() {
        if (!historyStore.isWritable()) {
            return false;
        }
        if (pendingSave != null) {
            return true;
        }
        try {
            pendingSave = plugin.getPaperScheduler().runGlobalLater(this::flushPendingSave, SAVE_DELAY_TICKS);
        } catch (RuntimeException exception) {
            // The scheduler may already be shutting down; write synchronously.
            return flush();
        }
        return true;
    }

    private synchronized void flushPendingSave() {
        pendingSave = null;
        historyBuffer.flushIfDirty();
    }

    public boolean recordEvacuationResult(String actor, UUID actorUuid, UUID playerUuid,
                                          String source, String result, String targetWorld) {
        return addEntry(actor, actorUuid, "EVACUATE", source + " -> " + targetWorld, result, playerUuid,
                null, null);
    }

    public String getStorageStatus() {
        return historyStore.status();
    }

    private File export(String format, HistoryFilter filter, List<HistoryEntry> snapshot) {
        List<HistoryEntry> exportHistory = snapshot;
        if (filter != null && filter.value() != null) {
            exportHistory = snapshot.stream()
                    .filter(filter::matches)
                    .toList();
        }

        // Timestamp with millis plus a counter to avoid overwrites on rapid exports.
        String timestamp = EXPORT_TIMESTAMP_FORMAT.format(Instant.now()) + "-" + EXPORT_COUNTER.incrementAndGet();
        File exportFile = new File(plugin.getDataFolder(), "history-" + timestamp + "." + lowerCaseToken(format));
        Path temporaryFile = null;
        try {
            String output = format.equalsIgnoreCase("json")
                    ? HistoryExport.buildJson(exportHistory)
                    : HistoryExport.buildCsv(exportHistory);
            Path exportPath = exportFile.toPath();
            Path parent = exportPath.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, ".history-export-", ".tmp");
            Files.writeString(temporaryFile, output, StandardCharsets.UTF_8);
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
}
