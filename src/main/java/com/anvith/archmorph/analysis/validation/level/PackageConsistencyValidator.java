package com.anvith.archmorph.analysis.validation.level;

import com.anvith.archmorph.analysis.model.SourceFile;
import com.anvith.archmorph.analysis.validation.LevelResult;
import com.anvith.archmorph.analysis.validation.LevelValidator;
import com.anvith.archmorph.analysis.validation.ValidationContext;
import com.anvith.archmorph.analysis.validation.ValidationIssue;
import com.anvith.archmorph.analysis.validation.ValidationLevel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Level 3: the package declaration of every file matches its directory below the source root. */
@Component
@Order(3)
public class PackageConsistencyValidator implements LevelValidator {

    @Override
    public ValidationLevel level() {
        return ValidationLevel.PACKAGE_CONSISTENCY;
    }

    @Override
    public LevelResult validate(ValidationContext context) {
        long started = System.nanoTime();
        List<ValidationIssue> issues = new ArrayList<>();
        var transformed = context.transformed().get();
        int checked = 0;

        for (SourceFile file : transformed.model().files()) {
            if (!file.isParseable() || file.getPackageName() == null) {
                continue;
            }
            String fileName = file.getFileName();
            if (fileName.equals("module-info.java")) {
                continue;
            }
            Path directory = file.getSourceRoot().relativize(file.getAbsolutePath()).getParent();
            String expected = directory == null ? "" : directory.toString().replace('\\', '/').replace('/', '.');
            checked++;
            if (!expected.equals(file.getPackageName())) {
                issues.add(ValidationIssue.error(file.getRelativePath(), 1,
                        "Package '" + file.getPackageName() + "' does not match the directory '" + expected + "'.",
                        "The package declaration was not updated for the new location."));
            }
            if (!fileName.equals("package-info.java") && file.getTopLevelTypes().stream().anyMatch(t -> t.isPublicType()
                    && !(t.getClassName() + ".java").equals(fileName))) {
                issues.add(ValidationIssue.error(file.getRelativePath(), 1,
                        "A public type does not match its file name.", "The file was renamed incorrectly."));
            }
        }
        return LevelResult.of(ValidationLevel.PACKAGE_CONSISTENCY, issues,
                checked + " files: package declarations match their directories", (System.nanoTime() - started) / 1_000_000);
    }
}
