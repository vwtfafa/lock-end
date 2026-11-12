package org.vwtfafa.lockend.core.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Shared persistence of the operational statistics counters
 * ({@code config/lock-end.stats.json}), mirroring the Paper plugin's config
 * {@code stats.*} section. Missing or corrupt files boot with zeros.
 */
public final class JsonStatsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping().create();
    private static final Path FILE = Path.of("lock-end.stats.json");

    private final Path file;
    private final Logger logger;

    /**
     * Creates the store around the mod's config directory.
     *
     * @param configDirectory the directory holding the stats file
     * @param logger          log sink for read failures
     */
    public JsonStatsStore(Path configDirectory, Logger logger) {
        this.file = configDirectory.resolve(FILE);
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Writes the current counters.
     *
     * @param counts the counters to persist
     * @return {@code true} when the write completed
     */
    public boolean save(StatsCounts counts) {
        StoredStats stored = new StoredStats();
        stored.lockCount = counts.lockCount();
        stored.unlockCount = counts.unlockCount();
        stored.blockedCount = counts.blockedCount();
        stored.evacuatedCount = counts.evacuatedCount();
        return JsonFileIO.writeAtomically(file, GSON.toJson(stored), logger);
    }

    /**
     * Reads the counters. Missing and corrupt files both read as zeros.
     *
     * @return the counters; never {@code null}
     */
    public StatsCounts load() {
        try {
            if (!Files.exists(file)) {
                return StatsCounts.EMPTY;
            }
            String json = Files.readString(file);
            StoredStats stored = GSON.fromJson(json, StoredStats.class);
            if (stored == null) {
                return StatsCounts.EMPTY;
            }
            return new StatsCounts(
                    stored.lockCount, stored.unlockCount,
                    stored.blockedCount, stored.evacuatedCount);
        } catch (IOException | JsonParseException exception) {
            logger.warn(
                    "lock-end.stats.json could not be read; starting with zero counts", exception);
            return StatsCounts.EMPTY;
        }
    }

    /**
     * Immutable counter snapshot.
     *
     * @param lockCount      locks performed
     * @param unlockCount    unlocks performed
     * @param blockedCount   blocked access attempts
     * @param evacuatedCount players moved out by evacuations
     */
    public record StatsCounts(int lockCount, int unlockCount, int blockedCount, int evacuatedCount) {
        /** Zeroed counters used when the file is missing or unreadable. */
        public static final StatsCounts EMPTY = new StatsCounts(0, 0, 0, 0);
    }

    private static final class StoredStats {
        int lockCount;
        int unlockCount;
        int blockedCount;
        int evacuatedCount;
    }
}
