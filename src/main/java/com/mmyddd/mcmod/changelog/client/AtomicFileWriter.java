package com.mmyddd.mcmod.changelog.client;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class AtomicFileWriter {
    private AtomicFileWriter() {
    }

    public static void write(Path target, byte[] contents) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(contents, "contents");

        Path resolvedTarget = target.toAbsolutePath();
        Path parent = resolvedTarget.getParent();
        if (parent == null) {
            throw new IOException("Cannot resolve parent directory for " + resolvedTarget);
        }

        Files.createDirectories(parent);
        String prefix = resolvedTarget.getFileName().toString();
        if (prefix.length() < 3) {
            prefix += ".tmp";
        }

        Path temporaryFile = Files.createTempFile(parent, prefix, ".tmp");
        boolean replaced = false;
        try {
            Files.write(temporaryFile, contents);
            replace(temporaryFile, resolvedTarget);
            replaced = true;
        } finally {
            if (!replaced) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    public static void writeString(Path target, String contents, Charset charset) throws IOException {
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(charset, "charset");
        write(target, contents.getBytes(charset));
    }

    private static void replace(Path temporaryFile, Path target) throws IOException {
        try {
            Files.move(temporaryFile, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}