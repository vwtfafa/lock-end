package org.vwtfafa.lockend.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Structural validator for the Lock-End vanilla datapack. Vanilla loads
 * datapack resources lazily and inconsistently given command failures, so
 * the build validates the pack itself:
 *
 * <ul>
 *   <li>{@code pack.mcmeta} parses and carries the current format bounds;</li>
 *   <li>the {@code minecraft:load}/{@code minecraft:tick} function tags
 *       parse and every namespace:function value resolves to a real
 *       {@code .mcfunction} file;</li>
 *   <li>functions called from other functions resolve to real files;</li>
 *   <li>every shipped {@code .mcfunction} file is reachable: either listed
 *       in a tag, called by another function or be a documented user-facing
 *       entry function ({@code lockend:lock}, {@code lockend:unlock},
 *       {@code lockend:status}, {@code lockend:set_spawn}).</li>
 * </ul>
 *
 * <p>This cannot prove command-level correctness (that needs a running
 * vanilla server) but catches the usual packaging typos.</p>
 */
public final class PackValidator {

    private static final Pattern FUNCTION_REFERENCE = Pattern.compile(
            "function\\s+([a-z0-9_.-]+):([a-z0-9_.-]+(?:/[a-z0-9_.-]+)*)");
    private static final Pattern PACK_FORMAT_NUMBER = Pattern.compile("-?\\d+");
    /**
     * Functions the player runs directly with {@code /function lockend:...};
     * they are deliberately not part of any function tag.
     */
    private static final Set<String> ENTRY_FUNCTIONS = Set.of(
            "lockend:lock", "lockend:unlock", "lockend:status", "lockend:set_spawn");

    /**
     * Validates the pack structure under the given root directory.
     *
     * @param packRoot the directory holding {@code pack.mcmeta} and
     *                 {@code data/}; may be a directory that gets zipped
     *                 later
     * @return the list of problems; empty when the pack is structurally
     *         sound
     */
    public static List<String> validate(Path packRoot) {
        List<String> problems = new ArrayList<>();
        Path mcmeta = packRoot.resolve("pack.mcmeta");
        if (!Files.isRegularFile(mcmeta)) {
            problems.add("missing pack.mcmeta");
        } else {
            validatePackMcmeta(mcmeta, problems);
        }

        TreeSet<String> functionFiles = collectFunctionFiles(packRoot, problems);
        TreeSet<String> referenced = new TreeSet<>();
        referenced.addAll(collectTagValues(packRoot, problems));
        referenced.addAll(collectFunctionCalls(packRoot, problems));
        for (String id : referenced) {
            if (!functionFiles.contains(id)) {
                problems.add("referenced function does not exist: " + id);
            }
        }
        for (String id : functionFiles) {
            if (!referenced.contains(id) && !ENTRY_FUNCTIONS.contains(id)) {
                problems.add("unreferenced function file: " + id + " (add it to a tag, call it or list it as an entry function)");
            }
        }
        return problems;
    }

