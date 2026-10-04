package com.anvith.archmorph.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/** Locates and copies the test fixture projects under src/test/resources/fixtures. */
public final class Fixtures {

    private Fixtures() {
    }

    public static Path fixturesRoot() {
        return Paths.get("src", "test", "resources", "fixtures").toAbsolutePath();
    }

    public static Path path(String name) {
        return fixturesRoot().resolve(name);
    }

    public static List<String> names() {
        try (Stream<Path> stream = Files.list(fixturesRoot())) {
            return stream.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Copy a fixture (without expected.json) into {@code target} and return the project directory. */
    public static Path copyTo(String name, Path target) throws IOException {
        Path source = path(name);
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                Path relative = source.relativize(file);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(destination);
                } else if (!file.getFileName().toString().equals("expected.json")) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return target;
    }
}
