package com.anvith.archmorph.analysis.module;

import com.anvith.archmorph.analysis.dependency.DependencyNode;

import java.util.LinkedHashSet;
import java.util.Set;

public class ModuleInfo {

    /*
     * Module Name
     */
    private String moduleName;

    /*
     * All classes belonging
     * to this module.
     */
    private final Set<DependencyNode> classes =
            new LinkedHashSet<>();

    public ModuleInfo() {
    }

    public ModuleInfo(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public Set<DependencyNode> getClasses() {
        return classes;
    }

    public void addClass(DependencyNode node) {

        if (node != null) {
            classes.add(node);
        }

    }

    /**
 * Does this module contain the node?
 */
public boolean contains(DependencyNode node) {

    return classes.contains(node);

}

/**
 * Number of classes.
 */
public int getClassCount() {

    return classes.size();

}


/**
 * Is module empty?
 */
public boolean isEmpty() {

    return classes.isEmpty();

}

    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder();

        builder.append(moduleName)
                .append("\n");

        for (DependencyNode node : classes) {

            builder.append("   - ")
                    .append(node.getClassName())
                    .append("\n");

        }

        return builder.toString();
    }

}