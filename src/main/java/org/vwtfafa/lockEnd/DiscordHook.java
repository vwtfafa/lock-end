package org.vwtfafa.lockEnd;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;

public final class DiscordHook {
    private final LockEnd plugin;
    private Object discordApi;
    private Method sendChannelMessage;

    public DiscordHook(LockEnd plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        if (!plugin.getConfig().getBoolean("integrations.discordsrv.enabled", true)) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("DiscordSRV") == null) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            Method getInstance = apiClass.getMethod("getPlugin");
            Object discordSrv = getInstance.invoke(null);
            Method getApi = discordSrv.getClass().getMethod("getDiscordSRV");
            discordApi = getApi.invoke(discordSrv);
            sendChannelMessage = discordApi.getClass().getMethod("sendChannelMessage", String.class, String.class);
            plugin.getLogger().info("DiscordSRV hook enabled");
        } catch (Exception e) {
            plugin.getLogger().warning("DiscordSRV hook failed: " + e.getMessage());
        }
    }

    public void notifyLockChange(boolean locked, String actor) {
        if (sendChannelMessage == null || discordApi == null) {
            return;
        }
        String channelId = plugin.getConfig().getString("integrations.discordsrv.channel-id", "");
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        String message = locked
                ? plugin.getMessages().format("discord-locked", "%player%", actor)
                : plugin.getMessages().format("discord-unlocked", "%player%", actor);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sendChannelMessage.invoke(discordApi, channelId, message);
            } catch (Exception e) {
                plugin.getLogger().warning("DiscordSRV message failed: " + e.getMessage());
            }
        });
    }
}
