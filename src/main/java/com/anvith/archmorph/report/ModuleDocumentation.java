package com.anvith.archmorph.report;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.module.ModuleCategory;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.module.ModuleInfo;
import com.anvith.archmorph.analysis.transformation.SafetyLevel;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlan;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanEntry;
import com.anvith.archmorph.analysis.transformation.target.TargetArchitecture;
import com.anvith.archmorph.parser.SourceScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Renders {@code MODULES.md}, the module map that ships inside the transformed project: what each module
 * contains, which of its types form its public API, which modules it depends on, what stayed in place, and
 * how to keep the boundaries verified. The content is deterministic (no timestamps), so the same plan always
 * produces the same file.
 */
@Component
public class ModuleDocumentation {

    public static final String FILE_NAME = "MODULES.md";
    public static final String FALLBACK_FILE_NAME = "ARCHMORPH-MODULES.md";

    public String render(String projectName, TransformationPlan plan, ModuleDiscoveryReport modules, DependencyGraph graph,
                         TargetArchitecture architecture) {
        return render(projectName, plan, modules, graph, architecture,
                com.anvith.archmorph.analysis.module.boundary.BoundaryReport.empty());
    }

    public String render(String projectName, TransformationPlan plan, ModuleDiscoveryReport modules, DependencyGraph graph,
                         TargetArchitecture architecture, com.anvith.archmorph.analysis.module.boundary.BoundaryReport boundaries) {
        String base = plan.getBasePackage();
        Map<String, TransformationPlanEntry> entryByType = new TreeMap<>();
        for (TransformationPlanEntry entry : plan.getEntries()) {
            if (entry.getScope() == SourceScope.MAIN && entry.getNode() != null) {
                entryByType.put(entry.getNode().getId(), entry);
            }
        }
        Map<String, Map<String, Set<String>>> uses = moduleDependencies(modules, graph);

        StringBuilder md = new StringBuilder();
        md.append("# Modules of ").append(projectName).append("\n\n");
        md.append("This project was restructured from a layered architecture into a **")
                .append(architecture.separatesApi() ? "modular monolith" : "package-by-module layout")
                .append("** by ArchMorph. The module boundaries are a starting point proposed from static analysis "
                        + "and reviewed by a person; refine them as the design evolves.\n\n");
        md.append("```\n").append(String.join("\n", architecture.describeLayout(base, businessNames(modules)))).append("\n```\n\n");
        if (architecture.separatesApi()) {
            md.append("**Rules.** Each module's root package is its public API: the types other modules may use. "
                    + "Its sub-packages are internal and must only be used inside the module. Shared code must not "
                    + "depend on business modules.\n\n");
        }

        md.append("## Overview\n\n| Module | Package | Classes | Public API | Depends on |\n|---|---|---|---|---|\n");
        for (ModuleInfo module : ordered(modules)) {
            String root = root(architecture, base, module);
            List<String> api = apiTypes(module, entryByType, root, architecture);
            md.append("| ").append(module.getModuleName()).append(" | `").append(root.isEmpty() ? "(default)" : root).append("` | ")
                    .append(module.getClassCount()).append(" | ")
                    .append(architecture.separatesApi() ? String.valueOf(api.size()) : "—").append(" | ")
                    .append(String.join(", ", uses.getOrDefault(module.getModuleName(), Map.of()).keySet())).append(" |\n");
        }
        md.append('\n');

        for (ModuleInfo module : ordered(modules)) {
            String root = root(architecture, base, module);
            md.append("## ").append(module.getModuleName()).append("\n\n");
            md.append("Package `").append(root.isEmpty() ? "(default)" : root).append("`");
            if (module.isBusinessModule()) {
                md.append(String.format(" · confidence %.2f · cohesion %.2f", module.getConfidence(), module.getCohesion()));
            }
            md.append("\n\n");
            if (architecture.separatesApi()) {
                List<String> api = apiTypes(module, entryByType, root, architecture);
                md.append("**Public API** (used by other modules): ")
                        .append(api.isEmpty() ? "none" : String.join(", ", api.stream().map(t -> "`" + t + "`").toList()))
                        .append("\n\n");
            }
            Map<String, List<String>> byPackage = new TreeMap<>();
            for (DependencyNode node : module.getClasses()) {
                TransformationPlanEntry entry = entryByType.get(node.getId());
                String pkg = entry == null ? node.getPackageName() : entry.getTargetPackage();
                String label = pkg.equals(root) ? "(module root)" : pkg.startsWith(root + ".") ? pkg.substring(root.length() + 1) : pkg;
                byPackage.computeIfAbsent(label, k -> new ArrayList<>()).add(node.getClassName());
            }
            md.append("| Package | Classes |\n|---|---|\n");
            byPackage.forEach((pkg, classes) -> md.append("| ").append(pkg).append(" | ")
                    .append(String.join(", ", new TreeSet<>(classes))).append(" |\n"));
            Map<String, Set<String>> dependencies = uses.getOrDefault(module.getModuleName(), Map.of());
            if (!dependencies.isEmpty()) {
                md.append("\n**Depends on**\n\n");
                dependencies.forEach((target, types) -> md.append("- ").append(target).append(" via ")
                        .append(String.join(", ", types.stream().limit(8).map(t -> "`" + t + "`").toList()))
                        .append(types.size() > 8 ? ", …" : "").append('\n'));
            }
            if (!module.getWarnings().isEmpty()) {
                md.append("\n**Review notes**\n\n");
                module.getWarnings().forEach(w -> md.append("- ").append(w.replaceFirst("^\\[opt\\] ", "")).append('\n'));
            }
            md.append('\n');
        }

        if (!boundaries.cycles().isEmpty()) {
            md.append("## Module cycles and how to break them\n\n");
            md.append("These modules depend on each other: ");
            md.append(String.join("; ", boundaries.cycles().stream().map(c -> String.join(" ↔ ", c)).toList()));
            md.append(". Moving packages cannot remove a cycle that exists in the code. Each suggestion below removes "
                    + "one module dependency; together they make the module graph acyclic.\n\n");
            for (var suggestion : boundaries.suggestions()) {
                md.append("### ").append(suggestion.title()).append("\n\n");
                md.append("`").append(suggestion.from()).append("` → `").append(suggestion.to()).append("`, ")
                        .append(suggestion.dependencyCount()).append(" class-level dependencies. ")
                        .append(suggestion.rationale()).append("\n\n");
                suggestion.steps().forEach(step -> md.append("1. ").append(step).append('\n'));
                md.append("\n<details><summary>Dependencies removed</summary>\n\n");
                suggestion.evidence().forEach(e -> md.append("- ").append(e).append('\n'));
                md.append("\n</details>\n\n");
            }
        }

        List<TransformationPlanEntry> kept = plan.getEntries().stream()
                .filter(e -> e.getScope() == SourceScope.MAIN)
                .filter(e -> e.getSafety() == SafetyLevel.MANUAL_REVIEW || e.getSafety() == SafetyLevel.UNSUPPORTED)
                .toList();
        if (!kept.isEmpty()) {
            md.append("## Kept in place (manual review)\n\nThese files were not moved automatically because moving them "
                    + "was not provably safe. They still compile where they are; move them by hand once the reason is resolved.\n\n");
            for (TransformationPlanEntry entry : kept) {
                md.append("- `").append(entry.getSourceFile().toString().replace('\\', '/')).append("`: ")
                        .append(entry.getReasons().isEmpty() ? "manual review" : entry.getReasons().getLast()).append('\n');
            }
            md.append('\n');
        }

        if (architecture.separatesApi()) {
            String application = modules.getModules().stream()
                    .filter(m -> m.getCategory() == ModuleCategory.APPLICATION)
                    .flatMap(m -> m.getClasses().stream()).map(DependencyNode::getClassName).findFirst().orElse("Application");
            md.append("## Keeping the boundaries verified\n\n")
                    .append("The layout follows the [Spring Modulith](https://spring.io/projects/spring-modulith) conventions, "
                            + "so the boundaries can be checked on every build. Add the test dependency "
                            + "`org.springframework.modulith:spring-modulith-starter-test` (test scope, with the "
                            + "`spring-modulith-bom` matching your Spring Boot version) and this test:\n\n")
                    .append("```java\n")
                    .append("class ModularityTests {\n\n")
                    .append("    @Test\n")
                    .append("    void verifiesModularStructure() {\n")
                    .append("        ApplicationModules.of(").append(application).append(".class).verify();\n")
                    .append("    }\n")
                    .append("}\n```\n\n");
            if (!kept.isEmpty()) {
                md.append("Files kept in their old packages appear to Spring Modulith as extra modules until they are moved.\n");
            }
        }
        return md.toString();
    }

