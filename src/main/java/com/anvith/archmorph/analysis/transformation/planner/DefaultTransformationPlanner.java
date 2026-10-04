package com.anvith.archmorph.analysis.transformation.planner;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.dependency.resolve.TypeContext;
import com.anvith.archmorph.analysis.dependency.resolve.TypeResolver;
import com.anvith.archmorph.analysis.model.ProjectModel;
import com.anvith.archmorph.analysis.model.SourceFile;
import com.anvith.archmorph.analysis.module.ClassAssignment;
import com.anvith.archmorph.analysis.module.ModuleCategory;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.registry.ProjectClassRegistry;
import com.anvith.archmorph.analysis.transformation.SafetyLevel;
import com.anvith.archmorph.analysis.transformation.TransformationAction;
import com.anvith.archmorph.analysis.transformation.mapping.TransformationMapping;
import com.anvith.archmorph.analysis.transformation.mapping.TransformationMappingEngine;
import com.anvith.archmorph.analysis.transformation.mapping.TransformationMappingReport;
import com.anvith.archmorph.analysis.transformation.packaging.BasePackageResolver;
import com.anvith.archmorph.analysis.transformation.rewrite.ReferenceFinder;
import com.anvith.archmorph.analysis.transformation.target.TargetStrategy;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.parser.ClassMetadata;
import com.anvith.archmorph.parser.ComponentType;
import com.anvith.archmorph.parser.RiskFlag;
import com.anvith.archmorph.parser.SourceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds the complete, deterministic transformation plan. Planning is pure: it reads the parsed
 * project and never touches the file system.
 *
 * <h2>Steps</h2>
 * <ol>
 *   <li>map every classified top-level class to its target package ({@link TransformationMappingEngine})</li>
 *   <li>create one entry per Java file (a file moves as a whole, with all types it declares)</li>
 *   <li>assign safety levels from risk flags; {@code MANUAL_REVIEW} / {@code UNSUPPORTED} files stay in place</li>
 *   <li>detect {@code TARGET_PATH_COLLISION} / {@code TARGET_CLASS_NAME_COLLISION}; colliding files stay in place</li>
 *   <li>demote moves that would break compilation (package-private access, default-package dependencies,
 *       leaving the component-scan root); repeat until stable</li>
 *   <li>plan test sources: tests move next to the class they test; all other tests are only rewritten</li>
 *   <li>derive rewrite requirements and report configuration files that mention moved names</li>
 * </ol>
 * "Keep in place" is always safe because every class keeps a unique fully-qualified name.
 */
@Service
public class DefaultTransformationPlanner implements TransformationPlanner {

    private static final Logger log = LoggerFactory.getLogger(DefaultTransformationPlanner.class);

    private static final Set<ComponentType> SPRING_MANAGED = EnumSet.of(
            ComponentType.CONTROLLER, ComponentType.SERVICE, ComponentType.REPOSITORY, ComponentType.ENTITY,
            ComponentType.CONFIGURATION, ComponentType.COMPONENT, ComponentType.SECURITY, ComponentType.FILTER,
            ComponentType.EXCEPTION_HANDLER);

    private static final List<String> TEST_SUFFIXES = List.of(
            "IntegrationTest", "IntegrationTests", "Tests", "Test", "IT", "Spec");

    private final TransformationMappingEngine mappingEngine;
    private final BasePackageResolver basePackageResolver;
    private final ResourceReferenceScanner resourceScanner;
    private final ArchMorphProperties properties;

    public DefaultTransformationPlanner(TransformationMappingEngine mappingEngine, BasePackageResolver basePackageResolver,
                                        ResourceReferenceScanner resourceScanner, ArchMorphProperties properties) {
        this.mappingEngine = mappingEngine;
        this.basePackageResolver = basePackageResolver;
        this.resourceScanner = resourceScanner;
        this.properties = properties;
    }

