package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.dependency.DependencyNodeFactory;
import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.analysis.dependency.util.DependencyUtils;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MethodParameterDependencyExtractor
        implements DependencyExtractor {

    private final DependencyNodeFactory dependencyNodeFactory;

    @Override
    public void extract(
            CompilationUnit compilationUnit,
            DependencyNode sourceNode,
            DependencyGraph graph) {

        compilationUnit.findAll(MethodDeclaration.class)
                .forEach(method -> {

                    for (Parameter parameter :
                            method.getParameters()) {

                        String dependency =
                                DependencyUtils.normalizeType(
                                        parameter.getType().asString()
                                );

                        dependencyNodeFactory.connect(
                                graph,
                                sourceNode,
                                dependency,
                                DependencyType.METHOD_PARAMETER
                        );
                    }

                });

    }

}