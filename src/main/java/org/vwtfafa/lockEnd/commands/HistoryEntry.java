package org.vwtfafa.lockEnd.commands;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record HistoryEntry(Instant timestamp, String actor, String action,
                           String source, boolean previousState) {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    public String display() {
        return String.format("[%s] %s by %s (source: %s, previously: %s)",
                FORMAT.format(timestamp), action, actor, source,
                previousState ? "locked" : "unlocked");
    }
}
