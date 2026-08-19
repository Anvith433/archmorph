package com.anvith.archmorph.upload.service;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyGraphBuilder;
import com.anvith.archmorph.analysis.scanner.SourceScanner;
import com.anvith.archmorph.common.config.WorkspaceProperties;
import com.anvith.archmorph.common.constants.WorkspaceConstants;
import com.anvith.archmorph.common.util.ProjectIdGenerator;
import com.anvith.archmorph.parser.ClassMetadata;
import com.anvith.archmorph.parser.ComponentAnalyzer;
import com.anvith.archmorph.parser.ComponentType;
import com.anvith.archmorph.parser.JavaParserService;
import com.anvith.archmorph.parser.ProjectStructureDetector;
import com.anvith.archmorph.upload.dto.UploadResponse;
import com.github.javaparser.ast.CompilationUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.anvith.archmorph.analysis.architecture.ArchitectureAnalyzer;
import com.anvith.archmorph.analysis.architecture.ArchitectureReport;
import com.anvith.archmorph.analysis.registry.ProjectClassCollector;
import com.anvith.archmorph.analysis.registry.ProjectClassRegistry;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.anvith.archmorph.analysis.cycle.CircularDependencyDetector;
import com.anvith.archmorph.analysis.cycle.CycleReport;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryEngine;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.transformation.LayoutPlanner;
import com.anvith.archmorph.analysis.transformation.ModuleLayoutReport;
import com.anvith.archmorph.analysis.module.optimizer.ModuleOptimizer;
import com.anvith.archmorph.analysis.transformation.TransformationEngine;

@Service
@RequiredArgsConstructor
public class ProjectUploadServiceImpl implements ProjectUploadService {

    private final ProjectValidator projectValidator;

    private final WorkspaceManager workspaceManager;

    private final ArchiveStorageService archiveStorageService;

    private final ZipExtractionService zipExtractionService;

    private final ProjectStructureDetector projectStructureDetector;

    private final WorkspaceProperties workspaceProperties;

    private final SourceScanner sourceScanner;

    private final JavaParserService javaParserService;

    private final ComponentAnalyzer componentAnalyzer;

    private final DependencyGraphBuilder dependencyGraphBuilder;

    private final ArchitectureAnalyzer architectureAnalyzer;

    private final ProjectClassCollector projectClassCollector;

    private final ProjectClassRegistry projectClassRegistry;

    private final CircularDependencyDetector circularDependencyDetector;

    private final ModuleDiscoveryEngine moduleDiscoveryEngine;

    private final LayoutPlanner layoutPlanner;

    private final TransformationEngine transformationEngine;

    private final ModuleOptimizer moduleOptimizer;
    

