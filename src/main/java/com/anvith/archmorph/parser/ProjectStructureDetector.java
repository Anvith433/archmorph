package com.anvith.archmorph.parser;
import com.anvith.archmorph.common.exception.InvalidProjectStructureException;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Service
public class ProjectStructureDetector {

    public Path detectProjectRoot(Path originalDirectory) {

        try (Stream<Path> stream = Files.walk(originalDirectory)) {

            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .map(Path::getParent)
                    .findFirst()
                    .orElseThrow(() ->
                            new InvalidProjectStructureException(
                                    "No Maven project found. pom.xml is missing."
                            ));

        } catch (IOException e) {

            throw new InvalidProjectStructureException(
                    "Unable to inspect extracted project.",
                    e
            );

        }

    }

}