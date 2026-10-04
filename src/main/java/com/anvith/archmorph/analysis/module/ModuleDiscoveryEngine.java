package com.anvith.archmorph.analysis.module;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.parser.ClassMetadata;

import java.util.Map;

public interface ModuleDiscoveryEngine {

    /**
     * Discover business modules from the dependency graph and parsed class
     * facts (keyed by top-level qualified name; may be empty).
     */
    ModuleDiscoveryReport discover(DependencyGraph dependencyGraph, Map<String, ClassMetadata> facts);

    default ModuleDiscoveryReport discover(DependencyGraph dependencyGraph) {
        return discover(dependencyGraph, Map.of());
    }
}
