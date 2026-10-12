package org.vwtfafa.lockEnd;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvacuationMessageKeysTest {
    private static final List<String> LANGUAGES = List.of("de", "en", "es", "fr", "it", "ja", "ru", "zh");
    private static final List<String> REQUIRED_KEYS = List.of(
            "evacuation-disabled",
            "evacuation-not-locked",
            "evacuation-target-unavailable",
            "evacuation-preview",
            "evacuation-preview-required",
            "evacuation-preview-expired",
            "evacuation-preview-changed",
            "evacuation-execution-started"
    );

    @Test
    void everyLanguageHasTheEndEvacuationMessages() {
        for (String language : LANGUAGES) {
            String resource = "lang/messages_" + language + ".yml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource + " should be included in the test classpath");
                YamlConfiguration messages = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(input, StandardCharsets.UTF_8));
                for (String key : REQUIRED_KEYS) {
                    assertNotNull(messages.getString(key), resource + " is missing " + key);
                }
                assertTrue(messages.getString("evacuation-preview").contains("%count%"));
                assertTrue(messages.getString("evacuation-preview").contains("%world%"));
                assertTrue(messages.getString("evacuation-preview").contains("%bypassed%"));
                assertTrue(messages.getString("evacuation-preview-changed").contains("%count%"));
                assertTrue(messages.getString("evacuation-preview-changed").contains("%world%"));
                assertTrue(messages.getString("evacuation-preview-changed").contains("%bypassed%"));
            } catch (java.io.IOException exception) {
                throw new AssertionError("Could not close language resource " + resource, exception);
            }
        }
    }
}
