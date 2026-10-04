package com.anvith.archmorph.analysis.validation.level;

import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanEntry;
import com.anvith.archmorph.analysis.validation.LevelResult;
import com.anvith.archmorph.analysis.validation.LevelValidator;
import com.anvith.archmorph.analysis.validation.ValidationContext;
import com.anvith.archmorph.analysis.validation.ValidationIssue;
import com.anvith.archmorph.analysis.validation.ValidationLevel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Level 1: every planned file exists at its target, no Java file exists that the plan did not
 * produce, no two entries share a target, and every non-Java project file was preserved.
 */
@Component
@Order(1)
public class FilesystemValidator implements LevelValidator {

    @Override
    public ValidationLevel level() {
        return ValidationLevel.FILESYSTEM;
    }

    @Override
    public LevelResult validate(ValidationContext context) {
        long started = System.nanoTime();
        List<ValidationIssue> issues = new ArrayList<>();
        Path root = context.transformedRoot();

        Set<String> expectedJava = new HashSet<>();
        for (TransformationPlanEntry entry : context.plan().getEntries()) {
            String target = entry.getTargetFile().toString().replace('\\', '/');
            if (!expectedJava.add(target)) {
                issues.add(ValidationIssue.error(target, 0, "Two plan entries write to the same file.",
                        "Target path collision; this is a planner defect, please report it."));
            }
            if (!Files.isRegularFile(root.resolve(target))) {
                issues.add(ValidationIssue.error(target, 0, "Planned file is missing from the transformed project.",
                        "The file could not be written. Run the transformation again."));
            }
        }

        Set<String> actualJava = new HashSet<>();
        Set<String> actualOther = new HashSet<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                String relative = root.relativize(file).toString().replace('\\', '/');
                (relative.endsWith(".java") ? actualJava : actualOther).add(relative);
            });
        } catch (IOException e) {
            issues.add(ValidationIssue.error(null, 0, "The transformed project could not be read.", "Run the transformation again."));
        }
        for (String java : actualJava) {
            if (!expectedJava.contains(java)) {
                issues.add(ValidationIssue.warning(java, 0, "Java file is not part of the transformation plan.",
                        "Unplanned file; it should not exist."));
            }
        }

        Path originalRoot = context.original().structure().projectRoot();
        Set<String> plannedSources = new HashSet<>();
        context.plan().getEntries().forEach(e -> plannedSources.add(e.getSourceFile().toString().replace('\\', '/')));
        try (Stream<Path> stream = Files.walk(originalRoot)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                String relative = originalRoot.relativize(file).toString().replace('\\', '/');
                if (!plannedSources.contains(relative) && !actualOther.contains(relative)) {
                    issues.add(ValidationIssue.error(relative, 0, "A project file was not carried over.",
                            "Resources, build files and wrappers must be preserved."));
                }
            });
        } catch (IOException e) {
            issues.add(ValidationIssue.warning(null, 0, "The original project could not be compared.", null));
        }

        int javaCount = context.plan().getEntries().size();
        return LevelResult.of(ValidationLevel.FILESYSTEM, issues,
                javaCount + " Java files and " + actualOther.size() + " resource files present, no collisions",
                (System.nanoTime() - started) / 1_000_000);
    }
}
