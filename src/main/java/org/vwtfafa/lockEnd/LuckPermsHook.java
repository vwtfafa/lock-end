package org.vwtfafa.lockEnd;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class LuckPermsHook implements ContextCalculator<Player> {
    private final LockEnd plugin;
    private boolean active;

    public LuckPermsHook(LockEnd plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        if (!plugin.getConfig().getBoolean("integrations.luckperms-context.enabled", true)) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        try {
            LuckPerms api = LuckPermsProvider.get();
            api.getContextManager().registerCalculator(this);
            active = true;
            plugin.getLogger().info("LuckPerms context registered (lockend_locked)");
        } catch (Exception e) {
            plugin.getLogger().warning("LuckPerms hook failed: " + e.getMessage());
        }
    }

    @Override
    public void calculate(Player target, ContextConsumer consumer) {
        consumer.accept("lockend_locked", plugin.isLocked() ? "true" : "false");
    }

    @Override
    public ContextSet estimatePotentialContexts() {
        return ImmutableContextSet.builder()
                .add("lockend_locked", "true")
                .add("lockend_locked", "false")
                .build();
    }

    public void refreshPlayers() {
        if (!active) {
            return;
        }
        try {
            var contextManager = LuckPermsProvider.get().getContextManager();
            for (Player player : Bukkit.getOnlinePlayers()) {
                contextManager.signalContextUpdate(player);
            }
        } catch (Exception ignored) {
        }
    }
}
