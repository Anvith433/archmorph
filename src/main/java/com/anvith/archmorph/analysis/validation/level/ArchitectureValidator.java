package com.anvith.archmorph.analysis.validation.level;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.module.ClassAssignment;
import com.anvith.archmorph.analysis.module.ModuleCategory;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanEntry;
import com.anvith.archmorph.analysis.validation.LevelResult;
import com.anvith.archmorph.analysis.validation.LevelValidator;
import com.anvith.archmorph.analysis.validation.ValidationContext;
import com.anvith.archmorph.analysis.validation.ValidationIssue;
import com.anvith.archmorph.analysis.validation.ValidationLevel;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Level 6: the expected modular boundaries exist. Moved classes live under their module package,
 * shared classes under {@code shared}; reports module-level dependency cycles and shared code that
 * depends on a business module as warnings.
 */
@Component
@Order(6)
public class ArchitectureValidator implements LevelValidator {

    private final ArchMorphProperties properties;

    public ArchitectureValidator(ArchMorphProperties properties) {
        this.properties = properties;
    }

    @Override
    public ValidationLevel level() {
        return ValidationLevel.ARCHITECTURE_RULES;
    }

    @Override
    public LevelResult validate(ValidationContext context) {
        long started = System.nanoTime();
        List<ValidationIssue> issues = new ArrayList<>();
        DependencyGraph graph = context.transformed().get().graph();
        String base = context.plan().getBasePackage();
        String modulesRoot = join(base, properties.getTransformation().getModulesPackage());
        String sharedRoot = join(base, properties.getTransformation().getSharedPackage());

        // 1. Boundaries: every automatically moved class is under its module (or shared) package.
        for (TransformationPlanEntry entry : context.plan().getEntries()) {
            if (!entry.isMoved() || entry.getModule() == null || entry.getScope() != com.anvith.archmorph.parser.SourceScope.MAIN) {
                continue;
            }
            ClassAssignment assignment = context.modules().getAssignment(entry.getNode().getId());
            String expectedRoot = assignment != null && assignment.category() == ModuleCategory.BUSINESS_MODULE
                    ? join(modulesRoot, entry.getModule()) : sharedRoot;
            String pkg = entry.getTargetPackage();
            if (!(pkg.equals(expectedRoot) || pkg.startsWith(expectedRoot + "."))) {
                issues.add(ValidationIssue.error(entry.getTargetFile().toString().replace('\\', '/'), 1,
                        "Class is not inside the expected package '" + expectedRoot + "'.",
                        "The planned package does not follow the target architecture."));
            }
        }

        // 2. Module-level dependency graph on the transformed project.
        Map<String, Set<String>> moduleEdges = new HashMap<>();
        Set<String> reportedSharedDependencies = new java.util.HashSet<>();
        for (DependencyEdge edge : graph.getEdges()) {
            String from = moduleOf(edge.getSource(), modulesRoot, sharedRoot);
            String to = moduleOf(edge.getTarget(), modulesRoot, sharedRoot);
            if (from == null || to == null || from.equals(to)) {
                continue;
            }
            moduleEdges.computeIfAbsent(from, k -> new TreeSet<>()).add(to);
            if (from.equals("shared") && !to.equals("shared")
                    && reportedSharedDependencies.add(edge.getSource().getId() + "->" + edge.getTarget().getId())) {
                issues.add(ValidationIssue.warning(edge.getLocation() == null ? null : edge.getLocation().file(),
                        edge.getLocation() == null ? 0 : edge.getLocation().line(),
                        "Shared class " + edge.getSource().getClassName() + " depends on module '" + to + "' ("
                                + edge.getTarget().getClassName() + ").",
                        "Shared code should not depend on a business module; consider an interface in shared or moving the class."));
            }
        }
        for (Map.Entry<String, Set<String>> entry : moduleEdges.entrySet()) {
            for (String target : entry.getValue()) {
                if (entry.getKey().compareTo(target) < 0 && moduleEdges.getOrDefault(target, Set.of()).contains(entry.getKey())
                        && !entry.getKey().equals("shared") && !target.equals("shared")) {
                    issues.add(ValidationIssue.warning(null, 0,
                            "Modules '" + entry.getKey() + "' and '" + target + "' depend on each other.",
                            "Cyclic module dependency; review the module boundary or introduce an interface."));
                }
            }
        }
        return LevelResult.of(ValidationLevel.ARCHITECTURE_RULES, issues,
                "module boundaries respected, no cyclic module dependencies", (System.nanoTime() - started) / 1_000_000);
    }

    private String moduleOf(DependencyNode node, String modulesRoot, String sharedRoot) {
        String pkg = node.getPackageName() == null ? "" : node.getPackageName();
        if (pkg.equals(sharedRoot) || pkg.startsWith(sharedRoot + ".")) {
            return "shared";
        }
        String prefix = modulesRoot + ".";
        if (pkg.startsWith(prefix)) {
            String rest = pkg.substring(prefix.length());
            int dot = rest.indexOf('.');
            return dot < 0 ? rest : rest.substring(0, dot);
        }
        return null;
    }

    private static String join(String a, String b) {
        return a == null || a.isEmpty() ? b : a + "." + b;
    }
}
