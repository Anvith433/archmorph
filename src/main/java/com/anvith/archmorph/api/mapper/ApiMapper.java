package com.anvith.archmorph.api.mapper;

import com.anvith.archmorph.analysis.architecture.ArchitectureReport;
import com.anvith.archmorph.analysis.architecture.ClassMetric;
import com.anvith.archmorph.analysis.architecture.LayerViolation;
import com.anvith.archmorph.analysis.cycle.CycleReport;
import com.anvith.archmorph.analysis.cycle.DependencyCycle;
import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.model.SourceFile;
import com.anvith.archmorph.analysis.module.ClassAssignment;
import com.anvith.archmorph.analysis.module.ModuleCategory;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.module.ModuleInfo;
import com.anvith.archmorph.analysis.module.editing.ModuleEdit;
import com.anvith.archmorph.analysis.transformation.FileTransformation;
import com.anvith.archmorph.analysis.transformation.SafetyLevel;
import com.anvith.archmorph.analysis.transformation.TransformationAction;
import com.anvith.archmorph.analysis.transformation.TransformationResult;
import com.anvith.archmorph.analysis.transformation.planner.PlanConflict;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlan;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanEntry;
import com.anvith.archmorph.analysis.transformation.target.TargetArchitecture;
import com.anvith.archmorph.analysis.transformation.target.TargetArchitectureResolver;
import com.anvith.archmorph.analysis.validation.BuildResult;
import com.anvith.archmorph.analysis.validation.LevelResult;
import com.anvith.archmorph.analysis.validation.ValidationReport;
import com.anvith.archmorph.api.dto.AnalysisDtos;
import com.anvith.archmorph.api.dto.ArchitectureDtos;
import com.anvith.archmorph.api.dto.GraphDtos;
import com.anvith.archmorph.api.dto.ModuleDtos;
import com.anvith.archmorph.api.dto.PlanDtos;
import com.anvith.archmorph.api.dto.ProjectDtos;
import com.anvith.archmorph.api.dto.ValidationDtos;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.job.Job;
import com.anvith.archmorph.parser.ClassMetadata;
import com.anvith.archmorph.parser.ComponentType;
import com.anvith.archmorph.parser.ProjectStructure;
import com.anvith.archmorph.parser.SourceScope;
import com.anvith.archmorph.pipeline.AnalysisResult;
import com.anvith.archmorph.project.ProjectSession;
import com.anvith.archmorph.project.ProjectStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Maps internal domain objects to API DTOs. The API never serialises domain objects directly, so
 * absolute paths, ASTs and implementation classes cannot leak.
 */
@Component
public class ApiMapper {

    public static final String INDICATOR_NOTE =
            "ArchMorph static-analysis indicators. They summarise properties of the dependency graph and are not "
                    + "a verdict on code quality or a guarantee that a transformation preserves behaviour.";

    private final ArchMorphProperties properties;
    private final TargetArchitectureResolver architectureResolver;
    private final com.anvith.archmorph.analysis.module.boundary.BoundaryAdvisor boundaryAdvisor;

    public ApiMapper(ArchMorphProperties properties, TargetArchitectureResolver architectureResolver,
                     com.anvith.archmorph.analysis.module.boundary.BoundaryAdvisor boundaryAdvisor) {
        this.properties = properties;
        this.architectureResolver = architectureResolver;
        this.boundaryAdvisor = boundaryAdvisor;
    }

    // ================================================================== project & job

    public ProjectDtos.ProjectDto project(ProjectSession session, Job latestJob) {
        boolean ready = session.status() == ProjectStatus.READY_FOR_REVIEW || session.status() == ProjectStatus.COMPLETED;
        boolean completed = session.status() == ProjectStatus.COMPLETED;
        boolean downloadable = completed && session.transformedAvailable()
                && (!properties.getTransformation().isRequireValidation() || session.validation() != null);
        List<String> warnings = new ArrayList<>();
        if (session.plan() != null) {
            warnings.addAll(session.plan().getWarnings());
        }
        Instant expires = session.createdAt().plus(properties.getWorkspace().getRetention());
        ProjectDtos.ErrorInfo failure = session.failureCode() == null ? null
                : new ProjectDtos.ErrorInfo(session.failureCode(), session.failureMessage(), session.failureHint());
        return new ProjectDtos.ProjectDto(session.projectId(), session.displayName(), session.status().name(),
                session.createdAt(), expires, session.archiveBytes(),
                new ProjectDtos.Capabilities(ready && session.analysed(), ready && session.analysed(), downloadable,
                        session.plan() != null, session.validation() != null,
                        session.plan() != null && session.plan().requiresManualReview()),
                failure, latestJob == null ? null : latestJob.id(), latestJob == null ? null : latestJob.status().name(),
                warnings);
    }

