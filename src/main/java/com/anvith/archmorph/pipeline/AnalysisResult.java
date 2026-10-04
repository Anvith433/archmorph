package com.anvith.archmorph.pipeline;

import com.anvith.archmorph.analysis.architecture.ArchitectureReport;
import com.anvith.archmorph.analysis.cycle.CycleReport;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.extractor.ResolutionStatistics;
import com.anvith.archmorph.analysis.model.ProjectModel;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.parser.ClassMetadata;

import java.util.Map;

/**
 * Everything the analysis phases produce. In-memory only; API layers map it to DTOs.
 *
 * @param suggestion ArchMorph's optimised module suggestion (never edited in place)
 * @param facts      parsed metadata of top-level classes by qualified name
 */
public record AnalysisResult(ProjectModel model, DependencyGraph graph, ResolutionStatistics resolution,
                             ArchitectureReport architecture, CycleReport cycles, ModuleDiscoveryReport suggestion,
                             Map<String, ClassMetadata> facts, long durationMillis) {
}
