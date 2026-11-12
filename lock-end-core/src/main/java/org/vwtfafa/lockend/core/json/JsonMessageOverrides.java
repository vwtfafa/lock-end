package org.vwtfafa.lockend.core.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.vwtfafa.lockend.core.messages.ModMessages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and stores the shared message overrides file
 * ({@code config/lock-end-messages.json}). The file maps language keys
 * (mirroring the Paper plugin's key names) to plain-text replacements with
 * {@code %placeholder%} tokens. A missing file is seeded once with the
 * built-in English defaults so admins discover the keys; a corrupt file
 * falls back to the defaults with a warning instead of failing the boot.
 */
public final class JsonMessageOverrides {

    private static final Path FILE = Path.of("lock-end-messages.json");

    private JsonMessageOverrides() {
    }

    /**
     * @return the overrides file name
     */
    public static String fileName() {
        return FILE.toString();
    }

    /**
     * Writes the built-in English defaults when the overrides file does not
     * exist yet. An existing file is never touched, so admin edits survive.
     *
     * @param configDirectory the mod's config directory
     * @param messages        the renderer providing the default texts
     * @param logger          log sink for write failures
     */
    public static void writeDefaultsIfMissing(Path configDirectory, ModMessages messages,
                                              Logger logger) {
        Path file = configDirectory.resolve(FILE);
        if (Files.exists(file)) {
            return;
        }
        Gson gson = new GsonBuilder()
                .setPrettyPrinting().disableHtmlEscaping().create();
        JsonFileIO.writeAtomically(file, gson.toJson(messages.defaults()), logger);
    }

    /**
     * Reads the override table. An empty map means "no overrides" - missing
     * and unreadable files behave identically so rendering keeps working.
     *
     * @param configDirectory the mod's config directory
     * @param logger          log sink for read failures
     * @return the overrides; never {@code null}
     */
    public static Map<String, String> load(Path configDirectory, Logger logger) {
        Path file = configDirectory.resolve(FILE);
        if (!Files.exists(file)) {
            return Map.of();
        }
        try {
            String json = Files.readString(file);
            Map<String, String> parsed = parse(json);
            if (parsed == null) {
                return Map.of();
            }
            Map<String, String> overrides = new HashMap<>();
            parsed.forEach((key, value) -> {
                if (key != null && value != null && !value.isBlank()) {
                    overrides.put(key, value);
                }
            });
            return Map.copyOf(overrides);
        } catch (IOException | JsonParseException exception) {
            logger.warn(
                    "lock-end-messages.json could not be read; using built-in defaults", exception);
            return Map.of();
        }
    }

    private static Map<String, String> parse(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, new TypeToken<Map<String, String>>() { }.getType());
    }
}
