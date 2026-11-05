package org.vwtfafa.lockend.core.history;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * One recorded lock/unlock/evacuation event, mirroring the Paper plugin's
 * {@code HistoryEntry} schema field for field. The record is a plain data
 * carrier without platform imports so every adapter can persist and export
 * the same information shape.
 *
 * <p>Wire format notes: {@code timestamp} is always an absolute
 * {@link Instant}; {@code actor} is a display name (console presets are
 * platform-owned); {@code actorUuid} and {@code subjectUuid} are optional.
 * {@code previousState}/{@code newState} are optional booleans; evacuation
 * entries carry {@code result} outcome codes (for example {@code MOVED},
 * {@code SKIPPED_BYPASS}, {@code FAILED}).</p>
 *
 * @param timestamp     when the event happened
 * @param actor         display name of the actor (player, console, System)
 * @param actorUuid     UUID of the acting entity, or {@code null}
 * @param action        action identifier (lock, unlock, UNDO, EVACUATE, ...)
 * @param source        action source beyond the action id (command category,
 *                      "AUTOMATIC"/"MANUAL -> world" for evacuations)
 * @param subjectUuid   UUID of the affected player when one exists
 * @param result        outcome identifier, {@code RECORDED} by default
 * @param previousState previous lock state, or {@code null} if not applicable
 * @param newState      resulting lock state, or {@code null} if not applicable
 */
public record HistoryEntry(Instant timestamp, String actor, UUID actorUuid, String action,
                           String source, UUID subjectUuid, String result,
                           Boolean previousState, Boolean newState) {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    /**
     * Creates a plain recorded entry for a state change without extra
     * identity or outcome details.
     */
    public HistoryEntry(Instant timestamp, String actor, String action, String source,
                        boolean previousState) {
        this(timestamp, actor, null, action, source, null, "RECORDED", previousState, null);
    }

    /**
     * @return the entry's UTC display timestamp, matching the Paper plugin's
     *         {@code history-entry} rendering
     */
    public String displayTimestamp() {
        return FORMAT.format(timestamp);
    }
}
