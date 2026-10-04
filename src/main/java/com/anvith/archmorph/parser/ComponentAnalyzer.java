package com.anvith.archmorph.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.TypeParameter;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds {@link ClassMetadata} for every type declared in a compilation unit:
 * top-level types, secondary top-level types and nested types.
 */
@Service
public class ComponentAnalyzer {

    private static final Pattern QUALIFIED_LITERAL =
            Pattern.compile("^[a-z_][a-z0-9_]*(\\.[A-Za-z_$][A-Za-z0-9_$]*){2,}$");

    private static final Set<String> REFLECTION_METHODS = Set.of(
            "forName", "loadClass");

    private static final Set<String> DYNAMIC_GENERATION_IMPORT_PREFIXES = Set.of(
            "net.bytebuddy", "net.sf.cglib", "javassist", "org.objectweb.asm");

    private final ComponentClassifier classifier;

    public ComponentAnalyzer(ComponentClassifier classifier) {
        this.classifier = classifier;
    }

    /**
     * Backwards-compatible entry point: metadata of the primary type of the
     * compilation unit, or an UNKNOWN placeholder when it declares no type.
     */
    public ClassMetadata analyze(CompilationUnit compilationUnit) {
        List<ClassMetadata> all = analyzeAll(compilationUnit, null, null, SourceScope.MAIN);
        if (all.isEmpty()) {
            ClassMetadata metadata = new ClassMetadata();
            metadata.setPackageName(packageOf(compilationUnit));
            metadata.setComponentType(ComponentType.UNKNOWN);
            return metadata;
        }
        return all.stream().filter(ClassMetadata::isPublicType).findFirst().orElse(all.getFirst());
    }

    /**
     * Analyse every type declared in the compilation unit.
     *
     * @param relativePath project-relative path ('/' separated), may be null in tests
     */
    public List<ClassMetadata> analyzeAll(CompilationUnit cu, Path sourceFile, String relativePath, SourceScope scope) {
        List<ClassMetadata> result = new ArrayList<>();
        String packageName = packageOf(cu);
        boolean generated = isGenerated(cu);

        for (TypeDeclaration<?> type : cu.getTypes()) {
            String qualified = packageName.isEmpty() ? type.getNameAsString() : packageName + "." + type.getNameAsString();
            collect(cu, type, packageName, qualified, null, qualified, sourceFile, relativePath, scope, generated, result);
        }
        return result;
    }

