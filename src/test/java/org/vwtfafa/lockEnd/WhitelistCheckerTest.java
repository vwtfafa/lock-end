package org.vwtfafa.lockEnd;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests whitelist bypass resolution for names, UUIDs, worlds and permissions.
 */
class WhitelistCheckerTest {
    private static final String UUID_STEVE = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

    private static WhitelistChecker checkerFromConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("whitelists.players", java.util.List.of("Notch"));
        config.set("whitelists.uuids", java.util.List.of(UUID_STEVE));
        config.set("whitelists.worlds", java.util.List.of("resource_world"));
        return new WhitelistChecker(config);
    }

    /**
     * Builds a minimal Player stub; only name, unique id and permission
     * checks are used by WhitelistChecker.
     */
    private static Player stubPlayer(String name, String uuid, Set<String> grantedPermissions) {
        return (Player) Proxy.newProxyInstance(
                WhitelistCheckerTest.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> UUID.fromString(uuid);
                    case "hasPermission" -> grantedPermissions.contains((String) args[0]);
                    case "toString" -> "StubPlayer[" + name + "]";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) return Boolean.FALSE;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0.0f;
        if (returnType == double.class) return 0.0d;
        if (returnType == char.class) return '\0';
        return 0;
    }

    private static World stubWorldNamed(String name) {
        return (World) Proxy.newProxyInstance(
                WhitelistCheckerTest.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "toString" -> "StubWorld[" + name + "]";
                    default -> defaultValue(method.getReturnType());
                });
    }

    @Test
    void whitelistedNameBypassesCaseInsensitively() {
        assertTrue(checkerFromConfig().canBypass(stubPlayer("NOTCH", UUID.randomUUID().toString(), Set.of())));
    }

    @Test
    void whitelistedUuidBypasses() {
        assertTrue(checkerFromConfig().canBypass(stubPlayer("Other", UUID_STEVE, Set.of())));
    }

    @Test
    void unknownPlayerWithoutTargetWorldCannotBypass() {
        assertFalse(checkerFromConfig().canBypass(stubPlayer("Alex", UUID.randomUUID().toString(), Set.of())));
    }

    @Test
    void whitelistedWorldAllowsBypassOnlyForThatWorld() {
        WhitelistChecker checker = checkerFromConfig();
        assertTrue(checker.canBypass(stubPlayer("Alex", UUID.randomUUID().toString(), Set.of()),
                stubWorldNamed("Resource_World")));
        assertFalse(checker.canBypass(stubPlayer("Alex", UUID.randomUUID().toString(), Set.of()),
                stubWorldNamed("world")));
    }

    @Test
    void worldPermissionGrantsBypass() {
        Player player = stubPlayer("Alex", UUID.randomUUID().toString(), Set.of("endlock.bypass.world.resource_world"));
        assertTrue(checkerFromConfig().canBypass(player, stubWorldNamed("Resource_World")));
    }

    @Test
    void generalBypassPermissionGrantsBypass() {
        Player player = stubPlayer("Alex", UUID.randomUUID().toString(), Set.of("endlock.whitelist.bypass"));
        assertTrue(checkerFromConfig().canBypass(player));
    }
}
