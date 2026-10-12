package org.vwtfafa.lockEnd.commands;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public record HistoryEntry(Instant timestamp, String actor, UUID actorUuid, String action,
                           String source, UUID subjectUuid, String result,
                           Boolean previousState, Boolean newState) {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    public HistoryEntry(Instant timestamp, String actor, String action, String source, boolean previousState) {
        this(timestamp, actor, null, action, source, null, "RECORDED", previousState, null);
    }

    public String display() {
        StringBuilder details = new StringBuilder(String.format("[%s] %s by %s (source: %s, result: %s",
                FORMAT.format(timestamp), action, actor, source, result));
        if (actorUuid != null) {
            details.append(", actor UUID: ").append(actorUuid);
        }
        if (subjectUuid != null) {
            details.append(", player UUID: ").append(subjectUuid);
        }
        if (previousState != null) {
            details.append(", state: ").append(previousState ? "locked" : "unlocked");
            if (newState != null) {
                details.append(" -> ").append(newState ? "locked" : "unlocked");
            }
        }
        return details.append(')').toString();
    }
}
