package com.anvith.archmorph.analysis.transformation.mapping;

import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.transformation.FolderType;

import java.nio.file.Path;

public class TransformationMapping {

    /*
     * Original Java class.
     */
    private DependencyNode node;

    /*
     * Target business module.
     */
    private String moduleName;

    /*
     * Folder inside the module.
     *
     * Examples:
     * controller
     * service
     * repository
     * entity
     * dto
     */
    private FolderType folderType;

    /*
     * Original Java package.
     */
    private String sourcePackage;

    /*
     * Common project package.
     *
     * Example:
     *
     * Source:
     * com.pmj.template.service
     *
     * Base:
     * com.pmj.template
     */
    private String basePackage;

    /*
     * Destination Java package.
     */
    private String targetPackage;

    /*
     * Destination Java file.
     */
    private Path targetFile;

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

    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder();

        builder.append("----------------------------------------\n");

        builder.append("Class           : ")
                .append(node.getClassName())
                .append("\n");

        builder.append("Module          : ")
                .append(moduleName)
                .append("\n");

        builder.append("Folder          : ")
                .append(folderType)
                .append("\n");

        builder.append("Source Package  : ")
                .append(sourcePackage)
                .append("\n");

        builder.append("Base Package    : ")
                .append(basePackage)
                .append("\n");

        builder.append("Target Package  : ")
                .append(targetPackage)
                .append("\n");

        builder.append("Target File     : ")
                .append(targetFile)
                .append("\n");

        builder.append("----------------------------------------");

        return builder.toString();
    }

}