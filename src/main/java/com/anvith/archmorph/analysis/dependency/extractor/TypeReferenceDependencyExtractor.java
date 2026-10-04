package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.TypeParameter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Remaining compile-time references: local variables, casts, instanceof,
 * class literals, method references, throws/catch, generic bounds, explicit
 * type arguments and static member access ({@code AppConstants.MAX}).
 */
@Component
@Order(80)
public class TypeReferenceDependencyExtractor implements DependencyExtractor {

    @Override
    public void extract(ExtractionContext context) {
        Node root = context.declaration();
        DependencyType kind = DependencyType.TYPE_REFERENCE;

        root.findAll(VariableDeclarationExpr.class).forEach(v ->
                v.getVariables().forEach(var -> context.connect(var.getType(), kind, var)));
        root.findAll(CastExpr.class).forEach(c -> context.connect(c.getType(), kind, c));
        root.findAll(InstanceOfExpr.class).forEach(i -> context.connect(i.getType(), kind, i));
        root.findAll(ArrayCreationExpr.class).forEach(a -> context.connect(a.getElementType(), kind, a));
        root.findAll(ClassExpr.class).stream()
                .filter(c -> !insideAnnotation(c))
                .forEach(c -> context.connect(c.getType(), kind, c));
        root.findAll(MethodReferenceExpr.class).forEach(ref -> {
            if (ref.getScope() instanceof TypeExpr typeExpr) {
                context.connect(typeExpr.getType(), kind, ref);
            }
        });
        root.findAll(CatchClause.class).forEach(c -> context.connect(c.getParameter().getType(), kind, c));
        root.findAll(CallableDeclaration.class).forEach(callable ->
                ((CallableDeclaration<?>) callable).getThrownExceptions().forEach(t -> context.connect(t, kind, callable)));
        root.findAll(TypeParameter.class).forEach(tp -> tp.getTypeBound().forEach(b -> context.connect(b, kind, tp)));
        root.findAll(MethodCallExpr.class).forEach(call ->
                call.getTypeArguments().ifPresent(args -> args.forEach(a -> context.connect(a, kind, call))));

        // Static member access: AppConstants.MAX, Role.ADMIN, com.demo.Constants.X
        root.findAll(FieldAccessExpr.class).forEach(access -> {
            if (insideAnnotation(access) && access.getParentNode().orElse(null) instanceof FieldAccessExpr) {
                return;
            }
            if (access.getScope() instanceof NameExpr name
                    && Character.isUpperCase(name.getNameAsString().charAt(0))
                    && context.variableType(name.getNameAsString(), access).isEmpty()) {
                context.connectName(name.getNameAsString(), kind, access);
            } else if (access.getScope() instanceof FieldAccessExpr scope && looksQualified(scope.toString())) {
                context.connectName(scope.toString(), kind, access);
            }
        });

        // Static imports of project members.
        context.typeContext().staticMemberOwners().values().forEach(owner -> context.connectName(owner, kind, null));
        context.typeContext().staticWildcardOwners().forEach(owner -> context.connectName(owner, kind, null));
    }

    private static boolean looksQualified(String text) {
        return text.contains(".") && Character.isLowerCase(text.charAt(0))
                && Character.isUpperCase(text.substring(text.lastIndexOf('.') + 1).charAt(0));
    }

    private static boolean insideAnnotation(Node node) {
        return node.findAncestor(AnnotationExpr.class).isPresent();
    }
}
