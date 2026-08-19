package com.anvith.archmorph.analysis.dependency;

import com.anvith.archmorph.parser.ComponentType;
import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents one Java class/interface
 * in the dependency graph.
 */
public class DependencyNode {

    /*
     * Class Name
     */
    private String className;

    /*
     * Package Name
     */
    private String packageName;

    /*
     * Component Type
     */
    private ComponentType componentType;

    /*
     * NEW
     * Original Java source file.
     */
    private Path sourceFile;

    /*
     * NEW
     * Parsed AST.
     * This avoids reparsing later.
     */
    private CompilationUnit compilationUnit;

    public DependencyNode() {
    }

    public DependencyNode(
            String className,
            String packageName,
            ComponentType componentType) {

        this.className = className;
        this.packageName = packageName;
        this.componentType = componentType;
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

    public ComponentType getComponentType() {
        return componentType;
    }

    public void setComponentType(ComponentType componentType) {
        this.componentType = componentType;
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

    public void setCompilationUnit(
            CompilationUnit compilationUnit) {

        this.compilationUnit = compilationUnit;
    }

    @Override
    public String toString() {

        return className +
                " (" +
                componentType +
                ")";
    }

    @Override
    public boolean equals(Object o) {

        if (this == o)
            return true;

        if (!(o instanceof DependencyNode))
            return false;

        DependencyNode that = (DependencyNode) o;

        return Objects.equals(className, that.className);
    }

    @Override
    public int hashCode() {

        return Objects.hash(className);
    }

}