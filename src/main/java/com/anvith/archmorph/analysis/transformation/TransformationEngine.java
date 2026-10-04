package com.anvith.archmorph.analysis.transformation;

import com.anvith.archmorph.analysis.model.ProjectModel;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlan;

import java.nio.file.Path;

public interface TransformationEngine {

    /**
     * Execute a plan: copy the project into {@code targetRoot} applying exactly the planned moves and rewrites.
     * The source project is only read. Planning and file mutation are separate: this method performs no decisions.
     */
    TransformationResult execute(ProjectModel model, TransformationPlan plan, Path targetRoot);

    /** Perform every rewrite in memory (nothing is written) to validate the plan and measure changes. */
    TransformationResult dryRun(ProjectModel model, TransformationPlan plan);
}
