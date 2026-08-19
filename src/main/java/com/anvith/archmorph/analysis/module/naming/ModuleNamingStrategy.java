package com.anvith.archmorph.analysis.module.naming;

import com.anvith.archmorph.analysis.dependency.DependencyNode;

import java.util.Set;

public interface ModuleNamingStrategy {

    /**
     * Determine the business
     * module name for one
     * connected graph component.
     */
    String determineModuleName(
            Set<DependencyNode> component
    );

}