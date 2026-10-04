package com.anvith.archmorph.analysis.graph;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Iterative traversal over the graph's adjacency maps, treating the graph as
 * undirected. O(V + E); no recursion, so deep graphs cannot overflow the stack.
 */
@Service
public class DefaultGraphTraversalEngine implements GraphTraversalEngine {

    @Override
    public Set<DependencyNode> traverse(DependencyGraph graph, DependencyNode startNode) {
        Set<DependencyNode> visited = new LinkedHashSet<>();
        if (startNode == null) {
            return visited;
        }
        Deque<DependencyNode> stack = new ArrayDeque<>();
        stack.push(startNode);
        while (!stack.isEmpty()) {
            DependencyNode current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            graph.getSuccessors(current).forEach(stack::push);
            graph.getPredecessors(current).forEach(stack::push);
        }
        return visited;
    }
}
