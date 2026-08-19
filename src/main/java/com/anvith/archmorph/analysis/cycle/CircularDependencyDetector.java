package com.anvith.archmorph.analysis.cycle;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;

public interface CircularDependencyDetector {

    CycleReport detect(
            DependencyGraph graph);

}