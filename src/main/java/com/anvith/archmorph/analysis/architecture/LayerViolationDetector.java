package com.anvith.archmorph.analysis.architecture;

import com.anvith.archmorph.analysis.architecture.rules.LayerRuleRegistry;
import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.parser.ComponentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LayerViolationDetector {

    /*
     * Rule Registry
     */
    private final LayerRuleRegistry layerRuleRegistry;

    /**
     * Detect architecture layer violations.
     */
    public void detect(
            DependencyGraph dependencyGraph,
            ArchitectureReport report) {

        for (DependencyEdge edge : dependencyGraph.getEdges()) {

            DependencyNode source = edge.getSource();
            DependencyNode target = edge.getTarget();

            ComponentType sourceType = source.getComponentType();
            ComponentType targetType = target.getComponentType();

            if (sourceType == null || targetType == null) {
                continue;
            }

            /*
             * Ask the Rule Engine whether
             * this dependency is allowed.
             */
            boolean allowed =
                    layerRuleRegistry.isAllowed(
                            sourceType,
                            targetType
                    );

            if (!allowed) {

                report.getViolations().add(

                        String.format(
                                "%s '%s' must not depend on %s '%s'",
                                sourceType,
                                source.getClassName(),
                                targetType,
                                target.getClassName()
                        )

                );

            }

        }

    }

}