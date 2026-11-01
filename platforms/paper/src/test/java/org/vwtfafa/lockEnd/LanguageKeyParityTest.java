package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LanguageKeyParityTest {
    private static final List<String> LANGUAGES = List.of("de", "en", "es", "fr", "it", "ja", "ru", "zh");

    @Test
    void everyLanguageFileHasTheSameKeys() {
        Set<String> reference = keysOf("en");
        assertFalse(reference.isEmpty(), "messages_en.yml should define message keys");
        for (String language : LANGUAGES) {
            assertEquals(reference, keysOf(language),
                    "lang/messages_" + language + ".yml keys differ from messages_en.yml");
        }
    }

    @Test
    void remainingPlaceholderMessagesArePlainText() {
        for (String language : LANGUAGES) {
            YamlConfiguration messages = load(language);
            String unlocked = messages.getString("remaining-unlocked");
            String permanent = messages.getString("remaining-permanent");
            assertNotNull(unlocked, "messages_" + language + ".yml is missing remaining-unlocked");
            assertNotNull(permanent, "messages_" + language + ".yml is missing remaining-permanent");
            assertFalse(unlocked.contains("§"), "remaining-unlocked should be plain text for PlaceholderAPI");
            assertFalse(permanent.contains("§"), "remaining-permanent should be plain text for PlaceholderAPI");
        }
    }

    @Test
    void bundledLanguageFilesUseMiniMessageInsteadOfLegacyCodes() {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        for (String language : LANGUAGES) {
            YamlConfiguration messages = load(language);
            for (String key : messages.getKeys(true)) {
                String value = messages.getString(key);
                if (value == null) {
                    continue;
                }
                assertFalse(value.contains("§"),
                        "lang/messages_" + language + ".yml uses legacy color codes in " + key);
                assertDoesNotThrow(() -> miniMessage.deserialize(value),
                        "lang/messages_" + language + ".yml has invalid MiniMessage in " + key);
            }
        }
    }

    private static Set<String> keysOf(String language) {
        return new TreeSet<>(load(language).getKeys(false));
    }

    private static YamlConfiguration load(String language) {
        String resource = "lang/messages_" + language + ".yml";
        try (InputStream input = LanguageKeyParityTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource + " should be included in the test classpath");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new AssertionError("Could not close language resource " + resource, exception);
        }
    }
}