    public ProjectDtos.JobDto job(Job job) {
        ProjectDtos.ErrorInfo error = job.errorCode() == null ? null
                : new ProjectDtos.ErrorInfo(job.errorCode(), job.errorMessage(), job.errorHint());
        return new ProjectDtos.JobDto(job.id(), job.projectId(), job.type().name(), job.status().name(), job.createdAt(),
                job.startedAt(), job.finishedAt(), error,
                job.events().stream().map(e -> new ProjectDtos.JobEventDto(e.timestamp(), e.event().name(), e.detail())).toList());
    }

    // ================================================================== analysis

    public AnalysisDtos.AnalysisDto analysis(AnalysisResult analysis, ModuleDiscoveryReport modules, TransformationPlan plan) {
        ArchitectureReport architecture = analysis.architecture();
        ProjectStructure structure = analysis.model().structure();

        AnalysisDtos.Counts counts = new AnalysisDtos.Counts(
                analysis.model().mainFiles().size(), analysis.model().testFiles().size(),
                architecture.getClassCount(), architecture.getControllerCount(), architecture.getServiceCount(),
                architecture.getRepositoryCount(), architecture.getEntityCount(), architecture.getDtoCount(),
                architecture.getConfigurationCount(), architecture.getComponentCount(), architecture.getUnknownCount(),
                architecture.getDependencyCount(), architecture.getCycleCount(), architecture.getViolationCount(),
                modules.getBusinessModuleCount());

        double confidenceSum = 0;
        int weight = 0;
        for (ModuleInfo module : modules.getBusinessModules()) {
            confidenceSum += module.getConfidence() * module.getClassCount();
            weight += module.getClassCount();
        }
        int moduleConfidence = weight == 0 ? 0 : (int) Math.round(100 * confidenceSum / weight);
        AnalysisDtos.Indicators indicators = new AnalysisDtos.Indicators(architecture.getArchitectureHealthIndicator(),
                moduleConfidence, readiness(plan), INDICATOR_NOTE);

        List<AnalysisDtos.ClassificationDto> classes = new ArrayList<>();
        for (DependencyNode node : analysis.graph().getInternalNodes()) {
            ClassMetadata facts = analysis.facts().get(node.getId());
            var classification = facts == null ? null : facts.getClassification();
            classes.add(new AnalysisDtos.ClassificationDto(node.getId(), node.getClassName(),
                    node.getComponentType() == null ? ComponentType.UNKNOWN.name() : node.getComponentType().name(),
                    classification == null ? 0 : classification.confidence(),
                    classification == null ? List.of() : classification.evidence(),
                    modules.moduleOf(node.getId()), node.getRelativePath()));
        }
        classes.sort(Comparator.comparing(AnalysisDtos.ClassificationDto::qualifiedName));

        List<AnalysisDtos.ViolationDto> violations = architecture.getLayerViolations().stream().map(this::violation).toList();
        List<AnalysisDtos.CycleDto> cycles = analysis.cycles().getStructuredCycles().stream().map(this::cycle).toList();
        List<AnalysisDtos.ClassMetricDto> metrics = architecture.getClassMetrics().stream().map(this::metric).toList();

        List<AnalysisDtos.ParseProblemDto> problems = new ArrayList<>();
        for (SourceFile file : analysis.model().unparseableFiles()) {
            var problem = file.getParsed().problems().isEmpty() ? null : file.getParsed().problems().getFirst();
            problems.add(new AnalysisDtos.ParseProblemDto(file.getRelativePath(), problem == null ? 0 : problem.line(),
                    problem == null ? "Syntax error" : problem.message()));
        }

        Map<String, Integer> resolution = new TreeMap<>();
        analysis.resolution().getCounts().forEach((k, v) -> resolution.put(k.name(), v));

        return new AnalysisDtos.AnalysisDto(
                new AnalysisDtos.ProjectInfoDto(structure.buildTool().name(), structure.groupId(), structure.artifactId(),
                        structure.springBoot(), structure.multiModule(), structure.warnings()),
                counts, indicators,
                new AnalysisDtos.ArchitectureSummaryDto(architecture.getPackageStyle().name(), architecture.getAverageInstability(),
                        architecture.getWarnings(), architecture.getRecommendations()),
                classes, violations, cycles, metrics, problems, resolution);
    }

