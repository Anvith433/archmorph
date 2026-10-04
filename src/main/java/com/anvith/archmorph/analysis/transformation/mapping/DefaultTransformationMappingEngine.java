package com.anvith.archmorph.analysis.transformation.mapping;

import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.module.ClassAssignment;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.module.ModuleInfo;
import com.anvith.archmorph.analysis.transformation.packaging.BasePackageResolver;
import com.anvith.archmorph.analysis.transformation.target.TargetArchitecture;
import com.anvith.archmorph.analysis.transformation.target.TargetArchitectureResolver;
import com.anvith.archmorph.analysis.transformation.target.TargetPlacement;
import com.anvith.archmorph.analysis.transformation.target.TargetStrategy;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DefaultTransformationMappingEngine implements TransformationMappingEngine {

    private final BasePackageResolver basePackageResolver;
    private final TargetArchitectureResolver architectureResolver;

    public DefaultTransformationMappingEngine(BasePackageResolver basePackageResolver,
                                              TargetArchitectureResolver architectureResolver) {
        this.basePackageResolver = basePackageResolver;
        this.architectureResolver = architectureResolver;
    }

    @Override
    public TransformationMappingReport build(ModuleDiscoveryReport report, String basePackage, TargetStrategy strategy) {
        TargetArchitecture architecture = architectureResolver.resolve(strategy);
        TransformationMappingReport mappingReport = new TransformationMappingReport();

        List<DependencyNode> nodes = new ArrayList<>();
        report.getModules().forEach(module -> nodes.addAll(module.getClasses()));
        nodes.sort(Comparator.comparing(DependencyNode::getId));

        String base = basePackage != null ? basePackage : basePackageResolver.resolve(collectSourcePackages(report));
        mappingReport.setBasePackage(base);

        for (DependencyNode node : nodes) {
            ClassAssignment assignment = report.getAssignment(node.getId());
            if (assignment == null) {
                continue;
            }
            String sourcePackage = node.getPackageName() == null ? "" : node.getPackageName();
            TargetPlacement placement = architecture.place(base, assignment, node.getComponentType(), sourcePackage);

            TransformationMapping mapping = new TransformationMapping();
            mapping.setNode(node);
            mapping.setModuleName(assignment.moduleName());
            mapping.setCategory(assignment.category());
            mapping.setFolderType(placement.folder());
            mapping.setSourcePackage(sourcePackage);
            mapping.setBasePackage(base);
            mapping.setTargetPackage(placement.targetPackage());
            mapping.setTargetFile(buildTargetFile(placement.targetPackage(), node.getClassName()));
            mapping.setConfidence(assignment.confidence());
            mappingReport.addMapping(mapping);
        }
        return mappingReport;
    }

    private Set<String> collectSourcePackages(ModuleDiscoveryReport report) {
        Set<String> packages = new LinkedHashSet<>();
        for (ModuleInfo module : report.getModules()) {
            for (DependencyNode node : module.getClasses()) {
                packages.add(node.getPackageName() == null ? "" : node.getPackageName());
            }
        }
        return packages;
    }

    /** Project-relative destination: src/main/java/&lt;package path&gt;/&lt;Class&gt;.java */
    private Path buildTargetFile(String targetPackage, String className) {
        Path path = Path.of("src", "main", "java");
        if (targetPackage != null && !targetPackage.isEmpty()) {
            path = path.resolve(targetPackage.replace('.', '/'));
        }
        return path.resolve(className + ".java");
    }
}
