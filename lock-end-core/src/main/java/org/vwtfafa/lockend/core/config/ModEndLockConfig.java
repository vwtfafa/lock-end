package org.vwtfafa.lockend.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.vwtfafa.lockend.core.EndDimensionScope;
import org.vwtfafa.lockend.core.LockBypassChecker;
import org.vwtfafa.lockend.core.json.JsonLockStateRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/**
 * Reads and writes the Fabric/NeoForge mod settings file
 * ({@code config/lock-end.json}) holding the human-editable settings. The
 * persisted lock state lives in a separate file owned by
 * {@link JsonLockStateRepository}; the settings file is write-through on
 * every boot and mutable through the {@code setLockReason} and
 * {@link #reload} paths. Guarding options: the End dimension scope
 * ({@code endWorlds}; see {@link EndDimensionScope}) and the non-player
 * entity block ({@code blockEntities}). Schedule notification options
 * ({@code countdown}, {@code preview}) mirror the Paper plugin defaults.
 */
public final class ModEndLockConfig {

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

    /**
     * Player-interaction defaults. The broadcast, sound, grace-period and
     * join-notification values mirror the Paper plugin's config.yml; the
     * evacuation default is deliberately {@code true} on the mods so the
     * previously documented always-evacuate guard behavior survives the
     * parity change for existing installations.
     */
    private static final RuntimeSections DEFAULT_SECTIONS = new RuntimeSections(
            true, true, true,
            false, "BLOCK_ANVIL_LAND", 1.0f, 1.0f,
            false, 10,
            true, 10L, "", true,
            false);

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
    private final RuntimeSections sections;
    private final String parseFailure;

