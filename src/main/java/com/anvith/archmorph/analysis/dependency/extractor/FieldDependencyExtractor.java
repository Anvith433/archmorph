package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.parser.AnnotationConstants;
import com.anvith.archmorph.parser.ComponentAnalyzer;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Field and record-component dependencies. Fields annotated with a JPA/Mongo
 * relationship annotation produce {@link DependencyType#ENTITY_RELATIONSHIP}.
 */
@Component
@Order(10)
public class FieldDependencyExtractor implements DependencyExtractor {

    @Override
    public void extract(ExtractionContext context) {
        for (FieldDeclaration field : context.declaration().findAll(FieldDeclaration.class)) {
            boolean relationship = field.getAnnotations().stream()
                    .map(ComponentAnalyzer::simpleAnnotationName)
                    .anyMatch(AnnotationConstants.ENTITY_RELATIONSHIP_ANNOTATIONS::contains);
            DependencyType kind = relationship ? DependencyType.ENTITY_RELATIONSHIP : DependencyType.FIELD;
            for (VariableDeclarator variable : field.getVariables()) {
                context.connect(variable.getType(), kind, variable);
            }
        }
        for (RecordDeclaration record : context.declaration().findAll(RecordDeclaration.class)) {
            for (Parameter component : record.getParameters()) {
                context.connect(component.getType(), DependencyType.FIELD, component);
            }
        }
    }
}
