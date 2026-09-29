package org.vwtfafa.lockEnd;

import org.junit.jupiter.api.Test;

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
}