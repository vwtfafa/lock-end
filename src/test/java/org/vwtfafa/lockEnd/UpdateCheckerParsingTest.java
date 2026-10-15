package org.vwtfafa.lockEnd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the JSON parsing used by the update checker.
 */
class UpdateCheckerParsingTest {

    @Test
    void readsTheTagNameFromAReleasePayload() {
        String payload = """
                {
                  "url": "https://api.github.com/repos/vwtfafa/lock-end/releases/123",
                  "tag_name": "v3.0.0",
                  "name": "EndLock 3.0.0",
                  "prerelease": false
                }
                """;
        assertEquals("v3.0.0", UpdateChecker.parseTagName(payload));
    }

    @Test
    void missingFieldsAndMalformedPayloadsYieldNoTag() {
        assertNull(UpdateChecker.parseTagName("{\"message\":\"Not Found\"}"));
        assertNull(UpdateChecker.parseTagName("{\"tag_name\":null}"));
        assertNull(UpdateChecker.parseTagName("{\"tag_name\":42}"));
        assertNull(UpdateChecker.parseTagName("[1, 2, 3]"));
        assertNull(UpdateChecker.parseTagName("not json"));
        assertNull(UpdateChecker.parseTagName(""));
        assertNull(UpdateChecker.parseTagName(null));
    }
}
