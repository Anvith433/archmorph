package com.anvith.archmorph.analysis.dependency;

import com.anvith.archmorph.parser.ComponentType;
import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents one top-level Java type in the dependency graph.
 *
 * <p>Identity is the fully-qualified name. Two classes with the same simple
 * name in different packages are different nodes.</p>
 */
public class DependencyNode {

    private String className;

    private String packageName;

    private String qualifiedName;

    private ComponentType componentType;

    private double classificationConfidence;

    /** True for types outside the project (framework / JDK). */
    private boolean external;

    /** Project-relative source path. */
    private String relativePath;

    /** Original Java source file (server-side only). */
    private Path sourceFile;

    /** Parsed AST. This avoids reparsing later. */
    private transient CompilationUnit compilationUnit;

    public DependencyNode() {
    }

    public DependencyNode(String className, String packageName, ComponentType componentType) {
        this.className = className;
        this.packageName = packageName;
        this.componentType = componentType;
        this.qualifiedName = packageName == null || packageName.isBlank() ? className : packageName + "." + className;
    }

    public DependencyNode(String qualifiedName, String className, String packageName, ComponentType componentType) {
        this.qualifiedName = qualifiedName;
        this.className = className;
        this.packageName = packageName;
        this.componentType = componentType;
    }

    /** Identity key: qualified name, or simple name for legacy nodes. */
    public String getId() {
        return qualifiedName != null ? qualifiedName : className;
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

    public double getClassificationConfidence() {
        return classificationConfidence;
    }

    public void setClassificationConfidence(double classificationConfidence) {
        this.classificationConfidence = classificationConfidence;
    }

    public boolean isExternal() {
        return external;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(Path sourceFile) {
        this.sourceFile = sourceFile;
    }

    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    public void setCompilationUnit(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
    }

    @Override
    public String toString() {
        return className + " (" + componentType + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DependencyNode that)) {
            return false;
        }
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
