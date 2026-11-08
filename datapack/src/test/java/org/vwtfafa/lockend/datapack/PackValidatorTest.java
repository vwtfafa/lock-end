package org.vwtfafa.lockend.datapack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the structural checks of the datapack validator: good packs pass,
 * packaging mistakes (missing or broken pack.mcmeta, dangling references,
 * orphaned functions) fail with precise problems.
 */
class PackValidatorTest {

    @TempDir
    Path tempDir;

    private static final String EMPTY_LOAD_TAG = "{\"values\":[]}";

    private static final String PACK_MCMETA = """
            {
              "pack": {
                "description": "test pack",
                "min_format": 121,
                "max_format": 121
              }
            }
            """;

    @Test
    void validPackHasNoProblems() throws IOException {
        Path pack = newPack();
        write(pack, "pack.mcmeta", PACK_MCMETA);
        write(pack, "data/minecraft/tags/function/load.json", "{\"values\":[\"lockend:load\"]}");
        write(pack, "data/minecraft/tags/function/tick.json", "{\"values\":[\"lockend:tick\"]}");
        write(pack, "data/lockend/function/load.mcfunction", "say loaded");
        write(pack, "data/lockend/function/tick.mcfunction",
                "function lockend:helper\nfunction lockend:load");
        write(pack, "data/lockend/function/helper.mcfunction", "say ticked");
        write(pack, "data/lockend/function/lock.mcfunction", "say lock");

        List<String> problems = PackValidator.validate(pack);

        assertEquals(List.of(), problems, "expected the helper chain and the entry function to pass");
    }

    @Test
    void missingPackMcmetaFails() throws IOException {
        Path pack = newPack();
        write(pack, "data/minecraft/tags/function/load.json", EMPTY_LOAD_TAG);
        write(pack, "data/minecraft/tags/function/tick.json", EMPTY_LOAD_TAG);

        List<String> problems = PackValidator.validate(pack);

        assertContainsProblem(problems, "missing pack.mcmeta");
    }

    @Test
    void malformedPackMcmetaFails() throws IOException {
        Path pack = newPack();
        write(pack, "pack.mcmeta", "not json");

        List<String> problems = PackValidator.validate(pack);

        assertContainsProblem(problems, "pack.mcmeta is not valid JSON");
    }

    @Test
    void danglingTagValueFails() throws IOException {
        Path pack = newPack();
        write(pack, "data/minecraft/tags/function/load.json", "{\"values\":[\"testpack:load\"]}");
        write(pack, "data/minecraft/tags/function/tick.json", EMPTY_LOAD_TAG);

        List<String> problems = PackValidator.validate(pack);

        assertContainsProblem(problems, "referenced function does not exist: testpack:load");
    }

    @Test
    void unreachableFunctionFails() throws IOException {
        Path pack = newPack();
        write(pack, "data/minecraft/tags/function/load.json", EMPTY_LOAD_TAG);
        write(pack, "data/minecraft/tags/function/tick.json", EMPTY_LOAD_TAG);
        write(pack, "data/testpack/function/abandoned.mcfunction", "say hello");

        List<String> problems = PackValidator.validate(pack);

        assertContainsProblem(problems, "unreferenced function file: testpack:abandoned");
    }

    @Test
    void entryFunctionsNeedNoReference() throws IOException {
        Path pack = newPack();
        write(pack, "pack.mcmeta", PACK_MCMETA);
        write(pack, "data/minecraft/tags/function/load.json", EMPTY_LOAD_TAG);
        write(pack, "data/minecraft/tags/function/tick.json", EMPTY_LOAD_TAG);
        write(pack, "data/lockend/function/status.mcfunction", "say status");

        List<String> problems = PackValidator.validate(pack);

        assertTrue(problems.isEmpty(), () -> "status is a documented entry function: " + problems);    }

    private static void assertContainsProblem(List<String> problems, String expected) {
        assertTrue(problems.stream().anyMatch(problem -> problem.contains(expected)),
                () -> "expected a problem containing '" + expected + "' but got: " + problems);
    }

    private Path newPack() throws IOException {
        Path pack = tempDir.resolve("pack-" + System.nanoTime());
        Files.createDirectories(pack);
        return pack;
    }

    private static void write(Path pack, String relative, String content) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
