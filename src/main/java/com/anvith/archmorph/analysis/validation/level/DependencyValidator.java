package com.anvith.archmorph.analysis.validation.level;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.validation.LevelResult;
import com.anvith.archmorph.analysis.validation.LevelValidator;
import com.anvith.archmorph.analysis.validation.ValidationContext;
import com.anvith.archmorph.analysis.validation.ValidationIssue;
import com.anvith.archmorph.analysis.validation.ValidationLevel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Level 5: the dependency graph rebuilt from the transformed sources preserves every dependency of
 * the original (mapped through the class map) and declares the same classes.
 */
@Component
@Order(5)
public class DependencyValidator implements LevelValidator {

    @Override
    public ValidationLevel level() {
        return ValidationLevel.DEPENDENCY_GRAPH;
    }

    @Override
    public LevelResult validate(ValidationContext context) {
        long started = System.nanoTime();
        List<ValidationIssue> issues = new ArrayList<>();
        DependencyGraph before = context.originalGraph();
        DependencyGraph after = context.transformed().get().graph();
        Map<String, String> classMap = context.plan().getClassMap();

        if (before.getNodeCount() != after.getNodeCount()) {
            issues.add(ValidationIssue.error(null, 0,
                    "The transformed project declares " + after.getNodeCount() + " top-level classes; the original declared "
                            + before.getNodeCount() + ".", "A class was lost or duplicated during the transformation."));
        }

        Set<String> afterPairs = new HashSet<>();
        for (DependencyEdge edge : after.getEdges()) {
            afterPairs.add(edge.getSource().getId() + "->" + edge.getTarget().getId());
        }
        int lost = 0;
        for (DependencyEdge edge : before.getEdges()) {
            String source = classMap.getOrDefault(edge.getSource().getId(), edge.getSource().getId());
            String target = classMap.getOrDefault(edge.getTarget().getId(), edge.getTarget().getId());
            if (!afterPairs.contains(source + "->" + target)) {
                lost++;
                if (lost <= 50) {
                    issues.add(ValidationIssue.error(edge.getLocation() == null ? null : edge.getLocation().file(),
                            edge.getLocation() == null ? 0 : edge.getLocation().line(),
                            edge.getSource().getClassName() + " no longer depends on " + edge.getTarget().getClassName()
                                    + " (" + edge.getDependencyType() + ").",
                            "The reference no longer resolves to the same class after the move."));
                }
            }
        }
        if (lost > 50) {
            issues.add(ValidationIssue.error(null, 0, (lost - 50) + " further dependencies were lost.", null));
        }
        return LevelResult.of(ValidationLevel.DEPENDENCY_GRAPH, issues,
                after.getEdgeCount() + " dependencies rebuilt; all " + before.getEdgeCount() + " original dependencies preserved",
                (System.nanoTime() - started) / 1_000_000);
    }
}