    /** Fraction of files the plan can transform automatically, reduced by conflicts and parse problems. */
    private int readiness(TransformationPlan plan) {
        if (plan == null || plan.getEntries().isEmpty()) {
            return 0;
        }
        long total = plan.getEntries().stream().filter(e -> e.getScope() == SourceScope.MAIN).count();
        long automatic = plan.getEntries().stream().filter(e -> e.getScope() == SourceScope.MAIN)
                .filter(e -> e.getSafety().allowsAutomaticMove()).count();
        double ratio = total == 0 ? 0 : (double) automatic / total;
        double penalty = Math.min(0.3, plan.getConflicts().size() * 0.1);
        return (int) Math.round(100 * Math.max(0, ratio - penalty));
    }

    private AnalysisDtos.ViolationDto violation(LayerViolation v) {
        return new AnalysisDtos.ViolationDto(v.source(), v.sourceClassName(), v.sourceType().name(), v.target(),
                v.targetClassName(), v.targetType().name(), v.dependencyType().name(),
                v.location() == null ? null : v.location().file(), v.location() == null ? 0 : v.location().line(),
                v.severity().name(), v.message(), v.rationale());
    }

    private AnalysisDtos.CycleDto cycle(DependencyCycle c) {
        return new AnalysisDtos.CycleDto(c.cycleId(), c.nodes(), c.classNames(), c.path(), c.edgeCount(),
                c.dependencyTypes().stream().map(Enum::name).sorted().toList(), c.severity().name(), c.recommendation());
    }

    private AnalysisDtos.ClassMetricDto metric(ClassMetric m) {
        return new AnalysisDtos.ClassMetricDto(m.qualifiedName(), m.className(),
                m.componentType() == null ? "UNKNOWN" : m.componentType().name(), m.afferentCoupling(),
                m.efferentCoupling(), m.instability());
    }

    // ================================================================== graph

    public GraphDtos.GraphDto graph(AnalysisResult analysis, ModuleDiscoveryReport modules, int maxEdges) {
        DependencyGraph graph = analysis.graph();
        CycleReport cycles = analysis.cycles();
        Set<String> cycleNodes = new HashSet<>();
        cycles.getStructuredCycles().forEach(c -> cycleNodes.addAll(c.nodes()));
        Set<String> violationEdges = new HashSet<>();
        analysis.architecture().getLayerViolations().forEach(v -> violationEdges.add(v.source() + "->" + v.target()));

        Map<String, ClassMetric> metrics = analysis.architecture().getClassMetrics().stream()
                .collect(Collectors.toMap(ClassMetric::qualifiedName, m -> m, (a, b) -> a));

        List<GraphDtos.NodeDto> nodes = new ArrayList<>();
        for (DependencyNode node : graph.getInternalNodes()) {
            ClassAssignment assignment = modules.getAssignment(node.getId());
            ClassMetric metric = metrics.get(node.getId());
            nodes.add(new GraphDtos.NodeDto(node.getId(), node.getClassName(), node.getPackageName(),
                    node.getComponentType() == null ? "UNKNOWN" : node.getComponentType().name(),
                    assignment == null ? null : assignment.moduleName(),
                    assignment == null ? null : assignment.category().name(), node.getClassificationConfidence(),
                    metric == null ? 0 : metric.afferentCoupling(), metric == null ? 0 : metric.efferentCoupling(),
                    cycleNodes.contains(node.getId()), node.getRelativePath()));
        }
        nodes.sort(Comparator.comparing(GraphDtos.NodeDto::id));

        List<GraphDtos.EdgeDto> edges = new ArrayList<>();
        for (DependencyEdge edge : graph.getEdges()) {
            String from = modules.moduleOf(edge.getSource().getId());
            String to = modules.moduleOf(edge.getTarget().getId());
            edges.add(new GraphDtos.EdgeDto(edge.getSource().getId() + "->" + edge.getTarget().getId() + ":" + edge.getDependencyType(),
                    edge.getSource().getId(), edge.getTarget().getId(), edge.getDependencyType().name(),
                    edge.getOccurrences(), round(edge.getConfidence()), from != null && to != null && !from.equals(to),
                    violationEdges.contains(edge.getSource().getId() + "->" + edge.getTarget().getId()),
                    cycleNodes.contains(edge.getSource().getId()) && cycleNodes.contains(edge.getTarget().getId()),
                    edge.getLocation() == null ? null : edge.getLocation().toString()));
        }
        edges.sort(Comparator.comparing(GraphDtos.EdgeDto::id));
        boolean truncated = edges.size() > maxEdges;
        return new GraphDtos.GraphDto(nodes, truncated ? edges.subList(0, maxEdges) : edges, nodes.size(), edges.size(), truncated);
    }

