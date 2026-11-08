package org.vwtfafa.lockend.api;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the shared, platform-independent data structures and
 * listener contract. These tests deliberately run without any Minecraft
 * dependency.
 */
class LockEndApiDataTest {

    @Test
    void lockStateCoversLockedAndUnlocked() {
        assertEquals(2, LockState.values().length);
        assertTrue(LockState.LOCKED.isLocked());
        assertFalse(LockState.UNLOCKED.isLocked());
        assertEquals(LockState.LOCKED, LockState.fromLocked(true));
        assertEquals(LockState.UNLOCKED, LockState.fromLocked(false));
    }

    @Test
    void lockActionContainsDocumentedValues() {
        assertEquals(5, LockAction.values().length);
        assertNotNull(LockAction.valueOf("LOCK"));
        assertNotNull(LockAction.valueOf("UNLOCK"));
        assertNotNull(LockAction.valueOf("UNDO"));
        assertNotNull(LockAction.valueOf("SCHEDULED_LOCK"));
        assertNotNull(LockAction.valueOf("SCHEDULED_UNLOCK"));
        assertThrows(IllegalArgumentException.class, () -> LockAction.valueOf("UNKNOWN"));
    }

    @Test
    void stateChangeRejectsEqualStates() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new LockStateChange(true, true, LockAction.LOCK, "System", null));
        assertTrue(exception.getMessage().contains("differ"));
    }

    @Test
    void stateChangeRejectsNullAction() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new LockStateChange(true, false, null, "System", null));
        assertTrue(exception.getMessage().contains("action"));
    }

    @Test
    void stateChangeKeepsOptionalActorFields() {
        UUID actorUuid = UUID.randomUUID();
        LockStateChange change = new LockStateChange(false, true, LockAction.LOCK, "Admin", actorUuid);
        assertFalse(change.previousLocked());
        assertTrue(change.newLocked());
        assertEquals(LockAction.LOCK, change.action());
        assertEquals("Admin", change.actorName());
        assertEquals(actorUuid, change.actorUuid());

        LockStateChange anonymousChange = new LockStateChange(true, false, LockAction.SCHEDULED_UNLOCK, null, null);
        assertNull(anonymousChange.actorName());
        assertNull(anonymousChange.actorUuid());
    }

    @Test
    void snapshotRejectsNullStateAndReason() {
        assertThrows(IllegalArgumentException.class, () -> new LockStateSnapshot(null, "reason", 0L));
        assertThrows(IllegalArgumentException.class, () -> new LockStateSnapshot(LockState.UNLOCKED, null, 0L));
    }

    @Test
    void snapshotExposesConsistentPair() {
        LockStateSnapshot snapshot = new LockStateSnapshot(LockState.LOCKED, "Maintenance", 42L);
        assertTrue(snapshot.isLocked());
        assertEquals("Maintenance", snapshot.lockReason());
        assertEquals(42L, snapshot.capturedAtEpochMillis());
    }

    @Test
    void listenerReceivesChange() {
        AtomicReference<LockStateChange> received = new AtomicReference<>();
        LockStateChangeListener listener = received::set;

        LockStateChange change = new LockStateChange(true, false, LockAction.UNDO, "Console", null);
        assertDoesNotThrow(() -> listener.onStateChange(change));
        assertEquals(change, received.get());
    }
}
