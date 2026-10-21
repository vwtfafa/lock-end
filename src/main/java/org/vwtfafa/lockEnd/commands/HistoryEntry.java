package org.vwtfafa.lockEnd.commands;

import org.vwtfafa.lockEnd.MessageService;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

public record HistoryEntry(Instant timestamp, String actor, UUID actorUuid, String action,
                           String source, UUID subjectUuid, String result,
                           Boolean previousState, Boolean newState) {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    public HistoryEntry(Instant timestamp, String actor, String action, String source, boolean previousState) {
        this(timestamp, actor, null, action, source, null, "RECORDED", previousState, null);
    }

    /**
     * Renders the entry through the language files. Optional details are
     * composed from separate keys so translators control both the main
     * sentence and each suffix.
     */
    public String display(MessageService messages) {
        String details = buildDetails(messages);
        return messages.msg("history-entry", Map.of(
                "%timestamp%", FORMAT.format(timestamp),
                "%action%", action,
                "%actor%", actor,
                "%source%", source,
                "%result%", result,
                "%details%", details));
    }

    private String buildDetails(MessageService messages) {
        StringBuilder details = new StringBuilder();
        if (actorUuid != null) {
            details.append(messages.msg("history-details-actor-uuid",
                    Map.of("%uuid%", actorUuid.toString())));
        }
        if (subjectUuid != null) {
            details.append(messages.msg("history-details-subject-uuid",
                    Map.of("%uuid%", subjectUuid.toString())));
        }
        if (previousState != null) {
            String state = stateName(messages, previousState);
            if (newState != null) {
                state += messages.msg("history-details-state-transition",
                        Map.of("%state%", stateName(messages, newState)));
            }
            details.append(messages.msg("history-details-state", Map.of("%state%", state)));
        }
        return details.toString();
    }

    private static String stateName(MessageService messages, boolean locked) {
        return messages.msg(locked ? "history-state-locked" : "history-state-unlocked");
    }
}
