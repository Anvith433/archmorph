package com.anvith.archmorph.analysis.architecture;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.parser.ComponentType;
import org.springframework.stereotype.Service;

@Service
public class LayerAnalyzer {

    /**
     * Analyze architectural layers.
     */
    public void analyze(
            DependencyGraph dependencyGraph,
            ArchitectureReport report) {

        for (DependencyNode node : dependencyGraph.getNodes()) {

            if (node.getComponentType() == null) {
                continue;
            }

            switch (node.getComponentType()) {

                case CONTROLLER ->
                        report.setControllerCount(
                                report.getControllerCount() + 1
                        );

                case SERVICE ->
                        report.setServiceCount(
                                report.getServiceCount() + 1
                        );

                case REPOSITORY ->
                        report.setRepositoryCount(
                                report.getRepositoryCount() + 1
                        );

                case ENTITY ->
                        report.setEntityCount(
                                report.getEntityCount() + 1
                        );

                case COMPONENT ->
                        report.setComponentCount(
                                report.getComponentCount() + 1
                        );

                case CONFIGURATION ->
                        report.setConfigurationCount(
                                report.getConfigurationCount() + 1
                        );

                default -> {
                    // Ignore other component types
                }
            }

        }

    }

}