    private void collect(CompilationUnit cu, TypeDeclaration<?> type, String packageName, String qualifiedName,
                         String outer, String topLevel, Path sourceFile, String relativePath, SourceScope scope,
                         boolean generated, List<ClassMetadata> sink) {

        ClassMetadata metadata = new ClassMetadata();
        metadata.setClassName(type.getNameAsString());
        metadata.setPackageName(packageName);
        metadata.setQualifiedName(qualifiedName);
        metadata.setNested(outer != null);
        metadata.setOuterClassName(outer);
        metadata.setTopLevelQualifiedName(topLevel);
        metadata.setSourceFile(sourceFile);
        metadata.setRelativePath(relativePath);
        metadata.setScope(scope);
        metadata.setCompilationUnit(cu);
        metadata.setDeclaration(type);
        metadata.setLine(type.getBegin().map(p -> p.line).orElse(0));
        metadata.setPublicType(type.isPublic());
        type.getModifiers().forEach(m -> metadata.getModifiers().add(m.getKeyword().asString()));

        describeKindAndSupertypes(type, metadata);
        type.getAnnotations().forEach(a -> metadata.getAnnotations().add(simpleAnnotationName(a)));

        cu.getImports().forEach(imp -> addImport(imp, metadata));

        describeMembers(type, metadata);
        describeEndpoints(type, metadata);
        describeBodySignals(type, metadata);
        describeScanning(type, metadata);

        if (generated || metadata.getAnnotations().contains(AnnotationConstants.GENERATED)) {
            metadata.getRiskFlags().add(RiskFlag.GENERATED_CODE);
        }
        if (metadata.getImports().stream().anyMatch(i -> DYNAMIC_GENERATION_IMPORT_PREFIXES.stream().anyMatch(i::startsWith))) {
            metadata.getRiskFlags().add(RiskFlag.DYNAMIC_CLASS_GENERATION);
        }

        metadata.setClassification(classifier.classify(metadata));
        sink.add(metadata);

        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) {
                String nestedName = qualifiedName + "." + nested.getNameAsString();
                metadata.getNestedTypes().add(nestedName);
                collect(cu, nested, packageName, nestedName, qualifiedName, topLevel,
                        sourceFile, relativePath, scope, generated, sink);
            }
        }
    }

    private void describeKindAndSupertypes(TypeDeclaration<?> type, ClassMetadata metadata) {
        if (type instanceof ClassOrInterfaceDeclaration decl) {
            metadata.setInterface(decl.isInterface());
            metadata.setKind(decl.isInterface() ? TypeKind.INTERFACE : TypeKind.CLASS);
            decl.getTypeParameters().forEach(tp -> metadata.getTypeParameters().add(tp.getNameAsString()));
            if (decl.isInterface()) {
                decl.getExtendedTypes().forEach(t -> metadata.getInterfaces().add(t.getNameWithScope()));
            } else {
                decl.getExtendedTypes().stream().findFirst()
                        .ifPresent(t -> metadata.setSuperClass(t.getNameWithScope()));
                decl.getImplementedTypes().forEach(t -> metadata.getInterfaces().add(t.getNameWithScope()));
            }
        } else if (type instanceof EnumDeclaration decl) {
            metadata.setKind(TypeKind.ENUM);
            decl.getImplementedTypes().forEach(t -> metadata.getInterfaces().add(t.getNameWithScope()));
        } else if (type instanceof RecordDeclaration decl) {
            metadata.setKind(TypeKind.RECORD);
            decl.getTypeParameters().forEach(tp -> metadata.getTypeParameters().add(tp.getNameAsString()));
            decl.getImplementedTypes().forEach(t -> metadata.getInterfaces().add(t.getNameWithScope()));
            decl.getParameters().forEach(p -> metadata.getFieldDetails().add(new FieldInfo(
                    p.getNameAsString(), p.getTypeAsString(), annotationNames(p), List.of("private", "final"),
                    p.getBegin().map(pos -> pos.line).orElse(0))));
        } else if (type instanceof AnnotationDeclaration) {
            metadata.setKind(TypeKind.ANNOTATION);
        }
    }

    private void addImport(ImportDeclaration imp, ClassMetadata metadata) {
        String name = imp.getNameAsString();
        if (imp.isStatic()) {
            metadata.getStaticImports().add(imp.isAsterisk() ? name + ".*" : name);
        } else if (imp.isAsterisk()) {
            metadata.getWildcardImports().add(name);
        } else {
            metadata.getImports().add(name);
        }
    }

    private void describeMembers(TypeDeclaration<?> type, ClassMetadata metadata) {
        boolean membersImplicitlyPublic = metadata.isInterface() || metadata.getKind() == TypeKind.ANNOTATION;
        boolean packagePrivate = false;

        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof FieldDeclaration field) {
                metadata.getFields().add(field.toString());
                List<String> annotations = annotationNames(field);
                metadata.getFieldAnnotations().addAll(annotations);
                List<String> modifiers = modifiers(field.getModifiers());
                field.getVariables().forEach(v -> metadata.getFieldDetails().add(new FieldInfo(
                        v.getNameAsString(), v.getTypeAsString(), annotations, modifiers,
                        v.getBegin().map(p -> p.line).orElse(0))));
                packagePrivate |= !membersImplicitlyPublic && isPackagePrivate(field.getModifiers());
            } else if (member instanceof MethodDeclaration method) {
                metadata.getMethods().add(method.getNameAsString());
                metadata.getMethodDetails().add(methodInfo(method, method.getTypeAsString(), false));
                metadata.getMethodAnnotations().addAll(annotationNames(method));
                packagePrivate |= !membersImplicitlyPublic && isPackagePrivate(method.getModifiers());
            } else if (member instanceof ConstructorDeclaration constructor) {
                metadata.getConstructors().add(methodInfo(constructor, null, true));
                packagePrivate |= isPackagePrivate(constructor.getModifiers()) && metadata.getKind() == TypeKind.CLASS;
            }
        }
        metadata.setHasPackagePrivateMembers(packagePrivate);
    }

    private MethodInfo methodInfo(CallableDeclaration<?> callable, String returnType, boolean constructor) {
        List<ParameterInfo> parameters = callable.getParameters().stream()
                .map(p -> new ParameterInfo(p.getNameAsString(), p.getTypeAsString(), annotationNames(p)))
                .toList();
        List<String> typeParameters = callable.getTypeParameters().stream().map(TypeParameter::getNameAsString).toList();
        return new MethodInfo(callable.getNameAsString(), returnType, parameters, annotationNames(callable),
                modifiers(callable.getModifiers()), typeParameters,
                callable.getBegin().map(p -> p.line).orElse(0), constructor);
    }

    private void describeEndpoints(TypeDeclaration<?> type, ClassMetadata metadata) {
        for (AnnotationExpr annotation : type.getAnnotations()) {
            if (AnnotationConstants.MAPPING_ANNOTATIONS.contains(simpleAnnotationName(annotation))) {
                metadata.getEndpointPaths().addAll(stringValues(annotation, "value", "path"));
            }
        }
        for (MethodDeclaration method : type.getMethods()) {
            for (AnnotationExpr annotation : method.getAnnotations()) {
                if (AnnotationConstants.MAPPING_ANNOTATIONS.contains(simpleAnnotationName(annotation))) {
                    metadata.getEndpointPaths().addAll(stringValues(annotation, "value", "path"));
                }
            }
        }
    }

    private void describeBodySignals(TypeDeclaration<?> type, ClassMetadata metadata) {
        for (MethodCallExpr call : type.findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            metadata.getInvokedMethods().add(name);
            if (REFLECTION_METHODS.contains(name)) {
                metadata.getRiskFlags().add(RiskFlag.REFLECTION);
            }
            if ("newProxyInstance".equals(name)) {
                metadata.getRiskFlags().add(RiskFlag.DYNAMIC_CLASS_GENERATION);
            }
        }
        for (StringLiteralExpr literal : type.findAll(StringLiteralExpr.class)) {
            String value = literal.getValue();
            if (value.length() < 300 && QUALIFIED_LITERAL.matcher(value).matches()) {
                metadata.getQualifiedStringLiterals().add(value);
            }
        }
    }

    private void describeScanning(TypeDeclaration<?> type, ClassMetadata metadata) {
        for (AnnotationExpr annotation : type.getAnnotations()) {
            if (AnnotationConstants.PACKAGE_SCAN_ANNOTATIONS.contains(simpleAnnotationName(annotation))) {
                List<String> packages = stringValues(annotation, "value", "basePackages", "scanBasePackages");
                if (!packages.isEmpty()) {
                    metadata.getScannedPackages().addAll(packages);
                    metadata.getRiskFlags().add(RiskFlag.PACKAGE_SCANNING_CONFIGURATION);
                }
            }
        }
    }

    /** Collect string values from an annotation's default member or the given named members. */
    static List<String> stringValues(AnnotationExpr annotation, String... memberNames) {
        List<String> values = new ArrayList<>();
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            addStrings(single.getMemberValue(), values);
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            Set<String> wanted = Set.of(memberNames);
            normal.getPairs().stream()
                    .filter(pair -> wanted.contains(pair.getNameAsString()))
                    .forEach(pair -> addStrings(pair.getValue(), values));
        }
        return values;
    }

    private static void addStrings(Expression expression, List<String> sink) {
        if (expression instanceof StringLiteralExpr literal) {
            sink.add(literal.getValue());
        } else if (expression instanceof ArrayInitializerExpr array) {
            array.getValues().forEach(v -> addStrings(v, sink));
        }
    }

    private boolean isGenerated(CompilationUnit cu) {
        Optional<Comment> header = cu.getComment();
        String text = header.map(Comment::getContent).orElse("")
                + cu.getOrphanComments().stream().limit(2).map(Comment::getContent).reduce("", String::concat);
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("generated") && (lower.contains("do not edit") || lower.contains("do not modify")
                || lower.contains("autogenerated") || lower.contains("auto-generated"));
    }

    private static boolean isPackagePrivate(NodeList<Modifier> modifiers) {
        return modifiers.stream().map(Modifier::getKeyword).noneMatch(k ->
                k == Modifier.Keyword.PUBLIC || k == Modifier.Keyword.PRIVATE || k == Modifier.Keyword.PROTECTED);
    }

    private static List<String> modifiers(NodeList<Modifier> modifiers) {
        return modifiers.stream().map(m -> m.getKeyword().asString()).toList();
    }

    private static List<String> annotationNames(NodeWithAnnotations<?> node) {
        return node.getAnnotations().stream().map(ComponentAnalyzer::simpleAnnotationName).toList();
    }

    public static String simpleAnnotationName(AnnotationExpr annotation) {
        return annotation.getName().getIdentifier();
    }

    private static String packageOf(CompilationUnit cu) {
        return cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
    }

    /** Text of a type as written, without type arguments. */
    public static String rawName(ClassOrInterfaceType type) {
        return type.getNameWithScope();
    }

    static boolean isWithin(Node node, Node ancestor) {
        return node.isDescendantOf(ancestor);
    }
}
