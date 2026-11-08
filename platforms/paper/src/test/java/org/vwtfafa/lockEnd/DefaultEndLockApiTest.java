package org.vwtfafa.lockEnd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the read-only API provider purely delegates the lock state
 * and applies plain text stripping to the raw reason (suppliers keep the test
 * free of plugin instantiation).
 */
class DefaultEndLockApiTest {

    @Test
    void isLockedDelegatesToState() {
        DefaultEndLockApi lockedApi = new DefaultEndLockApi(() -> true, () -> "Closed");
        assertTrue(lockedApi.isLocked());

        DefaultEndLockApi unlockedApi = new DefaultEndLockApi(() -> false, () -> "Closed");
        assertFalse(unlockedApi.isLocked());
    }

    @Test
    void lockReasonIsStripped() {
        DefaultEndLockApi api = new DefaultEndLockApi(() -> true,
                () -> "<red>§cWartung</red>");

        assertEquals("Wartung", api.getLockReason());
    }

    @Test
    void lockReasonNeverNullEvenOnNullSource() {
        // Installed in production with a supplier backed by the effective
        // reason; a null result still must not leak through the API.
        DefaultEndLockApi api = new DefaultEndLockApi(() -> false, () -> null);

        assertEquals("", api.getLockReason());
    }

    @Test
    void unicodeReasonSurvivesStripping() {
        DefaultEndLockApi api = new DefaultEndLockApi(() -> true,
                () -> " Geschlossen — 日本語 ");

        assertEquals("Geschlossen — 日本語", api.getLockReason());
    }
}
