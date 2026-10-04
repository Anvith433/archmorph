package com.anvith.archmorph.analysis.scanner;

import com.anvith.archmorph.common.exception.ArchMorphException;
import com.anvith.archmorph.common.exception.ErrorCode;
import com.anvith.archmorph.common.exception.SourceCodeNotFoundException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds Java source files under the given source roots, in a deterministic
 * (sorted) order, enforcing the configured file-count limit.
 */
@Service
public class SourceScanner {

    /** Backwards-compatible: src/main/java of a project root. */
    public List<Path> scanJavaSources(Path projectRoot) {
        Path sourceRoot = projectRoot.resolve("src").resolve("main").resolve("java");
        if (!Files.exists(sourceRoot)) {
            throw new SourceCodeNotFoundException("src/main/java directory not found.");
        }
        return scan(List.of(sourceRoot), Integer.MAX_VALUE);
    }

    public List<Path> scan(Collection<Path> sourceRoots, int maxFiles) {
        List<Path> files = new ArrayList<>();
        for (Path root : sourceRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(path -> path.toString().endsWith(".java"))
                        .filter(Files::isRegularFile)
                        .filter(path -> !Files.isSymbolicLink(path))
                        .forEach(files::add);
            } catch (IOException e) {
                throw new SourceCodeNotFoundException("Unable to scan Java source files.", e);
            }
            if (files.size() > maxFiles) {
                throw new ArchMorphException(ErrorCode.ANALYSIS_LIMIT_EXCEEDED,
                        "The project contains more than " + maxFiles + " Java source files.",
                        "Analyse a smaller project or raise archmorph.analysis.max-java-files.");
            }
        }
        files.sort(Path::compareTo);
        return files;
    }
}
