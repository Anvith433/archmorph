package com.anvith.archmorph.analysis.transformation.planner;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.model.ProjectModel;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.transformation.target.TargetStrategy;

public interface TransformationPlanner {

    /**
     * Creates the complete execution plan for transforming a layered project into a
     * modular monolith. Pure computation: no file is read for modification or written.
     *
     * @param moduleReport the final module assignment (suggestion plus user decisions)
     */
    TransformationPlan plan(ProjectModel model, DependencyGraph dependencyGraph,
                            ModuleDiscoveryReport moduleReport, TargetStrategy strategy);
}
