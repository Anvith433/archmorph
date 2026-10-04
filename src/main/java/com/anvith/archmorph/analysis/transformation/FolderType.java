package com.anvith.archmorph.analysis.transformation;

/** Sub-package inside a module (or inside {@code shared}) that holds one kind of class. */
public enum FolderType {

    CONTROLLER("controller"),

    SERVICE("service"),

    REPOSITORY("repository"),

    ENTITY("entity"),

    DTO("dto"),

    CONFIGURATION("config"),

    SECURITY("security"),

    INFRASTRUCTURE("infrastructure"),

    COMPONENT("component"),

    EXCEPTION("exception"),

    COMMON("common");

    private final String folderName;

    FolderType(String folderName) {
        this.folderName = folderName;
    }

    public String getFolderName() {
        return folderName;
    }
}