    @Override
    public UploadResponse uploadProject(MultipartFile file) {

        /*
         * Step 1 : Validate Uploaded ZIP
         */
        projectValidator.validate(file);


        /*
 * Clear registry from previous upload.
 */
projectClassRegistry.clear();

        /*
         * Step 2 : Generate Project ID
         */
        String projectId = ProjectIdGenerator.generate();

        /*
         * Step 3 : Create Workspace
         */
        workspaceManager.createWorkspace(projectId);

        /*
         * Step 4 : Store ZIP Archive
         */
        Path archivePath = archiveStorageService.saveArchive(projectId, file);

        /*
         * Step 5 : Extract ZIP
         */
        zipExtractionService.extractArchive(projectId, archivePath);

        /*
         * Step 6 : Detect Project Root
         */
        Path originalDirectory = workspaceProperties.getRoot()
                .resolve(WorkspaceConstants.PROJECTS)
                .resolve(projectId)
                .resolve(WorkspaceConstants.ORIGINAL);

        Path projectRoot =
                projectStructureDetector.detectProjectRoot(originalDirectory);

        /*
         * Step 7 : Scan Java Sources
         */
        List<Path> javaFiles =
                sourceScanner.scanJavaSources(projectRoot);

        /*
         * Step 8 : Parse & Analyze
         */

         DependencyGraph projectGraph = new DependencyGraph();

        Map<ComponentType, Integer> componentSummary =
                new EnumMap<>(ComponentType.class);

        System.out.println();
        System.out.println("========== COMPONENT ANALYSIS ==========");

        

      /*
 * PASS 1
 * Register every discovered class.
 */
for (Path javaFile : javaFiles) {

    CompilationUnit compilationUnit =
            javaParserService.parse(javaFile);

    ClassMetadata metadata =
            componentAnalyzer.analyze(compilationUnit);

    projectClassCollector.collect(metadata);

}

/*
 * PASS 2
 * Build dependency graph.
 */
for (Path javaFile : javaFiles) {

    CompilationUnit compilationUnit =
            javaParserService.parse(javaFile);

    ClassMetadata metadata =
            componentAnalyzer.analyze(compilationUnit);


    metadata.setSourceFile(javaFile);
metadata.setCompilationUnit(compilationUnit);

    DependencyGraph dependencyGraph =
            dependencyGraphBuilder.build(
                    compilationUnit,
                    metadata
            );

    projectGraph.addNodes(
            dependencyGraph.getNodes()
    );

    projectGraph.addEdges(
            dependencyGraph.getEdges()
    );

    componentSummary.merge(
            metadata.getComponentType(),
            1,
            Integer::sum
    );

    System.out.printf(
            "%-35s -> %s%n",
            metadata.getClassName(),
            metadata.getComponentType()
    );

}

        System.out.println("========================================");


        System.out.println();

System.out.println("===============================================");
System.out.println("PROJECT DEPENDENCY GRAPH");
System.out.println("===============================================");

if (projectGraph.getEdgeCount() == 0) {

    System.out.println("No dependencies found.");

} else {

    System.out.println(projectGraph);

}

System.out.println("===============================================");
/*
 * Step 9 : Architecture Analysis
 */
ArchitectureReport architectureReport =
        architectureAnalyzer.analyze(projectGraph);

/*
 * Step 10 : Circular Dependency Analysis
 */
CycleReport cycleReport =
        circularDependencyDetector.detect(projectGraph);



        /*
 * Step 11 : Discover Modules
 */
ModuleDiscoveryReport moduleReport =
        moduleDiscoveryEngine.discover(projectGraph);

        moduleReport =
        moduleOptimizer.optimize(moduleReport);


        Path transformedRoot =
        workspaceProperties.getRoot()
                .resolve(WorkspaceConstants.PROJECTS)
                .resolve(projectId)
                .resolve("transformed");

transformationEngine.transform(
        projectGraph,
        moduleReport,
        projectRoot,
        transformedRoot
);

/*
 * Step 12 : Plan Module Layout
 */
ModuleLayoutReport layoutReport =
        layoutPlanner.plan(moduleReport);


System.out.println();

System.out.println("===============================================");
System.out.println("ARCHITECTURE REPORT");
System.out.println("===============================================");

System.out.printf("Controllers      : %d%n",
        architectureReport.getControllerCount());

System.out.printf("Services         : %d%n",
        architectureReport.getServiceCount());

System.out.printf("Repositories     : %d%n",
        architectureReport.getRepositoryCount());

System.out.printf("Entities         : %d%n",
        architectureReport.getEntityCount());

System.out.printf("Components       : %d%n",
        architectureReport.getComponentCount());

System.out.printf("Configurations   : %d%n",
        architectureReport.getConfigurationCount());

System.out.printf("Dependencies     : %d%n",
        architectureReport.getDependencyCount());

System.out.println();

System.out.println("Architecture Violations");
System.out.println("-----------------------------------------------");

if (architectureReport.getViolations().isEmpty()) {

    System.out.println("No architecture violations detected.");

} else {

    architectureReport.getViolations()
            .forEach(System.out::println);

}

System.out.println("===============================================");

/*
 * Step 12 : Print Circular Dependency Report
 */
System.out.println();

System.out.println("===============================================");
System.out.println("CIRCULAR DEPENDENCY REPORT");
System.out.println("===============================================");

if (!cycleReport.hasCycles()) {

    System.out.println("No circular dependencies found.");

} else {

    int index = 1;

    for (var cycle : cycleReport.getCycles()) {

        System.out.println("Cycle " + index++);
        System.out.println();

        for (int i = 0; i < cycle.size(); i++) {

            System.out.println(cycle.get(i));

            if (i < cycle.size() - 1) {
                System.out.println("   ↓");
            }
        }

        System.out.println();
    }

}

System.out.println("===============================================");


System.out.println();

System.out.println("===============================================");
System.out.println("DISCOVERED MODULES");
System.out.println("===============================================");

System.out.println(moduleReport);

System.out.println("===============================================");



System.out.println();

System.out.println("===============================================");
System.out.println("MODULE LAYOUT");
System.out.println("===============================================");

for (var layout : layoutReport.getLayouts()) {

    System.out.println("Module : " + layout.getModuleName());

    layout.getFolders().forEach((folder, classes) -> {

        System.out.println("  " + folder);

        for (String clazz : classes) {
            System.out.println("      - " + clazz);
        }

    });

    System.out.println();
}

System.out.println("===============================================");

        /*
         * Step 9 : Print Analysis Summary
         */
        System.out.println();
        System.out.println("===============================================");
        System.out.println("            ARCHMORPH AI ANALYSIS");
        System.out.println("===============================================");

        System.out.println("Project ID      : " + projectId);
        System.out.println("Project Root    : " + projectRoot);
        System.out.println("Java Files      : " + javaFiles.size());

        System.out.println();
        System.out.println("Detected Components");
        System.out.println("-----------------------------------------------");

        for (ComponentType componentType : ComponentType.values()) {

            int count = componentSummary.getOrDefault(componentType, 0);

            if (count > 0) {

                System.out.printf(
                        "%-20s : %d%n",
                        componentType,
                        count
                );
            }
        }

        System.out.println("===============================================");

        /*
         * Step 10 : Return Response
         */
        return UploadResponse.builder()
                .projectId(projectId)
                .projectName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .status("UPLOADED")
                .build();
    }
}