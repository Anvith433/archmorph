package com.anvith.archmorph.analysis.scanner;

import com.anvith.archmorph.common.exception.SourceCodeNotFoundException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SourceScanner {

    public List<Path> scanJavaSources(Path projectRoot) {

        Path sourceRoot = projectRoot
                .resolve("src")
                .resolve("main")
                .resolve("java");

        if (!Files.exists(sourceRoot)) {

            throw new SourceCodeNotFoundException(
                    "src/main/java directory not found."
            );

        }

        try (Stream<Path> stream = Files.walk(sourceRoot)) {

            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

        } catch (IOException e) {

            throw new SourceCodeNotFoundException(
                    "Unable to scan Java source files.",
                    e
            );

        }

    }

}