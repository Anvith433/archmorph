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
import java.util.TreeSet;

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
        Set<String> originalImports = importsOf(context.original().files());
        Set<String> unverifiable = new TreeSet<>();
        int checked = 0;

        for (SourceFile file : transformed.model().files()) {
            if (!file.isParseable()) {
                continue;
            }
            for (ImportDeclaration imp : file.getParsed().compilationUnit().getImports()) {
                String name = imp.getNameAsString();
                String owner = imp.isStatic() && !imp.isAsterisk() && name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                int line = imp.getBegin().map(p -> p.line).orElse(0);
                boolean unchanged = originalImports.contains(key(imp));
                checked++;

                if (imp.isAsterisk() && !imp.isStatic()) {
                    if (belongsToProject(name, projectPackages) && now.classesInPackage(name).isEmpty()
                            && now.findByQualifiedName(name).isEmpty()
                            && !(unchanged && before.classesInPackage(name).isEmpty())) {
                        issues.add(ValidationIssue.error(file.getRelativePath(), line,
                                "Wildcard import '" + name + ".*' points to a package that no longer exists.",
                                "Classes moved out of this package; the import should have been replaced by explicit imports."));
                    }
                    continue;
                }
                if (movedAway.contains(owner)) {
                    issues.add(ValidationIssue.error(file.getRelativePath(), line,
                            "Import '" + owner + "' still refers to the old location of a moved class.",
                            "The import was not rewritten through the class map."));
                    continue;
                }
                if (now.findByQualifiedName(owner).isPresent() || isPackageName(owner, now)) {
                    continue;
                }
                if (before.findByQualifiedName(owner).isPresent()) {
                    issues.add(ValidationIssue.error(file.getRelativePath(), line,
                            "Import '" + owner + "' refers to a project class that no longer exists.",
                            "The class was lost or renamed during the transformation."));
                } else if (!unchanged && belongsToProject(owner, projectPackages)) {
                    issues.add(ValidationIssue.error(file.getRelativePath(), line,
                            "Import '" + owner + "' was introduced by the transformation but does not resolve.",
                            "The rewriter produced an import for a class that does not exist."));
                } else if (belongsToProject(owner, projectPackages)) {
                    // Unchanged import of a class that is not in the sources: generated during the build
                    // (openapi-generator, MapStruct, ...) or provided by a library sharing the namespace.
                    unverifiable.add(owner.contains(".") ? owner.substring(0, owner.lastIndexOf('.')) : owner);
                }
                // anything else is an external library import
            }
        }
        if (!unverifiable.isEmpty()) {
            List<String> sample = unverifiable.stream().limit(5).toList();
            issues.add(ValidationIssue.warning(null, 0,
                    "Unchanged imports refer to classes that are not in the sources (packages " + String.join(", ", sample)
                            + (unverifiable.size() > sample.size() ? ", ..." : "") + ").",
                    "They are probably generated during the build (e.g. OpenAPI generator, MapStruct) or come from a library; "
                            + "the Maven build level verifies them."));
        }
        return LevelResult.of(ValidationLevel.IMPORT_RESOLUTION, issues,
                checked + " imports checked, all project imports resolve", (System.nanoTime() - started) / 1_000_000);
    }

    private static String key(ImportDeclaration imp) {
        return (imp.isStatic() ? "static " : "") + imp.getNameAsString() + (imp.isAsterisk() ? ".*" : "");
    }

    private static Set<String> importsOf(List<SourceFile> files) {
        Set<String> keys = new HashSet<>();
        for (SourceFile file : files) {
            if (file.isParseable()) {
                file.getParsed().compilationUnit().getImports().forEach(imp -> keys.add(key(imp)));
            }
        }
        return keys;
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
