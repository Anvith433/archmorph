package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** {@code extends} (INHERITANCE) and {@code implements} (IMPLEMENTATION). */
@Component
@Order(60)
public class InheritanceDependencyExtractor implements DependencyExtractor {

    @Override
    public void extract(ExtractionContext context) {
        for (ClassOrInterfaceDeclaration type : context.declaration().findAll(ClassOrInterfaceDeclaration.class)) {
            type.getExtendedTypes().forEach(t -> context.connect(t, DependencyType.INHERITANCE, type));
            type.getImplementedTypes().forEach(t -> context.connect(t, DependencyType.IMPLEMENTATION, type));
        }
        for (EnumDeclaration type : context.declaration().findAll(EnumDeclaration.class)) {
            type.getImplementedTypes().forEach(t -> context.connect(t, DependencyType.IMPLEMENTATION, type));
        }
        for (RecordDeclaration type : context.declaration().findAll(RecordDeclaration.class)) {
            type.getImplementedTypes().forEach(t -> context.connect(t, DependencyType.IMPLEMENTATION, type));
        }
    }
}
