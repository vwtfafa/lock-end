package org.vwtfafa.lockend.core;

import org.junit.jupiter.api.Test;
import org.vwtfafa.lockend.api.LockAction;
import org.vwtfafa.lockend.api.LockStateChangeListener;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the dispatcher contract: order, duplicate registration, removal
 * and failure propagation.
 */
class StateChangeDispatcherTest {

    @Test
    void notifiesEveryListenerInRegistrationOrder() {
        StateChangeDispatcher dispatcher = new StateChangeDispatcher();
        List<String> order = new ArrayList<>();
        dispatcher.addListener(change -> order.add("first"));
        dispatcher.addListener(change -> order.add("second"));

        dispatcher.notifyListeners(new org.vwtfafa.lockend.api.LockStateChange(
                true, false, LockAction.UNLOCK, "System", null));

        assertEquals(List.of("first", "second"), order);
    }

    @Test
    void duplicateRegistrationIsIgnored() {
        StateChangeDispatcher dispatcher = new StateChangeDispatcher();
        List<LockAction> actions = new ArrayList<>();
        LockStateChangeListener listener = change -> actions.add(change.action());

        dispatcher.addListener(listener);
        dispatcher.addListener(listener);

        dispatcher.notifyListeners(new org.vwtfafa.lockend.api.LockStateChange(
                false, true, LockAction.LOCK, "System", null));

        assertEquals(1, actions.size());
    }

    @Test
    void removalStopsFutureDelivery() {
        StateChangeDispatcher dispatcher = new StateChangeDispatcher();
        List<LockAction> actions = new ArrayList<>();
        LockStateChangeListener listener = change -> actions.add(change.action());
        dispatcher.addListener(listener);

        dispatcher.notifyListeners(new org.vwtfafa.lockend.api.LockStateChange(
                false, true, LockAction.LOCK, "System", null));
        dispatcher.removeListener(listener);
        dispatcher.notifyListeners(new org.vwtfafa.lockend.api.LockStateChange(
                true, false, LockAction.UNDO, "System", null));

        assertEquals(1, actions.size());
        assertSame(LockAction.LOCK, actions.getFirst());
    }

    @Test
    void failuresPropagateToCaller() {
        StateChangeDispatcher dispatcher = new StateChangeDispatcher();
        dispatcher.addListener(change -> {
            throw new IllegalStateException("listener failure");
        });

        assertThrows(IllegalStateException.class, () -> dispatcher.notifyListeners(
                new org.vwtfafa.lockend.api.LockStateChange(
                        false, true, LockAction.LOCK, "System", null)));
    }

    @Test
    void nullArgumentsAreRejected() {
        StateChangeDispatcher dispatcher = new StateChangeDispatcher();

        assertThrows(IllegalArgumentException.class, () -> dispatcher.addListener(null));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.removeListener(null));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.notifyListeners(null));
    }
}