    private ModEndLockConfig(
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
            RuntimeSections sections,
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
        this.sections = sections;
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
    public static ModEndLockConfig load(Path configDirectory) {
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
            return new ModEndLockConfig(
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
                    stored.sectionsOrDefault(),
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
    public ModEndLockConfig reload() {
        return load(file.getParent());
    }

    private static ModEndLockConfig writeDefaults(Path file, String parseFailure) {
        ModEndLockConfig defaults = new ModEndLockConfig(
                file, DEFAULT_REASON, EMPTY_BYPASS_CHECKER, DEFAULT_SCOPE, true,
                DEFAULT_COUNTDOWN_ENABLED, DEFAULT_COUNTDOWN_INTERVAL, DEFAULT_COUNTDOWN_START_BEFORE,
                DEFAULT_PREVIEW_ENABLED, DEFAULT_PREVIEW_SECONDS,
                DEFAULT_STATS_ENABLED, DEFAULT_HISTORY_MAX_ENTRIES, DEFAULT_HISTORY_RETENTION_DAYS,
                DEFAULT_LOGGING_ENABLED, DEFAULT_LOG_FILE, DEFAULT_LOG_ATTEMPTS,
                DEFAULT_RATE_LIMIT_SECONDS, DEFAULT_SECTIONS, parseFailure);
        defaults.save();
        return defaults;
    }

    private static String recoveryNotice(Exception cause) {
        return "lock-end.json could not be read ("
                + Objects.toString(cause.getMessage(), "no details")
                + "); it was kept as " + CONFIG_FILE_NAME + ".corrupt and rebuilt with defaults";
    }

    /**
     * @return the configured lock reason; never {@code null}
     */
    public String lockReason() {
        return lockReason;
    }

    /**
     * @return the configured bypass (whitelist) rules; never {@code null}
     */
    public LockBypassChecker bypassChecker() {
        return bypassChecker;
    }

    /**
     * @return the configured End dimension scope; never {@code null}
     */
    public EndDimensionScope endScope() {
        return endScope;
    }

    /**
     * @return {@code true} if non-player entities may not enter the guarded
     *         End while the lock is active
     */
    public boolean blockEntities() {
        return blockEntities;
    }

    /**
     * @return {@code true} if periodic countdown announcements run for a
     *         pending scheduled action (default like the Paper plugin)
     */
    public boolean countdownEnabled() {
        return countdownEnabled;
    }

    /**
     * @return the minimum seconds between countdown announcements (at least 1)
     */
    public long countdownIntervalSeconds() {
        return countdownIntervalSeconds;
    }

    /**
     * @return countdown announcements only run while remaining seconds are at
     *         most this value
     */
    public long countdownStartBeforeSeconds() {
        return countdownStartBeforeSeconds;
    }

    /**
     * @return {@code true} if the one-shot preview notification runs shortly
     *         before a due scheduled action (default off, like Paper)
     */
    public boolean previewEnabled() {
        return previewEnabled;
    }

    /**
     * @return the preview notification window in seconds
     */
    public long previewSeconds() {
        return previewSeconds;
    }

    /**
     * @return {@code true} if the operational statistics counters record
     *         events (default like the Paper plugin)
     */
    public boolean statsEnabled() {
        return statsEnabled;
    }

    /**
     * @return the maximum retained history entries (at least 1)
     */
    public int historyMaxEntries() {
        return historyMaxEntries;
    }

    /**
     * @return the history retention window in days; non-positive keeps
     *         entries forever, exactly like the Paper plugin
     */
    public int historyRetentionDays() {
        return historyRetentionDays;
    }

    /**
     * @return {@code true} if the audit log file is written
     */
    public boolean loggingEnabled() {
        return loggingEnabled;
    }

    /**
     * @return the audit log file name, created inside
     *         {@code config/logs/}
     */
    public String logFile() {
        return logFile;
    }

    /**
     * @return {@code true} if blocked access attempts are logged
     */
    public boolean logAttempts() {
        return logAttempts;
    }

    /**
     * @return the minimum seconds between two logged attempts per player
     */
    public int logRateLimitSeconds() {
        return logRateLimitSeconds;
    }

    /**
     * @return {@code true} if lock/unlock state changes are broadcast
     */
    public boolean broadcastEnabled() {
        return sections.broadcastEnabled();
    }

    /**
     * @return {@code true} if broadcasts use the action bar (chat otherwise)
     */
    public boolean broadcastUseActionbar() {
        return sections.broadcastUseActionbar();
    }

    /**
     * @return {@code true} if every player is notified (admins only otherwise)
     */
    public boolean broadcastNotifyAll() {
        return sections.broadcastNotifyAll();
    }

    /**
     * @return {@code true} if access denial plays a sound
     */
    public boolean soundEffectsEnabled() {
        return sections.soundEffectsEnabled();
    }

    /**
     * @return the configured denial sound value (enum style or key)
     */
    public String soundEffectSound() {
        return sections.soundEffectSound();
    }

    /**
     * @return the denial sound volume (0..2)
     */
    public float soundEffectVolume() {
        return sections.soundEffectVolume();
    }

    /**
     * @return the denial sound pitch (0..2)
     */
    public float soundEffectPitch() {
        return sections.soundEffectPitch();
    }

    /**
     * @return {@code true} if a grace period delays lock enforcement
     */
    public boolean gracePeriodEnabled() {
        return sections.gracePeriodEnabled();
    }

    /**
     * @return the grace period duration in seconds
     */
    public int gracePeriodDuration() {
        return sections.gracePeriodDuration();
    }

    /**
     * @return {@code true} if locking also moves players out of the guarded
     *         End (true on the mods by default, preserving the previous
     *         always-evacuate guard behavior)
     */
    public boolean evacuationEnabled() {
        return sections.evacuationEnabled();
    }

    /**
     * @return seconds the evacuation warning is shown before the move
     */
    public long evacuationWarningSeconds() {
        return sections.evacuationWarningSeconds();
    }

    /**
     * @return the configured evacuation target world key; blank falls back
     *         to the server's overworld
     */
    public String evacuationTargetWorld() {
        return sections.evacuationTargetWorld();
    }

    /**
     * @return {@code true} if bypassing (whitelisted) players are skipped
     */
    public boolean evacuationExcludeBypass() {
        return sections.evacuationExcludeBypass();
    }

    /**
     * @return {@code true} if players joining while the End is locked get a
     *         notification
     */
    public boolean joinNotificationsEnabled() {
        return sections.joinNotificationsEnabled();
    }

    /**
     * @return a human-readable recovery notice when the last load fell back
     *         to defaults, or {@code null} when the file was read cleanly
     */
    public String parseFailureMessage() {
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
    public void setLockReason(String reason) {
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
    public void clearReason() {
        this.lockReason = DEFAULT_REASON;
        save();
    }

    /**
     * Writes the current settings back to disk.
     */
    public void save() {
        StoredConfig stored = new StoredConfig();
        stored.lockReason = lockReason;
        stored.endWorlds = endScope.configuredEnds();
        stored.blockEntities = blockEntities;
        stored.playerWhitelist = bypassChecker.playerNames();
        stored.uuidWhitelist = bypassChecker.uuids();
        stored.worldWhitelist = bypassChecker.worlds();
        stored.countdown = new StoredConfig.CountdownSettings(countdownEnabled,
                countdownIntervalSeconds, countdownStartBeforeSeconds);
        stored.preview = new StoredConfig.PreviewSettings(previewEnabled, previewSeconds);
        stored.stats = new StoredConfig.StatsSettings(statsEnabled);
        stored.history = new StoredConfig.HistorySettings(
                historyMaxEntries, historyRetentionDays);
        stored.logging = new StoredConfig.LoggingSettings(
                loggingEnabled, logFile, logAttempts, logRateLimitSeconds);
        stored.broadcast = new StoredConfig.BroadcastSettings(sections.broadcastEnabled(),
                sections.broadcastUseActionbar(), sections.broadcastNotifyAll());
        stored.soundEffects = new StoredConfig.SoundEffectSettings(sections.soundEffectsEnabled(),
                sections.soundEffectSound(), sections.soundEffectVolume(), sections.soundEffectPitch());
        stored.gracePeriod = new StoredConfig.GracePeriodSettings(sections.gracePeriodEnabled(),
                sections.gracePeriodDuration());
        stored.evacuation = new StoredConfig.EvacuationSettings(sections.evacuationEnabled(),
                sections.evacuationWarningSeconds(), sections.evacuationTargetWorld(),
                sections.evacuationExcludeBypass());
        stored.joinNotifications = new StoredConfig.JoinNotificationSettings(
                sections.joinNotificationsEnabled());
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
     * Resolved player-interaction sections (broadcast, denial sound, grace
     * period, evacuation, join notification).
     *
     * @param broadcastEnabled        state changes are broadcast
     * @param broadcastUseActionbar   broadcast uses the action bar
     * @param broadcastNotifyAll      every player is notified (admins only
     *                                when {@code false})
     * @param soundEffectsEnabled     access denial plays a sound
     * @param soundEffectSound        configured sound value
     * @param soundEffectVolume       sound volume (0..2)
     * @param soundEffectPitch        sound pitch (0..2)
     * @param gracePeriodEnabled      grace period after locking
     * @param gracePeriodDuration     grace period length in seconds
     * @param evacuationEnabled       locking moves End residents out
     * @param evacuationWarningSeconds warning shown before the move
     * @param evacuationTargetWorld   destination key; blank uses the
     *                                overworld
     * @param evacuationExcludeBypass bypassing players are skipped
     * @param joinNotificationsEnabled locked-End notice on player join
     */
    public record RuntimeSections(
            boolean broadcastEnabled,
            boolean broadcastUseActionbar,
            boolean broadcastNotifyAll,
            boolean soundEffectsEnabled,
            String soundEffectSound,
            float soundEffectVolume,
            float soundEffectPitch,
            boolean gracePeriodEnabled,
            int gracePeriodDuration,
            boolean evacuationEnabled,
            long evacuationWarningSeconds,
            String evacuationTargetWorld,
            boolean evacuationExcludeBypass,
            boolean joinNotificationsEnabled) {
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
        BroadcastSettings broadcast;
        SoundEffectSettings soundEffects;
        GracePeriodSettings gracePeriod;
        EvacuationSettings evacuation;
        JoinNotificationSettings joinNotifications;

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

        /**
         * Resolves the player-interaction sections, applying defaults like
         * the Paper plugin (evacuation defaults to {@code true} on the mods).
         */
        RuntimeSections sectionsOrDefault() {
            BroadcastSettings broadcastValues = broadcast == null
                    ? new BroadcastSettings(true, true, true) : broadcast;
            SoundEffectSettings soundValues = soundEffects == null
                    ? new SoundEffectSettings(false, "BLOCK_ANVIL_LAND", 1.0f, 1.0f)
                    : soundEffects;
            GracePeriodSettings graceValues = gracePeriod == null
                    ? new GracePeriodSettings(false, 10) : gracePeriod;
            EvacuationSettings evacuationValues = evacuation == null
                    ? new EvacuationSettings(true, 10L, "", true) : evacuation;
            JoinNotificationSettings joinValues = joinNotifications == null
                    ? new JoinNotificationSettings(false) : joinNotifications;
            return new RuntimeSections(
                    booleanOrDefault(broadcastValues.enabled, true),
                    booleanOrDefault(broadcastValues.useActionbar, true),
                    booleanOrDefault(broadcastValues.notifyAll, true),
                    booleanOrDefault(soundValues.enabled, false),
                    stringOrDefault(soundValues.sound, "BLOCK_ANVIL_LAND"),
                    floatOrDefault(soundValues.volume, 1.0f),
                    floatOrDefault(soundValues.pitch, 1.0f),
                    booleanOrDefault(graceValues.enabled, false),
                    intOrDefault(graceValues.duration, 10),
                    // Mods default to evacuating: the previous guard behavior
                    // moved living End residents once the lock became active.
                    booleanOrDefault(evacuationValues.enabled, true),
                    longOrDefault(evacuationValues.warningSeconds, 10L),
                    stringOrDefault(evacuationValues.targetWorld, ""),
                    booleanOrDefault(evacuationValues.excludeBypass, true),
                    booleanOrDefault(joinValues.enabled, false));
        }

        private static boolean booleanOrDefault(Boolean value, boolean fallback) {
            return value == null ? fallback : value;
        }

        private static String stringOrDefault(String value, String fallback) {
            return value == null ? fallback : value;
        }

        private static float floatOrDefault(Float value, float fallback) {
            return value == null ? fallback : Math.min(2.0f, Math.max(0.0f, value));
        }

        private static int intOrDefault(Integer value, int fallback) {
            return value == null ? fallback : Math.max(0, value);
        }

        private static long longOrDefault(Long value, long fallback) {
            if (value == null || value < 0) {
                return fallback;
            }
            return value;
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

        /**
         * JSON wire format of the lock-state broadcast settings.
         */
        private static final class BroadcastSettings {
            Boolean enabled;
            Boolean useActionbar;
            Boolean notifyAll;

            BroadcastSettings() {
            }

            BroadcastSettings(boolean enabled, boolean useActionbar, boolean notifyAll) {
                this.enabled = enabled;
                this.useActionbar = useActionbar;
                this.notifyAll = notifyAll;
            }
        }

        /**
         * JSON wire format of the denial-sound settings.
         */
        private static final class SoundEffectSettings {
            Boolean enabled;
            String sound;
            Float volume;
            Float pitch;

            SoundEffectSettings() {
            }

            SoundEffectSettings(boolean enabled, String sound, float volume, float pitch) {
                this.enabled = enabled;
                this.sound = sound;
                this.volume = volume;
                this.pitch = pitch;
            }
        }

        /**
         * JSON wire format of the grace-period settings.
         */
        private static final class GracePeriodSettings {
            Boolean enabled;
            Integer duration;

            GracePeriodSettings() {
            }

            GracePeriodSettings(boolean enabled, int duration) {
                this.enabled = enabled;
                this.duration = duration;
            }
        }

        /**
         * JSON wire format of the evacuation settings.
         */
        private static final class EvacuationSettings {
            Boolean enabled;
            Long warningSeconds;
            String targetWorld;
            Boolean excludeBypass;

            EvacuationSettings() {
            }

            EvacuationSettings(boolean enabled, long warningSeconds,
                               String targetWorld, boolean excludeBypass) {
                this.enabled = enabled;
                this.warningSeconds = warningSeconds;
                this.targetWorld = targetWorld;
                this.excludeBypass = excludeBypass;
            }
        }

        /**
         * JSON wire format of the join-notification settings.
         */
        private static final class JoinNotificationSettings {
            Boolean enabled;

            JoinNotificationSettings() {
            }

            JoinNotificationSettings(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }
}
