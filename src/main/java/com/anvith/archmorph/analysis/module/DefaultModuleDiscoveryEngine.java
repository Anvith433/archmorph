package com.anvith.archmorph.analysis.module;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.module.extractor.BusinessModuleExtractor;
import com.anvith.archmorph.parser.ClassMetadata;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DefaultModuleDiscoveryEngine implements ModuleDiscoveryEngine {

    private final BusinessModuleExtractor businessModuleExtractor;
    private final ModuleMetricsCalculator metricsCalculator;

    public DefaultModuleDiscoveryEngine(BusinessModuleExtractor businessModuleExtractor,
                                        ModuleMetricsCalculator metricsCalculator) {
        this.businessModuleExtractor = businessModuleExtractor;
        this.metricsCalculator = metricsCalculator;
    }

    @Override
    public ModuleDiscoveryReport discover(DependencyGraph dependencyGraph, Map<String, ClassMetadata> facts) {
        ModuleDiscoveryReport report = businessModuleExtractor.extract(dependencyGraph, facts);
        metricsCalculator.calculate(report, dependencyGraph);
        return report;
    }
}
