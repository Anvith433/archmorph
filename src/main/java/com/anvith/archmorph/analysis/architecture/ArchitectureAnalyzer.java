package com.anvith.archmorph.analysis.architecture;

import com.anvith.archmorph.analysis.cycle.CycleReport;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;

public interface ArchitectureAnalyzer {

    /** Analyse the complete dependency graph. */
    ArchitectureReport analyze(DependencyGraph dependencyGraph);

    /** Analyse the graph and incorporate cycle information into indicators. */
    ArchitectureReport analyze(DependencyGraph dependencyGraph, CycleReport cycleReport);
}
