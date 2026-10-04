package com.anvith.archmorph.analysis.architecture;

import com.anvith.archmorph.analysis.architecture.rules.LayerRule;
import com.anvith.archmorph.analysis.architecture.rules.LayerRuleRegistry;
import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Detects dependencies that break the configured layer rules. */
@Service
public class LayerViolationDetector {

    private final LayerRuleRegistry layerRuleRegistry;

    public LayerViolationDetector(LayerRuleRegistry layerRuleRegistry) {
        this.layerRuleRegistry = layerRuleRegistry;
    }

    public void detect(DependencyGraph dependencyGraph, ArchitectureReport report) {
        for (DependencyEdge edge : dependencyGraph.getEdges()) {
            if (edge.getSource().getComponentType() == null || edge.getTarget().getComponentType() == null) {
                continue;
            }
            Optional<LayerRule> rule = layerRuleRegistry.findRule(
                    edge.getSource().getComponentType(), edge.getTarget().getComponentType());
            if (rule.isPresent() && !rule.get().isAllowed()) {
                report.getLayerViolations().add(new LayerViolation(
                        edge.getSource().getQualifiedName(), edge.getSource().getClassName(),
                        edge.getSource().getComponentType(),
                        edge.getTarget().getQualifiedName(), edge.getTarget().getClassName(),
                        edge.getTarget().getComponentType(),
                        edge.getDependencyType(), edge.getLocation(),
                        rule.get().getSeverity(), rule.get().getRationale()));
            }
        }
    }
}
