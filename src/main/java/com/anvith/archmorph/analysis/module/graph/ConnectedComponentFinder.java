package com.anvith.archmorph.analysis.module.graph;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;

import java.util.List;
import java.util.Set;

public interface ConnectedComponentFinder {

    List<Set<DependencyNode>> findComponents(
            DependencyGraph graph
    );

}