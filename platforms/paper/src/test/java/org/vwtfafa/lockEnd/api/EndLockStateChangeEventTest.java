package org.vwtfafa.lockEnd.api;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the 3.1 event contract: synchronous, non-cancellable, single
 * handler list, record-style accessors.
 */
class EndLockStateChangeEventTest {

    @Test
    void eventIsSynchronous() {
        EndLockStateChangeEvent event = new EndLockStateChangeEvent(
                false, true, "LOCK", "SomeOne", UUID.randomUUID());

        assertFalse(event.isAsynchronous(), "contract requires a synchronous event");
    }

    @Test
    void eventIsNotCancellable() {
        // instanceof on a final non-cancellable class would not compile, so
        // check the type hierarchy instead: no Cancellable assignment exists.
        assertFalse(Cancellable.class.isAssignableFrom(EndLockStateChangeEvent.class),
                "completed changes must not be cancellable");
    }

    @Test
    void staticHandlerListMatchesInstanceHandlerList() {
        EndLockStateChangeEvent event = new EndLockStateChangeEvent(
                false, true, "UNDO", "SomeOne", UUID.randomUUID());

        HandlerList staticList = EndLockStateChangeEvent.getHandlerList();
        assertSame(staticList, event.getHandlers(),
                "Event requirement: getHandlers() returns the same list as static getHandlerList()");
    }

    @Test
    void accessorsReturnConstructorValues() {
        UUID actorUuid = UUID.randomUUID();
        EndLockStateChangeEvent event = new EndLockStateChangeEvent(
                false, true, "LOCK", "SomeOne", actorUuid);

        assertFalse(event.previousLocked());
        assertTrue(event.newLocked());
        assertEquals("LOCK", event.action());
        assertEquals("SomeOne", event.actorName());
        assertEquals(actorUuid, event.actorUuid());
    }

    @Test
    void scheduledActorCarriesSystemNameWithoutUuid() {
        EndLockStateChangeEvent event = new EndLockStateChangeEvent(
                true, false, "SCHEDULED_UNLOCK", "System", null);

        assertTrue(event.previousLocked());
        assertFalse(event.newLocked());
        assertEquals("SCHEDULED_UNLOCK", event.action());
        assertEquals("System", event.actorName());
        assertNull(event.actorUuid());
    }

    @Test
    void actorFieldsAcceptNull() {
        EndLockStateChangeEvent event = new EndLockStateChangeEvent(
                true, false, "SCHEDULED_UNLOCK", null, null);

        assertNull(event.actorName());
        assertNull(event.actorUuid());
    }
}
