package org.vwtfafa.lockend.core.api;

import org.junit.jupiter.api.Test;
import org.vwtfafa.lockend.api.LockAction;
import org.vwtfafa.lockend.api.LockEndApi;
import org.vwtfafa.lockend.api.LockStateChangeListener;
import org.vwtfafa.lockend.core.LockStateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the shared 4.x API provider purely delegates the runtime
 * state to the shared core manager and applies the same plain text stripping
 * rule as the legacy provider (suppliers keep the test free of plugin
 * instantiation).
 */
class SharedLockEndApiProviderTest {

    @Test
    void isLockedDelegatesToStateManager() {
        LockStateManager manager = new LockStateManager(true);
        LockEndApi api = new SharedLockEndApiProvider(manager, () -> "Closed");
        assertTrue(api.isLocked());

        api = new SharedLockEndApiProvider(new LockStateManager(false), () -> "Closed");
        assertFalse(api.isLocked());
    }

    @Test
    void lockReasonIsStripped() {
        LockEndApi api = new SharedLockEndApiProvider(new LockStateManager(true),
                () -> " <red>§cWartung</red> ");

        assertEquals("Wartung", api.getLockReason());
    }

    @Test
    void lockReasonNeverNullOnNullSource() {
        LockEndApi api = new SharedLockEndApiProvider(new LockStateManager(false), () -> null);

        assertEquals("", api.getLockReason());
    }

    @Test
    void getStatePairsStateAndStrippedReason() {
        LockStateManager manager = new LockStateManager(true);
        LockEndApi api = new SharedLockEndApiProvider(manager, () -> "<red>§cWartung</red>");

        var snapshot = api.getState();
        assertTrue(snapshot.isLocked());
        assertEquals("Wartung", snapshot.lockReason());
    }

    @Test
    void listenersAreManagedByTheCoreState() {
        LockStateManager manager = new LockStateManager(true);
        LockEndApi api = new SharedLockEndApiProvider(manager, () -> "");
        List<LockAction> actions = new ArrayList<>();
        LockStateChangeListener listener = change -> actions.add(change.action());

        api.addStateChangeListener(listener);
        manager.updateLocked(false, LockAction.UNLOCK, "System", null);
        assertEquals(1, actions.size());
        assertEquals(LockAction.UNLOCK, actions.getFirst());

        // Duplicate registrations must not double-dispatch, and removal must
        // stop delivery (core contract, exercised through the adapter).
        api.addStateChangeListener(listener);
        UUID actorUuid = UUID.randomUUID();
        manager.updateLocked(true, LockAction.LOCK, "Admin", actorUuid);
        assertEquals(2, actions.size());

        api.removeStateChangeListener(listener);
        assertFalse(manager.updateLocked(true, LockAction.LOCK, "System", null));
        assertEquals(2, actions.size());
    }
}
