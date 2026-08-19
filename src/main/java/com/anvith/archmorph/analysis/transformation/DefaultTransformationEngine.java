package com.anvith.archmorph.analysis.transformation;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.anvith.archmorph.analysis.transformation.mapping.TransformationMappingEngine;
import com.anvith.archmorph.analysis.transformation.mapping.TransformationMappingReport;
import com.anvith.archmorph.analysis.transformation.mapping.TransformationMapping;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class DefaultTransformationEngine
        implements TransformationEngine {

            private final TransformationMappingEngine
        transformationMappingEngine;

    @Override
    public void transform(
            DependencyGraph dependencyGraph,
            ModuleDiscoveryReport moduleReport,
            Path sourceRoot,
            Path targetRoot) {

        System.out.println();
        System.out.println("===============================================");
        System.out.println("TRANSFORMATION ENGINE");
        System.out.println("===============================================");

        System.out.println("Source Project : " + sourceRoot);
        System.out.println("Target Project : " + targetRoot);

        System.out.println();
        System.out.println("Discovered Modules : "
                + moduleReport.getModuleCount());
            TransformationMappingReport mappingReport =
        transformationMappingEngine.build(
                moduleReport
        );

        System.out.println();
System.out.println(
        "===============================================");
System.out.println(
        "TRANSFORMATION MAPPINGS");
System.out.println(
        "===============================================");

for (TransformationMapping mapping
        : mappingReport.getMappings()) {

    System.out.println(mapping);
    System.out.println();

}

        System.out.println("===============================================");

        /*
         * Sprint 15
         * Create modular folder structure.
         */

        /*
         * Sprint 16
         * Locate source Java files.
         */

        /*
         * Sprint 17
         * Copy Java files.
         */

        /*
         * Sprint 18
         * Rewrite package declarations.
         */

        /*
         * Sprint 19
         * Rewrite imports.
         */

    }

}