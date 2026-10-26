package org.vwtfafa.lockEnd.commands;

import net.kyori.adventure.text.Component;
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
     * composed as components so user-controlled values stay literal and never
     * get reparsed as MiniMessage formatting.
     */
    public Component display(MessageService messages) {
        Component details = buildDetails(messages);
        return messages.message("history-entry", Map.of(
                "%timestamp%", FORMAT.format(timestamp),
                "%action%", action,
                "%actor%", actor,
                "%source%", source,
                "%result%", result), Map.of("%details%", details));
    }

    private Component buildDetails(MessageService messages) {
        Component details = Component.empty();
        if (actorUuid != null) {
            details = details.append(messages.message("history-details-actor-uuid",
                    Map.of("%uuid%", actorUuid.toString())));
        }
        if (subjectUuid != null) {
            details = details.append(messages.message("history-details-subject-uuid",
                    Map.of("%uuid%", subjectUuid.toString())));
        }
        if (previousState != null) {
            Component state = messages.message(stateKey(previousState), Map.of());
            if (newState != null) {
                state = state.append(messages.message("history-details-state-transition", Map.of(),
                        Map.of("%state%", messages.message(stateKey(newState), Map.of()))));
            }
            details = details.append(messages.message("history-details-state", Map.of(),
                    Map.of("%state%", state)));
        }
        return details;
    }

    private static String stateKey(boolean locked) {
        return locked ? "history-state-locked" : "history-state-unlocked";
    }
}
