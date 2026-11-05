package org.vwtfafa.lockend.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.vwtfafa.lockend.core.EndDimensionScope;
import org.vwtfafa.lockend.core.LockBypassChecker;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Reads and writes the Fabric mod's settings file
 * ({@code config/lock-end.json}) holding the human-editable settings. The
 * persisted lock state lives in a separate file owned by
 * {@link JsonLockStateRepository}; the settings file is write-through on
 * every boot and mutable through the {@code setLockReason} and
 * {@link #reload} paths. Guarding options: the End dimension scope
 * ({@code endWorlds}; see {@link EndDimensionScope}) and the non-player
 * entity block ({@code blockEntities}). Schedule notification options
 * ({@code countdown}, {@code preview}) mirror the Paper plugin defaults.
 */
final class FabricEndLockConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DEFAULT_REASON = "Maintenance";
    private static final String CONFIG_FILE_NAME = "lock-end.json";
    // Countdown and preview defaults mirror the Paper plugin's config.yml so
    // both platforms behave identically on a fresh installation.
    private static final boolean DEFAULT_COUNTDOWN_ENABLED = true;
    private static final long DEFAULT_COUNTDOWN_INTERVAL = 10L;
    private static final long DEFAULT_COUNTDOWN_START_BEFORE = 300L;
    private static final boolean DEFAULT_PREVIEW_ENABLED = false;
    private static final long DEFAULT_PREVIEW_SECONDS = 30L;
    // History, statistics and audit-logging defaults also mirror the Paper
    // plugin's config.yml.
    private static final boolean DEFAULT_STATS_ENABLED = true;
    private static final int DEFAULT_HISTORY_MAX_ENTRIES = 1000;
    private static final int DEFAULT_HISTORY_RETENTION_DAYS = 30;
    private static final boolean DEFAULT_LOGGING_ENABLED = true;
    private static final String DEFAULT_LOG_FILE = "EndLock.log";
    private static final boolean DEFAULT_LOG_ATTEMPTS = true;
    private static final int DEFAULT_RATE_LIMIT_SECONDS = 5;
    private static final LockBypassChecker EMPTY_BYPASS_CHECKER =
            new LockBypassChecker(List.of(), List.of(), List.of());
    private static final EndDimensionScope DEFAULT_SCOPE = new EndDimensionScope(List.of());

    private final Path file;
    private String lockReason;
    private final LockBypassChecker bypassChecker;
    private final EndDimensionScope endScope;
    private final boolean blockEntities;
    private final boolean countdownEnabled;
    private final long countdownIntervalSeconds;
    private final long countdownStartBeforeSeconds;
    private final boolean previewEnabled;
    private final long previewSeconds;
    private final boolean statsEnabled;
    private final int historyMaxEntries;
    private final int historyRetentionDays;
    private final boolean loggingEnabled;
    private final String logFile;
    private final boolean logAttempts;
    private final int logRateLimitSeconds;
    private final String parseFailure;

    private FabricEndLockConfig(
            Path file,
            String lockReason,
            LockBypassChecker bypassChecker,
            EndDimensionScope endScope,
            boolean blockEntities,
            boolean countdownEnabled,
            long countdownIntervalSeconds,
            long countdownStartBeforeSeconds,
            boolean previewEnabled,
            long previewSeconds,
            boolean statsEnabled,
            int historyMaxEntries,
            int historyRetentionDays,
            boolean loggingEnabled,
            String logFile,
            boolean logAttempts,
            int logRateLimitSeconds,
            String parseFailure) {
        this.file = file;
        this.lockReason = lockReason;
        this.bypassChecker = bypassChecker;
        this.endScope = endScope;
        this.blockEntities = blockEntities;
        this.countdownEnabled = countdownEnabled;
        this.countdownIntervalSeconds = countdownIntervalSeconds;
        this.countdownStartBeforeSeconds = countdownStartBeforeSeconds;
        this.previewEnabled = previewEnabled;
        this.previewSeconds = previewSeconds;
        this.statsEnabled = statsEnabled;
        this.historyMaxEntries = historyMaxEntries;
        this.historyRetentionDays = historyRetentionDays;
        this.loggingEnabled = loggingEnabled;
        this.logFile = logFile;
        this.logAttempts = logAttempts;
        this.logRateLimitSeconds = logRateLimitSeconds;
        this.parseFailure = parseFailure;
    }

    /**
     * Loads the settings from the given file, creating a fresh default file
     * when it does not exist. Unparsable files fall back to defaults instead
     * of failing the mod boot; a backup copy of the offending file is kept
     * next to it and the recovery is reported via {@link #parseFailureMessage()}
     * so callers can surface it.
     *
     * @param configDirectory the directory holding the settings file
     * @return the loaded settings; never {@code null}
     */
    static FabricEndLockConfig load(Path configDirectory) {
        Path file = configDirectory.resolve(CONFIG_FILE_NAME);
        if (!Files.exists(file)) {
            return writeDefaults(file, null);
        }
        try {
            String json = Files.readString(file);
            StoredConfig stored = GSON.fromJson(json, StoredConfig.class);
            if (stored == null) {
                stored = new StoredConfig();
            }
            return new FabricEndLockConfig(
                    file,
                    stored.reasonOrDefault(),
                    stored.bypassChecker(),
                    stored.endScopeOrDefault(),
                    stored.blockEntitiesOrDefault(),
                    stored.countdownEnabledOrDefault(),
                    stored.countdownIntervalOrDefault(),
                    stored.countdownStartBeforeOrDefault(),
                    stored.previewEnabledOrDefault(),
                    stored.previewSecondsOrDefault(),
                    stored.statsEnabledOrDefault(),
                    stored.historyMaxEntriesOrDefault(),
                    stored.historyRetentionDaysOrDefault(),
                    stored.loggingEnabledOrDefault(),
                    stored.logFileOrDefault(),
                    stored.logAttemptsOrDefault(),
                    stored.rateLimitSecondsOrDefault(),
                    null);
        } catch (IOException | JsonParseException exception) {
            backupCorruptFile(file, exception);
            return writeDefaults(file, recoveryNotice(exception));
        }
    }

    /**
     * Reloads the settings from the same file, applying the same
     * corrupt-file recovery rules as {@link #load(Path)}. Assignments replace
     * the boot settings, so callers must rewire the consumers (lock state
     * reason supplier, guard rules) with the returned instance.
     *
     * @return the freshly loaded settings; never {@code null}
     */
    FabricEndLockConfig reload() {
        return load(file.getParent());
    }

    private static FabricEndLockConfig writeDefaults(Path file, String parseFailure) {
        FabricEndLockConfig defaults = new FabricEndLockConfig(
                file, DEFAULT_REASON, EMPTY_BYPASS_CHECKER, DEFAULT_SCOPE, true,
                DEFAULT_COUNTDOWN_ENABLED, DEFAULT_COUNTDOWN_INTERVAL, DEFAULT_COUNTDOWN_START_BEFORE,
                DEFAULT_PREVIEW_ENABLED, DEFAULT_PREVIEW_SECONDS,
                DEFAULT_STATS_ENABLED, DEFAULT_HISTORY_MAX_ENTRIES, DEFAULT_HISTORY_RETENTION_DAYS,
                DEFAULT_LOGGING_ENABLED, DEFAULT_LOG_FILE, DEFAULT_LOG_ATTEMPTS,
                DEFAULT_RATE_LIMIT_SECONDS, parseFailure);
        defaults.save();
        return defaults;
    }

    private static String recoveryNotice(Exception cause) {
        return "lock-end.json could not be read ("
                + java.util.Objects.toString(cause.getMessage(), "no details")
                + "); it was kept as " + CONFIG_FILE_NAME + ".corrupt and rebuilt with defaults";
    }

    /**
     * @return the configured lock reason; never {@code null}
     */
    String lockReason() {
        return lockReason;
    }

    /**
     * @return the configured bypass (whitelist) rules; never {@code null}
     */
    LockBypassChecker bypassChecker() {
        return bypassChecker;
    }

    /**
     * @return the configured End dimension scope; never {@code null}
     */
    EndDimensionScope endScope() {
        return endScope;
    }

    /**
     * @return {@code true} if non-player entities may not enter the guarded
     *         End while the lock is active
     */
    boolean blockEntities() {
        return blockEntities;
    }

    /**
     * @return {@code true} if periodic countdown announcements run for a
     *         pending scheduled action (default like the Paper plugin)
     */
    boolean countdownEnabled() {
        return countdownEnabled;
    }

    /**
     * @return the minimum seconds between countdown announcements (at least 1)
     */
    long countdownIntervalSeconds() {
        return countdownIntervalSeconds;
    }

    /**
     * @return countdown announcements only run while remaining seconds are at
     *         most this value
     */
    long countdownStartBeforeSeconds() {
        return countdownStartBeforeSeconds;
    }

    /**
     * @return {@code true} if the one-shot preview notification runs shortly
     *         before a due scheduled action (default off, like Paper)
     */
    boolean previewEnabled() {
        return previewEnabled;
    }

    /**
     * @return the preview notification window in seconds
     */
    long previewSeconds() {
        return previewSeconds;
    }

    /**
     * @return {@code true} if the operational statistics counters record
     *         events (default like the Paper plugin)
     */
    boolean statsEnabled() {
        return statsEnabled;
    }

    /**
     * @return the maximum retained history entries (at least 1)
     */
    int historyMaxEntries() {
        return historyMaxEntries;
    }

    /**
     * @return the history retention window in days; non-positive keeps
     *         entries forever, exactly like the Paper plugin
     */
    int historyRetentionDays() {
        return historyRetentionDays;
    }

    /**
     * @return {@code true} if the audit log file is written
     */
    boolean loggingEnabled() {
        return loggingEnabled;
    }

    /**
     * @return the audit log file name, created inside
     *         {@code config/logs/}
     */
    String logFile() {
        return logFile;
    }

    /**
     * @return {@code true} if blocked access attempts are logged
     */
    boolean logAttempts() {
        return logAttempts;
    }

    /**
     * @return the minimum seconds between two logged attempts per player
     */
    int logRateLimitSeconds() {
        return logRateLimitSeconds;
    }

    /**
     * @return a human-readable recovery notice when the last load fell back
     *         to defaults, or {@code null} when the file was read cleanly
     */
    String parseFailureMessage() {
        return parseFailure;
    }

    /**
     * Updates the lock reason at runtime and writes the settings file, so the
     * change survives a restart. The live reason supplier resolves this field
     * on every call, so status and denial feedback switch immediately.
     *
     * @param reason the new reason; must not be {@code null} or blank
     * @throws IllegalArgumentException if the reason is {@code null} or blank
     */
    void setLockReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        this.lockReason = reason.trim();
        save();
    }

    /**
     * Restores the built-in default lock reason and writes the settings file,
     * matching what a fresh default file contains.
     */
    void clearReason() {
        this.lockReason = DEFAULT_REASON;
        save();
    }

    /**
     * Writes the current settings back to disk.
     */
    void save() {
        StoredConfig stored = new StoredConfig();
        stored.lockReason = lockReason;
        stored.endWorlds = endScope.configuredEnds();
        stored.blockEntities = blockEntities;
        if (bypassChecker != null) {
            stored.playerWhitelist = bypassChecker.playerNames();
            stored.uuidWhitelist = bypassChecker.uuids();
            stored.worldWhitelist = bypassChecker.worlds();
        }
        stored.countdown = new StoredConfig.CountdownSettings(countdownEnabled,
                countdownIntervalSeconds, countdownStartBeforeSeconds);
        stored.preview = new StoredConfig.PreviewSettings(previewEnabled, previewSeconds);
        stored.stats = new StoredConfig.StatsSettings(statsEnabled);
        stored.history = new StoredConfig.HistorySettings(
                historyMaxEntries, historyRetentionDays);
        stored.logging = new StoredConfig.LoggingSettings(
                loggingEnabled, logFile, logAttempts, logRateLimitSeconds);
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(stored));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write the Lock-End settings file", exception);
        }
    }

    private static void backupCorruptFile(Path file, Exception cause) {
        try {
            Path backup = file.resolveSibling(CONFIG_FILE_NAME + ".corrupt");
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveFailure) {
            addSuppressedQuietly(moveFailure, cause);
        }
    }

    private static void addSuppressedQuietly(Exception suppressed, Exception primary) {
        if (primary != suppressed) {
            primary.addSuppressed(suppressed);
        }
    }

    /**
     * JSON wire format of the settings file.
     */
    private static final class StoredConfig {
        String lockReason;
        List<String> endWorlds;
        Boolean blockEntities;
        List<String> playerWhitelist;
        List<String> uuidWhitelist;
        List<String> worldWhitelist;
        CountdownSettings countdown;
        PreviewSettings preview;
        StatsSettings stats;
        HistorySettings history;
        LoggingSettings logging;

        String reasonOrDefault() {
            if (lockReason == null || lockReason.isBlank()) {
                return DEFAULT_REASON;
            }
            return lockReason;
        }

        LockBypassChecker bypassChecker() {
            return new LockBypassChecker(
                    listOrEmpty(playerWhitelist),
                    listOrEmpty(uuidWhitelist),
                    listOrEmpty(worldWhitelist));
        }

        EndDimensionScope endScopeOrDefault() {
            return new EndDimensionScope(listOrEmpty(endWorlds));
        }

        boolean blockEntitiesOrDefault() {
            // The Paper default blocks entities: true, also when a stored
            // file predates this option.
            return blockEntities == null || blockEntities;
        }

        boolean countdownEnabledOrDefault() {
            // The Paper default enables the countdown; files predating the
            // option keep that behavior.
            return countdown == null || countdown.enabled == null || countdown.enabled;
        }

        long countdownIntervalOrDefault() {
            if (countdown == null || countdown.interval == null || countdown.interval < 1) {
                return DEFAULT_COUNTDOWN_INTERVAL;
            }
            return countdown.interval;
        }

        long countdownStartBeforeOrDefault() {
            if (countdown == null || countdown.startBefore == null || countdown.startBefore < 1) {
                return DEFAULT_COUNTDOWN_START_BEFORE;
            }
            return countdown.startBefore;
        }

        boolean previewEnabledOrDefault() {
            return preview != null && preview.enabled != null && preview.enabled;
        }

        long previewSecondsOrDefault() {
            if (preview == null || preview.seconds == null || preview.seconds < 1) {
                return DEFAULT_PREVIEW_SECONDS;
            }
            return preview.seconds;
        }

        boolean statsEnabledOrDefault() {
            // The Paper default records stats: a file predating the option
            // keeps that behavior.
            return stats == null || stats.enabled == null || stats.enabled;
        }

        int historyMaxEntriesOrDefault() {
            if (history == null || history.maxEntries == null || history.maxEntries < 1) {
                return DEFAULT_HISTORY_MAX_ENTRIES;
            }
            return history.maxEntries;
        }

        int historyRetentionDaysOrDefault() {
            if (history == null || history.retentionDays == null) {
                return DEFAULT_HISTORY_RETENTION_DAYS;
            }
            // Non-positive values keep entries forever, like the Paper plugin.
            return history.retentionDays;
        }

        boolean loggingEnabledOrDefault() {
            return logging == null || logging.enabled == null || logging.enabled;
        }

        String logFileOrDefault() {
            if (logging == null || logging.logFile == null || logging.logFile.isBlank()) {
                return DEFAULT_LOG_FILE;
            }
            return logging.logFile.trim();
        }

        boolean logAttemptsOrDefault() {
            return logging == null || logging.logAttempts == null || logging.logAttempts;
        }

        int rateLimitSecondsOrDefault() {
            if (logging == null || logging.rateLimitSeconds == null || logging.rateLimitSeconds < 0) {
                return DEFAULT_RATE_LIMIT_SECONDS;
            }
            return logging.rateLimitSeconds;
        }

        private static List<String> listOrEmpty(List<String> values) {
            return values == null ? List.of() : values;
        }

        /**
         * JSON wire format of the countdown notification settings.
         */
        private static final class CountdownSettings {
            Boolean enabled;
            Long interval;
            Long startBefore;

            CountdownSettings() {
            }

            CountdownSettings(boolean enabled, long interval, long startBefore) {
                this.enabled = enabled;
                this.interval = interval;
                this.startBefore = startBefore;
            }
        }

        /**
         * JSON wire format of the preview notification settings.
         */
        private static final class PreviewSettings {
            Boolean enabled;
            Long seconds;

            PreviewSettings() {
            }

            PreviewSettings(boolean enabled, long seconds) {
                this.enabled = enabled;
                this.seconds = seconds;
            }
        }

        /**
         * JSON wire format of the statistics settings.
         */
        private static final class StatsSettings {
            Boolean enabled;

            StatsSettings() {
            }

            StatsSettings(boolean enabled) {
                this.enabled = enabled;
            }
        }

        /**
         * JSON wire format of the history retention settings.
         */
        private static final class HistorySettings {
            Integer maxEntries;
            Integer retentionDays;

            HistorySettings() {
            }

            HistorySettings(int maxEntries, int retentionDays) {
                this.maxEntries = maxEntries;
                this.retentionDays = retentionDays;
            }
        }

        /**
         * JSON wire format of the audit logging settings.
         */
        private static final class LoggingSettings {
            Boolean enabled;
            String logFile;
            Boolean logAttempts;
            Integer rateLimitSeconds;

            LoggingSettings() {
            }

            LoggingSettings(boolean enabled, String logFile, boolean logAttempts,
                            int rateLimitSeconds) {
                this.enabled = enabled;
                this.logFile = logFile;
                this.logAttempts = logAttempts;
                this.rateLimitSeconds = rateLimitSeconds;
            }
        }
    }
}
