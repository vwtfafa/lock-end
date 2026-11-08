package org.vwtfafa.lockend.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the dimension-scope rule matrix that mirrors the Paper
 * {@code EndAccessGate} world-scope semantics on dimension keys: the default
 * End dimension is always guarded, configured keys add guarded dimensions,
 * and travel inside the guarded scope is not an entry attempt.
 */
class EndDimensionScopeTest {

    private static final EndDimensionScope DEFAULT = new EndDimensionScope(List.of());
    private static final EndDimensionScope SCOPED =
            new EndDimensionScope(List.of("resource_end", "custom:maintenance_end"));

    @Test
    void defaultEndIsAlwaysGuarded() {
        assertTrue(DEFAULT.isGuarded("minecraft:the_end"));
        assertTrue(SCOPED.isGuarded("minecraft:the_end"));
    }

    @Test
    void scopedEntriesMatchFullKeysAndBarePaths() {
        assertTrue(SCOPED.isGuarded("minecraft:resource_end"));
        assertTrue(SCOPED.isGuarded("custom:resource_end"));
        assertTrue(SCOPED.isGuarded("custom:maintenance_end"));
        assertFalse(SCOPED.isGuarded("minecraft:maintenance_end"));
        assertTrue(DEFAULT.isGuarded("minecraft:the_end"));
        assertFalse(DEFAULT.isGuarded("custom:maintenance_end"));
    }

    @Test
    void scopeMatchingIsCaseInsensitive() {
        assertTrue(SCOPED.isGuarded("MINECRAFT:RESOURCE_END"));
        assertTrue(DEFAULT.isGuarded("MINECRAFT:THE_END"));
    }

    @Test
    void nonEndTargetsAreNeverGuarded() {
        assertFalse(DEFAULT.isGuarded("minecraft:the_nether"));
        assertFalse(SCOPED.isGuarded("minecraft:overworld"));
        assertFalse(DEFAULT.isGuarded(null));
    }

    @Test
    void leavingTheEndIsNeverAnEntryAttempt() {
        assertFalse(DEFAULT.isEntryAttempt("minecraft:the_end", "minecraft:overworld"));
    }

    @Test
    void arrivalFromTheOverworldIsAnEntryAttempt() {
        assertTrue(DEFAULT.isEntryAttempt("minecraft:overworld", "minecraft:the_end"));
    }

    @Test
    void travelInsideTheGuardedScopeIsNotAnEntryAttempt() {
        assertFalse(DEFAULT.isEntryAttempt("minecraft:the_end", "minecraft:the_end"));
        assertFalse(SCOPED.isEntryAttempt("minecraft:resource_end", "minecraft:the_end"));
        assertFalse(SCOPED.isEntryAttempt("minecraft:the_end", "minecraft:resource_end"));
    }

    @Test
    void travelFromAnUnguardedEndDimensionIsStillAnEntryAttempt() {
        // The vanilla End is the only guarded dimension by default, so a
        // custom "other_end" dimension is unguarded: moving from it into the
        // End is an entry attempt (Paper: from unscoped End world).
        assertTrue(DEFAULT.isEntryAttempt("custom:other_end", "minecraft:the_end"));
        assertTrue(SCOPED.isEntryAttempt("custom:other_end", "minecraft:resource_end"));
    }

    @Test
    void nullOriginCountsAsUnguarded() {
        assertTrue(DEFAULT.isEntryAttempt(null, "minecraft:the_end"));
        assertFalse(DEFAULT.isEntryAttempt(null, "minecraft:overworld"));
    }
}
