package com.anvith.archmorph.analysis.architecture;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultArchitectureAnalyzer
        implements ArchitectureAnalyzer {

    /*
     * Specialized Analyzers
     */
    private final LayerAnalyzer layerAnalyzer;

    private final LayerViolationDetector layerViolationDetector;

    @Override
    public ArchitectureReport analyze(
            DependencyGraph dependencyGraph) {

        ArchitectureReport report =
                new ArchitectureReport();

        /*
         * Total Dependencies
         */
        report.setDependencyCount(
                dependencyGraph.getEdgeCount()
        );

        /*
         * Analyze Architecture Layers
         */
        layerAnalyzer.analyze(
                dependencyGraph,
                report
        );

        layerViolationDetector.detect(
        dependencyGraph,
        report
);

        /*
         * Future Analyzers
         *
         * layerViolationDetector.detect(...);
         * circularDependencyDetector.detect(...);
         * recommendationEngine.generate(...);
         */

        return report;
    }

}