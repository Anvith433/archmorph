package com.anvith.archmorph.analysis.graph;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class DefaultGraphTraversalEngine
        implements GraphTraversalEngine {

    @Override
    public Set<DependencyNode> traverse(
            DependencyGraph graph,
            DependencyNode startNode) {

        Set<DependencyNode> visited =
                new LinkedHashSet<>();

        dfs(
                graph,
                startNode,
                visited
        );

        return visited;
    }

    /**
     * Performs Depth First Search (DFS)
     * treating the dependency graph as an
     * undirected graph.
     *
     * Why?
     *
     * Business module discovery requires
     * connected components rather than
     * dependency direction.
     */
    private void dfs(
            DependencyGraph graph,
            DependencyNode current,
            Set<DependencyNode> visited) {

        if (current == null) {
            return;
        }

        if (!visited.add(current)) {
            return;
        }

        for (DependencyEdge edge : graph.getEdges()) {

            /*
             * Outgoing dependency
             */
            if (edge.getSource().equals(current)) {

                dfs(
                        graph,
                        edge.getTarget(),
                        visited
                );
            }

            /*
             * Incoming dependency
             */
            else if (edge.getTarget().equals(current)) {

                dfs(
                        graph,
                        edge.getSource(),
                        visited
                );
            }
        }
    }
}