package com.anvith.archmorph.analysis.diagram;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;

public interface DiagramGenerator {

    DiagramReport generate(
            DependencyGraph graph);

}