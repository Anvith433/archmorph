package com.anvith.archmorph.analysis.cycle;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DefaultCircularDependencyDetector
        implements CircularDependencyDetector {

    @Override
    public CycleReport detect(DependencyGraph graph) {

        CycleReport report = new CycleReport();

        /*
         * Build adjacency list.
         */
        Map<String, List<String>> adjacency =
                new HashMap<>();

        for (DependencyEdge edge : graph.getEdges()) {

            String source =
                    edge.getSource().getClassName();

            String target =
                    edge.getTarget().getClassName();

            adjacency
                    .computeIfAbsent(
                            source,
                            k -> new ArrayList<>()
                    )
                    .add(target);
        }

        Set<String> visited = new HashSet<>();

        Set<String> recursionStack =
                new HashSet<>();

        Deque<String> path =
                new ArrayDeque<>();

        for (DependencyNode node : graph.getNodes()) {

            if (!visited.contains(node.getClassName())) {

                dfs(
                        node.getClassName(),
                        adjacency,
                        visited,
                        recursionStack,
                        path,
                        report
                );

            }

        }

        return report;
    }

    /**
     * DFS traversal.
     */
    private void dfs(

            String current,

            Map<String, List<String>> adjacency,

            Set<String> visited,

            Set<String> recursionStack,

            Deque<String> path,

            CycleReport report) {

        visited.add(current);

        recursionStack.add(current);

        path.addLast(current);

        for (String neighbour :

                adjacency.getOrDefault(
                        current,
                        Collections.emptyList()
                )) {

            if (!visited.contains(neighbour)) {

                dfs(

                        neighbour,

                        adjacency,

                        visited,

                        recursionStack,

                        path,

                        report

                );

            }

            else if (recursionStack.contains(neighbour)) {

                List<String> cycle =
                        new ArrayList<>();

                boolean collect = false;

                for (String node : path) {

                    if (node.equals(neighbour)) {

                        collect = true;

                    }

                    if (collect) {

                        cycle.add(node);

                    }

                }

                cycle.add(neighbour);

                report.addCycle(cycle);

            }

        }

        recursionStack.remove(current);

        path.removeLast();

    }

}