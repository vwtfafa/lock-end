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
import java.util.Objects;

/**
 * Loads and stores the shared message overrides file
 * ({@code config/lock-end-messages.json}). The file maps language keys
 * (mirroring the Paper plugin's key names) to plain-text replacements with
 * {@code %placeholder%} tokens. A missing file is seeded once as an empty
 * object so admins can add sparse overrides without freezing the bundled
 * language packs to English; a corrupt file falls back to no overrides with
 * a warning instead of failing the boot.
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
     * Writes an empty override object when the overrides file does not exist
     * yet. An existing file is never touched, so admin edits survive. The
     * empty seed keeps bundled language packs effective until an admin adds
     * sparse key overrides.
     *
     * @param configDirectory the mod's config directory
     * @param messages        retained for call-site compatibility; unused
     * @param logger          log sink for write failures
     */
    public static void writeDefaultsIfMissing(Path configDirectory, ModMessages messages,
                                              Logger logger) {
        Objects.requireNonNull(messages, "messages");
        Path file = configDirectory.resolve(FILE);
        if (Files.exists(file)) {
            return;
        }
        Gson gson = new GsonBuilder()
                .setPrettyPrinting().disableHtmlEscaping().create();
        JsonFileIO.writeAtomically(file, gson.toJson(Map.of()), logger);
    }

    /**
     * Reads the override table. An empty map means "no overrides" - missing
     * and unreadable files behave identically so rendering keeps working.
     *
     * <p>Entries whose value is exactly the built-in English default are
     * ignored. Earlier versions seeded the whole English table into this
     * file; without this filter those installs would keep English text and
     * never show their configured language. A deliberate override that
     * repeats the English default text therefore has no effect outside
     * English.</p>
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
                if (key != null && value != null && !value.isBlank()
                        && !ModMessages.isBuiltInDefault(key, value)) {
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
