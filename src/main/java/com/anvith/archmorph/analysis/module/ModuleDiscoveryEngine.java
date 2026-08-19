package com.anvith.archmorph.analysis.module;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;

public interface ModuleDiscoveryEngine {

    /**
     * Discover business modules from the
     * dependency graph.
     */
    ModuleDiscoveryReport discover(
            DependencyGraph dependencyGraph
    );

}