    /**
     * Validates the given pack and exits with a nonzero status when problems
     * exist, so Gradle JavaExec and CI fail on packaging mistakes.
     *
     * @param args args[0] is the path of the pack root directory
     * @throws UncheckedIOException when reading the pack fails outside of a
     *                              recorded problem
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: PackValidator <pack-root-directory>");
            System.exit(2);
        }
        Path packRoot = Path.of(args[0]);
        if (!Files.isDirectory(packRoot)) {
            System.err.println("not a directory: " + packRoot);
            System.exit(2);
        }
        List<String> problems = validate(packRoot);
        if (problems.isEmpty()) {
            System.out.println("Lock-End datapack validation: OK (" + packRoot + ")");
            return;
        }
        System.err.println("Lock-End datapack validation: FAILED");
        for (String problem : problems) {
            System.err.println("  - " + problem);
        }
        System.exit(1);
    }

    private static void validatePackMcmeta(Path mcmeta, List<String> problems) {
        try {
            JsonObject root = parseJson(mcmeta).getAsJsonObject();
            JsonObject pack = root.getAsJsonObject("pack");
            if (pack == null
                    || !hasNumberField(pack, "min_format")
                    || !hasNumberField(pack, "max_format")
                    || !pack.has("description")) {
                problems.add("pack.mcmeta must contain 'pack' with 'min_format', 'max_format' and 'description'");
            }
        } catch (IOException | RuntimeException exception) {
            problems.add("pack.mcmeta is not valid JSON: " + exception.getMessage());
        }
    }

    private static boolean hasNumberField(JsonObject object, String name) {
        JsonElement field = object.get(name);
        return field != null && field.isJsonPrimitive()
                && PACK_FORMAT_NUMBER.matcher(field.getAsString().strip()).matches();
    }

    private static Set<String> collectTagValues(Path packRoot, List<String> problems) {
        Set<String> referenced = new TreeSet<>();
        for (String tagName : List.of("load", "tick")) {
            Path tagFile = packRoot.resolve("data/minecraft/tags/function/" + tagName + ".json");
            if (!Files.isRegularFile(tagFile)) {
                problems.add("missing function tag file: data/minecraft/tags/function/" + tagName + ".json");
                continue;
            }
            try {
                JsonObject tag = parseJson(tagFile).getAsJsonObject();
                JsonArray values = tag.getAsJsonArray("values");
                if (values == null) {
                    problems.add(tagName + " tag does not contain a 'values' array");
                    continue;
                }
                for (JsonElement value : values) {
                    referenced.add(value.getAsString().strip().toLowerCase(Locale.ROOT));
                }
            } catch (IOException | RuntimeException exception) {
                problems.add(tagName + " tag is not valid JSON: " + exception.getMessage());
            }
        }
        return referenced;
    }

    private static Set<String> collectFunctionCalls(Path packRoot, List<String> problems) {
        Set<String> referenced = new TreeSet<>();
        TreeSet<String> functionFiles = collectFunctionFiles(packRoot, problems);
        for (String id : functionFiles) {
            Path file = functionFile(packRoot, id);
            try {
                Matcher matcher = FUNCTION_REFERENCE.matcher(Files.readString(file));
                while (matcher.find()) {
                    referenced.add(matcher.group(1) + ":" + matcher.group(2));
                }
            } catch (IOException exception) {
                problems.add("cannot read function file " + id + ": " + exception.getMessage());
            }
        }
        return referenced;
    }

    private static TreeSet<String> collectFunctionFiles(Path packRoot, List<String> problems) {
        TreeSet<String> files = new TreeSet<>();
        Path dataDir = packRoot.resolve("data");
        if (!Files.isDirectory(dataDir)) {
            problems.add("missing 'data' directory");
            return files;
        }
        try (Stream<Path> walk = Files.walk(dataDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".mcfunction"))
                    .forEach(path -> {
                        String id = toFunctionId(packRoot, path);
                        if (id != null) {
                            files.add(id);
                        } else {
                            problems.add("function file outside a namespace function directory: " + packRoot.relativize(path));
                        }
                    });
        } catch (IOException exception) {
            problems.add("cannot list data files: " + exception.getMessage());
        }
        return files;
    }

    private static String toFunctionId(Path packRoot, Path file) {
        // <packRoot>/data/<namespace>/function/<path>.mcfunction
        Path relative = packRoot.relativize(file);
        if (relative.getNameCount() < 4
                || !"data".equals(relative.getName(0).toString())
                || !"function".equals(relative.getName(2).toString())) {
            return null;
        }
        String namespace = relative.getName(1).toString();
        String rest = relative.subpath(3, relative.getNameCount()).toString().replace('\\', '/');
        return namespace + ":" + rest.substring(0, rest.length() - ".mcfunction".length());
    }

    private static Path functionFile(Path packRoot, String id) {
        int separator = id.indexOf(':');
        String namespace = id.substring(0, separator);
        String path = id.substring(separator + 1);
        return packRoot.resolve("data").resolve(namespace).resolve("function").resolve(path + ".mcfunction");
    }

    private static JsonElement parseJson(Path file) throws IOException, JsonSyntaxException {
        try (JsonReader reader = new JsonReader(new StringReader(Files.readString(file)))) {
            reader.setLenient(false);
            return JsonParser.parseReader(reader);
        }
    }
}
