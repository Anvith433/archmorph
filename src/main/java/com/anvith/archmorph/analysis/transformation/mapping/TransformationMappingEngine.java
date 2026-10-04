package com.anvith.archmorph.analysis.transformation.mapping;

import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.transformation.target.TargetStrategy;

public interface TransformationMappingEngine {

    /**
     * Map every classified top-level class to its target location.
     *
     * @param basePackage common root package of the project; when null it is derived from the classes
     */
    TransformationMappingReport build(ModuleDiscoveryReport report, String basePackage, TargetStrategy strategy);

    default TransformationMappingReport build(ModuleDiscoveryReport report) {
        return build(report, null, TargetStrategy.MODULAR_BY_DOMAIN);
    }
}
