package org.vwtfafa.lockend.core;

import org.junit.jupiter.api.Test;
import org.vwtfafa.lockend.api.LockAction;
import org.vwtfafa.lockend.api.LockState;
import org.vwtfafa.lockend.api.LockStateChange;
import org.vwtfafa.lockend.api.LockStateChangeListener;
import org.vwtfafa.lockend.api.LockStateSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the core runtime lock state semantics without a Minecraft
 * server: state reads, transitions, reason resolution, listener dispatch
 * and snapshot consistency.
 */
class LockStateManagerTest {

    @Test
    void startsInInitialState() {
        assertTrue(new LockStateManager(true).isLocked());
        assertFalse(new LockStateManager(false).isLocked());
        assertEquals(LockState.LOCKED, new LockStateManager(true).getCurrentState());
        assertEquals(LockState.UNLOCKED, new LockStateManager(false).getCurrentState());
    }

    @Test
    void loadsInitialStateFromRepository() {
        LockStateManager manager = new LockStateManager(true)
                .loadFromPersistence(new LockStateRepository() {
                    @Override
                    public void save(LockStateSnapshot state) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public LockStateSnapshot load() {
                        return new LockStateSnapshot(LockState.UNLOCKED, "", 0L);
                    }
                });

        assertFalse(manager.isLocked());
    }

    @Test
    void updateNotifiesWithCompleteChangeAndReturnsTrue() {
        LockStateManager manager = new LockStateManager(true);
        List<LockStateChange> changes = new ArrayList<>();
        manager.addStateChangeListener(changes::add);

        UUID actorUuid = UUID.randomUUID();
        boolean changed = manager.updateLocked(false, LockAction.UNLOCK, "Admin", actorUuid);

        assertTrue(changed);
        assertFalse(manager.isLocked());
        assertEquals(1, changes.size());
        LockStateChange change = changes.getFirst();
        assertTrue(change.previousLocked());
        assertFalse(change.newLocked());
        assertEquals(LockAction.UNLOCK, change.action());
        assertEquals("Admin", change.actorName());
        assertEquals(actorUuid, change.actorUuid());
    }

    @Test
    void updateRejectsNoOpChangeWithoutDispatch() {
        LockStateManager manager = new LockStateManager(true);
        AtomicInteger dispatches = new AtomicInteger();
        manager.addStateChangeListener(change -> dispatches.incrementAndGet());

        assertFalse(manager.updateLocked(true, LockAction.LOCK, "System", null));
        assertEquals(0, dispatches.get());
        assertTrue(manager.isLocked());
    }

    @Test
    void updateRejectsNullAction() {
        LockStateManager manager = new LockStateManager(false);

        assertThrows(IllegalArgumentException.class,
                () -> manager.updateLocked(true, null, "System", null));
        assertFalse(manager.isLocked());
    }

    @Test
    void reasonSupplierIsResolvedFreshOnEveryCall() {
        LockStateManager manager = new LockStateManager(false);
        assertEquals("", manager.getLockReason());

        manager.setReasonSupplier(() -> "Closed");
        assertEquals("Closed", manager.getLockReason());
    }

    @Test
    void nullReasonResultFallsBackToEmptyString() {
        LockStateManager manager = new LockStateManager(false);
        manager.setReasonSupplier(() -> null);

        assertEquals("", manager.getLockReason());
    }

    @Test
    void nullReasonSupplierIsRejected() {
        LockStateManager manager = new LockStateManager(false);

        assertThrows(IllegalArgumentException.class, () -> manager.setReasonSupplier(null));
    }

    @Test
    void snapshotPairsStateAndReason() {
        LockStateManager manager = new LockStateManager(true);
        manager.setReasonSupplier(() -> "Maintenance");

        LockStateSnapshot snapshot = manager.getState();
        assertTrue(snapshot.isLocked());
        assertEquals("Maintenance", snapshot.lockReason());
        assertSame(LockState.LOCKED, snapshot.state());
    }

    @Test
    void listenerRegistrationDelegates() {
        LockStateManager manager = new LockStateManager(true);
        List<LockAction> actions = new ArrayList<>();
        LockStateChangeListener listener = change -> actions.add(change.action());

        manager.addStateChangeListener(listener);
        manager.updateLocked(false, LockAction.UNLOCK, "System", null);
        assertEquals(1, actions.size());

        // A second registration of the same instance must not duplicate the
        // changes delivered.
        manager.addStateChangeListener(listener);
        manager.updateLocked(true, LockAction.LOCK, "System", null);
        assertEquals(2, actions.size());

        manager.removeStateChangeListener(listener);
        assertFalse(manager.updateLocked(true, LockAction.LOCK, "System", null));
        assertEquals(2, actions.size());
    }
}
