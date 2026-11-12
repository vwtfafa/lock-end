package org.vwtfafa.lockend.neoforge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.function.Predicate;

/**
 * Named NeoForge permission nodes for the {@code /endlock} command tree,
 * registered through {@link PermissionGatherEvent.Nodes} under mod id
 * {@code lockend}. Defaults grant the node when the player has the vanilla
 * admin command permission ({@link Permissions#COMMANDS_ADMIN}, the
 * {@code Commands.LEVEL_ADMINS} op fallback). Console (non-player) sources
 * are always allowed by the command predicates in {@link #requires}.
 */
final class LockEndPermissions {

    private static final String MOD_ID = "lockend";

    static final PermissionNode<Boolean> ADMIN = booleanNode(
            "admin",
            "EndLock admin",
            "Lock, unlock, reason, schedule, evacuate, cancel, pause and resume");
    static final PermissionNode<Boolean> RELOAD = booleanNode(
            "reload",
            "EndLock reload",
            "Reload EndLock configuration");
    static final PermissionNode<Boolean> TOGGLE = booleanNode(
            "toggle",
            "EndLock toggle schedule",
            "Schedule lock/unlock with lockin, unlockin, lockat and unlockat");
    static final PermissionNode<Boolean> HISTORY = booleanNode(
            "history",
            "EndLock history",
            "View and export EndLock history");
    static final PermissionNode<Boolean> UNDO = booleanNode(
            "undo",
            "EndLock undo",
            "Undo the last EndLock state change");

    private LockEndPermissions() {
    }

    /**
     * Registers every EndLock permission node. Must run on
     * {@link PermissionGatherEvent.Nodes}.
     *
     * @param event the gather-nodes event
     */
    static void registerNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(ADMIN, RELOAD, TOGGLE, HISTORY, UNDO);
    }

    /**
     * Command predicate: console always passes; players resolve through
     * {@link PermissionAPI#getPermission}.
     *
     * @param node the boolean permission node to require
     * @return a Brigadier requirement predicate
     */
    static Predicate<CommandSourceStack> requires(PermissionNode<Boolean> node) {
        return source -> {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                return true;
            }
            return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
        };
    }

    private static PermissionNode<Boolean> booleanNode(
            String name, String readableName, String description) {
        return new PermissionNode<>(
                MOD_ID,
                name,
                PermissionTypes.BOOLEAN,
                (player, playerUuid, contexts) -> player != null
                        && player.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .setInformation(Component.literal(readableName), Component.literal(description));
    }
}
