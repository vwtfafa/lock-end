package org.vwtfafa.lockend.core.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the operational counters and their revision bookkeeping.
 */
class LockStatisticsTest {

    @Test
    void enabledStatisticsCountAllEventKinds() {
        LockStatistics stats = new LockStatistics(true);
        stats.recordStateChange(true);
        stats.recordStateChange(false);
        stats.recordStateChange(true);
        stats.recordBlockedAttempt();
        stats.recordEvacuatedPlayer();

        assertEquals(2, stats.lockCount());
        assertEquals(1, stats.unlockCount());
        assertEquals(1, stats.blockedCount());
        assertEquals(1, stats.evacuatedCount());
        assertEquals(5, stats.revision());
    }

    @Test
    void disabledStatisticsKeepCountersAndRevision() {
        LockStatistics stats = new LockStatistics(false);
        stats.recordStateChange(true);
        stats.recordBlockedAttempt();
        stats.recordEvacuatedPlayer();

        assertEquals(0, stats.lockCount());
        assertEquals(0, stats.blockedCount());
        assertEquals(0, stats.evacuatedCount());
        assertEquals(0, stats.revision());
        assertFalse(stats.isEnabled());
    }

    @Test
    void togglingRecordingMidwayKeepsHistoryOfExistingCounts() {
        LockStatistics stats = new LockStatistics(true);
        stats.recordStateChange(true);
        stats.setEnabled(false);
        stats.recordStateChange(false);

        assertEquals(1, stats.lockCount());
        assertEquals(0, stats.unlockCount());
        assertEquals(1, stats.revision());
    }

    @Test
    void loadingCountsRestoresAbsoluteValues() {
        LockStatistics stats = new LockStatistics(true);
        stats.recordStateChange(true);
        stats.loadCounts(41, 7, 13, 2);

        assertEquals(41, stats.lockCount());
        assertEquals(7, stats.unlockCount());
        assertEquals(13, stats.blockedCount());
        assertEquals(2, stats.evacuatedCount());
    }

    @Test
    void negativeLoadedCountsAreClamped() {
        LockStatistics stats = new LockStatistics(true);
        stats.loadCounts(-5, 0, -1, 4);

        assertEquals(0, stats.lockCount());
        assertEquals(0, stats.blockedCount());
        assertEquals(4, stats.evacuatedCount());
        assertTrue(stats.isEnabled());
    }
}
