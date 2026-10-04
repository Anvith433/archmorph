package com.anvith.archmorph.analysis.validation.level;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.module.ClassAssignment;
import com.anvith.archmorph.analysis.module.ModuleCategory;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanEntry;
import com.anvith.archmorph.analysis.transformation.target.TargetArchitecture;
import com.anvith.archmorph.analysis.transformation.target.TargetArchitectureResolver;
import com.anvith.archmorph.analysis.validation.LevelResult;
import com.anvith.archmorph.analysis.validation.LevelValidator;
import com.anvith.archmorph.analysis.validation.ValidationContext;
import com.anvith.archmorph.analysis.validation.ValidationIssue;
import com.anvith.archmorph.analysis.validation.ValidationLevel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Level 6: the expected modular boundaries exist. Moved classes live under their module package, shared
 * classes under shared; module-level dependency cycles and shared code that depends on a business module
 * are reported as warnings. For layouts that separate a module's public API from its internals
 * ({@code MODULAR_MONOLITH}), every dependency between modules must target the other module's API package —
 * the rule Spring Modulith's {@code ApplicationModules.verify()} enforces.
 */
@Component
@Order(6)
public class ArchitectureValidator implements LevelValidator {

    private final TargetArchitectureResolver architectures;

    public ArchitectureValidator(TargetArchitectureResolver architectures) {
        this.architectures = architectures;
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
        TargetArchitecture architecture = architectures.resolve(context.plan().getStrategy());
        String sharedRoot = architecture.sharedRoot(base);
        Map<String, String> moduleRoots = new java.util.LinkedHashMap<>();
        context.modules().getBusinessModules().stream()
                .map(m -> m.getModuleName())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .forEach(m -> moduleRoots.put(m, architecture.moduleRoot(base, m)));

        // 1. Every automatically moved class is under its module (or shared) package.
        for (TransformationPlanEntry entry : context.plan().getEntries()) {
            if (!entry.isMoved() || entry.getModule() == null || entry.getScope() != com.anvith.archmorph.parser.SourceScope.MAIN) {
                continue;
            }
            ClassAssignment assignment = context.modules().getAssignment(entry.getNode().getId());
            if (assignment != null && assignment.category() == ModuleCategory.APPLICATION) {
                continue; // application wiring lives in the root package, outside every module
            }
            String expectedRoot = assignment != null && assignment.category() == ModuleCategory.BUSINESS_MODULE
                    ? architecture.moduleRoot(base, entry.getModule()) : sharedRoot;
            String pkg = entry.getTargetPackage();
            if (!(pkg.equals(expectedRoot) || pkg.startsWith(expectedRoot + "."))) {
                issues.add(ValidationIssue.error(entry.getTargetFile().toString().replace('\\', '/'), 1,
                        "Class is not inside the expected package '" + expectedRoot + "'.",
                        "The planned package does not follow the target architecture."));
            }
        }

        // 2. Module-level dependency graph on the transformed project.
        Map<String, Set<String>> moduleEdges = new HashMap<>();
        Set<String> reported = new java.util.HashSet<>();
        int crossModule = 0;
        for (DependencyEdge edge : graph.getEdges()) {
            String from = moduleOf(edge.getSource(), moduleRoots, sharedRoot);
            String to = moduleOf(edge.getTarget(), moduleRoots, sharedRoot);
            if (from == null || to == null || from.equals(to)) {
                continue;
            }
            crossModule++;
            moduleEdges.computeIfAbsent(from, k -> new TreeSet<>()).add(to);
            String pair = edge.getSource().getId() + "->" + edge.getTarget().getId();
            if (from.equals("shared") && !to.equals("shared") && reported.add(pair)) {
                issues.add(ValidationIssue.warning(edge.getLocation() == null ? null : edge.getLocation().file(),
                        edge.getLocation() == null ? 0 : edge.getLocation().line(),
                        "Shared class " + edge.getSource().getClassName() + " depends on module '" + to + "' ("
                                + edge.getTarget().getClassName() + ").",
                        "Shared code should not depend on a business module; consider an interface in shared or moving the class."));
            }
            // 3. Encapsulation: other modules may only use a module's API package.
            if (architecture.separatesApi()) {
                String apiPackage = to.equals("shared") ? sharedRoot : moduleRoots.get(to);
                String targetPackage = edge.getTarget().getPackageName() == null ? "" : edge.getTarget().getPackageName();
                if (!targetPackage.equals(apiPackage) && reported.add("api:" + pair)) {
                    issues.add(ValidationIssue.error(edge.getLocation() == null ? null : edge.getLocation().file(),
                            edge.getLocation() == null ? 0 : edge.getLocation().line(),
                            edge.getSource().getClassName() + " (module '" + from + "') uses " + edge.getTarget().getClassName()
                                    + ", an internal class of module '" + to + "'.",
                            "Only the module's API package '" + apiPackage + "' may be used from other modules."));
                }
            }
        }
        for (Map.Entry<String, Set<String>> entry : moduleEdges.entrySet()) {
            for (String target : entry.getValue()) {
                if (entry.getKey().compareTo(target) < 0 && moduleEdges.getOrDefault(target, Set.of()).contains(entry.getKey())
                        && !entry.getKey().equals("shared") && !target.equals("shared")) {
                    issues.add(ValidationIssue.warning(null, 0,
                            "Modules '" + entry.getKey() + "' and '" + target + "' depend on each other.",
                            "Cyclic module dependency; review the module boundary or introduce an interface or event."));
                }
            }
        }
        String summary = architecture.separatesApi()
                ? moduleRoots.size() + " modules; all " + crossModule + " cross-module dependencies go through module APIs; no cyclic module dependencies"
                : "module boundaries respected, no cyclic module dependencies";
        return LevelResult.of(ValidationLevel.ARCHITECTURE_RULES, issues, summary, (System.nanoTime() - started) / 1_000_000);
    }

    private String moduleOf(DependencyNode node, Map<String, String> moduleRoots, String sharedRoot) {
        String pkg = node.getPackageName() == null ? "" : node.getPackageName();
        if (pkg.equals(sharedRoot) || pkg.startsWith(sharedRoot + ".")) {
            return "shared";
        }
        for (Map.Entry<String, String> module : moduleRoots.entrySet()) {
            String root = module.getValue();
            if (pkg.equals(root) || pkg.startsWith(root + ".")) {
                return module.getKey();
            }
        }
        return null;
    }
}
