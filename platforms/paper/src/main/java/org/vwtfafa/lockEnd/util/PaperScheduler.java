package org.vwtfafa.lockEnd.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Provides scheduler access using Paper's schedulers, which also route tasks
 * correctly on Folia.
 */
public final class PaperScheduler {
    private final Plugin plugin;

    public PaperScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public ScheduledTask runGlobalLater(Runnable action, long delayTicks) {
        return plugin.getServer().getGlobalRegionScheduler()
                .runDelayed(plugin, ignored -> action.run(), Math.max(1L, delayTicks));
    }

    public ScheduledTask runGlobalRepeating(Runnable action, long initialDelayTicks, long periodTicks) {
        return plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, ignored -> action.run(),
                        Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
    }

    public void executeGlobal(Runnable action) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, action);
    }

    public ScheduledTask runAsync(Runnable action) {
        return plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> action.run());
    }

    public ScheduledTask runEntity(Entity entity, Runnable action, Runnable retired) {
        return entity.getScheduler().run(plugin, ignored -> action.run(), retired);
    }

    public boolean executeEntity(Entity entity, Runnable action, Runnable retired) {
        return entity.getScheduler().execute(plugin, action, retired, 1L);
    }

    public void sendMessage(CommandSender sender, Component message) {
        if (sender instanceof Entity entity) {
            executeEntity(entity, () -> entity.sendMessage(message), () -> {});
            return;
        }
        sender.sendMessage(message);
    }

    public void cancelAsyncTasks() {
        plugin.getServer().getAsyncScheduler().cancelTasks(plugin);
    }
}
