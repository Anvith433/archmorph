package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.analysis.dependency.SourceLocation;
import com.anvith.archmorph.analysis.dependency.resolve.ResolvedType;
import com.anvith.archmorph.analysis.dependency.resolve.TypeContext;
import com.anvith.archmorph.analysis.dependency.resolve.TypeResolver;
import com.anvith.archmorph.parser.ClassMetadata;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.IntersectionType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnionType;
import com.github.javaparser.ast.type.WildcardType;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Everything an extractor needs to add dependencies of one top-level type.
 * Dependencies of nested types are attributed to their top-level owner
 * because files are moved as a whole.
 */
public final class ExtractionContext {

    private final TypeDeclaration<?> declaration;
    private final ClassMetadata metadata;
    private final DependencyNode sourceNode;
    private final DependencyGraph graph;
    private final TypeResolver resolver;
    private final TypeContext typeContext;
    private final Function<String, DependencyNode> nodeLookup;
    private final boolean symbolSolverEnabled;
    private final ResolutionStatistics statistics;

    public ExtractionContext(TypeDeclaration<?> declaration, ClassMetadata metadata, DependencyNode sourceNode,
                             DependencyGraph graph, TypeResolver resolver, TypeContext typeContext,
                             Function<String, DependencyNode> nodeLookup, boolean symbolSolverEnabled,
                             ResolutionStatistics statistics) {
        this.declaration = declaration;
        this.metadata = metadata;
        this.sourceNode = sourceNode;
        this.graph = graph;
        this.resolver = resolver;
        this.typeContext = typeContext;
        this.nodeLookup = nodeLookup;
        this.symbolSolverEnabled = symbolSolverEnabled;
        this.statistics = statistics;
    }

    public TypeDeclaration<?> declaration() {
        return declaration;
    }

    public ClassMetadata metadata() {
        return metadata;
    }

    public DependencyNode sourceNode() {
        return sourceNode;
    }

    public TypeContext typeContext() {
        return typeContext;
    }

    /**
     * Add dependencies for a written type: the outer type receives {@code kind},
     * type arguments receive {@link DependencyType#GENERIC} (except entity
     * relationships, which keep their kind).
     */
    public void connect(Type type, DependencyType kind, Node at) {
        if (type == null) {
            return;
        }
        if (type instanceof ArrayType array) {
            connect(array.getComponentType(), kind, at);
        } else if (type instanceof WildcardType wildcard) {
            wildcard.getExtendedType().ifPresent(t -> connect(t, kind, at));
            wildcard.getSuperType().ifPresent(t -> connect(t, kind, at));
        } else if (type instanceof UnionType union) {
            union.getElements().forEach(t -> connect(t, kind, at));
        } else if (type instanceof IntersectionType intersection) {
            intersection.getElements().forEach(t -> connect(t, kind, at));
        } else if (type instanceof ClassOrInterfaceType classType) {
            String name = classType.getNameWithScope();
            if (!isTypeVariable(name, at)) {
                connectName(name, kind, at);
            }
            DependencyType argumentKind = kind == DependencyType.ENTITY_RELATIONSHIP
                    ? DependencyType.ENTITY_RELATIONSHIP : DependencyType.GENERIC;
            classType.getTypeArguments().ifPresent(args -> args.forEach(arg -> connect(arg, argumentKind, at)));
            // Scope type arguments, e.g. Outer<User>.Inner
            classType.getScope().flatMap(ClassOrInterfaceType::getTypeArguments)
                    .ifPresent(args -> args.forEach(arg -> connect(arg, argumentKind, at)));
        }
    }

    /** Resolve a type name in this file's scope and add an edge if it is a project type. */
    public void connectName(String name, DependencyType kind, Node at) {
        ResolvedType resolved = resolver.resolve(name, typeContext);
        connectResolved(resolved, kind, at);
    }

    public void connectResolved(ResolvedType resolved,
                                DependencyType kind, Node at) {
        statistics.record(resolved);
        if (resolved == null || !resolved.internal()) {
            return;
        }
        DependencyNode target = nodeLookup.apply(resolved.topLevelQualifiedName());
        if (target == null || target.equals(sourceNode)) {
            return;
        }
        int line = at == null ? 0 : at.getBegin().map(p -> p.line).orElse(0);
        graph.addEdge(new DependencyEdge(sourceNode, target, kind, resolved.confidence(),
                new SourceLocation(metadata.getRelativePath(), line)));
    }

    /** True when the name is a type parameter visible at the node. */
    public boolean isTypeVariable(String name, Node at) {
        if (name.contains(".")) {
            return false;
        }
        return typeVariablesAt(at).contains(name);
    }

    private Set<String> typeVariablesAt(Node at) {
        Set<String> names = new HashSet<>();
        Node current = at;
        while (current != null) {
            if (current instanceof NodeWithTypeParameters<?> withParams) {
                withParams.getTypeParameters().forEach(tp -> names.add(tp.getNameAsString()));
            }
            current = current.getParentNode().orElse(null);
        }
        return names;
    }

    /** Declared type of a variable, parameter or field visible from {@code from}. */
    public Optional<Type> variableType(String name, Node from) {
        Node current = from;
        while (current != null) {
            if (current instanceof CallableDeclaration<?> || current instanceof LambdaExpr) {
                Optional<Type> local = findDeclared(current, name);
                if (local.isPresent()) {
                    return local;
                }
            }
            if (current instanceof TypeDeclaration<?> type) {
                for (FieldDeclaration field : type.getFields()) {
                    for (VariableDeclarator variable : field.getVariables()) {
                        if (variable.getNameAsString().equals(name)) {
                            return Optional.of(variable.getType());
                        }
                    }
                }
                if (type instanceof RecordDeclaration record) {
                    for (Parameter parameter : record.getParameters()) {
                        if (parameter.getNameAsString().equals(name)) {
                            return Optional.of(parameter.getType());
                        }
                    }
                }
            }
            current = current.getParentNode().orElse(null);
        }
        return Optional.empty();
    }

    private Optional<Type> findDeclared(Node scope, String name) {
        for (Parameter parameter : scope.findAll(Parameter.class)) {
            if (parameter.getNameAsString().equals(name) && !parameter.getType().isUnknownType()) {
                return Optional.of(parameter.getType());
            }
        }
        for (VariableDeclarator variable : scope.findAll(VariableDeclarator.class)) {
            if (variable.getNameAsString().equals(name) && !(variable.getParentNode().orElse(null) instanceof FieldDeclaration)) {
                return Optional.of(variable.getType());
            }
        }
        return Optional.empty();
    }

    /**
     * Ask the JavaParser symbol solver for the type of an expression.
     * Framework types are not on the solver's classpath, so failures are normal
     * and simply return empty.
     */
    public Optional<String> solveExpressionType(Expression expression) {
        if (!symbolSolverEnabled) {
            return Optional.empty();
        }
        try {
            com.github.javaparser.resolution.types.ResolvedType type = expression.calculateResolvedType();
            if (type.isReferenceType()) {
                statistics.symbolSolverHit();
                return Optional.of(type.asReferenceType().getQualifiedName());
            }
        } catch (RuntimeException | StackOverflowError e) {
            statistics.symbolSolverMiss();
        }
        return Optional.empty();
    }

    /** Resolve a qualified name produced by the symbol solver. */
    public void connectQualified(String qualifiedName, DependencyType kind, Node at) {
        connectResolved(resolver.resolveQualified(qualifiedName, 0.9,
                ResolvedType.Resolution.SYMBOL_SOLVER), kind, at);
    }
}
