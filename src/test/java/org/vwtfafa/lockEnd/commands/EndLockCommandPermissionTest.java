package org.vwtfafa.lockEnd.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndLockCommandPermissionTest {

    @Test
    void naturalScheduleAliasesAreAvailableToTogglePermissionAlone() {
        LiteralCommandNode<CommandSourceStack> root = new EndLockCommand(null).buildNode();
        CommandSourceStack toggleSource = sourceWithPermissions(Set.of("endlock.toggle"));
        CommandSourceStack adminSource = sourceWithPermissions(Set.of("endlock.admin"));
        CommandSourceStack unprivilegedSource = sourceWithPermissions(Set.of());

        for (String name : new String[]{"lock", "unlock"}) {
            LiteralCommandNode<CommandSourceStack> parent = (LiteralCommandNode<CommandSourceStack>) root.getChild(name);
            var schedule = parent.getChild("in");

            assertTrue(parent.canUse(toggleSource));
            assertTrue(schedule.canUse(toggleSource));
            assertTrue(parent.canUse(adminSource));
            assertFalse(schedule.canUse(adminSource));
            assertFalse(parent.canUse(unprivilegedSource));
        }
    }

    private static CommandSourceStack sourceWithPermissions(Set<String> permissions) {
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> method.getName().equals("hasPermission")
                        && permissions.contains(arguments[0]));

        return (CommandSourceStack) Proxy.newProxyInstance(
                CommandSourceStack.class.getClassLoader(),
                new Class<?>[]{CommandSourceStack.class},
                (proxy, method, arguments) -> method.getName().equals("getSender") ? sender : null);
    }
}