    @Override
    public TransformationPlan plan(ProjectModel model, DependencyGraph graph, ModuleDiscoveryReport moduleReport,
                                   TargetStrategy strategy) {
        TransformationPlan plan = new TransformationPlan();
        plan.setStrategy(strategy);

        Set<String> productionPackages = new TreeSet<>();
        model.mainFiles().forEach(f -> f.getTopLevelTypes().forEach(t -> productionPackages.add(t.getPackageName())));
        String basePackage = basePackageResolver.resolve(productionPackages);
        plan.setBasePackage(basePackage);

        TransformationMappingReport mappings = mappingEngine.build(moduleReport, basePackage, strategy,
                exposedTypes(graph, moduleReport));
        Map<String, ClassMetadata> typeByName = new HashMap<>();
        model.allTypes().forEach(t -> typeByName.put(t.getQualifiedName(), t));

        List<TransformationPlanEntry> main = new ArrayList<>();
        for (SourceFile file : model.mainFiles().stream().sorted(Comparator.comparing(SourceFile::getRelativePath)).toList()) {
            main.add(createMainEntry(file, mappings, moduleReport, plan));
        }

        applyStringReferenceRules(main, model, plan);
        applyResourceReferenceRule(main, model);
        applyScanRootRule(main, model);
        stabilise(main, graph, plan, SourceScope.MAIN);

        List<TransformationPlanEntry> tests = new ArrayList<>();
        if (properties.getTransformation().isPreserveTests()) {
            for (SourceFile file : model.testFiles().stream().sorted(Comparator.comparing(SourceFile::getRelativePath)).toList()) {
                tests.add(createTestEntry(file, main));
            }
        } else {
            for (SourceFile file : model.testFiles().stream().sorted(Comparator.comparing(SourceFile::getRelativePath)).toList()) {
                tests.add(keepEntry(file, SourceScope.TEST, SafetyLevel.SAFE, "tests are not transformed (archmorph.transformation.preserve-tests=false)"));
            }
        }
        stabilise(tests, graph, plan, SourceScope.TEST);

        List<TransformationPlanEntry> all = new ArrayList<>(main);
        all.addAll(tests);
        buildClassMap(all, plan);
        deriveRewriteRequirements(all, model, plan);
        addPackagePrivateWarnings(main, graph);
        finish(all, plan);

        plan.getEntries().addAll(all);
        collectResourceFindings(plan, model, all);
        collectWarnings(plan, model, moduleReport);
        log.info("Planned {} entries: {} moved, {} conflicts, {} manual review",
                all.size(), plan.movedCount(), plan.getConflicts().size(),
                all.stream().filter(e -> e.getSafety() == SafetyLevel.MANUAL_REVIEW).count());
        return plan;
    }

    /**
     * Classes used by code outside their own module. In a layout that separates module APIs from internals
     * they form the module's public API.
     */
    static Set<String> exposedTypes(DependencyGraph graph, ModuleDiscoveryReport modules) {
        Set<String> exposed = new TreeSet<>();
        for (var edge : graph.getEdges()) {
            String from = modules.moduleOf(edge.getSource().getId());
            String to = modules.moduleOf(edge.getTarget().getId());
            if (from != null && to != null && !from.equals(to)) {
                exposed.add(edge.getTarget().getId());
            }
        }
        return exposed;
    }

    // ================================================================== entries

