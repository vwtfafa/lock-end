package org.vwtfafa.lockEnd;

import org.bukkit.World;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the full End access rule matrix for players and entities.
 */
class EndAccessGateTest {
    private static final EndAccessGate.AccessRequest INTO_THE_END =
            new EndAccessGate.AccessRequest(World.Environment.THE_END, "world_the_end");
    private static final EndAccessGate.AccessRequest INTO_OVERWORLD =
            new EndAccessGate.AccessRequest(World.Environment.NORMAL, "world");
    private static final EndAccessGate.AccessRequest INTO_SCOPED_WORLD =
            new EndAccessGate.AccessRequest(World.Environment.THE_END, "resource_end");

    private static EndAccessGate gate(boolean locked, boolean grace, boolean blockGateway, List<String> worlds) {
        return new EndAccessGate(locked, grace, blockGateway, worlds);
    }

    @Test
    void unlockedEndAllowsEverything() {
        assertEquals(EndAccessGate.Verdict.ALLOWED,
                gate(false, false, true, List.of()).checkPlayer(INTO_THE_END, cause(), () -> false));
        assertEquals(EndAccessGate.Verdict.ALLOWED,
                gate(false, false, true, List.of()).checkEntity(INTO_THE_END, false));
    }

    @Test
    void lockedEndBlocksPlayersWithoutBypass() {
        assertEquals(EndAccessGate.Verdict.BLOCKED,
                gate(true, false, true, List.of()).checkPlayer(INTO_THE_END, cause(), () -> false));
    }

    @Test
    void bypassPermissionAllowsEntryWhileLocked() {
        assertEquals(EndAccessGate.Verdict.ALLOWED,
                gate(true, false, true, List.of()).checkPlayer(INTO_THE_END, cause(), () -> true));
    }

    @Test
    void leavingTheEndIsAlwaysAllowed() {
        assertEquals(EndAccessGate.Verdict.ALLOWED,
                gate(true, false, true, List.of()).checkPlayer(INTO_OVERWORLD, cause(), () -> false));
        assertEquals(EndAccessGate.Verdict.ALLOWED,
                gate(true, false, true, List.of()).checkEntity(INTO_OVERWORLD, false));
    }

    @Test
    void nonEndTargetsAreIgnored() {
        EndAccessGate.AccessRequest nether =
                new EndAccessGate.AccessRequest(World.Environment.NETHER, "world_nether");
        assertEquals(EndAccessGate.Verdict.ALLOWED,
                gate(true, false, true, List.of()).checkPlayer(nether, cause(), () -> false));
    }

    @Test
    void endGatewayHonorsTheBlockFlagForPlayers() {
        assertEquals(EndAccessGate.Verdict.BLOCKED,
                gate(true, false, true, List.of())
                        .checkPlayer(INTO_THE_END, PlayerTeleportEvent.TeleportCause.END_GATEWAY, () -> false));
        assertEquals(EndAccessGate.Verdict.ALLOWED,
                gate(true, false, false, List.of())
                        .checkPlayer(INTO_THE_END, PlayerTeleportEvent.TeleportCause.END_GATEWAY, () -> false));
    }

    @Test
    void endGatewayHonorsTheBlockFlagForEntities() {
        assertEquals(EndAccessGate.Verdict.BLOCKED,
                gate(true, false, true, List.of()).checkEntity(INTO_THE_END, true));
        assertEquals(EndAccessGate.Verdict.ALLOWED,
                gate(true, false, false, List.of()).checkEntity(INTO_THE_END, true));
        // Regular entity teleports stay blocked even with gateways allowed.
        assertEquals(EndAccessGate.Verdict.BLOCKED,
                gate(true, false, false, List.of()).checkEntity(INTO_THE_END, false));
    }

    @Test
    void worldScopeRestrictsWhichEndWorldsAreGuarded() {
        EndAccessGate scoped = gate(true, false, true, List.of("resource_end"));
        assertEquals(EndAccessGate.Verdict.ALLOWED, scoped.checkPlayer(INTO_THE_END, cause(), () -> false));
        assertEquals(EndAccessGate.Verdict.BLOCKED, scoped.checkPlayer(INTO_SCOPED_WORLD, cause(), () -> false));
        assertEquals(EndAccessGate.Verdict.ALLOWED, scoped.checkEntity(INTO_THE_END, false));
        // Case-insensitive scope matching.
        assertEquals(EndAccessGate.Verdict.BLOCKED,
                scoped.checkPlayer(new EndAccessGate.AccessRequest(
                        World.Environment.THE_END, "RESOURCE_END"), cause(), () -> false));
    }

    @Test
    void gracePeriodDelaysEnforcementBeforeBlocking() {
        assertEquals(EndAccessGate.Verdict.GRACE_PERIOD,
                gate(true, true, true, List.of()).checkPlayer(INTO_THE_END, cause(), () -> false));
        assertEquals(EndAccessGate.Verdict.GRACE_PERIOD,
                gate(true, true, true, List.of()).checkPlayer(INTO_THE_END, cause(), () -> true));
        assertEquals(EndAccessGate.Verdict.GRACE_PERIOD,
                gate(true, true, true, List.of()).checkEntity(INTO_THE_END, false));
    }

    private static PlayerTeleportEvent.TeleportCause cause() {
        return PlayerTeleportEvent.TeleportCause.PLUGIN;
    }
}
