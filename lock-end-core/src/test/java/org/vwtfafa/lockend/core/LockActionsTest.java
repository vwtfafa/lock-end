package org.vwtfafa.lockend.core;

import org.junit.jupiter.api.Test;
import org.vwtfafa.lockend.api.LockAction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the strict mapping of legacy action names onto the shared enum.
 */
class LockActionsTest {

    @Test
    void mapsAllDocumentedNames() {
        assertEquals(LockAction.LOCK, LockActions.fromActionName("LOCK"));
        assertEquals(LockAction.UNLOCK, LockActions.fromActionName("UNLOCK"));
        assertEquals(LockAction.UNDO, LockActions.fromActionName("UNDO"));
        assertEquals(LockAction.SCHEDULED_LOCK, LockActions.fromActionName("SCHEDULED_LOCK"));
        assertEquals(LockAction.SCHEDULED_UNLOCK, LockActions.fromActionName("SCHEDULED_UNLOCK"));
    }

    @Test
    void mappingIsCaseInsensitive() {
        assertEquals(LockAction.LOCK, LockActions.fromActionName("lock"));
        assertEquals(LockAction.SCHEDULED_UNLOCK, LockActions.fromActionName("scheduled_unlock"));
    }

    @Test
    void unknownNamesFailLoudly() {
        assertThrows(IllegalArgumentException.class, () -> LockActions.fromActionName("TEST"));
        assertThrows(IllegalArgumentException.class, () -> LockActions.fromActionName("SCHEDULED_UNLOCK "));
        assertThrows(IllegalArgumentException.class, () -> LockActions.fromActionName("SCHED-LOCK"));
        assertThrows(IllegalArgumentException.class, () -> LockActions.fromActionName(null));
    }
}
