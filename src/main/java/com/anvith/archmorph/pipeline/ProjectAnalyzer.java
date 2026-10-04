package com.anvith.archmorph.pipeline;

import com.anvith.archmorph.analysis.architecture.ArchitectureAnalyzer;
import com.anvith.archmorph.analysis.architecture.ArchitectureReport;
import com.anvith.archmorph.analysis.cycle.CircularDependencyDetector;
import com.anvith.archmorph.analysis.cycle.CycleReport;
import com.anvith.archmorph.analysis.dependency.DependencyGraphBuilder;
import com.anvith.archmorph.analysis.model.ProjectModel;
import com.anvith.archmorph.analysis.model.ProjectModelBuilder;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryEngine;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.module.optimizer.ModuleOptimizer;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.parser.ClassMetadata;
import com.anvith.archmorph.parser.ProjectStructure;
import com.anvith.archmorph.parser.ProjectStructureDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Analysis phases: structure detection → scanning → parsing → registry → dependency graph →
 * architecture analysis → cycle detection → module discovery → optimisation. Pure: reads the
 * extracted project and returns structured data; the same code serves the web API and the CLI.
 */
@Service
public class ProjectAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ProjectAnalyzer.class);

    private final ProjectStructureDetector structureDetector;
    private final ProjectModelBuilder modelBuilder;
    private final DependencyGraphBuilder graphBuilder;
    private final ArchitectureAnalyzer architectureAnalyzer;
    private final CircularDependencyDetector cycleDetector;
    private final ModuleDiscoveryEngine moduleDiscoveryEngine;
    private final ModuleOptimizer moduleOptimizer;
    private final ArchMorphProperties properties;

    public ProjectAnalyzer(ProjectStructureDetector structureDetector, ProjectModelBuilder modelBuilder,
                           DependencyGraphBuilder graphBuilder, ArchitectureAnalyzer architectureAnalyzer,
                           CircularDependencyDetector cycleDetector, ModuleDiscoveryEngine moduleDiscoveryEngine,
                           ModuleOptimizer moduleOptimizer, ArchMorphProperties properties) {
        this.structureDetector = structureDetector;
        this.modelBuilder = modelBuilder;
        this.graphBuilder = graphBuilder;
        this.architectureAnalyzer = architectureAnalyzer;
        this.cycleDetector = cycleDetector;
        this.moduleDiscoveryEngine = moduleDiscoveryEngine;
        this.moduleOptimizer = moduleOptimizer;
        this.properties = properties;
    }

    public AnalysisResult analyze(Path extractedDirectory, ProgressListener progress, Deadline deadline) {
        long started = System.nanoTime();
        ProjectStructure structure = structureDetector.detect(extractedDirectory);
        progress.onEvent(ProgressEvent.STRUCTURE_DETECTED, "Maven project detected");
        deadline.check();

        progress.onEvent(ProgressEvent.PARSING_STARTED, "Parsing Java sources");
        ProjectModel model = modelBuilder.build(structure, properties.getAnalysis().getMaxJavaFiles(),
                properties.getAnalysis().isSymbolSolverEnabled());
        progress.onEvent(ProgressEvent.PARSING_COMPLETE, model.files().size() + " Java files parsed");
        deadline.check();

        DependencyGraphBuilder.Result built = graphBuilder.build(model, Set.of(), properties.getAnalysis().isSymbolSolverEnabled());
        progress.onEvent(ProgressEvent.DEPENDENCY_ANALYSIS_COMPLETE,
                built.graph().getNodeCount() + " classes, " + built.graph().getEdgeCount() + " dependencies");
        deadline.check();

        CycleReport cycles = cycleDetector.detect(built.graph());
        ArchitectureReport architecture = architectureAnalyzer.analyze(built.graph(), cycles);
        progress.onEvent(ProgressEvent.ARCHITECTURE_ANALYSIS_COMPLETE,
                architecture.getViolationCount() + " layer violations, " + cycles.getCycleCount() + " cycles");
        deadline.check();

        Map<String, ClassMetadata> facts = new HashMap<>();
        model.mainTopLevelTypes().forEach(t -> facts.put(t.getQualifiedName(), t));

        ModuleDiscoveryReport raw = moduleDiscoveryEngine.discover(built.graph(), facts);
        ModuleDiscoveryReport suggestion = moduleOptimizer.optimize(raw, built.graph());
        progress.onEvent(ProgressEvent.MODULE_DISCOVERY_COMPLETE, suggestion.getBusinessModuleCount() + " business modules");

        long millis = (System.nanoTime() - started) / 1_000_000;
        log.info("Analysis finished in {} ms: {} classes, {} modules", millis, built.graph().getNodeCount(),
                suggestion.getBusinessModuleCount());
        return new AnalysisResult(model, built.graph(), built.statistics(), architecture, cycles, suggestion, facts, millis);
    }
}
