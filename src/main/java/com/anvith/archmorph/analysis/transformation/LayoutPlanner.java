package com.anvith.archmorph.analysis.transformation;

import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;

public interface LayoutPlanner {

    ModuleLayoutReport plan(
            ModuleDiscoveryReport moduleReport
    );

}