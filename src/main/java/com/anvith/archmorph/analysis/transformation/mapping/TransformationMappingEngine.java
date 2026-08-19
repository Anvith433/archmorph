package com.anvith.archmorph.analysis.transformation.mapping;

import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;

public interface TransformationMappingEngine {

    TransformationMappingReport build(
            ModuleDiscoveryReport report
    );

}