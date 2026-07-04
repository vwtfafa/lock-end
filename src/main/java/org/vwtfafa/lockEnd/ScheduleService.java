package org.vwtfafa.lockEnd;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScheduleService {
    private static final Pattern DURATION = Pattern.compile("(\\d+)([dhmws])", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final LockEnd plugin;
    private final File dataFile;
    private FileConfiguration data;
    private Long unlockAtMillis;
    private BukkitTask task;

    public ScheduleService(LockEnd plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        load();
        startTask();
    }

    public void load() {
        if (!dataFile.exists()) {
            return;
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (data.contains("schedule.unlock-at")) {
            unlockAtMillis = data.getLong("schedule.unlock-at");
            if (unlockAtMillis <= System.currentTimeMillis()) {
                unlockAtMillis = null;
                clearSchedule();
            }
        }
    }

    public void save() {
        if (data == null) {
            data = YamlConfiguration.loadConfiguration(dataFile);
        }
        if (unlockAtMillis == null) {
            data.set("schedule.unlock-at", null);
        } else {
            data.set("schedule.unlock-at", unlockAtMillis);
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save schedule: " + e.getMessage());
        }
    }

    public void clearSchedule() {
        unlockAtMillis = null;
        save();
    }

    public boolean hasSchedule() {
        return unlockAtMillis != null;
    }

    public Long getUnlockAtMillis() {
        return unlockAtMillis;
    }

    public Duration getRemaining() {
        if (unlockAtMillis == null) {
            return Duration.ZERO;
        }
        long diff = unlockAtMillis - System.currentTimeMillis();
        return diff > 0 ? Duration.ofMillis(diff) : Duration.ZERO;
    }

    public String formatRemaining(MessageService messages) {
        Duration remaining = getRemaining();
        if (remaining.isZero() || remaining.isNegative()) {
            return messages.raw("remaining-none");
        }
        long days = remaining.toDays();
        long hours = remaining.toHoursPart();
        long minutes = remaining.toMinutesPart();
        if (days > 0) {
            return messages.format("remaining-days", "%days%", String.valueOf(days), "%hours%", String.valueOf(hours));
        }
        if (hours > 0) {
            return messages.format("remaining-hours", "%hours%", String.valueOf(hours), "%minutes%", String.valueOf(minutes));
        }
        return messages.format("remaining-minutes", "%minutes%", String.valueOf(Math.max(1, minutes)));
    }

    public boolean scheduleIn(String input) {
        Duration duration = parseDuration(input);
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return false;
        }
        unlockAtMillis = System.currentTimeMillis() + duration.toMillis();
        if (!plugin.isLocked()) {
            plugin.setLocked(true, "Schedule", false);
        }
        save();
        startTask();
        return true;
    }

    public boolean scheduleAt(String input) {
        LocalDateTime dateTime;
        try {
            dateTime = LocalDateTime.parse(input.trim(), DATE_TIME);
        } catch (DateTimeParseException e) {
            return false;
        }
        Instant instant = dateTime.atZone(ZoneId.systemDefault()).toInstant();
        if (instant.isBefore(Instant.now())) {
            return false;
        }
        unlockAtMillis = instant.toEpochMilli();
        if (!plugin.isLocked()) {
            plugin.setLocked(true, "Schedule", false);
        }
        save();
        startTask();
        return true;
    }

    private Duration parseDuration(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        Matcher matcher = DURATION.matcher(input.toLowerCase(Locale.ROOT));
        long totalSeconds = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            long value = Long.parseLong(matcher.group(1));
            totalSeconds += switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "w" -> value * 7 * 24 * 60 * 60;
                case "d" -> value * 24 * 60 * 60;
                case "h" -> value * 60 * 60;
                case "m" -> value * 60;
                case "s" -> value;
                default -> 0;
            };
        }
        return found ? Duration.ofSeconds(Math.max(1, totalSeconds)) : null;
    }

    private void startTask() {
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (unlockAtMillis == null) {
                return;
            }
            if (System.currentTimeMillis() >= unlockAtMillis) {
                unlockAtMillis = null;
                save();
                if (plugin.isLocked()) {
                    plugin.setLocked(false, "Schedule", true);
                }
            }
        }, 20L, 20L * 30);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
    }
}
