package org.vwtfafa.lockEnd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the semantic version comparison used by the update checker.
 */
class UpdateCheckerVersionTest {

    @Test
    void equalVersionsAreNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("1.2.0", "1.2.0"));
    }

    @Test
    void higherPatchIsNewer() {
        assertTrue(UpdateChecker.isNewerVersion("1.2.1", "1.2.0"));
        assertFalse(UpdateChecker.isNewerVersion("1.2.0", "1.2.1"));
    }

    @Test
    void comparisonIsNumericNotLexicographic() {
        assertTrue(UpdateChecker.isNewerVersion("1.10", "1.9"));
        assertFalse(UpdateChecker.isNewerVersion("1.9", "1.10"));
    }

    @Test
    void vPrefixIsIgnored() {
        assertTrue(UpdateChecker.isNewerVersion("v1.3", "1.2"));
        assertFalse(UpdateChecker.isNewerVersion("V1.2", "1.3"));
    }

    @Test
    void prereleaseSuffixIsIgnored() {
        assertFalse(UpdateChecker.isNewerVersion("2.0.0", "2.0.0-SNAPSHOT"));
        assertTrue(UpdateChecker.isNewerVersion("2.0.0-beta.1", "2.0.0"));
    }

    @Test
    void missingSegmentsAreTreatedAsZero() {
        assertFalse(UpdateChecker.isNewerVersion("2.0", "2.0.0"));
        assertTrue(UpdateChecker.isNewerVersion("2.0.1", "2.0"));
    }

    @Test
    void unparseableVersionsNeverReportAnUpdate() {
        assertFalse(UpdateChecker.isNewerVersion("not-a-version", "1.0"));
        assertFalse(UpdateChecker.isNewerVersion("1.x.3", "1.0"));
    }
}
