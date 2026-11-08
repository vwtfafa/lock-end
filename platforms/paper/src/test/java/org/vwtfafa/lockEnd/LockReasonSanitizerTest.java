package org.vwtfafa.lockEnd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the plain-text stripping for the 3.1 API lock reason: MiniMessage
 * tags and section codes removed, Unicode kept, input trimmed.
 */
class LockReasonSanitizerTest {

    @Test
    void removesMiniMessageTags() {
        assertEquals("Closed", LockReasonSanitizer.sanitize("<red>Closed</red>"));
        assertEquals("Moment please", LockReasonSanitizer.sanitize("<bold>Moment</bold> <i>please"));
    }

    @Test
    void removesSectionCodes() {
        assertEquals("Closed", LockReasonSanitizer.sanitize("§cClosed§r"));
        assertEquals("x", LockReasonSanitizer.sanitize("§cx"));
    }

    @Test
    void removesMixedMarkup() {
        assertEquals("Wartung im End", LockReasonSanitizer.sanitize("§cWartung <bold>im End</bold>"));
    }

    @Test
    void keepsUnicodeText() {
        assertEquals("Geschlossen — Wartung!", LockReasonSanitizer.sanitize("<gold>Geschlossen — Wartung!"));
        assertEquals("日本語", LockReasonSanitizer.sanitize(" 日本語 "));
    }

    @Test
    void preservesAngleBracketTextThatIsNotMiniMessage() {
        assertEquals("Portal <The End>", LockReasonSanitizer.sanitize("Portal <The End>"));
    }

    @Test
    void preservesUnknownSectionSequences() {
        assertEquals("§z is not a legacy formatting code", LockReasonSanitizer.sanitize(
                "§z is not a legacy formatting code"));
    }

    @Test
    void emptyInputStaysEmpty() {
        assertEquals("", LockReasonSanitizer.sanitize(""));
        assertEquals("", LockReasonSanitizer.sanitize("   "));
    }

    @Test
    void nullInputBecomesEmpty() {
        // Defensive contract: stripping must never produce null so the API
        // lock reason can never be null - even with a raw null source.
        assertEquals("", LockReasonSanitizer.sanitize(null));
    }

    @Test
    void plainReasonIsOnlyTrimmed() {
        assertEquals("Untouched", LockReasonSanitizer.sanitize(" Untouched "));
    }
}
