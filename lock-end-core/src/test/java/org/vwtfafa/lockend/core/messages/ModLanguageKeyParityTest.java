package org.vwtfafa.lockend.core.messages;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Ensures every bundled mod language pack matches {@link ModMessages#defaults()}
 * and stays plain text (no MiniMessage / legacy color codes).
 */
class ModLanguageKeyParityTest {

    private static final Gson GSON = new Gson();

    @Test
    void everyBundledLanguageMatchesDefaultKeysAndStaysPlain() throws IOException {
        Set<String> expected = new ModMessages().defaults().keySet();
        for (String language : ModMessages.SUPPORTED_LANGUAGES) {
            Map<String, String> pack = loadPack(language);
            assertEquals(expected, pack.keySet(), "key set mismatch for " + language);
            for (Map.Entry<String, String> entry : pack.entrySet()) {
                String value = entry.getValue();
                assertNotNull(value, language + "/" + entry.getKey());
                assertFalse(value.isBlank(), language + "/" + entry.getKey());
                assertFalse(value.indexOf('§') >= 0, "legacy code in " + language + "/" + entry.getKey());
                assertFalse(value.matches("(?s).*<(?:red|green|gold|gradient)[^>]*>.*"),
                        "MiniMessage tag in " + language + "/" + entry.getKey() + ": " + value);
            }
        }
    }

    private static Map<String, String> loadPack(String language) throws IOException {
        String resource = "lang/messages_" + language + ".json";
        try (InputStream stream = ModLanguageKeyParityTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(stream, "missing classpath resource " + resource);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Map<String, String> parsed = GSON.fromJson(
                        reader, new TypeToken<Map<String, String>>() { }.getType());
                assertNotNull(parsed, resource);
                return parsed;
            }
        }
    }
}
