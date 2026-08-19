package com.anvith.archmorph.analysis.module.optimizer;

import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import org.springframework.stereotype.Service;

@Service
public class DefaultModuleOptimizer
        implements ModuleOptimizer {

    @Override
    public ModuleDiscoveryReport optimize(
            ModuleDiscoveryReport report) {

        /*
         * Optimization logic
         * will be added gradually.
         */

        return report;
    }

}