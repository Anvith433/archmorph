package com.anvith.archmorph.analysis.transformation.planner;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;

import java.nio.file.Path;

public interface TransformationPlanner {

    /**
     * Creates the complete execution plan
     * for transforming a layered project
     * into a modular monolith.
     */
    TransformationPlan plan(

            DependencyGraph dependencyGraph,

            ModuleDiscoveryReport moduleReport,

            Path sourceRoot,

            Path targetRoot

    );

}