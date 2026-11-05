package org.vwtfafa.lockend.fabric;

import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import org.vwtfafa.lockend.core.history.HistoryBuffer;
import org.vwtfafa.lockend.core.history.HistoryEntry;
import org.vwtfafa.lockend.core.history.HistoryExport;
import org.vwtfafa.lockend.core.history.HistoryFilter;
import org.vwtfafa.lockend.core.history.HistoryRotation;
import org.vwtfafa.lockend.core.messages.ModMessages;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Locale;

/**
 * View over the bounded lock history: pagination, filtering, storage status
 * and JSON/CSV exports, mirroring the Paper plugin's
 * {@code LockHistoryCommand.execute} semantics (newest-first pages, fixed
 * argument order {@code [page] [json|csv] [player|action <value>]}). All
 * output routes through the shared message keys; files are written into the
 * mod's config directory.
 */
final class LockHistoryService {

    private static final DateTimeFormatter EXPORT_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final Path configDirectory;
    private final HistoryBuffer history;
    private final JsonHistoryStore historyStore;
    private final AtomicLong exportCounter = new AtomicLong();

    /**
     * Creates the view around the buffer, its store and the config directory
     * that receives export files.
     *
     * @param configDirectory the mod's config directory
     * @param history         the bounded history buffer
     * @param historyStore    the persistence owning the file status flags
     */
    LockHistoryService(Path configDirectory, HistoryBuffer history, JsonHistoryStore historyStore) {
        this.configDirectory = configDirectory;
        this.history = history;
        this.historyStore = historyStore;
    }

    /**
     * Runs the {@code /endlock history} view with the Paper plugin's
     * positional arguments.
     *
     * @param source the command source receiving the output
     * @param args   the post-{@code history} arguments
     */
    void showHistory(CommandSourceStack source, String[] args) {
        List<HistoryEntry> snapshot = history.snapshot();
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            send(source, messages().text("history-storage-status", Map.of(
                    "%status%", String.valueOf(historyStore.status()),
                    "%entries%", String.valueOf(snapshot.size()))));
            return;
        }
        if (snapshot.isEmpty()) {
            send(source, messages().text("history.empty"));
            return;
        }

        int page = 1;
        String format = null;
        HistoryFilter filter = null;
        int index = 0;
        if (args.length > 0 && args[0].matches("\\d+")) {
            page = HistoryRotation.parsePageNumber(args[0]);
            index = 1;
        }
        if (index < args.length
                && (args[index].equalsIgnoreCase("json") || args[index].equalsIgnoreCase("csv"))) {
            format = args[index].toLowerCase(Locale.ROOT);
            index++;
        }
        if (index < args.length
                && (args[index].equalsIgnoreCase("player") || args[index].equalsIgnoreCase("action"))) {
            String filterType = args[index].toLowerCase(Locale.ROOT);
            index++;
            if (index < args.length) {
                filter = new HistoryFilter(filterType, args[index]);
            } else {
                send(source, messages().text("history-usage"));
                return;
            }
        }

        if (format != null) {
            showExport(source, format, filter, snapshot);
            return;
        }
        showPage(source, snapshot, filter, page);
    }

    private void showPage(CommandSourceStack source, List<HistoryEntry> snapshot,
                          HistoryFilter filter, int page) {
        List<HistoryEntry> displayedHistory = snapshot;
        if (filter != null) {
            displayedHistory = snapshot.stream().filter(filter::matches).toList();
            if (filter.type().equals("player")) {
                send(source, messages().text("history.filter-player",
                        Map.of("%player%", filter.value())));
            } else if (filter.type().equals("action")) {
                send(source, messages().text("history.filter-action",
                        Map.of("%action%", filter.value())));
            }
            if (displayedHistory.isEmpty()) {
                send(source, messages().text("history.filter-no-results", Map.of(
                        "%type%", filter.type(),
                        "%value%", filter.value())));
                return;
            }
        }

        int end = HistoryRotation.pageEndExclusive(displayedHistory.size(), page);
        int start = Math.max(0, end - HistoryRotation.PAGE_SIZE);
        if (start >= displayedHistory.size() || end <= 0) {
            send(source, messages().text("history-page-empty"));
            return;
        }
        send(source, messages().text("history-header-page",
                Map.of("%page%", String.valueOf(page))));
        for (int i = end - 1; i >= start; i--) {
            send(source, entryLine(displayedHistory.get(i)));
        }
    }

    private void showExport(CommandSourceStack source, String format,
                            HistoryFilter filter, List<HistoryEntry> snapshot) {
        List<HistoryEntry> exportHistory = snapshot;
        if (filter != null && filter.value() != null) {
            exportHistory = snapshot.stream().filter(filter::matches).toList();
        }
        String text = format.equals("json")
                ? HistoryExport.buildJson(exportHistory)
                : HistoryExport.buildCsv(exportHistory);
        String timestamp = EXPORT_TIMESTAMP_FORMAT.format(Instant.now());
        Path file = configDirectory.resolve("history-" + timestamp + "-"
                + exportCounter.incrementAndGet() + "." + format);
        if (JsonFileIO.writeAtomically(file, text, LockEndFabricMod.LOGGER)) {
            send(source, messages().text("history-exported",
                    Map.of("%file%", file.getFileName().toString())));
        } else {
            send(source, messages().text("history-export-failed"));
        }
    }

    /**
     * Renders one entry through the language keys; optional details are
     * appended as plain text (no values are reparsed), mirroring the Paper
     * plugin's component composition.
     */
    private String entryLine(HistoryEntry entry) {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("%timestamp%", entry.displayTimestamp());
        tokens.put("%action%", entry.action());
        tokens.put("%actor%", entry.actor());
        tokens.put("%source%", entry.source());
        tokens.put("%result%", entry.result());
        tokens.put("%details%", detailsText(entry));
        return "  " + messages().text("history-entry", Map.copyOf(tokens));
    }

    private String detailsText(HistoryEntry entry) {
        StringBuilder details = new StringBuilder();
        if (entry.actorUuid() != null) {
            details.append(messages().text("history-details-actor-uuid",
                    Map.of("%uuid%", entry.actorUuid().toString())));
        }
        if (entry.subjectUuid() != null) {
            details.append(messages().text("history-details-subject-uuid",
                    Map.of("%uuid%", entry.subjectUuid().toString())));
        }
        if (entry.previousState() != null) {
            String state = messages().text(stateKey(entry.previousState()));
            if (entry.newState() != null) {
                state = state + messages().text("history-details-state-transition",
                        Map.of("%state%", messages().text(stateKey(entry.newState()))));
            }
            details.append(messages().text("history-details-state",
                    Map.of("%state%", state)));
        }
        return details.toString();
    }

    private static String stateKey(Boolean locked) {
        return Boolean.TRUE.equals(locked)
                ? "history-state-locked" : "history-state-unlocked";
    }

    private static ModMessages messages() {
        return LockEndFabricMod.messages();
    }

    private static void send(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}
