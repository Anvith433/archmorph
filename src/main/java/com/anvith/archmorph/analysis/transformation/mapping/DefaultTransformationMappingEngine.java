package com.anvith.archmorph.analysis.transformation.mapping;

import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.module.ModuleInfo;
import com.anvith.archmorph.analysis.transformation.FolderClassifier;
import com.anvith.archmorph.analysis.transformation.FolderType;
import com.anvith.archmorph.analysis.transformation.packaging.BasePackageResolver;
import com.anvith.archmorph.analysis.transformation.packaging.PackagePlanner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DefaultTransformationMappingEngine
        implements TransformationMappingEngine {

    private final FolderClassifier folderClassifier;

    private final BasePackageResolver basePackageResolver;

    private final PackagePlanner packagePlanner;

    @Override
    public TransformationMappingReport build(
            ModuleDiscoveryReport report) {

        TransformationMappingReport mappingReport =
                new TransformationMappingReport();

        /*
         * Collect all unique source packages
         * from the project.
         */
        Set<String> sourcePackages =
                collectSourcePackages(report);

        /*
         * Compute the common project base package.
         */
        String basePackage =
                basePackageResolver.resolve(
                        sourcePackages
                );

        /*
         * Build mappings.
         */
        for (ModuleInfo module : report.getModules()) {

            for (DependencyNode node : module.getClasses()) {

                TransformationMapping mapping =
                        createMapping(
                                node,
                                module.getModuleName(),
                                basePackage
                        );

                mappingReport.addMapping(mapping);

            }

        }

        return mappingReport;
    }

    /**
     * Collect every unique Java package
     * present in the project.
     */
    private Set<String> collectSourcePackages(
            ModuleDiscoveryReport report) {

        Set<String> packages =
                new LinkedHashSet<>();

        for (ModuleInfo module : report.getModules()) {

            for (DependencyNode node : module.getClasses()) {

                if (node.getPackageName() != null &&
                        !node.getPackageName().isBlank()) {

                    packages.add(
                            node.getPackageName()
                    );

                }

            }

        }

        return packages;
    }

    /**
     * Creates one transformation mapping.
     */
    private TransformationMapping createMapping(

            DependencyNode node,

            String moduleName,

            String basePackage) {

        TransformationMapping mapping =
                new TransformationMapping();

        FolderType folder =
                folderClassifier.classify(
                        node.getComponentType()
                );

        String sourcePackage =
                node.getPackageName();

        String targetPackage =
                packagePlanner.plan(
                        basePackage,
                        moduleName,
                        folder
                );

        Path targetFile =
                buildTargetFile(
                        targetPackage,
                        node.getClassName()
                );

        mapping.setNode(node);
        mapping.setModuleName(moduleName);
        mapping.setFolderType(folder);

        mapping.setSourcePackage(sourcePackage);
        mapping.setBasePackage(basePackage);

        mapping.setTargetPackage(targetPackage);
        mapping.setTargetFile(targetFile);

        return mapping;
    }

    /**
     * Compute destination Java file.
     */
    private Path buildTargetFile(

            String targetPackage,

            String className) {

        String packagePath =
                targetPackage.replace('.', '/');

        return Path.of(
                "src",
                "main",
                "java"
        )
        .resolve(packagePath)
        .resolve(className + ".java");
    }

}