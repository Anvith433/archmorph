package com.anvith.archmorph.analysis.transformation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ModuleLayout {

    private String moduleName;

    /*
     * Folder -> Classes
     */
    private final Map<FolderType, Set<String>> folders =
            new LinkedHashMap<>();

    public ModuleLayout() {
    }

    public ModuleLayout(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public void addClass(
            FolderType folder,
            String className) {

        folders.computeIfAbsent(
                folder,
                f -> new LinkedHashSet<>())
                .add(className);
    }

    public Map<FolderType, Set<String>> getFolders() {
        return folders;
    }

}