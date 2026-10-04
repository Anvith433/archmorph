package com.anvith.archmorph.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything ArchMorph knows about one Java type declaration.
 *
 * <p>One source file can produce several instances: the primary top-level
 * type, secondary top-level types and nested types. Nested types are always
 * moved together with their enclosing top-level type.</p>
 */
public class ClassMetadata {

    /*
     * Basic Information
     */
    private String className;

    private String packageName;

    /** Canonical name, e.g. {@code com.demo.Outer.Inner}. */
    private String qualifiedName;

    private ComponentType componentType;

    private ComponentClassification classification;

    /*
     * Class Type Information
     */
    private boolean isInterface;

    private TypeKind kind = TypeKind.CLASS;

    private boolean nested;

    /** Canonical name of the enclosing type for nested types. */
    private String outerClassName;

    /** Canonical name of the top-level type that owns the source file position. */
    private String topLevelQualifiedName;

    private boolean publicType;

    private List<String> modifiers = new ArrayList<>();

    private List<String> typeParameters = new ArrayList<>();

    private List<String> nestedTypes = new ArrayList<>();

    /*
     * Java Information
     */
    private String superClass;

    private List<String> interfaces = new ArrayList<>();

    private List<String> annotations = new ArrayList<>();

    /** Annotations on methods (e.g. GetMapping, Bean, ExceptionHandler). */
    private Set<String> methodAnnotations = new LinkedHashSet<>();

    /** Annotations on fields (e.g. Autowired, OneToMany). */
    private Set<String> fieldAnnotations = new LinkedHashSet<>();

    /** Request paths declared by @RequestMapping-style annotations (type + methods). */
    private List<String> endpointPaths = new ArrayList<>();

    /*
     * Source Code Information
     */
    private List<String> imports = new ArrayList<>();

    private List<String> staticImports = new ArrayList<>();

    private List<String> wildcardImports = new ArrayList<>();

    /** Field declarations as written (kept for backwards compatibility). */
    private List<String> fields = new ArrayList<>();

    /** Method names (kept for backwards compatibility). */
    private List<String> methods = new ArrayList<>();

    private List<FieldInfo> fieldDetails = new ArrayList<>();

    private List<MethodInfo> methodDetails = new ArrayList<>();

    private List<MethodInfo> constructors = new ArrayList<>();

    /** Simple names of methods invoked inside the type (for terminology signals). */
    private Set<String> invokedMethods = new LinkedHashSet<>();

    /** String literals that look like package or class names. */
    private Set<String> qualifiedStringLiterals = new LinkedHashSet<>();

    /** Package names given to scanning annotations such as @ComponentScan. */
    private Set<String> scannedPackages = new LinkedHashSet<>();

    private Set<RiskFlag> riskFlags = EnumSet.noneOf(RiskFlag.class);

    /** True when the type declares package-private (default access) members. */
    private boolean hasPackagePrivateMembers;

    /*
     * ============================================
     * Transformation Metadata
     * ============================================
     */

    /** Original Java file (absolute, server-side only — never serialised to clients). */
    private Path sourceFile;

    /** Path relative to the project root, using '/' separators. */
    private String relativePath;

    private SourceScope scope = SourceScope.MAIN;

    private int line;

    /** Parsed AST. */
    private transient CompilationUnit compilationUnit;

    /** Declaration node inside {@link #compilationUnit}. */
    private transient TypeDeclaration<?> declaration;

    public ClassMetadata() {
    }

    /** True when this type is the declaration that lives at file level. */
    public boolean isTopLevel() {
        return !nested;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public ComponentType getComponentType() {
        return componentType;
    }

    public void setComponentType(ComponentType componentType) {
        this.componentType = componentType;
    }

    public ComponentClassification getClassification() {
        return classification;
    }

    public void setClassification(ComponentClassification classification) {
        this.classification = classification;
        if (classification != null) {
            this.componentType = classification.type();
        }
    }

    public boolean isInterface() {
        return isInterface;
    }

    public void setInterface(boolean isInterface) {
        this.isInterface = isInterface;
    }

    public TypeKind getKind() {
        return kind;
    }

    public void setKind(TypeKind kind) {
        this.kind = kind;
    }

    public boolean isNested() {
        return nested;
    }

    public void setNested(boolean nested) {
        this.nested = nested;
    }

    public String getOuterClassName() {
        return outerClassName;
    }

    public void setOuterClassName(String outerClassName) {
        this.outerClassName = outerClassName;
    }

    public String getTopLevelQualifiedName() {
        return topLevelQualifiedName;
    }

    public void setTopLevelQualifiedName(String topLevelQualifiedName) {
        this.topLevelQualifiedName = topLevelQualifiedName;
    }

    public boolean isPublicType() {
        return publicType;
    }

    public void setPublicType(boolean publicType) {
        this.publicType = publicType;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public void setModifiers(List<String> modifiers) {
        this.modifiers = modifiers;
    }

    public List<String> getTypeParameters() {
        return typeParameters;
    }

    public void setTypeParameters(List<String> typeParameters) {
        this.typeParameters = typeParameters;
    }

    public List<String> getNestedTypes() {
        return nestedTypes;
    }

    public void setNestedTypes(List<String> nestedTypes) {
        this.nestedTypes = nestedTypes;
    }

    public String getSuperClass() {
        return superClass;
    }

    public void setSuperClass(String superClass) {
        this.superClass = superClass;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public void setInterfaces(List<String> interfaces) {
        this.interfaces = interfaces;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations;
    }

    public Set<String> getMethodAnnotations() {
        return methodAnnotations;
    }

    public Set<String> getFieldAnnotations() {
        return fieldAnnotations;
    }

    public List<String> getEndpointPaths() {
        return endpointPaths;
    }

    public List<String> getImports() {
        return imports;
    }

    public void setImports(List<String> imports) {
        this.imports = imports;
    }

    public List<String> getStaticImports() {
        return staticImports;
    }

    public List<String> getWildcardImports() {
        return wildcardImports;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }

    public List<FieldInfo> getFieldDetails() {
        return fieldDetails;
    }

    public List<MethodInfo> getMethodDetails() {
        return methodDetails;
    }

    public List<MethodInfo> getConstructors() {
        return constructors;
    }

    public Set<String> getInvokedMethods() {
        return invokedMethods;
    }

    public Set<String> getQualifiedStringLiterals() {
        return qualifiedStringLiterals;
    }

    public Set<String> getScannedPackages() {
        return scannedPackages;
    }

    public Set<RiskFlag> getRiskFlags() {
        return riskFlags;
    }

    public boolean isHasPackagePrivateMembers() {
        return hasPackagePrivateMembers;
    }

    public void setHasPackagePrivateMembers(boolean hasPackagePrivateMembers) {
        this.hasPackagePrivateMembers = hasPackagePrivateMembers;
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(Path sourceFile) {
        this.sourceFile = sourceFile;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public SourceScope getScope() {
        return scope;
    }

    public void setScope(SourceScope scope) {
        this.scope = scope;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    public void setCompilationUnit(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
    }

    public TypeDeclaration<?> getDeclaration() {
        return declaration;
    }

    public void setDeclaration(TypeDeclaration<?> declaration) {
        this.declaration = declaration;
    }

    @Override
    public String toString() {
        return qualifiedName + " (" + componentType + ")";
    }
}
