package org.vwtfafa.lockend.core;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Platform-independent bypass (whitelist) rules for players attempting to
 * enter a locked End. Mirrors the Paper {@code WhitelistChecker} rules so all
 * platforms make the same decision from the same configuration shape:
 *
 * <ul>
 *   <li>configured player names (case-insensitive display names),</li>
 *   <li>configured player UUIDs (case-insensitive),</li>
 *   <li>configured destination world keys (case-insensitive world/dimension
 *       keys), where an entry without {@code ':'} matches a namespaced
 *       destination key in any namespace, and</li>
 *   <li>a platform-provided permission check, passed in as a
 *       {@link BooleanSupplier} so this class stays free of Minecraft APIs.</li>
 * </ul>
 *
 * <p>Like the Paper checker, callers should report the destination world the
 * player would enter; a bypassed player may then enter (and stay) inside a
 * locked End, so call sites must also exempt bypassed players from
 * evacuation scans that run while the lock is active.</p>
 */
public final class LockBypassChecker {

    private static final String NAMESPACE_SEPARATOR = ":";

    private final List<String> playerNames;
    private final List<String> uuids;
    private final List<String> worlds;

    /**
     * Creates the checker from the configured allow lists.
     *
     * @param playerNames configured player names; entries are compared
     *                    case-insensitively
     * @param uuids       configured player UUIDs; entries are compared
     *                    case-insensitively
     * @param worlds      configured destination world keys; entries are
     *                    compared case-insensitively
     */
    public LockBypassChecker(List<String> playerNames, List<String> uuids, List<String> worlds) {
        this.playerNames = List.copyOf(playerNames);
        this.uuids = List.copyOf(uuids);
        this.worlds = List.copyOf(worlds);
    }

    /**
     * Decides whether a player bypasses the lock.
     *
     * @param playerName      the player's display name; may be
     *                        {@code null} for sources without a name
     * @param playerUuid      the player's UUID; may be {@code null} when the
     *                        platform cannot provide one
     * @param destinationWorldKey key of the world the player would enter,
     *                        such as {@code minecraft:the_end}; may be
     *                        {@code null} when no destination is known
     * @param permissionBypass the platform's permission decision; must not be
     *                        {@code null} and is only queried after the
     *                        configured lists did not already allow the player
     * @return {@code true} if the player may bypass the lock
     */
    public boolean canBypass(String playerName, UUID playerUuid, String destinationWorldKey,
                             BooleanSupplier permissionBypass) {
        if (permissionBypass == null) {
            throw new IllegalArgumentException("permissionBypass must not be null");
        }
        if (matchesAny(playerName, playerNames)) {
            return true;
        }
        if (playerUuid != null && matchesAny(playerUuid.toString(), uuids)) {
            return true;
        }
        if (destinationWorldKey != null && worlds.stream().anyMatch(
                world -> matchesWorldKey(world, destinationWorldKey))) {
            return true;
        }
        return permissionBypass.getAsBoolean();
    }

    /**
     * Case-insensitive non-blank list comparison, shared by all list rules.
     */
    private static boolean matchesAny(String value, List<String> entries) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return entries.stream().anyMatch(entry -> entry.equalsIgnoreCase(value));
    }

    /**
     * World key rule: a configured key with a namespace matches the
     * destination key exactly; a configured key without a namespace matches
     * the destination's path in any namespace, so {@code the_end} also covers
     * {@code minecraft:the_end}.
     */
    private static boolean matchesWorldKey(String configured, String destinationKey) {
        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.equalsIgnoreCase(destinationKey)) {
            return true;
        }
        return !normalized.contains(NAMESPACE_SEPARATOR)
                && destinationKey.contains(NAMESPACE_SEPARATOR)
                && destinationKey.substring(
                        destinationKey.indexOf(NAMESPACE_SEPARATOR) + NAMESPACE_SEPARATOR.length())
                        .equalsIgnoreCase(normalized);
    }

    /**
     * @return the configured player names; unmodifiable
     */
    public List<String> playerNames() {
        return playerNames;
    }

    /**
     * @return the configured player UUIDs; unmodifiable
     */
    public List<String> uuids() {
        return uuids;
    }

    /**
     * @return the configured destination world keys; unmodifiable
     */
    public List<String> worlds() {
        return worlds;
    }
}
