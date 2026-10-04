package com.anvith.archmorph.analysis.validation.level;

import com.anvith.archmorph.analysis.model.SourceFile;
import com.anvith.archmorph.analysis.registry.ProjectClassRegistry;
import com.anvith.archmorph.analysis.validation.LevelResult;
import com.anvith.archmorph.analysis.validation.LevelValidator;
import com.anvith.archmorph.analysis.validation.ValidationContext;
import com.anvith.archmorph.analysis.validation.ValidationIssue;
import com.anvith.archmorph.analysis.validation.ValidationLevel;
import com.github.javaparser.ast.ImportDeclaration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Level 4: imports of project classes point to classes that exist in the transformed project, and
 * no import still refers to the old location of a moved class.
 */
@Component
@Order(4)
public class ImportValidator implements LevelValidator {

    @Override
    public ValidationLevel level() {
        return ValidationLevel.IMPORT_RESOLUTION;
    }

    @Override
    public LevelResult validate(ValidationContext context) {
        long started = System.nanoTime();
        List<ValidationIssue> issues = new ArrayList<>();
        var transformed = context.transformed().get();
        ProjectClassRegistry now = transformed.model().registry();
        ProjectClassRegistry before = context.original().registry();

        Set<String> projectPackages = new HashSet<>(now.packages());
        projectPackages.addAll(before.packages());
        Set<String> movedAway = context.plan().getClassMap().keySet();
        int checked = 0;

        for (SourceFile file : transformed.model().files()) {
            if (!file.isParseable()) {
                continue;
            }
            for (ImportDeclaration imp : file.getParsed().compilationUnit().getImports()) {
                String name = imp.getNameAsString();
                String owner = imp.isStatic() && !imp.isAsterisk() && name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                int line = imp.getBegin().map(p -> p.line).orElse(0);
                checked++;

                if (imp.isAsterisk() && !imp.isStatic()) {
                    if (belongsToProject(name, projectPackages) && now.classesInPackage(name).isEmpty()
                            && now.findByQualifiedName(name).isEmpty()) {
                        issues.add(ValidationIssue.error(file.getRelativePath(), line,
                                "Wildcard import '" + name + ".*' points to a package that no longer exists.",
                                "Classes moved out of this package; the import should have been replaced by explicit imports."));
                    }
                    continue;
                }
                if (!belongsToProject(owner, projectPackages) && now.findByQualifiedName(owner).isEmpty()) {
                    continue; // external library
                }
                if (movedAway.contains(owner)) {
                    issues.add(ValidationIssue.error(file.getRelativePath(), line,
                            "Import '" + owner + "' still refers to the old location of a moved class.",
                            "The import was not rewritten through the class map."));
                } else if (now.findByQualifiedName(owner).isEmpty() && !isPackageName(owner, now)) {
                    issues.add(ValidationIssue.error(file.getRelativePath(), line,
                            "Import '" + owner + "' does not resolve to a class of the transformed project.",
                            "The imported class does not exist; it may have been an unresolved import in the original project."));
                }
            }
        }
        return LevelResult.of(ValidationLevel.IMPORT_RESOLUTION, issues,
                checked + " imports checked, all project imports resolve", (System.nanoTime() - started) / 1_000_000);
    }

    private boolean belongsToProject(String name, Set<String> projectPackages) {
        for (String pkg : projectPackages) {
            if (!pkg.isEmpty() && (name.equals(pkg) || name.startsWith(pkg + "."))) {
                return true;
            }
        }
        return false;
    }

    private boolean isPackageName(String name, ProjectClassRegistry registry) {
        return registry.packages().contains(name);
    }
}
