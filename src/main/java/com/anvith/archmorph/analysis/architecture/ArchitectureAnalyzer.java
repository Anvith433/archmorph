package com.anvith.archmorph.analysis.architecture;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;

public interface ArchitectureAnalyzer {

    /**
     * Analyse the complete dependency graph.
     */
    ArchitectureReport analyze(
            DependencyGraph dependencyGraph
    );

}