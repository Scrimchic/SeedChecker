package com.scrimchic.seedchecker.storage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Replaces a file so that a crash leaves either the old content or the new, never half of one.
 *
 * <p>The content goes to a temporary sibling first - same directory, so the same filesystem - and is
 * flushed to the device, then moved over the target atomically where the filesystem supports it,
 * and with a plain replacing move where it does not.
 */
final class AtomicFiles {

    static final String TEMP_SUFFIX = ".tmp";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private AtomicFiles() {
    }

    /**
     * @throws IOException when the content could not be put in place; the target is then untouched
     *                     and the temporary file removed
     */
    static void write(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        try {
            OutputStream out = Files.newOutputStream(temporary);
            try {
                out.write(content.getBytes(UTF_8));
                out.flush();
            } finally {
                out.close();
            }
            try {
                Files.move(temporary, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Nothing useful to do; the next write replaces it anyway.
            }
            throw failure;
        }
    }
}
