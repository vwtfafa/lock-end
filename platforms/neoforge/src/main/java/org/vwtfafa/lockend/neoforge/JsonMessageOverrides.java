package org.vwtfafa.lockend.neoforge;

import org.vwtfafa.lockend.core.messages.ModMessages;

import java.nio.file.Path;
import java.util.Map;

/**
 * Loads and stores the shared message overrides file
 * ({@code config/lock-end-messages.json}). The file maps language keys
 * (mirroring the Paper plugin's key names) to plain-text replacements with
 * {@code %placeholder%} tokens. A missing file is seeded once with the
 * built-in English defaults so admins discover the keys; a corrupt file
 * falls back to the defaults with a warning instead of failing the boot.
 */
final class JsonMessageOverrides {

    private static final Path FILE = Path.of("lock-end-messages.json");

    private JsonMessageOverrides() {
    }

    /**
     * @return the overrides file name
     */
    static String fileName() {
        return FILE.toString();
    }

    /**
     * Writes the built-in English defaults when the overrides file does not
     * exist yet. An existing file is never touched, so admin edits survive.
     *
     * @param configDirectory the mod's config directory
     * @param messages        the renderer providing the default texts
     */
    static void writeDefaultsIfMissing(Path configDirectory, ModMessages messages) {
        Path file = configDirectory.resolve(FILE);
        if (java.nio.file.Files.exists(file)) {
            return;
        }
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .setPrettyPrinting().disableHtmlEscaping().create();
        JsonFileIO.writeAtomically(file, gson.toJson(messages.defaults()),
                LockEndNeoForgeMod.LOGGER);
    }

    /**
     * Reads the override table. An empty map means "no overrides" - missing
     * and unreadable files behave identically so rendering keeps working.
     *
     * @param configDirectory the mod's config directory
     * @return the overrides; never {@code null}
     */
    static Map<String, String> load(Path configDirectory) {
        Path file = configDirectory.resolve(FILE);
        if (!java.nio.file.Files.exists(file)) {
            return Map.of();
        }
        try {
            String json = java.nio.file.Files.readString(file);
            Map<String, String> parsed = parse(json);
            if (parsed == null) {
                return Map.of();
            }
            Map<String, String> overrides = new java.util.HashMap<>();
            parsed.forEach((key, value) -> {
                if (key != null && value != null && !value.isBlank()) {
                    overrides.put(key, value);
                }
            });
            return Map.copyOf(overrides);
        } catch (java.io.IOException | com.google.gson.JsonParseException exception) {
            LockEndNeoForgeMod.LOGGER.warn(
                    "lock-end-messages.json could not be read; using built-in defaults", exception);
            return Map.of();
        }
    }

    private static Map<String, String> parse(String json) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        return gson.fromJson(json,
                new com.google.gson.reflect.TypeToken<Map<String, String>>() { }.getType());
    }
}
