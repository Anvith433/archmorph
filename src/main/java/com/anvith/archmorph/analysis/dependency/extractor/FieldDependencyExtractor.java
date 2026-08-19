package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.dependency.DependencyNodeFactory;
import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.analysis.dependency.util.DependencyUtils;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FieldDependencyExtractor
        implements DependencyExtractor {

    private final DependencyNodeFactory dependencyNodeFactory;

    @Override
    public void extract(
            CompilationUnit compilationUnit,
            DependencyNode sourceNode,
            DependencyGraph graph) {

        compilationUnit.findAll(FieldDeclaration.class)
                .forEach(field -> {

                    for (VariableDeclarator variable
                            : field.getVariables()) {

                        String dependency =
                                DependencyUtils.normalizeType(
                                        variable.getType().asString()
                                );

                        dependencyNodeFactory.connect(
                                graph,
                                sourceNode,
                                dependency,
                                DependencyType.FIELD
                        );
                    }

                });

    }

}