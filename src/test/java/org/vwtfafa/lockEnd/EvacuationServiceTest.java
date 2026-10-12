package org.vwtfafa.lockEnd;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvacuationServiceTest {

    @Test
    void completionIsNotHandledAfterShutdownOrDisconnect() {
        assertTrue(EvacuationService.canNotifyCompletion(false, true, true));
        assertFalse(EvacuationService.canNotifyCompletion(true, true, true));
        assertFalse(EvacuationService.canNotifyCompletion(false, false, true));
        assertFalse(EvacuationService.canNotifyCompletion(false, true, false));
    }

    @Test
    void evacuationConfirmationRequiresTheSameFreshImpactSnapshot() {
        UUID included = UUID.randomUUID();
        Set<UUID> plannedPlayers = Set.of(included);

        assertTrue(EvacuationService.samePreview(
                "world", plannedPlayers, 1, "world", Set.of(included), 1));
        assertFalse(EvacuationService.samePreview(
                "world", plannedPlayers, 1, "other", Set.of(included), 1));
        assertFalse(EvacuationService.samePreview(
                "world", plannedPlayers, 1, "world", Set.of(), 1));
        assertFalse(EvacuationService.samePreview(
                "world", plannedPlayers, 1, "world", Set.of(included), 0));
    }
}