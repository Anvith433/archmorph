package com.anvith.archmorph.analysis.module.optimizer;

import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;

public interface ModuleOptimizer {

    ModuleDiscoveryReport optimize(
            ModuleDiscoveryReport report
    );

}