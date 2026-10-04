package com.anvith.archmorph.analysis.architecture;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.parser.ComponentType;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Counts components per layer and detects the package organisation style. */
@Service
public class LayerAnalyzer {

    private static final Set<String> LAYER_SEGMENTS = Set.of(
            "controller", "controllers", "web", "rest", "api", "service", "services", "repository",
            "repositories", "dao", "entity", "entities", "model", "domain", "dto", "dtos", "config",
            "configuration", "security", "exception", "exceptions", "util", "utils", "mapper");

    public void analyze(DependencyGraph dependencyGraph, ArchitectureReport report) {
        int layered = 0;
        int other = 0;
        Map<String, Integer> segmentCounts = new HashMap<>();

        for (DependencyNode node : dependencyGraph.getInternalNodes()) {
            ComponentType type = node.getComponentType() == null ? ComponentType.UNKNOWN : node.getComponentType();
            report.setClassCount(report.getClassCount() + 1);
            report.increment(type);
            switch (type) {
                case CONTROLLER -> report.setControllerCount(report.getControllerCount() + 1);
                case SERVICE -> report.setServiceCount(report.getServiceCount() + 1);
                case REPOSITORY -> report.setRepositoryCount(report.getRepositoryCount() + 1);
                case ENTITY -> report.setEntityCount(report.getEntityCount() + 1);
                case DTO -> report.setDtoCount(report.getDtoCount() + 1);
                case COMPONENT -> report.setComponentCount(report.getComponentCount() + 1);
                case CONFIGURATION -> report.setConfigurationCount(report.getConfigurationCount() + 1);
                case SECURITY, FILTER -> report.setSecurityCount(report.getSecurityCount() + 1);
                case EXCEPTION, EXCEPTION_HANDLER -> report.setExceptionCount(report.getExceptionCount() + 1);
                case UNKNOWN -> report.setUnknownCount(report.getUnknownCount() + 1);
                default -> {
                    // APPLICATION and others are only part of the distribution.
                }
            }

            String packageName = node.getPackageName() == null ? "" : node.getPackageName();
            String last = packageName.substring(packageName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (LAYER_SEGMENTS.contains(last)) {
                layered++;
            } else {
                other++;
            }
            segmentCounts.merge(last, 1, Integer::sum);
        }

        int total = layered + other;
        if (total == 0 || segmentCounts.size() <= 1) {
            report.setPackageStyle(ArchitectureReport.PackageStyle.FLAT);
        } else if (layered >= total * 0.75) {
            report.setPackageStyle(ArchitectureReport.PackageStyle.LAYERED);
        } else if (layered <= total * 0.25) {
            report.setPackageStyle(ArchitectureReport.PackageStyle.FEATURE_BASED);
        } else {
            report.setPackageStyle(ArchitectureReport.PackageStyle.MIXED);
        }
    }
}
