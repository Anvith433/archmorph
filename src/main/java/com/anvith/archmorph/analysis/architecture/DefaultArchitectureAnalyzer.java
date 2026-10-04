package com.anvith.archmorph.analysis.architecture;

import com.anvith.archmorph.analysis.cycle.CycleReport;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Service
public class DefaultArchitectureAnalyzer implements ArchitectureAnalyzer {

    private final LayerAnalyzer layerAnalyzer;
    private final LayerViolationDetector layerViolationDetector;

    public DefaultArchitectureAnalyzer(LayerAnalyzer layerAnalyzer, LayerViolationDetector layerViolationDetector) {
        this.layerAnalyzer = layerAnalyzer;
        this.layerViolationDetector = layerViolationDetector;
    }

    @Override
    public ArchitectureReport analyze(DependencyGraph dependencyGraph) {
        return analyze(dependencyGraph, null);
    }

    @Override
    public ArchitectureReport analyze(DependencyGraph graph, CycleReport cycleReport) {
        ArchitectureReport report = new ArchitectureReport();
        report.setDependencyCount(graph.getEdgeCount());
        layerAnalyzer.analyze(graph, report);
        layerViolationDetector.detect(graph, report);
        computeCoupling(graph, report);

        if (cycleReport != null) {
            report.setCycleCount(cycleReport.getCycleCount());
        }
        report.setArchitectureHealthIndicator(healthIndicator(report));
        addFindings(report);
        return report;
    }

    private void computeCoupling(DependencyGraph graph, ArchitectureReport report) {
        double instabilitySum = 0;
        int counted = 0;
        for (DependencyNode node : graph.getInternalNodes()) {
            int ca = graph.getPredecessors(node).size();
            int ce = graph.getSuccessors(node).size();
            double instability = ca + ce == 0 ? 0 : (double) ce / (ca + ce);
            report.getClassMetrics().add(new ClassMetric(node.getQualifiedName(), node.getClassName(),
                    node.getComponentType(), ca, ce, round(instability)));
            if (ca + ce > 0) {
                instabilitySum += instability;
                counted++;
            }
        }
        report.getClassMetrics().sort(Comparator.comparing(ClassMetric::qualifiedName));
        report.setAverageInstability(counted == 0 ? 0 : round(instabilitySum / counted));
    }

    /**
     * Static-analysis indicator (0..100). Penalties: layer violations relative to
     * dependencies, cycles relative to classes, and unclassified classes.
     * It is a heuristic summary, not a quality score.
     */
    private int healthIndicator(ArchitectureReport report) {
        if (report.getClassCount() == 0) {
            return 0;
        }
        double violationRatio = report.getDependencyCount() == 0 ? 0
                : (double) report.getViolationCount() / report.getDependencyCount();
        double cycleRatio = (double) report.getCycleCount() / report.getClassCount();
        double unknownRatio = (double) report.getUnknownCount() / report.getClassCount();
        double score = 100
                - Math.min(40, violationRatio * 200)
                - Math.min(30, cycleRatio * 150)
                - Math.min(20, unknownRatio * 40);
        return (int) Math.max(0, Math.round(score));
    }

    private void addFindings(ArchitectureReport report) {
        if (report.getUnknownCount() > 0) {
            report.getWarnings().add(report.getUnknownCount()
                    + " classes could not be classified into an architectural role.");
        }
        if (report.getViolationCount() > 0) {
            report.getRecommendations().add("Resolve layer violations before or right after modularisation; "
                    + "they become cross-module dependencies.");
        }
        if (report.getCycleCount() > 0) {
            report.getRecommendations().add("Review dependency cycles: cycles spanning modules prevent clean module boundaries.");
        }
        if (report.getPackageStyle() == ArchitectureReport.PackageStyle.FEATURE_BASED) {
            report.getWarnings().add("The project already appears to be organised by feature; transformation may add little value.");
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
