package com.anvith.archmorph.api.dto;

import java.util.List;
import java.util.Map;

/** Analysis report views. All indicators are static-analysis indicators, not quality verdicts. */
public final class AnalysisDtos {

    private AnalysisDtos() {
    }

    public record Counts(int javaFiles, int testFiles, int classes, int controllers, int services, int repositories,
                         int entities, int dtos, int configurations, int components, int unknown, int dependencies,
                         int cycles, int layerViolations, int businessModules) {
    }

    public record Indicators(int architectureHealth, int moduleConfidence, int transformationReadiness, String note) {
    }

    public record ClassificationDto(String qualifiedName, String className, String type, double confidence,
                                    List<String> evidence, String module, String file) {
    }

    public record ViolationDto(String source, String sourceClassName, String sourceType, String target,
                               String targetClassName, String targetType, String dependencyType, String file, int line,
                               String severity, String message, String rationale) {
    }

    public record CycleDto(String cycleId, List<String> nodes, List<String> classNames, List<String> path, int edgeCount,
                           List<String> dependencyTypes, String severity, String recommendation) {
    }

    public record ClassMetricDto(String qualifiedName, String className, String type, int afferentCoupling,
                                 int efferentCoupling, double instability) {
    }

    public record ParseProblemDto(String file, int line, String message) {
    }

    public record ProjectInfoDto(String buildTool, String groupId, String artifactId, boolean springBoot,
                                 boolean multiModule, List<String> notes) {
    }

    public record ArchitectureSummaryDto(String packageStyle, double averageInstability, List<String> warnings,
                                         List<String> recommendations) {
    }

    public record AnalysisDto(ProjectInfoDto project, Counts counts, Indicators indicators,
                              ArchitectureSummaryDto architecture, List<ClassificationDto> classes,
                              List<ViolationDto> violations, List<CycleDto> cycles, List<ClassMetricDto> metrics,
                              List<ParseProblemDto> parseProblems, Map<String, Integer> resolution) {
    }
}
