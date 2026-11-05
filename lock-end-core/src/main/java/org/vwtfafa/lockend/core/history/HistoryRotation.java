package org.vwtfafa.lockend.core.history;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared history sizing rules: bounded rotation by entry count and age, plus
 * the page math for the paginated history command. Mirrors the Paper plugin
 * so all platforms keep the same view on the same data.
 */
public final class HistoryRotation {

    /** Number of entries shown per history page. */
    public static final int PAGE_SIZE = 10;

    private HistoryRotation() {
    }

    /**
     * Trims a history list to the retention window and the maximum size,
     * using the current instant as the age reference.
     *
     * <p>A non-positive {@code retentionDays} disables age filtering; entries
     * beyond {@code maxEntries} are dropped oldest-first.</p>
     *
     * @param entries       the unrotated entries, oldest first
     * @param maxEntries    maximum number of retained entries
     * @param retentionDays maximum age in days; non-positive keeps forever
     * @return the rotated list (oldest first); never {@code null}
     */
    public static List<HistoryEntry> applyRotation(
            List<HistoryEntry> entries, int maxEntries, int retentionDays) {
        return applyRotation(entries, maxEntries, retentionDays, Instant.now());
    }

    /**
     * Rotation with an injectable age reference for deterministic tests.
     *
     * @param entries       the unrotated entries, oldest first
     * @param maxEntries    maximum number of retained entries
     * @param retentionDays maximum age in days; non-positive keeps forever
     * @param now           the instant age filtering is measured against
     * @return the rotated list (oldest first); never {@code null}
     */
    public static List<HistoryEntry> applyRotation(
            List<HistoryEntry> entries, int maxEntries, int retentionDays, Instant now) {
        List<HistoryEntry> result = entries;
        if (retentionDays > 0 && now != null) {
            Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
            result = result.stream()
                    .filter(entry -> !entry.timestamp().isBefore(cutoff))
                    .toList();
        }
        if (maxEntries > 0 && result.size() > maxEntries) {
            result = result.subList(result.size() - maxEntries, result.size());
        }
        return new ArrayList<>(result);
    }

    /**
     * Parses a page argument. Invalid input resolves to the last page
     * (over-large number) instead of failing the command, mirroring the
     * Paper plugin.
     *
     * @param value the raw page argument
     * @return the 1-based page number
     */
    public static int parsePageNumber(String value) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Exclusive end index of a 1-based page within the given total size. The
     * pagination walks newest-first, so page 1 ends at {@code historySize}.
     *
     * @param historySize total number of entries
     * @param page        1-based page number
     * @return exclusive end index; always at least 0
     */
    public static int pageEndExclusive(int historySize, int page) {
        long pageOffset = Math.max(0L, (long) page - 1L) * PAGE_SIZE;
        return (int) Math.max(0L, historySize - pageOffset);
    }
}
