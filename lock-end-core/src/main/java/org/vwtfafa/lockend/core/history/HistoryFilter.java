package org.vwtfafa.lockend.core.history;

/**
 * A history filter (type + value) with matching logic, mirroring the Paper
 * plugin's {@code /endlock history player|action <value>} filters. Only two
 * filter types exist on every platform; unknown types match everything.
 *
 * @param type  {@code player} or {@code action}
 * @param value the case-insensitive value to match
 */
public record HistoryFilter(String type, String value) {

    /**
     * @param entry the entry to test
     * @return {@code true} when the entry passes this filter
     */
    public boolean matches(HistoryEntry entry) {
        if (type == null || value == null) {
            return true;
        }
        return switch (type) {
            case "player" -> entry.actor().equalsIgnoreCase(value);
            case "action" -> entry.action().equalsIgnoreCase(value);
            default -> true;
        };
    }
}
