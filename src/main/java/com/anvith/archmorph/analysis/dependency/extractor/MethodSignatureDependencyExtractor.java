package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Method parameters and return types. */
@Component
@Order(30)
public class MethodSignatureDependencyExtractor implements DependencyExtractor {

    @Override
    public void extract(ExtractionContext context) {
        for (MethodDeclaration method : context.declaration().findAll(MethodDeclaration.class)) {
            for (Parameter parameter : method.getParameters()) {
                context.connect(parameter.getType(), DependencyType.METHOD_PARAMETER, parameter);
            }
            if (!method.getType().isVoidType()) {
                context.connect(method.getType(), DependencyType.METHOD_RETURN, method);
            }
        }
    }
}
