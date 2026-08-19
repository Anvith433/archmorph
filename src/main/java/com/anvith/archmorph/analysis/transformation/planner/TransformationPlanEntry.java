package com.anvith.archmorph.analysis.transformation.planner;

import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.transformation.mapping.TransformationMapping;

import java.nio.file.Path;

public class TransformationPlanEntry {

    /*
     * Original class being transformed.
     */
    private DependencyNode node;

    /*
     * Mapping used to generate this entry.
     */
    private TransformationMapping mapping;

    /*
     * Original Java file.
     */
    private Path sourceFile;

    /*
     * Destination Java file.
     */
    private Path targetFile;

    /*
     * Original package.
     */
    private String sourcePackage;

    /*
     * New package.
     */
    private String targetPackage;

    public DependencyNode getNode() {
        return node;
    }

    public void setNode(DependencyNode node) {
        this.node = node;
    }

    public TransformationMapping getMapping() {
        return mapping;
    }

    public void setMapping(TransformationMapping mapping) {
        this.mapping = mapping;
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(Path sourceFile) {
        this.sourceFile = sourceFile;
    }

    public Path getTargetFile() {
        return targetFile;
    }

    public void setTargetFile(Path targetFile) {
        this.targetFile = targetFile;
    }

    public String getSourcePackage() {
        return sourcePackage;
    }

    public void setSourcePackage(String sourcePackage) {
        this.sourcePackage = sourcePackage;
    }

    public String getTargetPackage() {
        return targetPackage;
    }

    public void setTargetPackage(String targetPackage) {
        this.targetPackage = targetPackage;
    }

    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder();

        builder.append("====================================\n");

        if (node != null) {
            builder.append("Class           : ")
                    .append(node.getClassName())
                    .append("\n");
        }

        builder.append("Source File     : ")
                .append(sourceFile)
                .append("\n");

        builder.append("Target File     : ")
                .append(targetFile)
                .append("\n");

        builder.append("Source Package  : ")
                .append(sourcePackage)
                .append("\n");

        builder.append("Target Package  : ")
                .append(targetPackage)
                .append("\n");

        if (mapping != null) {

            builder.append("Module          : ")
                    .append(mapping.getModuleName())
                    .append("\n");

            builder.append("Folder          : ")
                    .append(mapping.getFolderType())
                    .append("\n");
        }

        builder.append("====================================");

        return builder.toString();
    }

}