package com.anvith.archmorph.analysis.transformation.planner;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class DefaultTransformationPlanner
        implements TransformationPlanner {

    @Override
    public TransformationPlan plan(

            DependencyGraph dependencyGraph,

            ModuleDiscoveryReport moduleReport,

            Path sourceRoot,

            Path targetRoot) {

        /*
         * Sprint 17
         *
         * Planner is currently a placeholder.
         *
         * Actual execution is performed by
         * TransformationEngine.
         *
         * Later sprints may populate this
         * plan with executable entries.
         */

        return new TransformationPlan();
    }

}