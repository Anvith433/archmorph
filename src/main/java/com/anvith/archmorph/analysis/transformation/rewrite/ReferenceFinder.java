package com.anvith.archmorph.analysis.transformation.rewrite;

import com.anvith.archmorph.analysis.dependency.resolve.ResolvedType;
import com.anvith.archmorph.analysis.dependency.resolve.TypeContext;
import com.anvith.archmorph.analysis.dependency.resolve.TypeResolver;
import com.anvith.archmorph.analysis.registry.ProjectClassInfo;
import com.anvith.archmorph.analysis.registry.ProjectClassRegistry;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds every reference from a compilation unit to a top-level project type,
 * resolved through the import-aware {@link TypeResolver}. Used by the planner
 * (what must be rewritten) and by the rewriter (where).
 */
public final class ReferenceFinder {

    private ReferenceFinder() {
    }

    public enum Kind {
        /** {@code User} resolved through same-package, single import or wildcard import. */
        SIMPLE,
        /** {@code com.demo.entity.User} written as a type. */
        QUALIFIED_TYPE,
        /** {@code com.demo.entity.User.CONST} written as an expression. */
        QUALIFIED_EXPRESSION,
        /** {@code @com.demo.Auditable}. */
        QUALIFIED_ANNOTATION
    }

    /**
     * @param node        the AST node carrying the reference
     * @param oldTopLevel old fully-qualified name of the referenced top-level type
     * @param remainder   for qualified annotations: text after the top-level name (e.g. ".Inner")
     */
    public record Reference(Kind kind, Node node, String oldTopLevel, String remainder) {
    }

    public static List<Reference> find(CompilationUnit cu, TypeContext context, TypeResolver resolver) {
        ProjectClassRegistry registry = resolver.registry();
        List<Reference> references = new ArrayList<>();

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (insideHeader(type)) {
                continue;
            }
            if (type.getScope().isPresent()) {
                String name = type.getNameWithScope();
                if (isTopLevel(registry, name)) {
                    references.add(new Reference(Kind.QUALIFIED_TYPE, type, name, ""));
                }
            } else {
                simple(type.getNameAsString(), type, context, resolver, references);
            }
        }

        for (NameExpr name : cu.findAll(NameExpr.class)) {
            if (insideHeader(name) || !isStaticScope(name)) {
                continue;
            }
            String identifier = name.getNameAsString();
            if (!identifier.isEmpty() && Character.isUpperCase(identifier.charAt(0))) {
                simple(identifier, name, context, resolver, references);
            }
        }

        for (FieldAccessExpr access : cu.findAll(FieldAccessExpr.class)) {
            if (insideHeader(access)) {
                continue;
            }
            String text = access.toString();
            if (text.indexOf('.') > 0 && Character.isLowerCase(text.charAt(0)) && isTopLevel(registry, text)) {
                references.add(new Reference(Kind.QUALIFIED_EXPRESSION, access, text, ""));
            }
        }

        for (AnnotationExpr annotation : cu.findAll(AnnotationExpr.class)) {
            if (insideHeader(annotation)) {
                continue;
            }
            String name = annotation.getNameAsString();
            if (annotation.getName().getQualifier().isPresent()) {
                longestTopLevelPrefix(registry, name).ifPresent(top ->
                        references.add(new Reference(Kind.QUALIFIED_ANNOTATION, annotation, top, name.substring(top.length()))));
            } else {
                simple(name, annotation, context, resolver, references);
            }
        }
        return references;
    }

    /** Distinct old top-level qualified names referenced by simple name or qualified name. */
    public static Set<String> referencedTypes(List<Reference> references) {
        Set<String> types = new LinkedHashSet<>();
        references.forEach(r -> types.add(r.oldTopLevel()));
        return types;
    }

    /** Old top-level names of project classes named by single-type imports. */
    public static Set<String> importedTypes(CompilationUnit cu, ProjectClassRegistry registry) {
        Set<String> imported = new LinkedHashSet<>();
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isAsterisk()) {
                continue;
            }
            String name = imp.getNameAsString();
            if (imp.isStatic() && name.contains(".")) {
                name = name.substring(0, name.lastIndexOf('.'));
            }
            registry.findByQualifiedName(name).ifPresent(info -> imported.add(info.getTopLevelQualifiedName()));
        }
        return imported;
    }

    private static void simple(String name, Node node, TypeContext context, TypeResolver resolver, List<Reference> sink) {
        ResolvedType resolved = resolver.resolve(name, context);
        if (!resolved.internal() || resolved.confidence() < 0.9) {
            return;
        }
        switch (resolved.resolution()) {
            case SINGLE_IMPORT, SAME_PACKAGE, WILDCARD_IMPORT ->
                    sink.add(new Reference(Kind.SIMPLE, node, resolved.topLevelQualifiedName(), ""));
            default -> {
                // declared in this file, fallback or unresolved: no rewrite necessary
            }
        }
    }

    private static boolean isTopLevel(ProjectClassRegistry registry, String qualified) {
        Optional<ProjectClassInfo> info = registry.findByQualifiedName(qualified);
        return info.isPresent() && !info.get().isNested();
    }

    private static Optional<String> longestTopLevelPrefix(ProjectClassRegistry registry, String name) {
        String candidate = name;
        while (!candidate.isEmpty()) {
            if (isTopLevel(registry, candidate)) {
                return Optional.of(candidate);
            }
            int dot = candidate.lastIndexOf('.');
            if (dot < 0) {
                break;
            }
            candidate = candidate.substring(0, dot);
        }
        return Optional.empty();
    }

    private static boolean isStaticScope(NameExpr name) {
        Node parent = name.getParentNode().orElse(null);
        if (parent instanceof com.github.javaparser.ast.expr.MethodCallExpr call) {
            return call.getScope().filter(s -> s == name).isPresent();
        }
        if (parent instanceof FieldAccessExpr access) {
            return access.getScope() == name;
        }
        return false;
    }

    private static boolean insideHeader(Node node) {
        return node.findAncestor(ImportDeclaration.class).isPresent()
                || node.findAncestor(PackageDeclaration.class).isPresent();
    }
}
