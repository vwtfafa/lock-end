package org.vwtfafa.lockend.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * Pure schedule helper logic shared by all platforms: strict schedule time
 * parsing, remaining-time computation, compact duration formatting and the
 * supported scheduled action names.
 *
 * <p>This class contains no platform dependency; platform schedule managers
 * own the scheduling execution and persistence and delegate these helpers.</p>
 */
public final class ScheduleSupport {

    /**
     * Strict schedule format: impossible dates like 2026-02-30 are rejected
     * instead of being silently rounded to the end of the month. Shared
     * constant so every platform renders and parses the same wire format.
     */
    public static final DateTimeFormatter SCHEDULE_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    private ScheduleSupport() {
    }

    /**
     * Parses a schedule time in the shared schedule format.
     *
     * @param value the raw persisted or typed value; may be {@code null}
     * @return the parsed time, or {@code null} when blank or malformed
     */
    public static LocalDateTime parseScheduleTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, SCHEDULE_FORMAT);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Remaining whole seconds for a captured target. Taking the target as a
     * parameter avoids a second field read after a concurrent schedule clear;
     * a {@code null} target yields {@code -1} (no pending action) and lapsed
     * targets clamp to 0.
     *
     * @param target the captured schedule target
     * @param now    the reference point the caller captured
     * @return remaining whole seconds, or {@code -1} for a missing target
     */
    public static long remainingSeconds(LocalDateTime target, ZonedDateTime now) {
        if (target == null) {
            return -1;
        }
        return Math.max(0, Duration.between(now, target.atZone(now.getZone())).getSeconds());
    }

    /**
     * Formats a duration as a compact human readable string.
     *
     * @param seconds the duration in whole seconds
     * @return the formatted duration; never {@code null}
     */
    public static String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + remainingSeconds + "s";
        return remainingSeconds + "s";
    }

    /**
     * Tells whether the action name is an explicitly supported schedule
     * action.
     *
     * <p>Only explicit lock and unlock actions are supported. Unknown values
     * must never be coerced to unlock, because a typo in the config would
     * then open a locked End instead of keeping it closed.</p>
     *
     * @param action the action name to check; may be {@code null}
     * @return {@code true} for {@code lock} and {@code unlock},
     *         case-insensitively
     */
    public static boolean isSupportedAction(String action) {
        return "lock".equalsIgnoreCase(action) || "unlock".equalsIgnoreCase(action);
    }
}