    private TransformationPlanEntry createMainEntry(SourceFile file, TransformationMappingReport mappings,
                                                    ModuleDiscoveryReport report, TransformationPlan plan) {
        TransformationPlanEntry entry = baseEntry(file, SourceScope.MAIN);

        if (!file.isParseable()) {
            String problem = file.getParsed().problems().isEmpty() ? "syntax error"
                    : "line " + file.getParsed().problems().getFirst().line() + ": " + file.getParsed().problems().getFirst().message();
            demote(entry, SafetyLevel.MANUAL_REVIEW, "the file could not be parsed (" + problem + "); it is copied unchanged");
            return entry;
        }
        ClassMetadata primary = file.getPrimaryType();
        if (primary == null) {
            keep(entry, SafetyLevel.SAFE, "file declares no type (for example package-info.java)");
            return entry;
        }

        entry.setNode(DependencyNodeLookup.of(primary));
        Optional<TransformationMapping> mapping = mappings.find(primary.getQualifiedName());
        ClassAssignment assignment = report.getAssignment(primary.getQualifiedName());
        if (mapping.isEmpty() || assignment == null) {
            keep(entry, SafetyLevel.SAFE_WITH_WARNING, "the class was not placed in any module");
            return entry;
        }
        TransformationMapping m = mapping.get();
        entry.setMapping(m);
        entry.setModule(assignment.moduleName());
        entry.setFolder(m.getFolderType());
        entry.setConfidence(assignment.confidence());
        entry.setTargetPackage(m.getTargetPackage());
        entry.setTargetFile(targetPath(SourceScope.MAIN, m.getTargetPackage(), file.getFileName()));

        if (assignment.excluded()) {
            keep(entry, SafetyLevel.SAFE, "excluded from the transformation by the user");
            entry.getActions().add(TransformationAction.EXCLUDE);
            return entry;
        }
        if (assignment.category() == ModuleCategory.APPLICATION && primary.getComponentType() == ComponentType.APPLICATION) {
            keep(entry, SafetyLevel.SAFE, "application entry point stays at the component-scan root");
            return entry;
        }

        SafetyLevel safety = SafetyLevel.SAFE;
        List<String> reasons = new ArrayList<>();
        for (ClassMetadata type : file.getTypes()) {
            for (RiskFlag flag : type.getRiskFlags()) {
                switch (flag) {
                    case DYNAMIC_CLASS_GENERATION -> {
                        safety = worse(safety, SafetyLevel.UNSUPPORTED);
                        reasons.add(type.getClassName() + " uses dynamic class generation or proxies");
                    }
                    case REFLECTION -> {
                        safety = worse(safety, SafetyLevel.MANUAL_REVIEW);
                        reasons.add(type.getClassName() + " looks classes up by name (Class.forName / loadClass)");
                    }
                    case GENERATED_CODE -> {
                        safety = worse(safety, SafetyLevel.MANUAL_REVIEW);
                        reasons.add("the file looks generated; generated sources are overwritten when regenerated");
                    }
                    case PACKAGE_SCANNING_CONFIGURATION -> {
                        safety = worse(safety, SafetyLevel.MANUAL_REVIEW);
                        reasons.add(type.getClassName() + " configures explicit package scanning " + type.getScannedPackages());
                    }
                    default -> {
                        // PACKAGE_PRIVATE_ACCESS and STRING_CLASS_REFERENCE are evaluated later
                    }
                }
            }
        }
        if (file.getTopLevelTypes().size() > 1) {
            safety = worse(safety, SafetyLevel.SAFE_WITH_WARNING);
            reasons.add("file declares " + file.getTopLevelTypes().size() + " top-level types; all move together");
        }
        if (assignment.confidence() < 0.5) {
            safety = worse(safety, SafetyLevel.SAFE_WITH_WARNING);
            reasons.add(String.format("low-confidence module placement (%.2f)", assignment.confidence()));
        }

        entry.setSafety(safety);
        entry.getReasons().addAll(reasons);
        if (!safety.allowsAutomaticMove()) {
            makeKept(entry);
            entry.getActions().add(TransformationAction.MANUAL_REVIEW);
        } else if (entry.getTargetPackage().equals(entry.getSourcePackage())) {
            entry.getActions().add(TransformationAction.KEEP);
            entry.setTargetFile(entry.getSourceFile());
        } else {
            entry.getActions().add(TransformationAction.MOVE);
            entry.getActions().add(TransformationAction.REWRITE_PACKAGE);
            retargetClasses(entry);
        }
        return entry;
    }

    private TransformationPlanEntry createTestEntry(SourceFile file, List<TransformationPlanEntry> mainEntries) {
        TransformationPlanEntry entry = baseEntry(file, SourceScope.TEST);
        ClassMetadata primary = file.getPrimaryType();
        if (!file.isParseable()) {
            demote(entry, SafetyLevel.MANUAL_REVIEW, "the test file could not be parsed; it is copied unchanged");
            return entry;
        }
        if (primary == null) {
            keep(entry, SafetyLevel.SAFE, "file declares no type");
            return entry;
        }
        boolean risky = file.getTypes().stream().anyMatch(t -> t.getRiskFlags().contains(RiskFlag.REFLECTION)
                || t.getRiskFlags().contains(RiskFlag.DYNAMIC_CLASS_GENERATION));
        if (risky) {
            keep(entry, SafetyLevel.MANUAL_REVIEW, "test uses reflection; kept in place");
            entry.getActions().add(TransformationAction.MANUAL_REVIEW);
            return entry;
        }

        String subject = subjectName(primary.getClassName());
        Optional<TransformationPlanEntry> tested = subject == null ? Optional.empty() : mainEntries.stream()
                .filter(e -> e.isMoved() && e.getNode() != null
                        && e.getNode().getClassName().equals(subject)
                        && e.getSourcePackage().equals(primary.getPackageName()))
                .findFirst();

        if (tested.isPresent()) {
            entry.setModule(tested.get().getModule());
            entry.setFolder(tested.get().getFolder());
            entry.setTargetPackage(tested.get().getTargetPackage());
            entry.setTargetFile(targetPath(SourceScope.TEST, tested.get().getTargetPackage(), file.getFileName()));
            entry.getActions().add(TransformationAction.MOVE);
            entry.getActions().add(TransformationAction.REWRITE_PACKAGE);
            retargetClasses(entry);
            entry.getReasons().add("moved next to the class it tests: " + subject);
            entry.setSafety(SafetyLevel.SAFE);
            entry.setConfidence(0.9);
        } else {
            keep(entry, SafetyLevel.SAFE, "no moved class under test; only references are updated");
        }
        return entry;
    }

