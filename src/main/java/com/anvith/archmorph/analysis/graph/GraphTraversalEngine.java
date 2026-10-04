package com.anvith.archmorph.analysis.graph;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;

import java.util.Set;

public interface GraphTraversalEngine {

    /**
     * Returns every node reachable
     * from the given starting node.
     */
    Set<DependencyNode> traverse(
            DependencyGraph graph,
            DependencyNode startNode
    );

}