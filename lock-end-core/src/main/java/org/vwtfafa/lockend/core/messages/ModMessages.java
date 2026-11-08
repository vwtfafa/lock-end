package org.vwtfafa.lockend.core.messages;

import java.util.HashMap;
import java.util.Map;

/**
 * Message key registry and renderer for the mod adapters. Keys mirror the
 * Paper plugin's language keys so {@code config/lock-end-messages.json}
 * overrides can be written against the same names; values are plain text
 * with {@code %placeholder%} tokens (the mods render no MiniMessage).
 *
 * <p>Resolution order: overrides first (platform modules load them from the
 * config directory), then the built-in English defaults, then the key itself
 * so a missing translation can never blank out feedback entirely.</p>
 *
 * <p>This class is stateful-but-simple: one overrides map, replaced on
 * reload. Rendering does pure token replacement - values are never
 * re-parsed, so user-controlled input cannot inject formatting.</p>
 */
public final class ModMessages {

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            // --- access and state --- -----------------------------------
            Map.entry("open", "open"),
            Map.entry("closed", "locked"),
            Map.entry("status", "The End is currently %status%."),
            Map.entry("toggle", "The End is now %status%."),
            Map.entry("locked-reason", "The End is locked! Reason: %reason%"),
            Map.entry("already-locked", "The End is already locked!"),
            Map.entry("already-unlocked", "The End is already unlocked!"),
            Map.entry("permission", "You do not have permission to lock or unlock the End."),
            // --- reason --- ---------------------------------------------
            Map.entry("reason-set", "Lock reason set to: %reason%"),
            Map.entry("reason-cleared",
                    "Lock reason cleared; restored default: %reason%"),
            Map.entry("reason-usage", "Usage: /endlock reason <reason>"),
            // --- reload --- ---------------------------------------------
            Map.entry("reload-success", "Configuration reloaded successfully!"),
            Map.entry("reload-recovered",
                    "Lock-End settings reloaded, but recovery was needed: %failure%"
                            + ". Fix the file and run /endlock reload again."),
            // --- scheduled actions --- ----------------------------------
            Map.entry("scheduled-unlock-set-days", "Scheduled unlock in %days% days."),
            Map.entry("scheduled-unlock-set-at", "Scheduled unlock at %datetime%."),
            Map.entry("scheduled-unlock-invalid",
                    "Usage: /endlock unlockin <days> or /endlock unlockat <yyyy-MM-dd> <HH:mm>"),
            Map.entry("scheduled-lock-set-minutes", "Scheduled lock in %minutes% minutes."),
            Map.entry("scheduled-lock-set-at", "Scheduled lock at %datetime%."),
            Map.entry("scheduled-lock-invalid",
                    "Usage: /endlock lockin <minutes> or /endlock lockat <yyyy-MM-dd> <HH:mm>"),
            Map.entry("schedule-cancelled", "Scheduled unlock cancelled."),
            Map.entry("schedule-cancel", "Scheduled action cancelled."),
            Map.entry("schedule-cleared", "Scheduled action cleared."),
            Map.entry("schedule-status",
                    "[EndLock] Action: %action% | Target: %target% | Remaining: %remaining%"
                            + " | Paused: %paused%"),
            Map.entry("schedule-none", "No scheduled action."),
            Map.entry("schedule-paused",
                    "[EndLock] Scheduled action has been paused."
                            + " Use /endlock resume to resume."),
            Map.entry("schedule-resumed", "[EndLock] Scheduled action has been resumed."),
            Map.entry("schedule-not-paused", "No paused schedule to resume."),
            Map.entry("schedule-pause-none", "No schedule to pause."),
            Map.entry("countdown-notification", "[EndLock] The End will be unlocked in %time%."),
            Map.entry("countdown-lock-notification",
                    "[EndLock] The End will be locked in %time%."),
            Map.entry("preview-lock", "The End will be locked in %seconds% seconds!"),
            Map.entry("preview-unlock", "The End will be unlocked in %seconds% seconds!"),
            // --- broadcast --- ------------------------------------------
            Map.entry("broadcast-locked",
                    "[EndLock] End has been locked by %player%"),
            Map.entry("broadcast-unlocked",
                    "[EndLock] End has been unlocked by %player%"),
            Map.entry("actionbar-locked", "✗ End is locked"),
            Map.entry("actionbar-unlocked", "✓ End is unlocked"),
            // --- stats --- ----------------------------------------------
            Map.entry("stats-header",
                    "Stats: Lock count %lockcount% | Blocked count %blockedcount%"),
            Map.entry("stats-empty", "No stats recorded yet."),
            Map.entry("stats-line-unlocks", "Unlocks: %unlockcount%"),
            Map.entry("stats-line-evacuated", "Evacuated players: %evacuated%"),
            // --- history --- --------------------------------------------
            Map.entry("history-header-page",
                    "[EndLock] Lock History (page %page%):"),
            Map.entry("history.empty", "[EndLock] No lock history recorded."),
            Map.entry("history-page-empty", "[EndLock] This history page is empty."),
            Map.entry("history-usage",
                    "Usage: /endlock history [status|page] [json|csv] [player|action <value>]"),
            Map.entry("history-exported", "History exported to %file%."),
            Map.entry("history-export-failed",
                    "Could not export history. See console for details."),
            Map.entry("history-storage-status",
                    "History storage: %status%; %entries% entries in memory."
                            + " This is a bounded, rotating record, not an immutable"
                            + " or complete long-term audit."),
            Map.entry("history-entry",
                    "- [%timestamp%] %action% by %actor% (source: %source%,"
                            + " result: %result%%details%)"),
            Map.entry("history-details-actor-uuid", ", actor UUID: %uuid%"),
            Map.entry("history-details-subject-uuid", ", player UUID: %uuid%"),
            Map.entry("history-details-state", ", state: %state%"),
            Map.entry("history-details-state-transition", " -> %state%"),
            Map.entry("history-state-locked", "locked"),
            Map.entry("history-state-unlocked", "unlocked"),
            Map.entry("history.filter-player", "Filtering history by player: %player%"),
            Map.entry("history.filter-action", "Filtering history by action: %action%"),
            Map.entry("history.filter-no-results",
                    "[EndLock] No history entries match the filter: %type% = %value%"),
            // --- undo --- -----------------------------------------------
            Map.entry("undo.success",
                    "[EndLock] Successfully undone last action. End is now %action%."),
            Map.entry("undo.empty", "[EndLock] There is no action to undo."),
            // --- broadcasts + player events --- -------------------------
            Map.entry("join-notification", "The End is currently locked."),
            Map.entry("grace-period-active",
                    "The End lock grace period is active - access will be blocked shortly."),
            // --- evacuation --- -----------------------------------------
            Map.entry("evacuation-warning",
                    "You will be moved out of the End in %seconds% seconds"
                            + " because it is locked."),
            Map.entry("evacuation-complete",
                    "You were moved out of the End because it is locked."),
            Map.entry("evacuation-disabled", "Manual evacuation is disabled in the EndLock config."),
            Map.entry("evacuation-not-locked", "Lock the End before evacuating players."),
            Map.entry("evacuation-target-unavailable",
                    "No valid evacuation destination is available. No players were moved."),
            Map.entry("evacuation-preview",
                    "End evacuation preview: %count% players to %world%; %bypassed% bypassed."
                            + " Review this result, then run /endlock evacuate execute"
                            + " within 30 seconds."),
            Map.entry("evacuation-preview-required",
                    "Run /endlock evacuate preview first."
                            + " The preview must be less than 30 seconds old."),
            Map.entry("evacuation-preview-expired",
                    "The evacuation preview expired. Run /endlock evacuate preview again."),
            Map.entry("evacuation-preview-changed",
                    "The affected players or destination changed. Updated preview:"
                            + " %count% players to %world%; %bypassed% bypassed."
                            + " Review it, then run /endlock evacuate execute."),
            Map.entry("evacuation-execution-started",
                    "End evacuation started for up to %count% players."),
            // --- sounds --- ---------------------------------------------
            Map.entry("sound-denial", "block.anvil_land")
    );

    private volatile Map<String, String> overrides = Map.of();

    /**
     * @return an immutable copy of the built-in English defaults; platform
     *         modules use it to seed the writable overrides file
     */
    public Map<String, String> defaults() {
        return Map.copyOf(DEFAULTS);
    }

    /**
     * Replaces the override table (platform modules load it from
     * {@code config/lock-end-messages.json}). Unknown keys inside the map
     * are ignored: they can never surface through this renderer.
     *
     * @param newOverrides key-to-text map; {@code null} clears the table
     */
    public void setOverrides(Map<String, String> newOverrides) {
        overrides = newOverrides == null ? Map.of() : Map.copyOf(newOverrides);
    }

    /**
     * Resolves a raw template: override first, then the built-in default,
     * then the key itself.
     *
     * @param key the message key
     * @return the raw text; never {@code null}
     */
    public String raw(String key) {
        String override = overrides.get(key);
        if (override != null) {
            return override;
        }
        return DEFAULTS.getOrDefault(key, key);
    }

    /**
     * @param key the message key
     * @return {@code true} when an override or a built-in default exists
     */
    public boolean knows(String key) {
        return overrides.containsKey(key) || DEFAULTS.containsKey(key);
    }

    /**
     * Renders a message with {@code %token%} replacements applied to the
     * resolved template.
     *
     * @param key          the message key
     * @param placeholders tokens (including the percent signs) and values
     * @return the rendered text; never {@code null}
     */
    public String text(String key, Map<String, String> placeholders) {
        return render(raw(key), placeholders);
    }

    /**
     * Convenience for messages without placeholders.
     */
    public String text(String key) {
        return raw(key);
    }

    /**
     * Pure literal replacement; values are never re-parsed.
     */
    public static String render(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(),
                    entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    /**
     * Builds a mutable placeholder builder seeded with the given tokens.
     *
     * @param key1   first token, including the percent signs
     * @param value1 first value
     * @return a mutable map for further tokens
     */
    public static Map<String, String> placeholders(String key1, String value1) {
        Map<String, String> map = new HashMap<>();
        map.put(key1, value1);
        return map;
    }
}