    // ================================================================== architecture

    public ArchitectureDtos.ArchitectureDto architecture(AnalysisResult analysis, ModuleDiscoveryReport modules,
                                                           TransformationPlan plan) {
        Map<ComponentType, List<String>> byType = new EnumMap<>(ComponentType.class);
        Map<String, Integer> packages = new TreeMap<>();
        for (DependencyNode node : analysis.graph().getInternalNodes()) {
            byType.computeIfAbsent(node.getComponentType() == null ? ComponentType.UNKNOWN : node.getComponentType(),
                    k -> new ArrayList<>()).add(node.getClassName());
            packages.merge(node.getPackageName() == null ? "" : node.getPackageName(), 1, Integer::sum);
        }
        List<ArchitectureDtos.LayerDto> layers = new ArrayList<>();
        byType.forEach((type, names) -> {
            names.sort(String::compareTo);
            layers.add(new ArchitectureDtos.LayerDto(type.name().toLowerCase(), type.name(), names.size(), names));
        });

        TargetArchitecture target = plan == null ? architectureResolver.defaultArchitecture()
                : architectureResolver.resolve(plan.getStrategy());
        String base = plan == null ? "" : plan.getBasePackage();
        List<ArchitectureDtos.ProposedModuleDto> proposedModules = new ArrayList<>();
        Map<String, List<String>> shared = new TreeMap<>();
        List<String> application = new ArrayList<>();
        for (ModuleInfo module : modules.getModules()) {
            Map<String, List<String>> folders = new TreeMap<>();
            for (DependencyNode node : module.getClasses()) {
                ClassAssignment assignment = modules.getAssignment(node.getId());
                var entry = plan == null ? null : plan.getEntries().stream()
                        .filter(e -> e.getNode() != null && e.getNode().getId().equals(node.getId())).findFirst().orElse(null);
                String folder = entry != null && entry.getFolder() != null ? entry.getFolder().getFolderName() : "common";
                if (target.separatesApi() && entry != null && entry.getTargetPackage() != null) {
                    // show where the class really goes: the module API (root package) or an internal sub-package
                    String root = module.getCategory() == ModuleCategory.BUSINESS_MODULE
                            ? target.moduleRoot(base, module.getModuleName()) : target.sharedRoot(base);
                    String pkg = entry.getTargetPackage();
                    if (pkg.equals(root)) {
                        folder = "(api)";
                    } else if (pkg.startsWith(root + ".")) {
                        folder = pkg.substring(root.length() + 1);
                    }
                }
                if (module.getCategory() == ModuleCategory.BUSINESS_MODULE) {
                    folders.computeIfAbsent(folder, k -> new ArrayList<>()).add(node.getClassName());
                } else if (module.getCategory() == ModuleCategory.APPLICATION) {
                    application.add(node.getClassName());
                } else {
                    shared.computeIfAbsent(folder, k -> new ArrayList<>()).add(node.getClassName());
                }
                if (assignment == null) {
                    continue;
                }
            }
            folders.values().forEach(l -> l.sort(String::compareTo));
            if (module.getCategory() == ModuleCategory.BUSINESS_MODULE) {
                proposedModules.add(new ArchitectureDtos.ProposedModuleDto(module.getModuleName(), module.getConfidence(),
                        module.getClassCount(), folders));
            }
        }
        shared.values().forEach(l -> l.sort(String::compareTo));

        List<String> layout = target.describeLayout(base, proposedModules.stream().map(ArchitectureDtos.ProposedModuleDto::name).toList());
        return new ArchitectureDtos.ArchitectureDto(
                new ArchitectureDtos.CurrentDto(analysis.architecture().getPackageStyle().name(), layers, packages),
                new ArchitectureDtos.ProposedDto(target.strategy().name(), base, layout,
                        proposedModules, shared, application),
                List.of("The application entry point stays at the component-scan root; moving it would stop Spring from scanning the modules."));
    }

    // ================================================================== modules