    private String subjectName(String testClassName) {
        for (String suffix : TEST_SUFFIXES) {
            if (testClassName.endsWith(suffix) && testClassName.length() > suffix.length()) {
                return testClassName.substring(0, testClassName.length() - suffix.length());
            }
        }
        if (testClassName.startsWith("Test") && testClassName.length() > 4) {
            return testClassName.substring(4);
        }
        return null;
    }

    private TransformationPlanEntry baseEntry(SourceFile file, SourceScope scope) {
        TransformationPlanEntry entry = new TransformationPlanEntry();
        entry.setScope(scope);
        entry.setSourceFile(Path.of(file.getRelativePath()));
        entry.setTargetFile(Path.of(file.getRelativePath()));
        String pkg = file.getPackageName();
        entry.setSourcePackage(pkg == null ? "" : pkg);
        entry.setTargetPackage(pkg == null ? "" : pkg);
        for (ClassMetadata type : file.getTypes()) {
            entry.getClasses().add(new ClassMove(type.getQualifiedName(), type.getQualifiedName(), type.isNested()));
        }
        return entry;
    }

    private Path targetPath(SourceScope scope, String targetPackage, String fileName) {
        Path path = Path.of("src", scope == SourceScope.MAIN ? "main" : "test", "java");
        if (targetPackage != null && !targetPackage.isEmpty()) {
            path = path.resolve(targetPackage.replace('.', '/'));
        }
        return path.resolve(fileName);
    }

    // ================================================================== rules

    /** A string literal naming a project class keeps that class in place; naming a package flags the file. */
    private void applyStringReferenceRules(List<TransformationPlanEntry> entries, ProjectModel model, TransformationPlan plan) {
        ProjectClassRegistry registry = model.registry();
        Map<String, TransformationPlanEntry> entryByType = entryIndex(entries);
        Set<String> projectPackages = registry.packages();

        for (SourceFile file : model.mainFiles()) {
            for (ClassMetadata type : file.getTopLevelTypes()) {
                for (String literal : type.getQualifiedStringLiterals()) {
                    var named = registry.findByQualifiedName(literal);
                    if (named.isPresent()) {
                        TransformationPlanEntry target = entryByType.get(named.get().getTopLevelQualifiedName());
                        if (target != null && target.isMoved()) {
                            demote(target, SafetyLevel.MANUAL_REVIEW, "its name appears in a string literal in "
                                    + file.getFileName() + " (string-based class reference)");
                        }
                    } else if (projectPackages.contains(literal) || projectPackages.stream().anyMatch(p -> literal.startsWith(p + "."))) {
                        TransformationPlanEntry owner = entryByType.get(type.getQualifiedName());
                        if (owner != null && owner.isMoved()) {
                            demote(owner, SafetyLevel.MANUAL_REVIEW,
                                    "a string literal refers to project package '" + literal + "'");
                        } else if (owner != null) {
                            owner.getReasons().add("string literal refers to project package '" + literal + "'");
                        }
                    }
                }
            }
        }
    }

    /**
     * Resource files are never rewritten. A class whose fully-qualified name appears in one (an OpenAPI spec
     * naming a validation annotation, {@code spring.factories}, an XML bean definition, ...) therefore stays
     * where it is, otherwise the build or the runtime would look for it at the old location.
     */
    private void applyResourceReferenceRule(List<TransformationPlanEntry> entries, ProjectModel model) {
        Map<String, TransformationPlanEntry> entryByType = entryIndex(entries);
        Set<String> tokens = new TreeSet<>();
        entries.stream().filter(TransformationPlanEntry::isMoved)
                .forEach(e -> e.getClasses().stream().filter(c -> !c.nested()).forEach(c -> tokens.add(c.sourceQualifiedName())));
        for (ResourceFinding finding : resourceScanner.scan(model.structure(), tokens)) {
            TransformationPlanEntry target = entryByType.get(finding.reference());
            if (target != null && target.isMoved()) {
                demote(target, SafetyLevel.MANUAL_REVIEW, "its fully-qualified name appears in " + finding.file() + ":"
                        + finding.line() + "; resource files are not rewritten, so the class stays where it is");
            }
        }
    }