    private static List<String> businessNames(ModuleDiscoveryReport modules) {
        return modules.getBusinessModules().stream().map(ModuleInfo::getModuleName).sorted().toList();
    }

    private static List<ModuleInfo> ordered(ModuleDiscoveryReport modules) {
        List<ModuleInfo> list = new ArrayList<>();
        modules.getBusinessModules().stream().sorted((a, b) -> a.getModuleName().compareTo(b.getModuleName())).forEach(list::add);
        modules.getModules().stream().filter(m -> !m.isBusinessModule() && m.getCategory() != ModuleCategory.APPLICATION)
                .sorted((a, b) -> a.getModuleName().compareTo(b.getModuleName())).forEach(list::add);
        return list;
    }

    private static String root(TargetArchitecture architecture, String base, ModuleInfo module) {
        return module.isBusinessModule() ? architecture.moduleRoot(base, module.getModuleName()) : architecture.sharedRoot(base);
    }

    private static List<String> apiTypes(ModuleInfo module, Map<String, TransformationPlanEntry> entryByType, String root,
                                         TargetArchitecture architecture) {
        if (!architecture.separatesApi()) {
            return List.of();
        }
        return module.getClasses().stream()
                .filter(n -> {
                    TransformationPlanEntry entry = entryByType.get(n.getId());
                    return entry != null && root.equals(entry.getTargetPackage());
                })
                .map(DependencyNode::getClassName).sorted().toList();
    }

    /** module → (module it depends on → types it uses there). */
    private static Map<String, Map<String, Set<String>>> moduleDependencies(ModuleDiscoveryReport modules, DependencyGraph graph) {
        Map<String, Map<String, Set<String>>> uses = new TreeMap<>();
        for (DependencyEdge edge : graph.getEdges()) {
            String from = modules.moduleOf(edge.getSource().getId());
            String to = modules.moduleOf(edge.getTarget().getId());
            if (from == null || to == null || from.equals(to) || ModuleDiscoveryReport.APPLICATION.equals(to)) {
                continue;
            }
            uses.computeIfAbsent(from, k -> new TreeMap<>()).computeIfAbsent(to, k -> new TreeSet<>())
                    .add(edge.getTarget().getClassName());
        }
        return uses;
    }
}