    public ModuleDtos.ModulesDto modules(ProjectSession session) {
        ModuleDiscoveryReport suggestion = session.analysis().suggestion();
        ModuleDiscoveryReport finalReport = session.finalModules() == null ? suggestion : session.finalModules();
        List<String> warnings = new ArrayList<>(finalReport.getWarnings());
        var boundaries = boundaryAdvisor.advise(finalReport, session.analysis().graph(), session.analysis().facts());
        return new ModuleDtos.ModulesDto(modules(suggestion), session.decisions().stream().map(this::edit).toList(),
                modules(finalReport), warnings,
                "Module confidence, cohesion and coupling are static-analysis indicators derived from the dependency graph.",
                boundaries.cycles(), boundaries.suggestions().stream().map(s -> new ModuleDtos.BoundarySuggestionDto(
                        s.id(), s.kind().name(), s.from(), s.to(), s.subject(), s.title(), s.rationale(), s.dependencyCount(), s.steps(),
                        s.evidence(), s.edit() == null ? null : edit(s.edit()))).toList());
    }

    public List<ModuleDtos.ModuleDto> modules(ModuleDiscoveryReport report) {
        List<ModuleDtos.ModuleDto> result = new ArrayList<>();
        for (ModuleInfo module : report.getModules()) {
            List<ModuleDtos.ModuleClassDto> classes = new ArrayList<>();
            for (DependencyNode node : module.getClasses()) {
                ClassAssignment a = report.getAssignment(node.getId());
                classes.add(new ModuleDtos.ModuleClassDto(node.getId(), node.getClassName(),
                        node.getComponentType() == null ? "UNKNOWN" : node.getComponentType().name(),
                        a == null ? 0 : round(a.confidence()), a == null ? "AUTOMATIC" : a.origin().name(),
                        a != null && a.locked(), a != null && a.excluded(), a == null ? List.of() : a.reasons()));
            }
            classes.sort(Comparator.comparing(ModuleDtos.ModuleClassDto::qualifiedName));
            result.add(new ModuleDtos.ModuleDto(module.getModuleName(), module.getCategory().name(), module.getConfidence(),
                    module.getCohesion(), module.getExternalCoupling(), module.getClassCount(),
                    module.getInternalDependencies(), module.getExternalDependencies(),
                    new TreeMap<>(module.getDependenciesOnModules()),
                    module.getEvidence().stream().map(e -> e.replace("[metric] ", "")).toList(),
                    module.getWarnings().stream().map(w -> w.replace("[metric] ", "").replace("[opt] ", "")).toList(), classes));
        }
        result.sort(Comparator.comparing((ModuleDtos.ModuleDto m) -> !m.category().equals("BUSINESS_MODULE"))
                .thenComparing(ModuleDtos.ModuleDto::name));
        return result;
    }

    private ModuleDtos.ModuleEditDto edit(ModuleEdit edit) {
        return new ModuleDtos.ModuleEditDto(edit.type().name(), edit.module(), edit.newName(), edit.target(),
                edit.sources(), edit.className(), edit.classes());
    }

    public ModuleEdit edit(ModuleDtos.ModuleEditDto dto) {
        ModuleEdit.Type type;
        try {
            type = ModuleEdit.Type.valueOf(dto.type());
        } catch (IllegalArgumentException e) {
            throw new com.anvith.archmorph.common.exception.ArchMorphException(
                    com.anvith.archmorph.common.exception.ErrorCode.INVALID_MODULE_OPERATION, "Unknown module operation.");
        }
        return new ModuleEdit(type, dto.module(), dto.newName(), dto.target(), dto.sources(), dto.className(), dto.classes());
    }

    // ================================================================== plan

