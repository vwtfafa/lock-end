package org.vwtfafa.lockEnd.commands;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record HistoryEntry(LocalDateTime timestamp, String actor, String action,
                           String source, boolean previousState) {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String display() {
        return String.format("[%s] %s by %s (source: %s, previously: %s)",
                timestamp.format(FORMAT), action, actor, source,
                previousState ? "locked" : "unlocked");
    }
}
