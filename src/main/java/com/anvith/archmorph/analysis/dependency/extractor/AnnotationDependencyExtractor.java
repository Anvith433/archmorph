package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Project-defined annotations and class literals inside annotations, e.g.
 * {@code @Import(SecurityConfig.class)} or a custom {@code @Auditable}.
 */
@Component
@Order(70)
public class AnnotationDependencyExtractor implements DependencyExtractor {

    @Override
    public void extract(ExtractionContext context) {
        for (AnnotationExpr annotation : context.declaration().findAll(AnnotationExpr.class)) {
            context.connectName(annotation.getNameAsString(), DependencyType.ANNOTATION, annotation);
            for (ClassExpr literal : annotation.findAll(ClassExpr.class)) {
                context.connect(literal.getType(), DependencyType.ANNOTATION, literal);
            }
        }
    }
}
