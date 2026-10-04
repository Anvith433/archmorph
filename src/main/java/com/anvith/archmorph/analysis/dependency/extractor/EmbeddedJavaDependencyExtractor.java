package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.parser.EmbeddedJava;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Classes named by fully-qualified name inside embedded Java code, such as MapStruct
 * {@code expression = "java(com.demo.util.Money.sum(order))"}: MapStruct copies that code into the generated mapper.
 */
@Component
@Order(90)
public class EmbeddedJavaDependencyExtractor implements DependencyExtractor {

    @Override
    public void extract(ExtractionContext context) {
        for (StringLiteralExpr literal : context.declaration().findAll(StringLiteralExpr.class)) {
            if (!EmbeddedJava.isEmbeddedJava(literal)) {
                continue;
            }
            for (String dotted : EmbeddedJava.dottedNames(literal.getValue())) {
                for (String candidate : EmbeddedJava.prefixes(dotted)) {
                    if (context.isProjectType(candidate)) {
                        context.connectQualified(candidate, DependencyType.TYPE_REFERENCE, literal);
                        break;
                    }
                }
            }
        }
    }
}
