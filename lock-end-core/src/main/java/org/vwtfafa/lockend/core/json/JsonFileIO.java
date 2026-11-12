package org.vwtfafa.lockend.core.json;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Shared atomic file-writing helper for the mod's JSON stores: a temp file
 * in the target directory is filled and moved onto the target (falling back
 * to a plain replace when atomic moves are unsupported), so a killed server
 * never truncates an existing file.
 */
public final class JsonFileIO {

    private JsonFileIO() {
    }

    /**
     * Writes the given text atomically onto the target file.
     *
     * @param target the destination file
     * @param text   the full file content
     * @param logger log sink for failures
     * @return {@code true} when the write completed
     */
    public static boolean writeAtomically(Path target, String text, Logger logger) {
        Path temporaryFile = null;
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            temporaryFile = Files.createTempFile(parent, ".lock-end-", ".tmp");
            Files.writeString(temporaryFile, text);
            replaceAtomically(temporaryFile, target);
            return true;
        } catch (IOException exception) {
            logger.warn("Could not write the file {}", target, exception);
            return false;
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupFailure) {
                    logger.warn("Could not remove the temporary file {}",
                            temporaryFile, cleanupFailure);
                }
            }
        }
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
