package org.vwtfafa.lockend.core.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.vwtfafa.lockend.core.PersistedSchedule;
import org.vwtfafa.lockend.core.ScheduleSupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Shared persistence of the single pending scheduled action, stored as a
 * small JSON document in the mod's config directory
 * ({@code config/lock-end.schedule.json}), separate from the settings and
 * lock state files. Missing, unparsable or malformed data is treated as "no
 * schedule"; corrupted files never schedule anything on boot. I/O happens
 * in the tick / server-thread context of the callers - schedule changes are
 * rare, so a synchronous small file write is acceptable.
 */
public final class JsonScheduleRepository {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String SCHEDULE_FILE_NAME = "lock-end.schedule.json";

    private final Path baseConfigDirectory;

    /**
     * Creates the repository around the mod's config directory; the schedule
     * file name is appended on every access so the path stays resolved
     * against the live directory.
     *
     * @param baseConfigDirectory the directory holding the schedule file
     */
    public JsonScheduleRepository(Path baseConfigDirectory) {
        this.baseConfigDirectory = Objects.requireNonNull(baseConfigDirectory, "baseConfigDirectory");
    }

    /**
     * Loads the persisted schedule. A missing file, corrupt JSON, an empty
     * document or a malformed target time yields the empty schedule - never
     * a guessed default action, because the runtime restore additionally
     * validates the action name.
     *
     * @return the persisted schedule; never {@code null}
     */
    public PersistedSchedule load() {
        Path file = scheduleFile();
        if (!Files.exists(file)) {
            return PersistedSchedule.empty();
        }
        try {
            String json = Files.readString(file);
            StoredSchedule stored = GSON.fromJson(json, StoredSchedule.class);
            if (stored == null || stored.target == null || stored.target.isBlank()) {
                return PersistedSchedule.empty();
            }
            LocalDateTime target = ScheduleSupport.parseScheduleTime(stored.target);
            if (target == null) {
                return PersistedSchedule.empty();
            }
            String action = stored.action == null ? "unlock" : stored.action;
            return new PersistedSchedule(action, target, stored.pausedOrDefault());
        } catch (IOException | JsonParseException exception) {
            // Corrupt schedule files must never schedule anything on boot.
            return PersistedSchedule.empty();
        }
    }

    /**
     * Persists the schedule, writing the target in the shared strict schedule
     * format so the file stays human-editable.
     *
     * @param schedule the schedule to persist; must not be {@code null}
     */
    public void save(PersistedSchedule schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must not be null");
        }
        StoredSchedule stored = new StoredSchedule();
        stored.action = schedule.action();
        stored.target = schedule.target() == null
                ? null
                : schedule.target().format(ScheduleSupport.SCHEDULE_FORMAT);
        stored.paused = schedule.paused();
        try {
            Path file = scheduleFile();
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(stored));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to persist the Lock-End schedule", exception);
        }
    }

    /**
     * Clears the persisted schedule.
     */
    public void clear() {
        save(PersistedSchedule.empty());
    }

    private Path scheduleFile() {
        return baseConfigDirectory.resolve(SCHEDULE_FILE_NAME);
    }

    /**
     * JSON wire format of the persisted schedule.
     */
    private static final class StoredSchedule {
        String action;
        String target;
        Boolean paused;

        boolean pausedOrDefault() {
            // A stored file predating the pause feature resumes counting.
            return paused != null && paused;
        }
    }
}
