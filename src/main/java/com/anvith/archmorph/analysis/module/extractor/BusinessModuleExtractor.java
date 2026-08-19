package com.anvith.archmorph.analysis.module.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;

public interface BusinessModuleExtractor {

    /**
     * Extract business modules
     * using graph connectivity.
     */
    ModuleDiscoveryReport extract(
            DependencyGraph graph
    );

}