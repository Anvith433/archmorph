package com.anvith.archmorph.common.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

/**
 * Streams a directory as a ZIP. Entries are sorted and carry a constant timestamp, so the same
 * directory always produces byte-identical archives. Symbolic links are skipped.
 */
public final class ZipWriter {

    private static final FileTime FIXED_TIME = FileTime.fromMillis(315532800000L); // 1980-01-01

    private ZipWriter() {
    }

    public static void write(Path directory, OutputStream target) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile).filter(p -> !Files.isSymbolicLink(p)).sorted().toList();
        }
        try (ZipOutputStream zip = new ZipOutputStream(target)) {
            for (Path file : files) {
                String name = directory.relativize(file).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(name);
                entry.setLastModifiedTime(FIXED_TIME);
                zip.putNextEntry(entry);
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }
}
