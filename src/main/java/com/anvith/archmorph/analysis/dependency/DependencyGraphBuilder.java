package com.anvith.archmorph.analysis.dependency;

import com.anvith.archmorph.analysis.dependency.extractor.ConstructorDependencyExtractor;
import com.anvith.archmorph.analysis.dependency.extractor.FieldDependencyExtractor;
import com.anvith.archmorph.parser.ClassMetadata;
import com.github.javaparser.ast.CompilationUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.anvith.archmorph.analysis.dependency.extractor.MethodParameterDependencyExtractor;

@Service
@RequiredArgsConstructor
public class DependencyGraphBuilder {

    private final FieldDependencyExtractor fieldDependencyExtractor;

    private final ConstructorDependencyExtractor constructorDependencyExtractor;

    private final MethodParameterDependencyExtractor methodParameterDependencyExtractor;

    /**
     * Build dependency graph for one class.
     */
    public DependencyGraph build(
            CompilationUnit compilationUnit,
            ClassMetadata metadata) {

        DependencyGraph graph = new DependencyGraph();

        DependencyNode sourceNode =
                new DependencyNode(
                        metadata.getClassName(),
                        metadata.getPackageName(),
                        metadata.getComponentType()
                );


        sourceNode.setSourceFile(
        metadata.getSourceFile()
);

sourceNode.setCompilationUnit(
        metadata.getCompilationUnit()
);

        graph.addNode(sourceNode);

        /*
         * Extract Field Dependencies
         */
        fieldDependencyExtractor.extract(
                compilationUnit,
                sourceNode,
                graph
        );

        /*
         * Extract Constructor Dependencies
         */
        constructorDependencyExtractor.extract(
                compilationUnit,
                sourceNode,
                graph
        );

        methodParameterDependencyExtractor.extract(
        compilationUnit,
        sourceNode,
        graph
);

        return graph;
    }

}