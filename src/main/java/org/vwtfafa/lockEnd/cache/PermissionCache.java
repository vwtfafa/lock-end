package org.vwtfafa.lockEnd.cache;

import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Cached permission checker using Guava LoadingCache pattern (simplified for Paper API).
 */
public class PermissionCache {
    private static final PermissionCache INSTANCE = new PermissionCache();
    private final ConcurrentHashMap<String, Boolean> cache = new ConcurrentHashMap<>();
    private long lastClear = 0;
    private static final long CACHE_EXPIRATION_MS = TimeUnit.MINUTES.toMillis(1);

    private PermissionCache() {}

    public static PermissionCache getInstance() {
        return INSTANCE;
    }

    /**
     * Checks if a player has a permission with caching.
     * @param player The player to check
     * @param permission The permission to check
     * @return The permission result
     */
    public boolean hasPermission(Player player, String permission) {
        clearExpired();
        String cacheKey = player.getUniqueId() + ":" + permission;
        return cache.computeIfAbsent(cacheKey, k -> player.hasPermission(permission));
    }

    /**
     * Invalidates the cache for a specific player.
     * @param player The player to invalidate
     */
    public void invalidate(Player player) {
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(player.getUniqueId() + ":"));
    }

    /**
     * Clears all expired cache entries.
     */
    private void clearExpired() {
        long now = System.currentTimeMillis();
        if (now - lastClear > CACHE_EXPIRATION_MS) {
            cache.clear();
            lastClear = now;
        }
    }

    /**
     * Clears the entire cache.
     */
    public void clear() {
        cache.clear();
        lastClear = System.currentTimeMillis();
    }
}