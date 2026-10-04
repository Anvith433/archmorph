package com.anvith.archmorph.analysis.validation.level;

import com.anvith.archmorph.analysis.model.SourceFile;
import com.anvith.archmorph.analysis.validation.LevelResult;
import com.anvith.archmorph.analysis.validation.LevelValidator;
import com.anvith.archmorph.analysis.validation.ValidationContext;
import com.anvith.archmorph.analysis.validation.ValidationIssue;
import com.anvith.archmorph.analysis.validation.ValidationLevel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Level 2: every Java source of the transformed project parses. Files that were already
 * unparseable in the original are reported as warnings, new parse errors as errors.
 */
@Component
@Order(2)
public class JavaParseValidator implements LevelValidator {

    @Override
    public ValidationLevel level() {
        return ValidationLevel.JAVA_PARSING;
    }

    @Override
    public LevelResult validate(ValidationContext context) {
        long started = System.nanoTime();
        List<ValidationIssue> issues = new ArrayList<>();
        var transformed = context.transformed().get();

        var originalBroken = context.original().unparseableFiles().stream().map(SourceFile::getFileName).toList();
        for (SourceFile file : transformed.model().files()) {
            if (file.isParseable()) {
                continue;
            }
            int line = file.getParsed().problems().isEmpty() ? 0 : file.getParsed().problems().getFirst().line();
            String message = file.getParsed().problems().isEmpty() ? "Syntax error" : file.getParsed().problems().getFirst().message();
            if (originalBroken.contains(file.getFileName())) {
                issues.add(ValidationIssue.warning(file.getRelativePath(), line, message,
                        "This file did not parse in the original project either; it was copied unchanged."));
            } else {
                issues.add(ValidationIssue.error(file.getRelativePath(), line, message,
                        "The rewrite produced invalid Java. Exclude the class in the module editor and report this file."));
            }
        }
        return LevelResult.of(ValidationLevel.JAVA_PARSING, issues,
                transformed.model().files().size() + " Java files parsed successfully",
                (System.nanoTime() - started) / 1_000_000);
    }
}
