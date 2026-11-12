package org.vwtfafa.lockend.core.messages;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the shared message registry: default resolution, override
 * precedence and literal placeholder rendering.
 */
class ModMessagesTest {

    @Test
    void defaultsResolveKnownKeysLiterally() {
        ModMessages messages = new ModMessages();

        assertEquals("The End is currently locked.", messages.text(
                "status", Map.of("%status%", "locked")));
        assertEquals("Lock the End before evacuating players.", messages.text(
                "evacuation-not-locked"));
        assertTrue(messages.knows("broadcast-locked"));
        assertFalse(messages.knows("not-a-key"));
    }

    @Test
    void reasonUsageDocumentsSetAndClear() {
        ModMessages messages = new ModMessages();

        String usage = messages.text("reason-usage");
        assertTrue(usage.contains("reason set"), usage);
        assertTrue(usage.contains("clear"), usage);
    }

    @Test
    void overridesWinOverDefaults() {
        ModMessages messages = new ModMessages();
        messages.setOverrides(Map.of("status", "Endestatus: %status%"));

        assertEquals("Endestatus: locked", messages.text(
                "status", Map.of("%status%", "locked")));
    }

    @Test
    void unknownKeysResolveToTheKeyItself() {
        ModMessages messages = new ModMessages();

        assertEquals("custom.server.key", messages.text("custom.server.key"));
    }

    @Test
    void nullOverrideMapClearsTheTable() {
        ModMessages messages = new ModMessages();
        messages.setOverrides(Map.of("status", "overridden: %status%"));
        messages.setOverrides(null);

        assertEquals("The End is currently locked.", messages.text(
                "status", Map.of("%status%", messages.text("closed"))));
    }

    @Test
    void placeholderRenderingIsLiteralAndOrderIndependent() {
        assertEquals("Move out in 10 seconds because it is locked.",
                ModMessages.render("Move out in %seconds% seconds because it is locked.",
                        Map.of("%seconds%", "10")));
    }

    @Test
    void renderingNeverReparsesInjectedValues() {
        // %player% containing markup stays literal text everywhere.
        assertEquals("locked by <red><script/>",
                ModMessages.render("locked by %player%", Map.of("%player%", "<red><script/>")));
    }
}
