package com.anvith.archmorph.analysis.module.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.parser.ClassMetadata;

import java.util.Map;

public interface BusinessModuleExtractor {

    /**
     * Extract candidate business modules. Combines naming, packages, entity
     * relationships, endpoints, type usage and graph connectivity (see
     * {@code ModuleAffinityModel}); connected components alone are never used.
     *
     * @param facts parsed metadata of top-level classes by qualified name (may be empty)
     */
    ModuleDiscoveryReport extract(DependencyGraph graph, Map<String, ClassMetadata> facts);
}
