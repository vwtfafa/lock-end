package org.vwtfafa.lockEnd.commands;

import net.kyori.adventure.text.Component;
import org.vwtfafa.lockEnd.MessageService;
import org.vwtfafa.lockend.core.history.HistoryEntry;

import java.util.Map;

/**
 * Adventure rendering for core {@link HistoryEntry} values. Keeps MiniMessage
 * composition on the Paper side while the shared record stays platform-free.
 */
public final class HistoryEntryDisplay {
    private HistoryEntryDisplay() {
    }

    /**
     * Renders the entry through the language files. Optional details are
     * composed as components so user-controlled values stay literal and never
     * get reparsed as MiniMessage formatting.
     */
    public static Component display(HistoryEntry entry, MessageService messages) {
        Component details = buildDetails(entry, messages);
        return messages.message("history-entry", Map.of(
                "%timestamp%", entry.displayTimestamp(),
                "%action%", entry.action(),
                "%actor%", entry.actor(),
                "%source%", entry.source(),
                "%result%", entry.result()), Map.of("%details%", details));
    }

    private static Component buildDetails(HistoryEntry entry, MessageService messages) {
        Component details = Component.empty();
        if (entry.actorUuid() != null) {
            details = details.append(messages.message("history-details-actor-uuid",
                    Map.of("%uuid%", entry.actorUuid().toString())));
        }
        if (entry.subjectUuid() != null) {
            details = details.append(messages.message("history-details-subject-uuid",
                    Map.of("%uuid%", entry.subjectUuid().toString())));
        }
        if (entry.previousState() != null) {
            Component state = messages.message(stateKey(entry.previousState()), Map.of());
            if (entry.newState() != null) {
                state = state.append(messages.message("history-details-state-transition", Map.of(),
                        Map.of("%state%", messages.message(stateKey(entry.newState()), Map.of()))));
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
