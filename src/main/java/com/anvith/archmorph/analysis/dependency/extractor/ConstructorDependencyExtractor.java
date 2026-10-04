package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Constructor parameters (constructor injection). */
@Component
@Order(20)
public class ConstructorDependencyExtractor implements DependencyExtractor {

    @Override
    public void extract(ExtractionContext context) {
        for (ConstructorDeclaration constructor : context.declaration().findAll(ConstructorDeclaration.class)) {
            for (Parameter parameter : constructor.getParameters()) {
                context.connect(parameter.getType(), DependencyType.CONSTRUCTOR, parameter);
            }
        }
    }
}
