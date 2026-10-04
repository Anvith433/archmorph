package com.anvith.archmorph.analysis.module.boundary;

import java.util.List;

/**
 * Module cycles and how to break them.
 *
 * @param cycles      groups of modules that depend on each other (strongly connected components), each sorted
 * @param suggestions one suggestion per module dependency that has to go for the modules to become acyclic
 */
public record BoundaryReport(List<List<String>> cycles, List<BoundarySuggestion> suggestions) {

    public static BoundaryReport empty() {
        return new BoundaryReport(List.of(), List.of());
    }

    public boolean acyclic() {
        return cycles.isEmpty();
    }
}
