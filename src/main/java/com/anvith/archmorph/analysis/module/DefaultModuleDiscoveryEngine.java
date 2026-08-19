package com.anvith.archmorph.analysis.module;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.module.extractor.BusinessModuleExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultModuleDiscoveryEngine
        implements ModuleDiscoveryEngine {

    /*
     * Graph-based module extractor.
     */
    private final BusinessModuleExtractor businessModuleExtractor;

    @Override
    public ModuleDiscoveryReport discover(
            DependencyGraph dependencyGraph) {

        return businessModuleExtractor.extract(
                dependencyGraph
        );

    }

}