package org.vwtfafa.apicons;

import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.vwtfafa.lockEnd.api.EndLockStateChangeEvent;

/**
 * Logs every {@link EndLockStateChangeEvent} payload plus the dispatch thread.
 *
 * <p>Read-only by contract (docs/3.1-api-contract.md §5.3): the handler only
 * consumes the payload; there are no world or player mutations and no
 * scheduler calls inside the handler itself.</p>
 */
public final class EndLockStateChangeLogger implements Listener {

    private final JavaPlugin plugin;

    public EndLockStateChangeLogger(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onStateChange(EndLockStateChangeEvent event) {
        String actorName = event.actorName();
        UUID actorUuid = event.actorUuid();
        // Plugin logger (java.util.logging) is safe to call from any thread.
        plugin.getLogger().info("state-change: previousLocked=" + event.previousLocked()
                + ", newLocked=" + event.newLocked()
                + ", action=" + event.action()
                + ", actorName=" + (actorName == null ? "null" : actorName)
                + ", actorUuid=" + (actorUuid == null ? "null" : actorUuid)
                + ", primaryThread=" + plugin.getServer().isPrimaryThread()
                + ", threadName=" + Thread.currentThread().getName());
    }
}
