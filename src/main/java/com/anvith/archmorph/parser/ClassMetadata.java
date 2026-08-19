package com.anvith.archmorph.parser;

import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ClassMetadata {

    /*
     * Basic Information
     */
    private String className;

    private String packageName;

    private ComponentType componentType;

    /*
     * Class Type Information
     */
    private boolean isInterface;

    /*
     * Java Information
     */
    private String superClass;

    private List<String> interfaces = new ArrayList<>();

    private List<String> annotations = new ArrayList<>();

    /*
     * Source Code Information
     */
    private List<String> imports = new ArrayList<>();

    private List<String> fields = new ArrayList<>();

    private List<String> methods = new ArrayList<>();

    /*
     * ============================================
     * Transformation Metadata
     * ============================================
     */

    /*
     * Original Java file
     */
    private Path sourceFile;

    /*
     * Parsed AST
     */
    private CompilationUnit compilationUnit;

    /*
     * Constructor
     */
    public ClassMetadata() {
    }

    /*
     * Basic Information
     */

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

    /*
     * Interface Information
     */

    public boolean isInterface() {
        return isInterface;
    }

    public void setInterface(boolean isInterface) {
        this.isInterface = isInterface;
    }

    /*
     * Java Information
     */

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

    /*
     * Source Code Information
     */

    public List<String> getImports() {
        return imports;
    }

    public void setImports(List<String> imports) {
        this.imports = imports;
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

    /*
     * ============================================
     * Transformation Metadata
     * ============================================
     */

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

}