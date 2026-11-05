package org.vwtfafa.lockend.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Path;

/**
 * Fabric persistence of the operational statistics counters
 * ({@code config/lock-end.stats.json}), mirroring the Paper plugin's config
 * {@code stats.*} section. Missing or corrupt files boot with zeros.
 */
final class JsonStatsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping().create();
    private static final Path FILE = Path.of("lock-end.stats.json");

    private final Path file;

    /**
     * Creates the store around the mod's config directory.
     */
    JsonStatsStore(Path configDirectory) {
        this.file = configDirectory.resolve(FILE);
    }

    /**
     * Writes the current counters.
     *
     * @param counts the counters to persist
     * @return {@code true} when the write completed
     */
    boolean save(StatsCounts counts) {
        StoredStats stored = new StoredStats();
        stored.lockCount = counts.lockCount();
        stored.unlockCount = counts.unlockCount();
        stored.blockedCount = counts.blockedCount();
        stored.evacuatedCount = counts.evacuatedCount();
        return JsonFileIO.writeAtomically(file, GSON.toJson(stored), LockEndFabricMod.LOGGER);
    }

    /**
     * Reads the counters. Missing and corrupt files both read as zeros.
     *
     * @return the counters; never {@code null}
     */
    StatsCounts load() {
        try {
            if (!java.nio.file.Files.exists(file)) {
                return StatsCounts.EMPTY;
            }
            String json = java.nio.file.Files.readString(file);
            StoredStats stored = GSON.fromJson(json, StoredStats.class);
            if (stored == null) {
                return StatsCounts.EMPTY;
            }
            return new StatsCounts(
                    stored.lockCount, stored.unlockCount,
                    stored.blockedCount, stored.evacuatedCount);
        } catch (java.io.IOException | com.google.gson.JsonParseException exception) {
            LockEndFabricMod.LOGGER.warn(
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
    record StatsCounts(int lockCount, int unlockCount, int blockedCount, int evacuatedCount) {
        private static final StatsCounts EMPTY = new StatsCounts(0, 0, 0, 0);
    }

    private static final class StoredStats {
        int lockCount;
        int unlockCount;
        int blockedCount;
        int evacuatedCount;
    }
}
