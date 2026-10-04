package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** {@code new User(...)}, including anonymous classes. */
@Component
@Order(50)
public class ObjectCreationDependencyExtractor implements DependencyExtractor {

    @Override
    public void extract(ExtractionContext context) {
        for (ObjectCreationExpr creation : context.declaration().findAll(ObjectCreationExpr.class)) {
            context.connect(creation.getType(), DependencyType.OBJECT_CREATION, creation);
        }
    }
}
