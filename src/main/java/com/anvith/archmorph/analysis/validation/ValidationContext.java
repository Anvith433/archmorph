package com.anvith.archmorph.analysis.validation;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.model.ProjectModel;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlan;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Inputs shared by all validators. The transformed project is parsed lazily, once, and reused.
 *
 * @param original        model of the original project
 * @param originalGraph   dependency graph of the original project
 * @param plan            the executed plan
 * @param modules         the final module assignment used by the plan
 * @param transformedRoot root of the generated project
 * @param scratch         private temporary directory validators may use (deleted afterwards)
 */
public record ValidationContext(ProjectModel original, DependencyGraph originalGraph, TransformationPlan plan,
                                ModuleDiscoveryReport modules, Path transformedRoot,
                                Path scratch, Supplier<TransformedProject> transformed) {

    /** The re-parsed transformed project. */
    public record TransformedProject(ProjectModel model, DependencyGraph graph) {
    }
}