    public PlanDtos.PlanDto plan(TransformationPlan plan, ModuleDiscoveryReport modules) {
        List<PlanDtos.PlanEntryDto> entries = plan.getEntries().stream().map(this::entry).toList();
        long moved = plan.getEntries().stream().filter(TransformationPlanEntry::isMoved).count();
        long kept = plan.getEntries().stream().filter(e -> !e.isMoved()).count();
        long excluded = plan.getEntries().stream().filter(e -> e.getActions().contains(TransformationAction.EXCLUDE)).count();
        Map<SafetyLevel, Long> safety = plan.safetyCounts();
        long rewrites = plan.getEntries().stream().filter(e -> e.getActions().contains(TransformationAction.REWRITE_IMPORT)
                || e.getActions().contains(TransformationAction.REWRITE_PACKAGE)
                || e.getActions().contains(TransformationAction.REWRITE_QUALIFIED_REFERENCE)).count();
        PlanDtos.PlanSummaryDto summary = new PlanDtos.PlanSummaryDto(entries.size(), (int) moved, (int) kept, (int) excluded,
                safety.get(SafetyLevel.MANUAL_REVIEW).intValue(), safety.get(SafetyLevel.UNSUPPORTED).intValue(),
                safety.get(SafetyLevel.SAFE).intValue(), safety.get(SafetyLevel.SAFE_WITH_WARNING).intValue(),
                plan.getConflicts().size(), (int) rewrites);

        List<String> businessModules = modules.getBusinessModules().stream().map(ModuleInfo::getModuleName).sorted().toList();
        List<String> layout = architectureResolver.resolve(plan.getStrategy()).describeLayout(plan.getBasePackage(), businessModules);
        return new PlanDtos.PlanDto(plan.getStrategy().name(), plan.getBasePackage(), plan.fingerprint(), summary, entries,
                plan.getConflicts().stream().map(this::conflict).toList(), plan.getWarnings(),
                plan.getResourceFindings().stream().map(f -> new PlanDtos.ResourceFindingDto(f.file(), f.line(), f.reference(), f.snippet())).toList(),
                layout, new LinkedHashMap<>(plan.getClassMap()), plan.isModulithVerification(), List.copyOf(plan.getGeneratedFiles()));
    }

    private PlanDtos.PlanEntryDto entry(TransformationPlanEntry e) {
        List<String> actions = e.getActions().stream().map(Enum::name).sorted().toList();
        return new PlanDtos.PlanEntryDto(e.getId(), e.getScope().name(),
                e.getNode() != null ? e.getNode().getClassName() : fileName(e), normalize(e.getSourceFile()),
                normalize(e.getTargetFile()), e.getSourcePackage(), e.getTargetPackage(), e.getModule(),
                e.getFolder() == null ? null : e.getFolder().getFolderName(), actions, e.getSafety().name(),
                e.getRisk().name(), round(e.getConfidence()), e.getRewriteRequirements(), e.getReasons(),
                e.getClasses().stream().map(c -> new PlanDtos.ClassMoveDto(c.sourceQualifiedName(), c.targetQualifiedName(), c.nested())).toList());
    }

    private PlanDtos.ConflictDto conflict(PlanConflict c) {
        return new PlanDtos.ConflictDto(c.type().name(), c.target(), c.sources(), c.resolution());
    }

    public PlanDtos.DryRunDto dryRun(TransformationPlan plan, ModuleDiscoveryReport modules, TransformationResult result) {
        List<PlanDtos.FileChangeDto> files = new ArrayList<>();
        for (FileTransformation f : result.files()) {
            files.add(new PlanDtos.FileChangeDto(f.entryId(), f.sourcePath(), f.targetPath(), f.changed(), f.packageChanged(),
                    f.importChanges().stream().map(c -> c.kind() + " " + (c.from() == null ? "" : c.from()) + (c.to() == null ? "" : " → " + c.to())).toList(),
                    f.qualifiedRewrites(), f.linesAdded(), f.linesRemoved(), f.warnings()));
        }
        return new PlanDtos.DryRunDto(plan(plan, modules), files, result.warnings(), result.durationMillis());
    }

    // ================================================================== validation

    public ValidationDtos.ValidationDto validation(ValidationReport report) {
        List<ValidationDtos.LevelDto> levels = report.levels().stream().map(this::level).toList();
        BuildResult b = report.build();
        return new ValidationDtos.ValidationDto(report.status().name(), report.completedAt(), levels,
                b == null ? null : new ValidationDtos.BuildDto(b.command(), b.exitCode(), b.stdout(), b.stderr(),
                        b.durationMillis(), b.timedOut(), b.truncated()));
    }

    private ValidationDtos.LevelDto level(LevelResult l) {
        return new ValidationDtos.LevelDto(l.level().name(), l.level().getLabel(), l.status().name(), l.summary(),
                l.issueCount(), l.issues().stream().map(i -> new ValidationDtos.IssueDto(i.severity().name(), i.file(), i.line(),
                i.message(), i.probableCause())).toList(), l.durationMillis());
    }

    // ================================================================== helpers

    private static String normalize(java.nio.file.Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String fileName(TransformationPlanEntry entry) {
        return entry.getSourceFile().getFileName().toString().replace(".java", "");
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
