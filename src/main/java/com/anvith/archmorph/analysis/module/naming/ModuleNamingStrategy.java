package com.anvith.archmorph.analysis.module.naming;

import com.anvith.archmorph.analysis.dependency.DependencyNode;

import java.util.Set;

public interface ModuleNamingStrategy {

    /** Determine the business module name for a group of classes. */
    String determineModuleName(Set<DependencyNode> component);

    /** Convert a domain term into a valid, non-reserved Java package segment. */
    String toPackageSegment(String term);
}
