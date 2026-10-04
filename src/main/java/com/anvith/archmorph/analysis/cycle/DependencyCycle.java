package com.anvith.archmorph.analysis.cycle;

import com.anvith.archmorph.analysis.architecture.Severity;
import com.anvith.archmorph.analysis.dependency.DependencyType;

import java.util.List;
import java.util.Set;

/**
 * One strongly connected group of classes.
 *
 * @param cycleId          stable ID ("cycle-001"), ordered by first member name
 * @param nodes            qualified names of every participating class
 * @param classNames       simple names of the participants (same order)
 * @param path             a representative closed path (first == last), simple names
 * @param edgeCount        dependencies between participants
 * @param dependencyTypes  kinds of those dependencies
 * @param severity         LOW for bidirectional entity relationships, HIGH for large/cross-layer cycles
 * @param recommendation   suggested resolution (never applied automatically)
 */
public record DependencyCycle(
        String cycleId,
        List<String> nodes,
        List<String> classNames,
        List<String> path,
        int edgeCount,
        Set<DependencyType> dependencyTypes,
        Severity severity,
        String recommendation) {
}
