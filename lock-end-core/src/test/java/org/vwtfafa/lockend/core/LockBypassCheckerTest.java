package org.vwtfafa.lockend.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the platform-independent bypass rules: player names, UUIDs,
 * destination world keys (namespaced and bare) and the delegated permission
 * decision, matching the Paper {@code WhitelistChecker} semantics.
 */
class LockBypassCheckerTest {

    private static final UUID PLAYER_A_UUID =
            UUID.fromString("abcdefab-cdef-abcd-efab-cdefabcdefab");
    private static final UUID PLAYER_B_UUID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final LockBypassChecker CHECKER = new LockBypassChecker(
            List.of("Admin", "Helper"),
            List.of("ABCDEFAB-CDEF-ABCD-EFAB-CDEFABCDEFAB"),
            List.of("world_the_end", "minecraft:maintenance_end"));

    private static BooleanSupplier neverAllowed() {
        return () -> false;
    }

    @Test
    void rejectsNullPermissionSupplier() {
        assertThrows(IllegalArgumentException.class, () ->
                CHECKER.canBypass("Admin", PLAYER_A_UUID, "minecraft:the_end", null));
    }

    @ParameterizedTest
    @CsvSource({
            "Admin", "admin", "ADMIN", "helper", "Helper"
    })
    void playerNameListMatchesCaseInsensitively(String playerName) {
        assertTrue(CHECKER.canBypass(playerName, null, "minecraft:the_end", neverAllowed()));
    }

    @Test
    void unknownPlayerIsNotAllowed() {
        assertFalse(CHECKER.canBypass("Guest", PLAYER_B_UUID, "minecraft:the_end", neverAllowed()));
    }

    @Test
    void uuidListMatchesCaseInsensitively() {
        assertTrue(CHECKER.canBypass(
                "Guest",
                PLAYER_A_UUID,
                "minecraft:the_end",
                neverAllowed()));
    }

    @Test
    void missingUuidDoesNotThrow() {
        assertFalse(CHECKER.canBypass("Guest", null, "minecraft:the_end", neverAllowed()));
    }

    @Test
    void namespacedWorldEntryMatchesExactly() {
        assertTrue(CHECKER.canBypass("Guest", PLAYER_B_UUID, "minecraft:maintenance_end", neverAllowed()));
        assertFalse(CHECKER.canBypass("Guest", PLAYER_B_UUID, "custom:maintenance_end", neverAllowed()));
    }

    @Test
    void bareWorldEntryMatchesDestinationPathInAnyNamespace() {
        LockBypassChecker bare = new LockBypassChecker(
                List.of(), List.of(), List.of("the_end"));

        assertTrue(bare.canBypass("Guest", PLAYER_A_UUID, "minecraft:the_end", neverAllowed()));
        assertTrue(bare.canBypass("Guest", PLAYER_A_UUID, "custom:the_end", neverAllowed()));
        assertFalse(bare.canBypass("Guest", PLAYER_A_UUID, "minecraft:the_nether", neverAllowed()));
    }

    @Test
    void blankAndUnknownWorldEntriesNeverMatch() {
        LockBypassChecker blankEntries = new LockBypassChecker(
                List.of(), List.of(), List.of("  "));

        assertFalse(blankEntries.canBypass("Guest", PLAYER_A_UUID, "minecraft:the_end", neverAllowed()));
    }

    @Test
    void unknownPlayerWithoutDestinationFallsBackToPermissionDecision() {
        assertFalse(CHECKER.canBypass("Guest", PLAYER_B_UUID, null, neverAllowed()));
        assertTrue(CHECKER.canBypass("Guest", PLAYER_B_UUID, null, () -> true));
    }

    @Test
    void permissionSupplierIsOnlyQueriedAfterConfiguredLists() {
        BooleanSupplier assertingSupplier = () -> {
            throw new AssertionError("permission supplier must not be queried after a list match");
        };

        assertTrue(CHECKER.canBypass("Admin", PLAYER_A_UUID, "minecraft:the_end", assertingSupplier));
    }

    @Test
    void listsStayUnmodifiable() {
        assertEquals(2, CHECKER.playerNames().size());
        assertThrows(UnsupportedOperationException.class, () -> CHECKER.playerNames().add("intruder"));
        assertThrows(UnsupportedOperationException.class, () -> CHECKER.uuids().add("intruder"));
        assertThrows(UnsupportedOperationException.class, () -> CHECKER.worlds().add("intruder"));
    }

    @Test
    void nullAndBlankPlayerNamesDoNotMatch() {
        assertFalse(CHECKER.canBypass(null, PLAYER_B_UUID, "minecraft:the_end", neverAllowed()));
        assertFalse(CHECKER.canBypass("  ", PLAYER_B_UUID, "minecraft:the_end", neverAllowed()));
    }
}