    /** Moving a Spring-managed class out of the component-scan root would silently stop it from being scanned. */
    private void applyScanRootRule(List<TransformationPlanEntry> entries, ProjectModel model) {
        Optional<ClassMetadata> app = model.mainTopLevelTypes().stream()
                .filter(t -> t.getComponentType() == ComponentType.APPLICATION).findFirst();
        if (app.isEmpty()) {
            return;
        }
        String root = app.get().getPackageName();
        for (TransformationPlanEntry entry : entries) {
            if (!entry.isMoved() || entry.getNode() == null) {
                continue;
            }
            ComponentType type = entry.getNode().getComponentType();
            boolean wasScanned = underPackage(entry.getSourcePackage(), root);
            boolean stillScanned = underPackage(entry.getTargetPackage(), root);
            if (type != null && SPRING_MANAGED.contains(type) && wasScanned && !stillScanned) {
                demote(entry, SafetyLevel.MANUAL_REVIEW,
                        "moving it would leave the component-scan root '" + root + "' of the application class");
            }
        }
    }

    /** Demote moves until no rule fires; each pass only demotes, so the loop terminates. */
    private void stabilise(List<TransformationPlanEntry> entries, DependencyGraph graph, TransformationPlan plan, SourceScope scope) {
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < entries.size() + 5) {
            changed = resolveCollisions(entries, plan);
            if (scope == SourceScope.MAIN) {
                changed |= demoteBrokenAccess(entries, graph);
            }
        }
    }

    private boolean resolveCollisions(List<TransformationPlanEntry> entries, TransformationPlan plan) {
        Map<String, List<TransformationPlanEntry>> byPath = new TreeMap<>();
        Map<String, Set<TransformationPlanEntry>> byClass = new TreeMap<>();
        for (TransformationPlanEntry entry : entries) {
            byPath.computeIfAbsent(entry.getTargetFile().toString().replace('\\', '/'), k -> new ArrayList<>()).add(entry);
            for (ClassMove move : entry.getClasses()) {
                byClass.computeIfAbsent(move.targetQualifiedName(), k -> new LinkedHashSet<>()).add(entry);
            }
        }

        // Detect everything first, then demote, so every kind of collision is reported.
        Map<TransformationPlanEntry, String> demotions = new LinkedHashMap<>();
        for (Map.Entry<String, List<TransformationPlanEntry>> group : byPath.entrySet()) {
            if (group.getValue().size() > 1 && group.getValue().stream().anyMatch(TransformationPlanEntry::isMoved)) {
                recordConflict(plan, PlanConflict.Type.TARGET_PATH_COLLISION, group.getKey(), group.getValue());
                group.getValue().stream().filter(TransformationPlanEntry::isMoved).forEach(e ->
                        demotions.putIfAbsent(e, "target path collides with another file (" + group.getKey() + ")"));
            }
        }
        for (Map.Entry<String, Set<TransformationPlanEntry>> group : byClass.entrySet()) {
            if (group.getValue().size() > 1 && group.getValue().stream().anyMatch(TransformationPlanEntry::isMoved)) {
                recordConflict(plan, PlanConflict.Type.TARGET_CLASS_NAME_COLLISION, group.getKey(), List.copyOf(group.getValue()));
                group.getValue().stream().filter(TransformationPlanEntry::isMoved).forEach(e ->
                        demotions.putIfAbsent(e, "target class name " + group.getKey() + " collides with another class"));
            }
        }
        demotions.forEach((entry, reason) -> demote(entry, SafetyLevel.MANUAL_REVIEW, reason));
        return !demotions.isEmpty();
    }

    private void recordConflict(TransformationPlan plan, PlanConflict.Type type, String target, List<TransformationPlanEntry> entries) {
        List<String> sources = entries.stream().map(e -> e.getSourceFile().toString().replace('\\', '/')).sorted().toList();
        boolean known = plan.getConflicts().stream().anyMatch(c -> c.type() == type && c.target().equals(target));
        if (!known) {
            plan.getConflicts().add(new PlanConflict(type, target, sources,
                    "Colliding files were kept in their original packages (distinct qualified names); choose different modules to move them."));
        }
    }

    /**
     * Package-private types used from another package after the move, and moves that would make a class depend
     * on a class that stays in the default package, cannot compile: keep them in place.
     */
    private boolean demoteBrokenAccess(List<TransformationPlanEntry> entries, DependencyGraph graph) {
        Map<String, TransformationPlanEntry> byType = entryIndex(entries);
        boolean changed = false;
        for (TransformationPlanEntry entry : entries) {
            if (entry.getNode() == null) {
                continue;
            }
            DependencyNode node = graph.findNode(entry.getNode().getId());
            if (node == null) {
                continue;
            }
            for (DependencyEdge edge : graph.getOutgoingEdges(node)) {
                TransformationPlanEntry other = byType.get(edge.getTarget().getId());
                if (other == null || other == entry) {
                    continue;
                }
                // 1. package-private target that was reachable only through the old shared package
                boolean targetIsPackagePrivate = isPackagePrivateType(other);
                boolean wasSamePackage = entry.getSourcePackage().equals(other.getSourcePackage());
                boolean nowDifferent = !entry.getTargetPackage().equals(other.getTargetPackage());
                if (targetIsPackagePrivate && wasSamePackage && nowDifferent) {
                    for (TransformationPlanEntry moved : List.of(entry, other)) {
                        if (moved.isMoved()) {
                            demote(moved, SafetyLevel.MANUAL_REVIEW, "package-private class "
                                    + other.getNode().getClassName() + " would no longer be accessible after the move");
                            changed = true;
                        }
                    }
                }
                // 2. classes in a named package cannot reference the default package
                if (entry.isMoved() && !entry.getTargetPackage().isEmpty() && other.getTargetPackage().isEmpty()
                        && !other.isMoved()) {
                    demote(entry, SafetyLevel.MANUAL_REVIEW,
                            "it depends on " + other.getNode().getClassName() + ", which stays in the default package and cannot be imported");
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean isPackagePrivateType(TransformationPlanEntry entry) {
        if (entry.getNode() == null || entry.getNode().getCompilationUnit() == null) {
            return false;
        }
        return entry.getNode().getCompilationUnit().getTypes().stream()
                .anyMatch(t -> t.getNameAsString().equals(entry.getNode().getClassName()) && !t.isPublic());
    }

    // ================================================================== class map & rewrites

    private void buildClassMap(List<TransformationPlanEntry> entries, TransformationPlan plan) {
        for (TransformationPlanEntry entry : entries) {
            if (!entry.isMoved()) {
                continue;
            }
            for (ClassMove move : entry.getClasses()) {
                plan.getClassMap().put(move.sourceQualifiedName(), move.targetQualifiedName());
            }
        }
    }

    private void deriveRewriteRequirements(List<TransformationPlanEntry> entries, ProjectModel model, TransformationPlan plan) {
        ProjectClassRegistry registry = model.registry();
        TypeResolver resolver = new TypeResolver(registry, Set.of());
        Map<String, SourceFile> filesByPath = new HashMap<>();
        model.files().forEach(f -> filesByPath.put(f.getRelativePath(), f));

        for (TransformationPlanEntry entry : entries) {
            SourceFile file = filesByPath.get(entry.getSourceFile().toString().replace('\\', '/'));
            if (file == null || !file.isParseable()) {
                continue;
            }
            if (entry.isMoved()) {
                entry.getRewriteRequirements().add("rewrite package " + entry.getSourcePackage() + " → " + entry.getTargetPackage());
            }
            var cu = file.getParsed().compilationUnit();
            List<ReferenceFinder.Reference> references = ReferenceFinder.find(cu, TypeContext.of(cu), resolver);

            long importRewrites = ReferenceFinder.importedTypes(cu, registry).stream().filter(plan.getClassMap()::containsKey).count();
            long simpleChanged = references.stream().filter(r -> r.kind() == ReferenceFinder.Kind.SIMPLE)
                    .map(ReferenceFinder.Reference::oldTopLevel).distinct()
                    .filter(t -> !plan.getClassMap().getOrDefault(t, t).equals(t) || entry.isMoved()).count();
            long qualified = references.stream().filter(r -> r.kind() != ReferenceFinder.Kind.SIMPLE)
                    .filter(r -> plan.getClassMap().containsKey(r.oldTopLevel())).count();
            boolean projectWildcard = cu.getImports().stream()
                    .anyMatch(i -> i.isAsterisk() && !i.isStatic() && !registry.classesInPackage(i.getNameAsString()).isEmpty());

            if (importRewrites > 0 || simpleChanged > 0 || projectWildcard) {
                entry.getActions().add(TransformationAction.REWRITE_IMPORT);
                entry.getRewriteRequirements().add("update imports (" + Math.max(importRewrites, simpleChanged) + " project types affected)");
            }
            if (qualified > 0) {
                entry.getActions().add(TransformationAction.REWRITE_QUALIFIED_REFERENCE);
                entry.getRewriteRequirements().add("rewrite " + qualified + " fully-qualified reference(s)");
            }
        }
    }

    private void addPackagePrivateWarnings(List<TransformationPlanEntry> main, DependencyGraph graph) {
        Map<String, TransformationPlanEntry> byType = entryIndex(main);
        for (TransformationPlanEntry entry : main) {
            if (!entry.isMoved() || entry.getNode() == null) {
                continue;
            }
            DependencyNode node = graph.findNode(entry.getNode().getId());
            if (node == null) {
                continue;
            }
            boolean hasPackagePrivate = entry.getNode().getCompilationUnit() != null
                    && entry.getNode().getCompilationUnit().getTypes().stream()
                    .anyMatch(t -> t.getMembers().stream().anyMatch(this::isPackagePrivateMember));
            if (!hasPackagePrivate) {
                continue;
            }
            Set<DependencyNode> neighbours = new HashSet<>(graph.getPredecessors(node));
            neighbours.addAll(graph.getSuccessors(node));
            boolean affected = neighbours.stream().map(n -> byType.get(n.getId()))
                    .anyMatch(o -> o != null && o.getSourcePackage().equals(entry.getSourcePackage())
                            && !o.getTargetPackage().equals(entry.getTargetPackage()));
            if (affected && entry.getSafety() == SafetyLevel.SAFE) {
                entry.setSafety(SafetyLevel.SAFE_WITH_WARNING);
                entry.getReasons().add("declares package-private members and neighbours from its old package moved elsewhere; "
                        + "visibility may need adjusting (the compile check will show it)");
            }
        }
    }

    private boolean isPackagePrivateMember(com.github.javaparser.ast.body.BodyDeclaration<?> member) {
        if (member instanceof com.github.javaparser.ast.nodeTypes.NodeWithModifiers<?> withModifiers
                && (member instanceof com.github.javaparser.ast.body.MethodDeclaration
                || member instanceof com.github.javaparser.ast.body.FieldDeclaration)) {
            var type = member.findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class).orElse(null);
            if (type instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration decl && decl.isInterface()) {
                return false;
            }
            return withModifiers.getModifiers().stream().noneMatch(m -> m.getKeyword() == com.github.javaparser.ast.Modifier.Keyword.PUBLIC
                    || m.getKeyword() == com.github.javaparser.ast.Modifier.Keyword.PRIVATE
                    || m.getKeyword() == com.github.javaparser.ast.Modifier.Keyword.PROTECTED);
        }
        return false;
    }

    private void finish(List<TransformationPlanEntry> entries, TransformationPlan plan) {
        int index = 1;
        for (TransformationPlanEntry entry : entries) {
            entry.setId(String.format("e-%04d", index++));
            if (entry.getActions().isEmpty()) {
                entry.getActions().add(TransformationAction.KEEP);
            }
        }
    }

    // ================================================================== reports

    private void collectWarnings(TransformationPlan plan, ProjectModel model, ModuleDiscoveryReport report) {
        plan.getWarnings().addAll(model.structure().warnings());
        plan.getWarnings().addAll(report.getWarnings());

        model.mainTopLevelTypes().stream()
                .filter(t -> t.getComponentType() == ComponentType.APPLICATION && !t.getScannedPackages().isEmpty())
                .forEach(t -> plan.getWarnings().add(t.getClassName() + " configures explicit package scanning "
                        + t.getScannedPackages() + "; verify the scanned packages cover the new module packages."));

        long manual = plan.getEntries().stream().filter(e -> e.getSafety() == SafetyLevel.MANUAL_REVIEW).count();
        long unsupported = plan.getEntries().stream().filter(e -> e.getSafety() == SafetyLevel.UNSUPPORTED).count();
        if (manual + unsupported > 0) {
            plan.getWarnings().add((manual + unsupported) + " file(s) were kept in place and need manual review.");
        }
        if (!model.unparseableFiles().isEmpty()) {
            plan.getWarnings().add(model.unparseableFiles().size() + " file(s) could not be parsed and are copied unchanged.");
        }
        if (!plan.getResourceFindings().isEmpty()) {
            plan.getWarnings().add("Configuration or resource files mention moved packages; they were not modified.");
        }
    }

    private void collectResourceFindings(TransformationPlan plan, ProjectModel model, List<TransformationPlanEntry> entries) {
        Set<String> tokens = new TreeSet<>();
        String base = plan.getBasePackage();
        for (TransformationPlanEntry entry : entries) {
            if (!entry.isMoved() || entry.getScope() != SourceScope.MAIN) {
                continue;
            }
            entry.getClasses().stream().filter(c -> !c.nested()).forEach(c -> tokens.add(c.sourceQualifiedName()));
            String oldPackage = entry.getSourcePackage();
            if (!oldPackage.isEmpty() && !base.equals(oldPackage) && !base.startsWith(oldPackage + ".")) {
                tokens.add(oldPackage);
            }
        }
        plan.getResourceFindings().addAll(resourceScanner.scan(model.structure(), tokens));
    }

    // ================================================================== helpers

    private Map<String, TransformationPlanEntry> entryIndex(List<TransformationPlanEntry> entries) {
        Map<String, TransformationPlanEntry> index = new LinkedHashMap<>();
        for (TransformationPlanEntry entry : entries) {
            entry.getClasses().stream().filter(c -> !c.nested()).forEach(c -> index.put(c.sourceQualifiedName(), entry));
        }
        return index;
    }

    private void keep(TransformationPlanEntry entry, SafetyLevel safety, String reason) {
        entry.setSafety(safety);
        entry.getReasons().add(reason);
        entry.getActions().add(TransformationAction.KEEP);
        entry.setTargetFile(entry.getSourceFile());
        entry.setTargetPackage(entry.getSourcePackage());
    }

    /** Keep a file in place and mark it for manual review (parse errors, risky constructs, collisions). */
    private void demote(TransformationPlanEntry entry, SafetyLevel safety, String reason) {
        makeKept(entry);
        entry.setSafety(worse(entry.getSafety(), safety));
        entry.getActions().add(TransformationAction.MANUAL_REVIEW);
        entry.getReasons().add(reason);
    }

    private void makeKept(TransformationPlanEntry entry) {
        entry.getActions().remove(TransformationAction.MOVE);
        entry.getActions().remove(TransformationAction.REWRITE_PACKAGE);
        entry.getActions().add(TransformationAction.KEEP);
        entry.setTargetFile(entry.getSourceFile());
        entry.setTargetPackage(entry.getSourcePackage());
        List<ClassMove> reset = entry.getClasses().stream()
                .map(c -> new ClassMove(c.sourceQualifiedName(), c.sourceQualifiedName(), c.nested())).toList();
        entry.getClasses().clear();
        entry.getClasses().addAll(reset);
    }

    private TransformationPlanEntry keepEntry(SourceFile file, SourceScope scope, SafetyLevel safety, String reason) {
        TransformationPlanEntry entry = baseEntry(file, scope);
        keep(entry, safety, reason);
        return entry;
    }

    private static SafetyLevel worse(SafetyLevel a, SafetyLevel b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private static boolean underPackage(String pkg, String root) {
        return root.isEmpty() || pkg.equals(root) || pkg.startsWith(root + ".");
    }

    /** Set the target qualified names of every type of a moved file from its target package. */
    private void retargetClasses(TransformationPlanEntry entry) {
        List<ClassMove> moved = entry.getClasses().stream()
                .map(c -> new ClassMove(c.sourceQualifiedName(),
                        retarget(c.sourceQualifiedName(), entry.getSourcePackage(), entry.getTargetPackage()), c.nested()))
                .toList();
        entry.getClasses().clear();
        entry.getClasses().addAll(moved);
    }

    static String retarget(String sourceQualifiedName, String sourcePackage, String targetPackage) {
        String rest = sourcePackage.isEmpty() ? sourceQualifiedName : sourceQualifiedName.substring(sourcePackage.length() + 1);
        return targetPackage.isEmpty() ? rest : targetPackage + "." + rest;
    }

    /** Small adapter so entries can reference the primary class' node without keeping the whole graph. */
    private static final class DependencyNodeLookup {
        static DependencyNode of(ClassMetadata metadata) {
            return com.anvith.archmorph.analysis.dependency.DependencyGraphBuilder.toNode(metadata);
        }
    }
}
