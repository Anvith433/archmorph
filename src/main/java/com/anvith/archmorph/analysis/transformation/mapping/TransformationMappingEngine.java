package com.anvith.archmorph.analysis.transformation.mapping;

import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.transformation.target.TargetStrategy;

public interface TransformationMappingEngine {

    /**
     * Map every classified top-level class to its target location.
     *
     * @param basePackage common root package of the project; when null it is derived from the classes
     */
    default TransformationMappingReport build(ModuleDiscoveryReport report, String basePackage, TargetStrategy strategy) {
        return build(report, basePackage, strategy, java.util.Set.of());
    }

    /**
     * @param exposedTypes top-level classes used by code outside their own module; layouts that separate a
     *                     module's public API from its internals place them in the module root package
     */
    TransformationMappingReport build(ModuleDiscoveryReport report, String basePackage, TargetStrategy strategy,
                                      java.util.Set<String> exposedTypes);

    default TransformationMappingReport build(ModuleDiscoveryReport report) {
        return build(report, null, TargetStrategy.MODULAR_BY_DOMAIN);
    }
}
