package com.anvith.archmorph.analysis.module.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.graph.GraphTraversalEngine;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.module.ModuleInfo;
import com.anvith.archmorph.parser.ComponentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class DefaultBusinessModuleExtractor
        implements BusinessModuleExtractor {

    /*
     * Performs DFS/BFS traversal
     * on the dependency graph.
     */
    private final GraphTraversalEngine traversalEngine;

    @Override
    public ModuleDiscoveryReport extract(
            DependencyGraph graph) {

        ModuleDiscoveryReport report =
                new ModuleDiscoveryReport();

        /*
         * Every controller is treated as
         * a business entry point.
         */
        for (DependencyNode node : graph.getNodes()) {

            if (node.getComponentType() == null) {
                continue;
            }

            if (node.getComponentType() != ComponentType.CONTROLLER) {
                continue;
            }

            /*
             * Collect every reachable class
             * from this controller.
             */
            Set<DependencyNode> reachableNodes =
                    traversalEngine.traverse(
                            graph,
                            node
                    );

            /*
             * Temporary module name.
             *
             * Later this will become:
             * User
             * Product
             * Order
             * etc.
             */
            ModuleInfo module =
                    report.getOrCreateModule(
                            node.getClassName()
                    );

            /*
             * Add every discovered class
             * into this module.
             */
            for (DependencyNode dependencyNode : reachableNodes) {

                module.addClass(dependencyNode);

            }

        }

        return report;
    }

}