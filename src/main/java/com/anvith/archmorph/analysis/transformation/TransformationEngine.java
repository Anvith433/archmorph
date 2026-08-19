package com.anvith.archmorph.analysis.transformation;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import java.nio.file.Path;

public interface TransformationEngine {

    /**
     * Transform a layered architecture project
     * into a modular monolith.
     *
     * @param dependencyGraph Dependency graph
     * @param moduleReport    Discovered modules
     * @param sourceRoot      Original project root
     * @param targetRoot      Target modular project
     */
    void transform(
            DependencyGraph dependencyGraph,
            ModuleDiscoveryReport moduleReport,
            Path sourceRoot,
            Path targetRoot
    );

}