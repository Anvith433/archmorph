package com.anvith.archmorph.analysis.module.optimizer;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;

public interface ModuleOptimizer {

    /**
     * Improve a raw discovery result: merge fragments, keep shared and
     * infrastructure classes out of business modules, and add warnings where
     * automatic transformation is unsafe. Returns a new report; the input is not modified.
     */
    ModuleDiscoveryReport optimize(ModuleDiscoveryReport report, DependencyGraph graph);
}
