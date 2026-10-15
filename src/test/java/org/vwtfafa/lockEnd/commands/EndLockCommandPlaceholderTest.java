package org.vwtfafa.lockEnd.commands;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against message templates that use placeholders the command never supplies,
 * which would leave raw {@code %tokens%} visible in the stats output.
 */
class EndLockCommandPlaceholderTest {
    private static final List<String> LANGUAGES = List.of("de", "en", "es", "fr", "it", "ja", "ru", "zh");
    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_]+%");

    @Test
    void statsHeaderMessageReceivesEveryPlaceholder() {
        Map<String, String> placeholders = EndLockCommand.statsPlaceholders(3, 7);
        for (String language : LANGUAGES) {
            String raw = load(language).getString("stats-header");
            assertNotNull(raw, "messages_" + language + ".yml is missing stats-header");
            Matcher matcher = PLACEHOLDER.matcher(raw);
            while (matcher.find()) {
                assertTrue(placeholders.containsKey(matcher.group()),
                        "stats-header (" + language + ") uses " + matcher.group()
                                + " but the command does not supply it");
            }
        }
    }

    private static YamlConfiguration load(String language) {
        String resource = "lang/messages_" + language + ".yml";
        try (InputStream input = EndLockCommandPlaceholderTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource + " should be included in the test classpath");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new AssertionError("Could not close language resource " + resource, exception);
        }
    }
}
