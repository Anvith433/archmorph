package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.type.Type;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Calls on project types: {@code userService.createUser(...)},
 * {@code UserMapper.toDto(user)} and, through the symbol solver,
 * chained calls such as {@code order.getCustomer().getAddress()}.
 */
@Component
@Order(40)
public class MethodInvocationDependencyExtractor implements DependencyExtractor {

    @Override
    public void extract(ExtractionContext context) {
        for (MethodCallExpr call : context.declaration().findAll(MethodCallExpr.class)) {
            call.getScope().ifPresent(scope -> resolveScope(context, call, scope));
        }
    }

    private void resolveScope(ExtractionContext context, MethodCallExpr call, Expression scope) {
        if (scope instanceof NameExpr name) {
            Optional<Type> variable = context.variableType(name.getNameAsString(), call);
            if (variable.isPresent() && !variable.get().isVarType()) {
                context.connect(variable.get(), DependencyType.METHOD_INVOCATION, call);
                return;
            }
            if (variable.isEmpty() && Character.isUpperCase(name.getNameAsString().charAt(0))) {
                // Static call on a type, e.g. UserMapper.toDto(...)
                context.connectName(name.getNameAsString(), DependencyType.METHOD_INVOCATION, call);
                return;
            }
        } else if (scope instanceof FieldAccessExpr access && access.getScope() instanceof ThisExpr) {
            Optional<Type> field = context.variableType(access.getNameAsString(), call);
            if (field.isPresent()) {
                context.connect(field.get(), DependencyType.METHOD_INVOCATION, call);
                return;
            }
        }
        context.solveExpressionType(scope)
                .ifPresent(qualified -> context.connectQualified(qualified, DependencyType.METHOD_INVOCATION, call));
    }
}
