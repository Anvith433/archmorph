package com.anvith.archmorph.analysis.transformation.mapping;

import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.module.ModuleCategory;
import com.anvith.archmorph.analysis.transformation.FolderType;

import java.nio.file.Path;

/**
 * Where one top-level class goes: source and target qualified names,
 * module, folder and project-relative target file.
 */
public class TransformationMapping {

    /** Original Java class. */
    private DependencyNode node;

    /** Target module ("shared"/"application" for non-business classes). */
    private String moduleName;

    private ModuleCategory category = ModuleCategory.BUSINESS_MODULE;

    /** Folder inside the module, e.g. controller, service. */
    private FolderType folderType;

    private String sourcePackage;

    /** Common project root package. */
    private String basePackage;

    private String targetPackage;

    /** Destination file relative to the project root (never absolute). */
    private Path targetFile;

    private double confidence = 1.0;

    public DependencyNode getNode() {
        return node;
    }

    public void setNode(DependencyNode node) {
        this.node = node;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public ModuleCategory getCategory() {
        return category;
    }

    public void setCategory(ModuleCategory category) {
        this.category = category;
    }

    public FolderType getFolderType() {
        return folderType;
    }

    public void setFolderType(FolderType folderType) {
        this.folderType = folderType;
    }

    public String getSourcePackage() {
        return sourcePackage;
    }

    public void setSourcePackage(String sourcePackage) {
        this.sourcePackage = sourcePackage;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    public String getTargetPackage() {
        return targetPackage;
    }

    public void setTargetPackage(String targetPackage) {
        this.targetPackage = targetPackage;
    }

    public Path getTargetFile() {
        return targetFile;
    }

    public void setTargetFile(Path targetFile) {
        this.targetFile = targetFile;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getSourceQualifiedName() {
        return node.getQualifiedName();
    }

    public String getTargetQualifiedName() {
        return targetPackage == null || targetPackage.isEmpty()
                ? node.getClassName() : targetPackage + "." + node.getClassName();
    }

    @Override
    public String toString() {
        return getSourceQualifiedName() + " -> " + getTargetQualifiedName() + " [" + moduleName + "/" + folderType + "]";
    }
}
