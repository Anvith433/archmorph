package com.anvith.archmorph.analysis.transformation;

public enum FolderType {

    CONTROLLER("controller"),

    SERVICE("service"),

    REPOSITORY("repository"),

    ENTITY("entity"),

    DTO("dto"),

    CONFIGURATION("config"),

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